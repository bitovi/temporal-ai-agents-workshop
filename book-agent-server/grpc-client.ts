import { ClientFactory, ClientFactoryOptions } from '@a2a-js/sdk/client';
import { GrpcTransportFactory } from '@a2a-js/sdk/client/grpc';
import { Message, MessageSendParams, SendMessageSuccessResponse, TextPart } from '@a2a-js/sdk';
import { v4 as uuidv4 } from 'uuid';

async function run() {
  const factory = new ClientFactory({
    transports: [new GrpcTransportFactory()]
  });

  // createFromUrl accepts baseUrl and optional path,
  // (the default path is /.well-known/agent-card.json)
  const client = await factory.createFromUrl('http://localhost:4000');

  const sendParams: MessageSendParams = {
    message: {
      messageId: uuidv4(),
      role: 'user',
      parts: [{ kind: 'text', text: 'Hi there!' }],
      kind: 'message',
    },
  };

  try {
    const response = await client.sendMessage(sendParams);
    const result = response as Message;
    console.log('Agent response:', (result.parts[0] as TextPart).text); // "Hello, world!"
  } catch(e) {
    console.error('Error:', e);
  }
}

run();