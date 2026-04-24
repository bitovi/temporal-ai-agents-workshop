#!/usr/bin/env node
/**
 * Push a single markdown file to an existing Confluence page.
 *
 * Usage:
 *   node scripts/push-to-confluence.mjs <markdown-file> <page-id>
 *   node scripts/push-to-confluence.mjs <markdown-file> <page-id> --dry-run
 *
 * Examples:
 *   node scripts/push-to-confluence.mjs 0-environment-setup/README.md 2187132935
 *   node scripts/push-to-confluence.mjs 0-environment-setup/README.md 2187132935 --dry-run
 *
 * Environment variables (from .env):
 *   CONFLUENCE_BASE_URL    - Base URL of your Confluence instance (e.g. https://bitovi.atlassian.net/wiki)
 *   ATLASSIAN_EMAIL        - Your Atlassian account email
 *   ATLASSIAN_API_TOKEN    - Your Atlassian API token
 */

import fs from "fs";
import path from "path";
import { execSync } from "child_process";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..");

// ---------------------------------------------------------------------------
// Config
// ---------------------------------------------------------------------------

loadEnv(path.join(ROOT, ".env"));

const ATLASSIAN_EMAIL = process.env.ATLASSIAN_EMAIL;
const ATLASSIAN_API_TOKEN = process.env.ATLASSIAN_API_TOKEN;
const CONFLUENCE_BASE_URL = process.env.CONFLUENCE_BASE_URL;

// CLI args
const args = process.argv.slice(2);
const DRY_RUN = args.includes("--dry-run");
const positional = args.filter((a) => !a.startsWith("--"));

if (positional.length < 2) {
  console.error(
    "Usage: node scripts/push-to-confluence.mjs <markdown-file> <page-id> [--dry-run]"
  );
  process.exit(1);
}

const markdownFile = path.resolve(positional[0]);
const PAGE_ID = positional[1];

if (!fs.existsSync(markdownFile)) {
  console.error(`File not found: ${markdownFile}`);
  process.exit(1);
}

if (!/^\d+$/.test(PAGE_ID)) {
  console.error(`Invalid page ID: "${PAGE_ID}". Expected a numeric ID.`);
  process.exit(1);
}

if (!CONFLUENCE_BASE_URL) {
  console.error("Missing CONFLUENCE_BASE_URL in .env (e.g. https://bitovi.atlassian.net/wiki)");
  process.exit(1);
}

// Strip trailing /wiki if present — we add it back in API paths
const ATLASSIAN_BASE = CONFLUENCE_BASE_URL.replace(/\/wiki\/?$/, "");

if (!ATLASSIAN_EMAIL || !ATLASSIAN_API_TOKEN) {
  console.error(
    "Missing ATLASSIAN_EMAIL or ATLASSIAN_API_TOKEN in .env (or environment)"
  );
  process.exit(1);
}

const AUTH_HEADER = `Basic ${Buffer.from(
  `${ATLASSIAN_EMAIL}:${ATLASSIAN_API_TOKEN}`
).toString("base64")}`;

if (DRY_RUN) console.log("[DRY RUN] No pages will be modified.\n");

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

async function main() {
  console.log(`→ Markdown: ${markdownFile}`);
  console.log(`→ Confluence page: ${PAGE_ID} (${ATLASSIAN_BASE})`);

  const raw = fs.readFileSync(markdownFile, "utf8");
  const markdownDir = path.dirname(markdownFile);

  // Parse the markdown — resolve images relative to the markdown file
  const { markdown, imagePaths, skippedElements } = parseMarkdown(
    raw,
    markdownDir
  );

  if (skippedElements.length) {
    console.log(`  Skipped elements: ${skippedElements.length}`);
    skippedElements.forEach((s) => console.log(`    - ${s.element}: ${s.reason}`));
  }

  // Convert to ADF
  const adf = markdownToAdf(markdown);

  if (DRY_RUN) {
    console.log(`\n[DRY RUN] Would update page ${PAGE_ID}`);
    console.log(`  Images to upload: ${imagePaths.length}`);
    if (imagePaths.length) {
      imagePaths.forEach((p) => console.log(`    - ${p}`));
    }
    console.log("\nGenerated ADF:");
    console.log(JSON.stringify(adf, null, 2));
    return;
  }

  // Update the page content
  await updatePageContent(PAGE_ID, adf);
  console.log(`  ✓ Page updated: ${PAGE_ID}`);

  // Upload images and update page with attachment references
  if (imagePaths.length > 0) {
    const attachmentMap = await uploadImages(PAGE_ID, imagePaths);
    if (Object.keys(attachmentMap).length > 0) {
      const updatedAdf = replaceImageRefsInAdf(adf, attachmentMap);
      await updatePageContent(PAGE_ID, updatedAdf);
      console.log(
        `  ✓ Updated page with ${Object.keys(attachmentMap).length} image(s)`
      );
    }
  }

  console.log("\n✓ Done.");
}

// ---------------------------------------------------------------------------
// Markdown parser
// ---------------------------------------------------------------------------

function parseMarkdown(raw, baseDir) {
  const imagePaths = [];
  const skippedElements = [];
  const lines = raw.split("\n");
  const processedLines = [];

  let i = 0;
  while (i < lines.length) {
    const line = lines[i];

    // <img src="..."> tags — collect image, replace with markdown image
    if (
      line.trim().startsWith("<img ") ||
      line.trim().startsWith("<img\t")
    ) {
      const imgLines = [line];
      let j = i;
      while (
        j < lines.length &&
        !imgLines.join("").includes("/>") &&
        !imgLines.join("").includes("</img>")
      ) {
        j++;
        if (j < lines.length) imgLines.push(lines[j]);
      }
      const imgTag = imgLines.join(" ");
      const srcMatch = imgTag.match(/src="([^"]+)"/);
      const altMatch = imgTag.match(/alt="([^"]*)"/);
      if (srcMatch) {
        const localPath = resolveImgPath(srcMatch[1], baseDir);
        if (localPath && fs.existsSync(localPath)) {
          imagePaths.push(localPath);
          const alt =
            altMatch?.[1] ||
            path.basename(localPath, path.extname(localPath));
          processedLines.push(
            `![${alt}](ATTACHMENT:${encodeURIComponent(localPath)})`
          );
        } else {
          processedLines.push(imgTag.trim());
          skippedElements.push({
            line: i + 1,
            element: "img",
            reason: `Local file not found: ${srcMatch[1]}`,
          });
        }
      }
      i = j + 1;
      continue;
    }

    // Markdown images ![alt](path) — resolve to local paths for upload
    const mdImgMatch = line.match(/^!\[([^\]]*)\]\(([^)]+)\)$/);
    if (mdImgMatch) {
      const alt = mdImgMatch[1];
      const src = mdImgMatch[2];
      // Skip external URLs
      if (src.startsWith("http://") || src.startsWith("https://")) {
        processedLines.push(line);
        i++;
        continue;
      }
      const localPath = resolveImgPath(src, baseDir);
      if (localPath && fs.existsSync(localPath)) {
        imagePaths.push(localPath);
        processedLines.push(
          `![${alt}](ATTACHMENT:${encodeURIComponent(localPath)})`
        );
      } else {
        processedLines.push(line);
        skippedElements.push({
          line: i + 1,
          element: "img",
          reason: `Local file not found: ${src}`,
        });
      }
      i++;
      continue;
    }

    // Inline markdown images within a line (not at start)
    if (line.includes("![") && line.includes("](") && !line.startsWith("![")) {
      let processed = line;
      const inlineImgRegex = /!\[([^\]]*)\]\(([^)]+)\)/g;
      let inlineMatch;
      while ((inlineMatch = inlineImgRegex.exec(line)) !== null) {
        const src = inlineMatch[2];
        if (src.startsWith("http://") || src.startsWith("https://")) continue;
        const localPath = resolveImgPath(src, baseDir);
        if (localPath && fs.existsSync(localPath)) {
          imagePaths.push(localPath);
          processed = processed.replace(
            inlineMatch[0],
            `![${inlineMatch[1]}](ATTACHMENT:${encodeURIComponent(localPath)})`
          );
        }
      }
      processedLines.push(processed);
      i++;
      continue;
    }

    processedLines.push(line);
    i++;
  }

  return {
    markdown: processedLines.join("\n"),
    imagePaths: [...new Set(imagePaths)],
    skippedElements,
  };
}

function resolveImgPath(src, baseDir) {
  // Handle relative paths
  if (src.startsWith("./") || src.startsWith("../") || !src.startsWith("/")) {
    return path.resolve(baseDir, src);
  }
  // Absolute paths relative to project root
  return path.join(ROOT, src);
}

// ---------------------------------------------------------------------------
// Markdown → ADF converter
// ---------------------------------------------------------------------------

function markdownToAdf(markdown) {
  const lines = markdown.split("\n");
  const content = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    // Blank line
    if (line.trim() === "") {
      i++;
      continue;
    }

    // Embedded ADF node
    if (line.startsWith("%%ADF_NODE%%")) {
      try {
        content.push(JSON.parse(line.slice(12)));
      } catch {
        /* ignore malformed */
      }
      i++;
      continue;
    }

    // Fenced code block
    if (line.startsWith("```")) {
      const lang = line.slice(3).trim();
      const codeLines = [];
      i++;
      while (i < lines.length && !lines[i].startsWith("```")) {
        codeLines.push(lines[i]);
        i++;
      }
      i++; // consume closing ```
      content.push({
        type: "codeBlock",
        attrs: { language: lang || null },
        content: [{ type: "text", text: codeLines.join("\n") }],
      });
      continue;
    }

    // Heading
    const headingMatch = line.match(/^(#{1,6})\s+(.+)$/);
    if (headingMatch) {
      const level = headingMatch[1].length;
      content.push({
        type: "heading",
        attrs: { level },
        content: parseInline(headingMatch[2]),
      });
      i++;
      continue;
    }

    // Horizontal rule
    if (
      line.match(/^---+$/) ||
      line.match(/^\*\*\*+$/) ||
      line.match(/^___+$/)
    ) {
      content.push({ type: "rule" });
      i++;
      continue;
    }

    // Blockquote
    if (line.startsWith("> ")) {
      const quoteLines = [];
      while (i < lines.length && lines[i].startsWith("> ")) {
        quoteLines.push(lines[i].slice(2));
        i++;
      }
      content.push({
        type: "blockquote",
        content: [
          {
            type: "paragraph",
            content: parseInline(quoteLines.join(" ")),
          },
        ],
      });
      continue;
    }

    // Unordered list
    if (line.match(/^(\s*)[-*+]\s/)) {
      const baseIndent = line.match(/^(\s*)/)[1].length;
      const result = parseList(lines, i, false, baseIndent);
      content.push(result.node);
      i += Math.max(1, result.consumed);
      continue;
    }

    // Ordered list
    if (line.match(/^(\s*)\d+\.\s/)) {
      const baseIndent = line.match(/^(\s*)/)[1].length;
      const result = parseList(lines, i, true, baseIndent);
      content.push(result.node);
      i += Math.max(1, result.consumed);
      continue;
    }

    // Table
    if (line.includes("|") && line.trim().startsWith("|")) {
      const result = parseTable(lines, i);
      if (result) {
        content.push(result.node);
        i += result.consumed;
        continue;
      }
    }

    // Image (markdown format — block level)
    const imgMatch = line.match(/^!\[([^\]]*)\]\(([^)]+)\)$/);
    if (imgMatch) {
      const alt = imgMatch[1];
      const src = imgMatch[2];
      content.push({
        type: "mediaSingle",
        attrs: { layout: "center" },
        content: [
          {
            type: "media",
            attrs: {
              type: "external",
              url: src,
              alt,
              __localPath: src.startsWith("ATTACHMENT:")
                ? decodeURIComponent(src.slice(11))
                : null,
            },
          },
        ],
      });
      i++;
      continue;
    }

    // Paragraph (default)
    const paraLines = [line.trim()];
    i++;
    while (i < lines.length) {
      const next = lines[i];
      if (next.trim() === "") break;
      if (next.startsWith("```")) break;
      if (next.match(/^#{1,6}\s/)) break;
      if (
        next.match(/^---+$/) ||
        next.match(/^\*\*\*+$/) ||
        next.match(/^___+$/)
      )
        break;
      if (next.startsWith("> ")) break;
      if (next.match(/^(\s*)[-*+]\s/)) break;
      if (next.match(/^(\s*)\d+\.\s/)) break;
      if (next.includes("|") && next.trim().startsWith("|")) break;
      if (next.match(/^!\[/)) break;
      if (next.trim().startsWith("<")) break;
      if (next.startsWith("%%ADF_NODE%%")) break;
      paraLines.push(next.trim());
      i++;
    }
    const paraText = paraLines.join(" ");
    if (paraText) {
      content.push({
        type: "paragraph",
        content: parseInline(paraText),
      });
    }
  }

  return { version: 1, type: "doc", content };
}

// ---------------------------------------------------------------------------
// Inline parser
// ---------------------------------------------------------------------------

function parseInline(text) {
  if (!text) return [];
  const nodes = [];

  // Tokenize: ![alt](url), [text](url), `code`, **bold**, *italic*, __bold__, _italic_
  const regex =
    /!\[([^\]]*)\]\(([^)]+)\)|\[([^\]]*)\]\(([^)]+)\)|`([^`]+)`|\*\*([^*]+)\*\*|\*([^*]+)\*|__([^_]+)__|_([^_]+)_/g;

  let lastIndex = 0;
  let match;

  while ((match = regex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      nodes.push({ type: "text", text: text.slice(lastIndex, match.index) });
    }

    if (match[1] !== undefined) {
      // Inline image ![alt](url) — emit as text (images handled at block level)
      nodes.push({ type: "text", text: match[1] || match[2] });
    } else if (match[3] !== undefined) {
      // Link [text](url)
      nodes.push({
        type: "text",
        text: match[3],
        marks: [{ type: "link", attrs: { href: match[4] } }],
      });
    } else if (match[5] !== undefined) {
      // `code`
      nodes.push({
        type: "text",
        text: match[5],
        marks: [{ type: "code" }],
      });
    } else if (match[6] !== undefined) {
      // **bold**
      nodes.push({
        type: "text",
        text: match[6],
        marks: [{ type: "strong" }],
      });
    } else if (match[7] !== undefined || match[9] !== undefined) {
      // *italic* or _italic_
      nodes.push({
        type: "text",
        text: match[7] || match[9],
        marks: [{ type: "em" }],
      });
    } else if (match[8] !== undefined) {
      // __bold__
      nodes.push({
        type: "text",
        text: match[8],
        marks: [{ type: "strong" }],
      });
    }

    lastIndex = regex.lastIndex;
  }

  if (lastIndex < text.length) {
    nodes.push({ type: "text", text: text.slice(lastIndex) });
  }

  return nodes.length > 0 ? nodes : [{ type: "text", text }];
}

// ---------------------------------------------------------------------------
// List parser
// ---------------------------------------------------------------------------

function parseList(lines, startIndex, ordered, baseIndent = 0) {
  const items = [];
  let i = startIndex;

  while (i < lines.length) {
    const line = lines[i];
    const listMatch = line.match(/^(\s*)([-*+]|\d+\.)\s+(.*)$/);
    if (!listMatch) break;

    const indent = listMatch[1].length;
    if (indent < baseIndent) break;
    if (indent > baseIndent) break;

    const marker = listMatch[2];
    const text = listMatch[3];
    const contIndent = indent + marker.length + 1;
    i++;

    const continuationBlocks = [];

    while (i < lines.length) {
      const cur = lines[i];

      if (cur.trim() === "") {
        let j = i + 1;
        while (j < lines.length && lines[j].trim() === "") j++;
        if (j >= lines.length) {
          i = j;
          break;
        }
        const afterIndent = lines[j].match(/^(\s*)/)[1].length;
        if (
          afterIndent === indent &&
          lines[j].match(/^(\s*)([-*+]|\d+\.)\s/)
        ) {
          i = j;
          break;
        }
        if (afterIndent < contIndent) {
          i = j;
          break;
        }
        i = j;
        continue;
      }

      const curIndent = cur.match(/^(\s*)/)[1].length;
      if (curIndent < contIndent) break;

      // Sub-list
      const subMatch = cur.match(/^(\s*)([-*+]|\d+\.)\s/);
      if (subMatch && subMatch[1].length >= contIndent) {
        const isOrdered = /^\d+\./.test(subMatch[2]);
        const result = parseList(lines, i, isOrdered, subMatch[1].length);
        continuationBlocks.push(result.node);
        i += result.consumed;
        continue;
      }

      // Fenced code block
      const dedented =
        cur.length >= contIndent ? cur.slice(contIndent) : cur.trimStart();
      if (dedented.startsWith("```")) {
        const lang = dedented.slice(3).trim();
        const codeLines = [];
        i++;
        while (i < lines.length) {
          const cl = lines[i];
          const dl =
            cl.length >= contIndent ? cl.slice(contIndent) : cl.trimStart();
          if (dl.startsWith("```")) {
            i++;
            break;
          }
          codeLines.push(dl);
          i++;
        }
        continuationBlocks.push({
          type: "codeBlock",
          attrs: { language: lang || null },
          content: [{ type: "text", text: codeLines.join("\n") }],
        });
        continue;
      }

      // Continuation paragraph
      const paraLines = [];
      while (i < lines.length) {
        const pl = lines[i];
        if (pl.trim() === "") break;
        const plIndent = pl.match(/^(\s*)/)[1].length;
        if (plIndent < contIndent) break;
        if (pl.match(/^(\s*)([-*+]|\d+\.)\s/) && plIndent >= contIndent) break;
        paraLines.push(pl.slice(contIndent));
        i++;
      }
      if (paraLines.length > 0) {
        continuationBlocks.push({
          type: "paragraph",
          content: parseInline(paraLines.join(" ")),
        });
      }
    }

    const itemContent = [{ type: "paragraph", content: parseInline(text) }];
    itemContent.push(...continuationBlocks);
    items.push({ type: "listItem", content: itemContent });
  }

  return {
    node: {
      type: ordered ? "orderedList" : "bulletList",
      content: items,
    },
    consumed: Math.max(1, i - startIndex),
  };
}

// ---------------------------------------------------------------------------
// Table parser
// ---------------------------------------------------------------------------

function parseTable(lines, startIndex) {
  let i = startIndex;
  const rows = [];

  while (i < lines.length) {
    const line = lines[i].trim();
    if (!line.startsWith("|")) break;
    if (line.match(/^\|[\s\-|:]+\|$/)) {
      i++;
      continue;
    }
    const cells = line
      .split("|")
      .slice(1, -1)
      .map((c) => c.trim());
    rows.push(cells);
    i++;
  }

  if (rows.length === 0) return null;

  const tableRows = rows.map((cells, rowIndex) => ({
    type: "tableRow",
    content: cells.map((cell) => ({
      type: rowIndex === 0 ? "tableHeader" : "tableCell",
      attrs: {},
      content: [{ type: "paragraph", content: parseInline(cell) }],
    })),
  }));

  return {
    node: {
      type: "table",
      attrs: { isNumberColumnEnabled: false, layout: "default" },
      content: tableRows,
    },
    consumed: i - startIndex,
  };
}

// ---------------------------------------------------------------------------
// Image handling
// ---------------------------------------------------------------------------

function getImageDimensions(filePath) {
  try {
    const out = execSync(
      `sips -g pixelWidth -g pixelHeight "${filePath}" 2>/dev/null`,
      { encoding: "utf8" }
    );
    const w = out.match(/pixelWidth:\s*(\d+)/)?.[1];
    const h = out.match(/pixelHeight:\s*(\d+)/)?.[1];
    if (w && h) return { width: parseInt(w, 10), height: parseInt(h, 10) };
  } catch (_) {}
  return {};
}

function replaceImageRefsInAdf(adf, attachmentMap) {
  return transformAdfNodes(adf, (node) => {
    if (node.type === "mediaSingle") {
      const mediaChild = node.content?.[0];
      if (mediaChild?.type === "media" && mediaChild.attrs?.__localPath) {
        const localPath = mediaChild.attrs.__localPath;
        const attachmentInfo = attachmentMap[localPath];
        if (attachmentInfo) {
          const mediaAttrs = {
            id: attachmentInfo.mediaId,
            type: "file",
            collection: attachmentInfo.collectionName,
            alt: mediaChild.attrs.alt || "",
          };
          if (attachmentInfo.width) mediaAttrs.width = attachmentInfo.width;
          if (attachmentInfo.height) mediaAttrs.height = attachmentInfo.height;
          const displayWidth = attachmentInfo.width
            ? Math.min(attachmentInfo.width, 760)
            : 760;
          return {
            ...node,
            attrs: { ...node.attrs, width: displayWidth, widthType: "pixel" },
            content: [{ type: "media", attrs: mediaAttrs }],
          };
        }
      }
    }
    return node;
  });
}

function transformAdfNodes(adf, transformer) {
  function transformNode(node) {
    const transformed = transformer(node);
    if (transformed.content) {
      return {
        ...transformed,
        content: transformed.content.map(transformNode),
      };
    }
    return transformed;
  }
  return { ...adf, content: adf.content.map(transformNode) };
}

// ---------------------------------------------------------------------------
// Confluence API
// ---------------------------------------------------------------------------

async function confluenceRequest(method, apiPath, body = null, isFormData = false) {
  const url = `${ATLASSIAN_BASE}${apiPath}`;
  const headers = {
    Authorization: AUTH_HEADER,
    Accept: "application/json",
  };
  if (body && !isFormData) {
    headers["Content-Type"] = "application/json";
  }
  if (isFormData) {
    headers["X-Atlassian-Token"] = "no-check";
  }

  const options = {
    method,
    headers,
    body: body ? (isFormData ? body : JSON.stringify(body)) : undefined,
  };

  const res = await fetch(url, options);

  if (!res.ok) {
    const text = await res.text();
    throw new Error(
      `Confluence API ${method} ${apiPath} → ${res.status}: ${text.slice(0, 500)}`
    );
  }

  const contentType = res.headers.get("content-type") || "";
  if (contentType.includes("application/json")) {
    return res.json();
  }
  return res.text();
}

async function updatePageContent(pageId, adf) {
  const existing = await confluenceRequest(
    "GET",
    `/wiki/rest/api/content/${pageId}?expand=version,title`
  );
  const body = {
    type: "page",
    title: existing.title,
    version: { number: existing.version.number + 1 },
    body: {
      atlas_doc_format: {
        value: JSON.stringify(adf),
        representation: "atlas_doc_format",
      },
    },
  };
  await confluenceRequest("PUT", `/wiki/rest/api/content/${pageId}`, body);
}

async function uploadImages(pageId, imagePaths) {
  const attachmentMap = {};

  for (const localPath of imagePaths) {
    if (!fs.existsSync(localPath)) {
      console.warn(`  [WARN] Image not found: ${localPath}`);
      continue;
    }

    const filename = path.basename(localPath);
    const ext = path.extname(localPath).toLowerCase();
    const mimeTypes = {
      ".png": "image/png",
      ".jpg": "image/jpeg",
      ".jpeg": "image/jpeg",
      ".gif": "image/gif",
      ".svg": "image/svg+xml",
    };
    const mimeType = mimeTypes[ext] || "application/octet-stream";

    try {
      let attachmentId = null;
      let mediaId = null;
      let collectionName = `contentId-${pageId}`;

      // Check if attachment exists already
      try {
        const existing = await confluenceRequest(
          "GET",
          `/wiki/rest/api/content/${pageId}/child/attachment?filename=${encodeURIComponent(filename)}`
        );
        if (existing.results?.length > 0) {
          attachmentId = existing.results[0].id;
          mediaId =
            existing.results[0].extensions?.fileId || attachmentId;
          collectionName =
            existing.results[0].extensions?.collectionName || collectionName;
          console.log(`  ↑ Attachment exists: ${filename}`);
        }
      } catch (_) {}

      if (!attachmentId) {
        const fileContent = fs.readFileSync(localPath);
        const formData = new FormData();
        formData.append(
          "file",
          new Blob([fileContent], { type: mimeType }),
          filename
        );
        formData.append("minorEdit", "true");

        const result = await confluenceRequest(
          "POST",
          `/wiki/rest/api/content/${pageId}/child/attachment`,
          formData,
          true
        );

        attachmentId = result.results?.[0]?.id;
        mediaId = result.results?.[0]?.extensions?.fileId || attachmentId;
        collectionName =
          result.results?.[0]?.extensions?.collectionName || collectionName;
        console.log(`  ↑ Uploaded: ${filename}`);
      }

      const imgDims = getImageDimensions(localPath);
      attachmentMap[localPath] = {
        attachmentId,
        mediaId,
        collectionName,
        ...imgDims,
      };
    } catch (e) {
      console.error(`  ✗ Failed to upload ${filename}: ${e.message}`);
    }
  }

  return attachmentMap;
}

// ---------------------------------------------------------------------------
// Env loader
// ---------------------------------------------------------------------------

function loadEnv(envPath) {
  if (!fs.existsSync(envPath)) return;
  const lines = fs.readFileSync(envPath, "utf8").split("\n");
  for (const line of lines) {
    const match = line.match(/^([^#=]+)=(.*)$/);
    if (match) {
      const key = match[1].trim();
      const value = match[2].trim().replace(/^['"]|['"]$/g, "");
      if (!process.env[key]) process.env[key] = value;
    }
  }
}

// ---------------------------------------------------------------------------
// Run
// ---------------------------------------------------------------------------

main().catch((e) => {
  console.error("Fatal error:", e);
  process.exit(1);
});
