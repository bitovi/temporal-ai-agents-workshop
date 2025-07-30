import { ChatCompletionTool } from 'openai/resources/chat/completions'

export const definition: ChatCompletionTool = {
  type: 'function',
  function: {
    name: 'get_weather_forecast',
    description: [
      `
Gets the weather forecast for a specific location by zip code.
`,
    ].join(' '),
    parameters: {
      type: 'object',
      properties: {
        zipcode: {
          type: 'number',
          description: 'The zip code of the location to get the current weather for.',
        },
        date: {
          type: 'number',
          description: 'The date for the weather forecast (Unix timestamp).',
        },
      },
      required: ['zipcode', 'date'],
    },
  },
}

export async function fn({ zipcode, date }: { zipcode: number, date: number }): Promise<string> {
  return `The weather forecast for zip code ${zipcode} on ${date} is sunny with a temperature of 75°F.`
}
