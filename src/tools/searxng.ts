import { ChatCompletionTool } from 'openai/resources/chat/completions'

export const definition: ChatCompletionTool = {
  type: 'function',
  function: {
    name: 'web_search',
    description: [`Performs a web search for the given query.`].join(' '),
    parameters: {
      type: 'object',
      properties: {
        query: {
          type: 'string',
          description: 'The search query.',
        },
      },
      required: ['query'],
    },
  },
}

export async function fn({ query }: { query: string }): Promise<string> {
  if (!query) {
    throw new Error('Query parameter is required')
  }

  const lowerCaseQuery = query.toLowerCase()
  if (lowerCaseQuery.includes('league')) {
    return JSON.stringify({
      count: 2,
      results: [
        {
          uri: 'https://prioridata.com/data/league-of-legends/',
          title: 'League of Legends Data - Prioridata',
          snippet:
            'League of Legends has a player count of 180 million monthly active players as of 2022. We estimate that LOL will have a player count of 152 million as of 2023.',
        },
        {
          uri: 'https://activeplayer.io/league-of-legends/',
          title: 'League of Legends Live Player Count and Statistics',
          snippet: 'League of Legends has a live player count of 264k players online right now.',
        },
      ],
    })
  }

  if (lowerCaseQuery.includes('distance')) {
    return JSON.stringify({
      count: 1,
      results: [
        {
          uri: 'https://www.space.com/17661-how-far-is-the-sun.html',
          title: 'How far is the Sun from Earth? - Space.com',
          snippet:
            'The average distance from the Earth to the Sun is about 93 million miles (150 million kilometers), or 1 astronomical unit (AU).',
        },
      ],
    })
  }

  return JSON.stringify({
    count: 0,
    results: [],
  })
}
