import { ChatCompletionTool } from 'openai/resources/chat/completions'

export const definition: ChatCompletionTool = {
  type: 'function',
  function: {
    name: 'simple_calculator',
    description: [
      `Performs basic arithmetic operations (addition, subtraction, multiplication, division) on two numbers. You can use this tool to perform basic calculations between two numbers.`,
    ].join(' '),
    parameters: {
      type: 'object',
      properties: {
        operation: {
          type: 'string',
          enum: ['add', 'sub', 'mul', 'div'],
          description: 'The arithmetic operation to perform.',
        },
        num1: {
          type: 'number',
          description: 'The first number.',
        },
        num2: {
          type: 'number',
          description: 'The second number.',
        },
      },
      required: ['operation', 'num1', 'num2'],
    },
  },
}

export async function fn({
  operation,
  num1,
  num2,
}: {
  operation: string
  num1: number
  num2: number
}): Promise<string> {
  let result: number

  switch (operation) {
    case 'add':
      result = num1 + num2
      break
    case 'sub':
      result = num1 - num2
      break
    case 'mul':
      result = num1 * num2
      break
    case 'div':
      result = num1 / num2
      break
    default:
      throw new Error('Invalid operation')
  }

  return `The result of '${operation}' between '${num1}' and '${num2}' is '${result}'.`
}
