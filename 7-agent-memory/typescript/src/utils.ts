import {
  BedrockAgentCoreClient,
  CreateEventCommand,
  CreateEventCommandInput,
  CreateEventCommandOutput,
  ListMemoryRecordsCommand,
  ListMemoryRecordsCommandOutput,
} from '@aws-sdk/client-bedrock-agentcore'

interface TemporalClientOptions {
  address: string
  tls?: {
    clientCertPair: {
      crt: Buffer
      key: Buffer
    }
  }
}

export const getTemporalClientOptions = (): TemporalClientOptions => {
  const temporalHostURL = process.env.TEMPORAL_HOST_PORT

  if (!temporalHostURL) {
    throw new Error('Temporal Host URL not defined')
  }

  const temporalClientOptions: TemporalClientOptions = {
    address: temporalHostURL,
  }

  const temporalCert = process.env.TEMPORAL_CERT
  const temporalCertKey = process.env.TEMPORAL_CERT_KEY

  if (temporalCert && temporalCertKey) {
    temporalClientOptions.tls = {
      clientCertPair: {
        crt: Buffer.from(String(temporalCert)),
        key: Buffer.from(String(temporalCertKey)),
      },
    }
  }

  return temporalClientOptions
}

export const getAWSBedrockCoreClient = () => {
  return new BedrockAgentCoreClient({
    region: process.env.AWS_REGION!,
    credentials: {
      accessKeyId: process.env.AWS_ACCESS_KEY_ID!,
      secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY!,
      sessionToken: process.env.AWS_SESSION_TOKEN!,
    },
  })
}

export const createBedrockEvent = async (
  input: CreateEventCommandInput
): Promise<CreateEventCommandOutput> => {
  const client = getAWSBedrockCoreClient()
  const command = new CreateEventCommand(input)
  const response = await client.send(command)
  return response
}

export async function listMemoryRecordsCommand(
  namespace: string
): Promise<ListMemoryRecordsCommandOutput> {
  const client = getAWSBedrockCoreClient()
  const command = new ListMemoryRecordsCommand({
    memoryId: process.env.AWS_BEDROCK_MEMORY_ID!,
    namespace,
    maxResults: 10,
  })
  const response = await client.send(command)
  return response
}
