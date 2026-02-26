import { Tool } from '@aws-sdk/client-bedrock-runtime';

// Truncation constants for token optimization
const MAX_SEARCH_RESULTS = 10;
const MAX_TEXT_FIELD_LENGTH = 500;
const MAX_ARRAY_ITEMS = 3;

export interface GutendexSearchParams {
  search?: string;
  topic?: string;
  languages?: string;
  author_year_start?: number;
  author_year_end?: number;
  sort?: string;
}

export interface GutendexBookIdParams {
  id: number;
}

/**
 * Get Gutendex API tool definitions for Bedrock.
 * 
 * @returns Array of Tool specifications for Bedrock's tool calling API
 */
export function getGutendexTools(): Tool[] {
  return [
    {
      toolSpec: {
        name: 'search_books',
        description: 'Search for books in the Project Gutenberg catalog by title, author, topic, language, or other filters',
        inputSchema: {
          json: {
            type: 'object',
            properties: {
              search: {
                type: 'string',
                description: 'Search terms for author names and book titles',
              },
              topic: {
                type: 'string',
                description: 'Key-phrase in bookshelves or subjects (e.g., "children", "science fiction")',
              },
              languages: {
                type: 'string',
                description: 'Comma-separated language codes (e.g., "en", "fr,es")',
              },
              author_year_start: {
                type: 'number',
                description: 'Authors alive after this year',
              },
              author_year_end: {
                type: 'number',
                description: 'Authors alive before this year',
              },
              sort: {
                type: 'string',
                description: 'Sort order - "ascending", "descending", or "popular"',
                enum: ['ascending', 'descending', 'popular'],
              },
            },
          },
        },
      },
    },
    {
      toolSpec: {
        name: 'get_book_by_id',
        description: 'Get detailed information about a specific book by its Project Gutenberg ID',
        inputSchema: {
          json: {
            type: 'object',
            properties: {
              id: {
                type: 'number',
                description: 'The Project Gutenberg book ID',
              },
            },
            required: ['id'],
          },
        },
      },
    },
  ];
}

/**
 * Truncate text to maximum length with ellipsis
 */
function truncateText(text: string, maxLength: number): string {
  if (!text || text.length <= maxLength) return text;
  return text.substring(0, maxLength - 3) + '...';
}

/**
 * Truncate array to maximum items
 */
function truncateArray<T>(arr: T[], maxItems: number): T[] {
  if (!arr || arr.length <= maxItems) return arr;
  return arr.slice(0, maxItems);
}

/**
 * Execute search_books tool - search Project Gutenberg catalog
 * 
 * @param params Search parameters
 * @returns JSON string with search results (truncated for token optimization)
 */
export async function executeSearchBooks(params: GutendexSearchParams): Promise<string> {
  try {
    const baseUrl = 'https://gutendex.com/books';
    const queryParams = new URLSearchParams();

    // Build query parameters
    if (params.search) queryParams.append('search', params.search);
    if (params.topic) queryParams.append('topic', params.topic);
    if (params.languages) queryParams.append('languages', params.languages);
    if (params.author_year_start) queryParams.append('author_year_start', params.author_year_start.toString());
    if (params.author_year_end) queryParams.append('author_year_end', params.author_year_end.toString());
    if (params.sort) queryParams.append('sort', params.sort);

    const url = `${baseUrl}?${queryParams.toString()}`;
    console.log(`[Gutendex] Searching: ${url}`);

    const response = await fetch(url);
    
    if (!response.ok) {
      return JSON.stringify({
        error: `Search failed with status ${response.status}: ${response.statusText}`
      });
    }

    const data = await response.json() as any;
    
    // Truncate results to MAX_SEARCH_RESULTS
    const truncatedResults = truncateArray(data.results || [], MAX_SEARCH_RESULTS).map((book: any) => ({
      id: book.id,
      title: truncateText(book.title, MAX_TEXT_FIELD_LENGTH),
      authors: truncateArray(book.authors || [], MAX_ARRAY_ITEMS).map((author: any) => ({
        name: author.name,
      })),
      subjects: truncateArray(book.subjects || [], MAX_ARRAY_ITEMS).map((subject: any) =>
        truncateText(subject, MAX_TEXT_FIELD_LENGTH)
      ),
      languages: book.languages || [],
      download_count: book.download_count,
    }));

    const result: any = {
      count: data.count || 0,
      results: truncatedResults,
    };

    // Add note if results were truncated
    if (data.count > MAX_SEARCH_RESULTS) {
      result.note = `Showing first ${MAX_SEARCH_RESULTS} of ${data.count} results`;
    }

    return JSON.stringify(result);
  } catch (error) {
    console.error('[Gutendex] Search error:', error);
    return JSON.stringify({
      error: `Could not search books: ${error instanceof Error ? error.message : 'Unknown error'}`
    });
  }
}

/**
 * Execute get_book_by_id tool - get detailed information about a specific book
 * 
 * @param params Book ID parameter
 * @returns JSON string with book details (truncated for token optimization)
 */
export async function executeGetBookById(params: GutendexBookIdParams): Promise<string> {
  try {
    const url = `https://gutendex.com/books/${params.id}`;
    console.log(`[Gutendex] Fetching book: ${url}`);

    const response = await fetch(url);
    
    if (!response.ok) {
      if (response.status === 404) {
        return JSON.stringify({
          error: `Book with ID ${params.id} not found`
        });
      }
      return JSON.stringify({
        error: `Could not fetch book with ID ${params.id}: ${response.statusText}`
      });
    }

    const book = await response.json() as any;
    
    // Truncate fields for token optimization
    const truncatedBook = {
      id: book.id,
      title: truncateText(book.title, MAX_TEXT_FIELD_LENGTH),
      authors: truncateArray(book.authors || [], MAX_ARRAY_ITEMS).map((author: any) => ({
        name: author.name,
        birth_year: author.birth_year,
        death_year: author.death_year,
      })),
      subjects: truncateArray(book.subjects || [], MAX_ARRAY_ITEMS).map((subject: any) =>
        truncateText(subject, MAX_TEXT_FIELD_LENGTH)
      ),
      bookshelves: truncateArray(book.bookshelves || [], MAX_ARRAY_ITEMS),
      languages: book.languages || [],
      copyright: book.copyright,
      media_type: book.media_type,
      download_count: book.download_count,
    };

    return JSON.stringify(truncatedBook);
  } catch (error) {
    console.error('[Gutendex] Fetch error:', error);
    return JSON.stringify({
      error: `Could not fetch book with ID ${params.id}: ${error instanceof Error ? error.message : 'Unknown error'}`
    });
  }
}
