import { expect, test, type Page } from '@playwright/test'

const POSITION_KEY = 'northstar.assistant-widget.position.v1'

async function mockNorthstarShell(page: Page): Promise<string[]> {
  const unexpectedRequests: string[] = []

  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    let response: unknown

    switch (url.pathname) {
      case '/api/auth/me':
        response = { authenticated: true, username: 'playwright' }
        break
      case '/api/notes':
        response = {
          content: [],
          page: { size: 1, number: 0, totalElements: 0, totalPages: 0 },
        }
        break
      case '/api/disciplines':
      case '/api/tasks/range':
      case '/api/tasks/someday':
        response = []
        break
      default:
        unexpectedRequests.push(`${route.request().method()} ${url.pathname}`)
        await route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'Unexpected E2E request' }),
        })
        return
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(response),
    })
  })

  return unexpectedRequests
}

test('Assistant follows the pointer, persists its position, and remains clickable', async ({ page }) => {
  const browserErrors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error') browserErrors.push(message.text())
  })
  page.on('pageerror', (error) => browserErrors.push(error.message))
  await page.addInitScript((key) => {
    const resetKey = `${key}.e2e-reset`
    if (sessionStorage.getItem(resetKey)) return
    localStorage.removeItem(key)
    sessionStorage.setItem(resetKey, 'true')
  }, POSITION_KEY)
  const unexpectedRequests = await mockNorthstarShell(page)

  await page.goto('/tasks')
  await expect(page.getByRole('heading', { name: 'Tasks' })).toBeVisible()

  const trigger = page.getByRole('button', { name: 'Open Assistant; drag to reposition' })
  await expect(trigger).toBeVisible()
  const start = await trigger.boundingBox()
  expect(start).not.toBeNull()
  if (!start) return

  const pointerStart = {
    x: start.x + start.width / 2,
    y: start.y + start.height / 2,
  }
  await page.mouse.move(pointerStart.x, pointerStart.y)
  await page.mouse.down()

  for (const offset of [{ x: -60, y: -40 }, { x: -140, y: -90 }, { x: -220, y: -130 }]) {
    const pointer = { x: pointerStart.x + offset.x, y: pointerStart.y + offset.y }
    await page.mouse.move(pointer.x, pointer.y)
    await page.evaluate(() => new Promise(requestAnimationFrame))
    const current = await trigger.boundingBox()
    expect(current).not.toBeNull()
    if (!current) continue
    expect(Math.abs(pointer.x - (current.x + current.width / 2))).toBeLessThanOrEqual(2)
    expect(Math.abs(pointer.y - (current.y + current.height / 2))).toBeLessThanOrEqual(2)
  }

  await page.mouse.up()
  await expect.poll(() => page.evaluate((key) => localStorage.getItem(key), POSITION_KEY)).not.toBeNull()
  const persisted = await page.evaluate((key) => JSON.parse(localStorage.getItem(key)!) as { x: number; y: number }, POSITION_KEY)
  await expect(page.getByLabel('Assistant chat widget')).toBeHidden()

  await page.reload()
  await expect(trigger).toBeVisible()
  await expect.poll(async () => (await trigger.boundingBox())?.x).toBeCloseTo(persisted.x, 0)
  await expect.poll(async () => (await trigger.boundingBox())?.y).toBeCloseTo(persisted.y, 0)

  await trigger.click()
  await expect(page.getByLabel('Assistant chat widget')).toBeVisible()
  expect(unexpectedRequests).toEqual([])
  expect(browserErrors).toEqual([])
})
