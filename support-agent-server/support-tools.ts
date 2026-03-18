import { Tool } from "@aws-sdk/client-bedrock-runtime";

/**
 * Support agent tools and mock data.
 *
 * This file defines:
 *   1. Mock account/billing data that simulates a real database
 *   2. Bedrock tool definitions (JSON Schema) so the LLM knows what it can call
 *   3. Tool executor functions that return mock results
 *
 * Key design pattern — sentinel tools:
 *   request_verification, request_information, and offer_resolution_options
 *   are "sentinel" tools. The LLM can call them, but they’re never routed to
 *   executeTool(). Instead, server.ts intercepts them to emit `input-required`
 *   status and pause the conversation, waiting for the user to respond.
 */

interface MockCharge {
  id: string;
  item: string;
  amount: number;
  date: string;
}

interface MockAccount {
  email: string;
  paymentLast4: string;
  playerName: string;
  charges: MockCharge[];
}

const MOCK_ACCOUNTS: Record<string, MockAccount> = {
  "#8821": {
    email: "mark@example.com",
    paymentLast4: "4242",
    playerName: "ValorantAce99",
    charges: [
      {
        id: "CHG-1001",
        item: "Episode 9 Battle Pass",
        amount: 9.99,
        date: "2026-03-01",
      },
      {
        id: "CHG-1002",
        item: "Episode 9 Battle Pass",
        amount: 9.99,
        date: "2026-03-01",
      },
    ],
  },
  "#9932": {
    email: "alex@example.com",
    paymentLast4: "1111",
    playerName: "NovaShard",
    charges: [
      {
        id: "CHG-2001",
        item: "Legendary Skin Bundle",
        amount: 24.99,
        date: "2026-02-15",
      },
    ],
  },
};

// ─── Tool Definitions (Bedrock format) ───────────────────────────────────────

export function getSupportTools(): Tool[] {
  return [
    {
      toolSpec: {
        name: "lookup_account",
        description:
          "Look up a player account by their player ID. Returns account summary without sensitive identity fields.",
        inputSchema: {
          json: {
            type: "object",
            properties: {
              player_id: {
                type: "string",
                description: 'The player ID (e.g., "#1234")',
              },
            },
            required: ["player_id"],
          },
        },
      },
    },
    {
      toolSpec: {
        name: "check_billing_history",
        description:
          "Get the charge history for a player account. Returns the raw list of charges — look for patterns like identical items charged on the same date.",
        inputSchema: {
          json: {
            type: "object",
            properties: {
              player_id: {
                type: "string",
                description: "The player ID to check billing for",
              },
            },
            required: ["player_id"],
          },
        },
      },
    },
    {
      toolSpec: {
        name: "request_verification",
        description:
          "Request identity verification from the user before taking account actions. This pauses the conversation until the user provides their verification details.",
        inputSchema: {
          json: {
            type: "object",
            properties: {
              player_id: {
                type: "string",
                description: "The player ID to verify",
              },
              message: {
                type: "string",
                description:
                  "The verification question to ask the user (e.g., asking for email or last 4 digits of payment method)",
              },
            },
            required: ["player_id", "message"],
          },
        },
      },
    },
    {
      toolSpec: {
        name: "verify_identity",
        description:
          "Verify a player's identity by checking their email or payment method last 4 digits against account records.",
        inputSchema: {
          json: {
            type: "object",
            properties: {
              player_id: {
                type: "string",
                description: "The player ID to verify",
              },
              email: {
                type: "string",
                description:
                  "The email address provided by the user for verification",
              },
              payment_last4: {
                type: "string",
                description:
                  "The last 4 digits of the payment method provided by the user",
              },
            },
            required: ["player_id"],
          },
        },
      },
    },
    {
      toolSpec: {
        name: "request_information",
        description:
          "Ask the user for information needed to proceed (e.g., player ID, order details). This pauses the conversation until the user responds. Use this whenever you need input from the user before you can continue.",
        inputSchema: {
          json: {
            type: "object",
            properties: {
              message: {
                type: "string",
                description: "The question to ask the user",
              },
            },
            required: ["message"],
          },
        },
      },
    },
    {
      toolSpec: {
        name: "offer_resolution_options",
        description:
          "Present the user with resolution options for a billing issue. This pauses the conversation until the user selects an option. Call this AFTER identity is verified and the billing problem is confirmed. Do NOT process a resolution without offering choices first.",
        inputSchema: {
          json: {
            type: "object",
            properties: {
              player_id: {
                type: "string",
                description: "The player ID",
              },
              charge_id: {
                type: "string",
                description: "The charge ID in question",
              },
              message: {
                type: "string",
                description:
                  "A message explaining the issue and presenting numbered resolution options for the player to choose from.",
              },
            },
            required: ["player_id", "charge_id", "message"],
          },
        },
      },
    },
    {
      toolSpec: {
        name: "apply_resolution",
        description:
          "Apply the resolution option chosen by the user. Only call this after the user has selected an option via offer_resolution_options.",
        inputSchema: {
          json: {
            type: "object",
            properties: {
              player_id: {
                type: "string",
                description: "The player ID",
              },
              charge_id: {
                type: "string",
                description: "The charge ID being resolved",
              },
              resolution: {
                type: "string",
                enum: ["refund", "vp_credit", "skin_bundle"],
                description:
                  'The resolution type chosen by the user: "refund" for full refund, "vp_credit" for Valorant Points credit, "skin_bundle" for exclusive skin bundle + bonus VP.',
              },
            },
            required: ["player_id", "charge_id", "resolution"],
          },
        },
      },
    },
  ];
}

// ─── Tool Executors ──────────────────────────────────────────────────────────

export function executeLookupAccount(input: Record<string, any>): string {
  const playerId = input.player_id as string;
  const account = MOCK_ACCOUNTS[playerId];

  if (!account) {
    return JSON.stringify({
      error: `No account found for player ID ${playerId}`,
    });
  }

  // Return account summary without sensitive identity fields
  return JSON.stringify({
    player_id: playerId,
    playerName: account.playerName,
    chargeCount: account.charges.length,
    status: "active",
  });
}

export function executeCheckBillingHistory(input: Record<string, any>): string {
  const playerId = input.player_id as string;
  const account = MOCK_ACCOUNTS[playerId];

  if (!account) {
    return JSON.stringify({
      error: `No account found for player ID ${playerId}`,
    });
  }

  const charges = account.charges.map((c) => ({
    id: c.id,
    item: c.item,
    amount: c.amount,
    date: c.date,
  }));

  return JSON.stringify({
    player_id: playerId,
    playerName: account.playerName,
    charges,
  });
}

export function executeVerifyIdentity(input: Record<string, any>): string {
  const playerId = input.player_id as string;
  const account = MOCK_ACCOUNTS[playerId];

  if (!account) {
    return JSON.stringify({
      verified: false,
      reason: `No account found for player ID ${playerId}`,
    });
  }

  const email = input.email as string | undefined;
  const paymentLast4 = input.payment_last4 as string | undefined;

  if (email && email.toLowerCase() === account.email.toLowerCase()) {
    return JSON.stringify({ verified: true, method: "email" });
  }

  if (paymentLast4 && paymentLast4 === account.paymentLast4) {
    return JSON.stringify({ verified: true, method: "payment_last4" });
  }

  return JSON.stringify({
    verified: false,
    reason: "Provided credentials do not match our records",
  });
}

export function executeApplyResolution(input: Record<string, any>): string {
  const playerId = input.player_id as string;
  const chargeId = input.charge_id as string;
  const resolution = input.resolution as string;
  const account = MOCK_ACCOUNTS[playerId];

  if (!account) {
    return JSON.stringify({
      error: `No account found for player ID ${playerId}`,
    });
  }

  const charge = account.charges.find((c) => c.id === chargeId);
  if (!charge) {
    return JSON.stringify({ error: `No charge found with ID ${chargeId}` });
  }

  const numericPart = chargeId.replace("CHG-", "");

  switch (resolution) {
    case "refund": {
      return JSON.stringify({
        confirmationNumber: `RF-${numericPart}`,
        resolutionType: "Refund",
        refundAmount: charge.amount,
        currency: "USD",
        originalChargeId: chargeId,
        item: charge.item,
        playerName: account.playerName,
        estimatedDays: "3-5 business days",
        status: "processed",
      });
    }
    case "vp_credit": {
      const vpAmount = Math.round(charge.amount * 110);
      return JSON.stringify({
        confirmationNumber: `VP-${numericPart}`,
        resolutionType: "Valorant Points Credit",
        vpAwarded: vpAmount,
        estimatedValue: `$${(charge.amount * 1.1).toFixed(2)}`,
        originalChargeId: chargeId,
        item: charge.item,
        playerName: account.playerName,
        status: "credited",
      });
    }
    case "skin_bundle": {
      const bonusVp = Math.round(charge.amount * 20);
      return JSON.stringify({
        confirmationNumber: `SB-${numericPart}`,
        resolutionType: "Exclusive Skin Bundle + Bonus VP",
        skinBundle: "Radiant Crisis 012 Collection",
        bonusVpAwarded: bonusVp,
        estimatedValue: `$${(charge.amount * 1.2).toFixed(2)}`,
        originalChargeId: chargeId,
        item: charge.item,
        playerName: account.playerName,
        status: "granted",
      });
    }
    default:
      return JSON.stringify({
        error: `Unknown resolution type: ${resolution}`,
      });
  }
}
