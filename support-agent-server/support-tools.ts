import { Tool } from '@aws-sdk/client-bedrock-runtime';

// ─── Mock Data ───────────────────────────────────────────────────────────────

interface MockCharge {
  id: string;
  item: string;
  amount: number;
  date: string;
  duplicate?: boolean;
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
    playerName: "PixelSlayer99",
    charges: [
      { id: "CHG-1001", item: "Season 12 Battle Pass", amount: 9.99, date: "2026-03-01" },
      { id: "CHG-1002", item: "Season 12 Battle Pass", amount: 9.99, date: "2026-03-01", duplicate: true },
    ]
  },
  "#9932": {
    email: "alex@example.com",
    paymentLast4: "1111",
    playerName: "NovaShard",
    charges: [
      { id: "CHG-2001", item: "Legendary Skin Bundle", amount: 24.99, date: "2026-02-15" },
    ]
  },
};

// ─── Tool Definitions (Bedrock format) ───────────────────────────────────────

export function getSupportTools(): Tool[] {
  return [
    {
      toolSpec: {
        name: 'lookup_account',
        description: 'Look up a player account by their player ID. Returns account summary without sensitive identity fields.',
        inputSchema: {
          json: {
            type: 'object',
            properties: {
              player_id: {
                type: 'string',
                description: 'The player ID (e.g., "#8821")',
              },
            },
            required: ['player_id'],
          },
        },
      },
    },
    {
      toolSpec: {
        name: 'check_billing_history',
        description: 'Get the charge history for a player account. Flags any duplicate charges.',
        inputSchema: {
          json: {
            type: 'object',
            properties: {
              player_id: {
                type: 'string',
                description: 'The player ID to check billing for',
              },
            },
            required: ['player_id'],
          },
        },
      },
    },
    {
      toolSpec: {
        name: 'request_verification',
        description: 'Request identity verification from the user before taking account actions. This pauses the conversation until the user provides their verification details. You MUST call this before processing any refund.',
        inputSchema: {
          json: {
            type: 'object',
            properties: {
              player_id: {
                type: 'string',
                description: 'The player ID to verify',
              },
              message: {
                type: 'string',
                description: 'The verification question to ask the user (e.g., asking for email or last 4 digits of payment method)',
              },
            },
            required: ['player_id', 'message'],
          },
        },
      },
    },
    {
      toolSpec: {
        name: 'verify_identity',
        description: 'Verify a player\'s identity by checking their email or payment method last 4 digits against account records.',
        inputSchema: {
          json: {
            type: 'object',
            properties: {
              player_id: {
                type: 'string',
                description: 'The player ID to verify',
              },
              email: {
                type: 'string',
                description: 'The email address provided by the user for verification',
              },
              payment_last4: {
                type: 'string',
                description: 'The last 4 digits of the payment method provided by the user',
              },
            },
            required: ['player_id'],
          },
        },
      },
    },
    {
      toolSpec: {
        name: 'request_information',
        description: 'Ask the user for information needed to proceed (e.g., player ID, order details). This pauses the conversation until the user responds. Use this whenever you need input from the user before you can continue.',
        inputSchema: {
          json: {
            type: 'object',
            properties: {
              message: {
                type: 'string',
                description: 'The question to ask the user',
              },
            },
            required: ['message'],
          },
        },
      },
    },
    {
      toolSpec: {
        name: 'process_refund',
        description: 'Process a refund for a specific charge. Only call this after identity has been verified via verify_identity.',
        inputSchema: {
          json: {
            type: 'object',
            properties: {
              player_id: {
                type: 'string',
                description: 'The player ID',
              },
              charge_id: {
                type: 'string',
                description: 'The charge ID to refund (e.g., "CHG-1002")',
              },
            },
            required: ['player_id', 'charge_id'],
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
    return JSON.stringify({ error: `No account found for player ID ${playerId}` });
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
    return JSON.stringify({ error: `No account found for player ID ${playerId}` });
  }

  const charges = account.charges.map(c => ({
    id: c.id,
    item: c.item,
    amount: c.amount,
    date: c.date,
    flagged: c.duplicate ? "DUPLICATE" : undefined,
  }));

  const duplicates = account.charges.filter(c => c.duplicate);

  return JSON.stringify({
    player_id: playerId,
    playerName: account.playerName,
    charges,
    duplicateChargesFound: duplicates.length,
    summary: duplicates.length > 0
      ? `Found ${duplicates.length} duplicate charge(s) totaling $${duplicates.reduce((s, c) => s + c.amount, 0).toFixed(2)}`
      : "No billing issues found",
  });
}

export function executeVerifyIdentity(input: Record<string, any>): string {
  const playerId = input.player_id as string;
  const account = MOCK_ACCOUNTS[playerId];
  
  if (!account) {
    return JSON.stringify({ verified: false, reason: `No account found for player ID ${playerId}` });
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

export function executeProcessRefund(input: Record<string, any>): string {
  const playerId = input.player_id as string;
  const chargeId = input.charge_id as string;
  const account = MOCK_ACCOUNTS[playerId];
  
  if (!account) {
    return JSON.stringify({ error: `No account found for player ID ${playerId}` });
  }

  const charge = account.charges.find(c => c.id === chargeId);
  if (!charge) {
    return JSON.stringify({ error: `No charge found with ID ${chargeId}` });
  }

  // Build deterministic confirmation number: RF- + numeric part of charge ID
  const numericPart = chargeId.replace('CHG-', '');
  const receipt = {
    confirmationNumber: `RF-${numericPart}`,
    refundAmount: charge.amount,
    currency: "USD",
    originalChargeId: chargeId,
    item: charge.item,
    playerName: account.playerName,
    estimatedDays: "3-5 business days",
    status: "processed",
  };

  return JSON.stringify(receipt);
}
