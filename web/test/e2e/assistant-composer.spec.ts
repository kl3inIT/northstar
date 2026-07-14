import { expect, test, type Page } from '@playwright/test'

const AI_TASKS = [
  'ASSISTANT',
  'CAPTURE',
  'ALIGNMENT',
  'TITLE',
  'STUDY_GRADER',
  'IMAGE_CAPTION',
  'TEXT_TO_SPEECH',
  'SPEECH_TO_TEXT',
  'REALTIME_TRANSCRIPTION',
  'IMAGE_GENERATION',
  'EMBEDDING',
]

async function mockAssistant(page: Page) {
  const requests: string[] = []
  const unexpectedRequests: string[] = []
  const browserErrors: string[] = []
  page.on('pageerror', (error) => browserErrors.push(error.message))
  page.on('console', (message) => {
    if (message.type() === 'error') browserErrors.push(message.text())
  })
  let chatStarted = false
  let finishChat: (() => void) | undefined
  const releaseChat = new Promise<void>((resolve) => {
    finishChat = resolve
  })
  const route = { gatewayId: 'test-gateway', modelId: 'test-model' }
  const routes = Object.fromEntries(AI_TASKS.map((task) => [task, {
    route,
    defaultRoute: route,
    overridden: false,
  }]))

  await page.route('**/api/**', async (requestRoute) => {
    const url = new URL(requestRoute.request().url())
    requests.push(`${requestRoute.request().method()} ${url.pathname}`)

    if (url.pathname === '/api/assistant/chat') {
      chatStarted = true
      await releaseChat
      await requestRoute.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        headers: { 'x-vercel-ai-ui-message-stream': 'v1' },
        body: [
          'data: {"type":"start","messageId":"assistant-test"}',
          '',
          'data: {"type":"start-step"}',
          '',
          'data: {"type":"text-start","id":"text-test"}',
          '',
          'data: {"type":"text-delta","id":"text-test","delta":"Done"}',
          '',
          'data: {"type":"text-end","id":"text-test"}',
          '',
          'data: {"type":"finish-step"}',
          '',
          'data: {"type":"finish","finishReason":"stop"}',
          '',
          'data: [DONE]',
          '',
        ].join('\n'),
      })
      return
    }

    let response: unknown
    if (url.pathname === '/api/auth/me') {
      response = { authenticated: true, username: 'playwright' }
    } else if (url.pathname === '/api/auth/csrf') {
      await requestRoute.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { 'set-cookie': 'XSRF-TOKEN=playwright-token; Path=/' },
        body: JSON.stringify({
          headerName: 'X-XSRF-TOKEN',
          parameterName: '_csrf',
          token: 'playwright-token',
        }),
      })
      return
    } else if (url.pathname === '/api/assistant/conversations') {
      response = []
    } else if (url.pathname === '/api/assistant/history') {
      response = []
    } else if (/^\/api\/assistant\/conversations\/[^/]+\/model$/.test(url.pathname)) {
      response = route
    } else if (url.pathname === '/api/settings/ai') {
      response = { routes, gateways: [] }
    } else if (url.pathname === '/api/tasks/today') {
      response = []
    } else if (url.pathname === '/api/notes') {
      response = {
        content: [],
        page: { size: 1, number: 0, totalElements: 0, totalPages: 0 },
      }
    } else {
      unexpectedRequests.push(`${requestRoute.request().method()} ${url.pathname}`)
      await requestRoute.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ message: `Unexpected E2E request: ${url.pathname}` }),
      })
      return
    }

    await requestRoute.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(response),
    })
  })

  return {
    chatStarted: () => chatStarted,
    finishChat: () => finishChat?.(),
    diagnostics: () => ({ requests, unexpectedRequests, browserErrors }),
  }
}

test('clears the composer as soon as an Assistant turn is dispatched', async ({ page }) => {
  const chat = await mockAssistant(page)
  await page.goto('/assistant')

  const composer = page.getByPlaceholder('Message Northstar…')
  await composer.waitFor({ state: 'visible' }).catch((error: unknown) => {
    throw new Error(`${String(error)}\n${JSON.stringify(chat.diagnostics(), null, 2)}`)
  })
  await composer.fill('check deadline zeromail')
  await composer.press('Enter')

  await expect.poll(chat.chatStarted).toBe(true)
  await expect(composer).toHaveValue('')

  chat.finishChat()
  await expect(page.getByText('Done')).toBeVisible()
  expect(chat.diagnostics().unexpectedRequests).toEqual([])
  expect(chat.diagnostics().browserErrors).toEqual([])
})
