import { expect, test } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

test('main pages and transaction dialog have no WCAG A/AA violations', async ({ page }) => {
  for (const path of ['/', '/transactions', '/budgets', '/settings']) {
    await page.goto(path, { waitUntil: 'networkidle' })
    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
    expect(results.violations, `accessibility violations on ${path}`).toEqual([])
  }

  await page.goto('/', { waitUntil: 'networkidle' })
  await page.locator('.page-heading').getByRole('button', { name: 'ثبت تراکنش' }).click()
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
  expect(results.violations, 'accessibility violations in transaction dialog').toEqual([])
})
