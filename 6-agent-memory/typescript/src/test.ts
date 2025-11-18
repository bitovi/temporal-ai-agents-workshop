import { checkBedrockConnection } from './activities'

async function test() {
  await checkBedrockConnection()
}

test().catch((error) => {
  console.error('Test failed:', error)
  process.exit(1)
})
