import { ChatCompletionTool } from 'openai/resources/chat/completions'

export const definition: ChatCompletionTool = {
  type: 'function',
  function: {
    name: 'get_todays_date',
    description: [
      `
Gets the current date.
`,
    ].join(' '),
    parameters: {},
  },
}

export async function fn(): Promise<string> {
  const dateString = new Date().toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })

  return `Today's date is ${dateString}.`
}
