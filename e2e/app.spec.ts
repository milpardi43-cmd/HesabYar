import { expect, test } from '@playwright/test'

test('demo dashboard renders charts and sample notice without console errors', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'نمای کلی', level: 1 })).toBeVisible()
  await expect(page.getByText('دفتر نمونه', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'جریان درآمد و هزینه' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'هزینه‌ها به تفکیک' })).toBeVisible()
  expect(errors).toEqual([])
})

test('create, persist, edit, delete and budget flow', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'شروع با دفتر خالی' }).click()
  await page.getByRole('button', { name: 'پاک کردن و شروع' }).click()
  await expect(page.getByText('دفتر خالی شد؛ حالا می‌توانید اولین تراکنش واقعی را ثبت کنید.')).toBeVisible()

  await page.locator('.page-heading').getByRole('button', { name: 'ثبت تراکنش' }).click()
  let dialog = page.getByRole('dialog', { name: 'ثبت تراکنش تازه' })
  await dialog.getByLabel(/مبلغ/).fill('123456')
  await dialog.getByLabel(/عنوان تراکنش/).fill('تست خرید نان')
  await dialog.getByLabel(/تاریخ شمسی/).click()
  await expect(page.getByRole('dialog', { name: 'انتخاب تاریخ شمسی' })).toBeVisible()
  await page.getByRole('button', { name: 'برو به امروز' }).click()
  await dialog.getByRole('button', { name: 'ثبت تراکنش', exact: true }).click()
  await expect(page.getByText('تراکنش تازه ثبت شد.')).toBeVisible()

  await page.locator('.main-nav').getByRole('link', { name: /تراکنش‌ها/ }).click()
  await expect(page.getByRole('button', { name: 'تست خرید نان', exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('button', { name: 'تست خرید نان', exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'ویرایش تست خرید نان' }).click()
  dialog = page.getByRole('dialog', { name: 'ویرایش تراکنش' })
  await dialog.getByLabel(/مبلغ/).fill('200000')
  await dialog.getByRole('button', { name: 'ذخیرهٔ تغییرات' }).click()
  await expect(page.getByText('تغییرات تراکنش ذخیره شد.')).toBeVisible()
  await expect(page.locator('.cell-amount')).toContainText('۲۰۰')

  await page.getByRole('button', { name: 'حذف تست خرید نان' }).click()
  await page.getByRole('button', { name: 'حذف تراکنش', exact: true }).click()
  await expect(page.getByText('تراکنش حذف شد.')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'دفترتان آمادهٔ اولین ثبت است' })).toBeVisible()

  await page.locator('.main-nav').getByRole('link', { name: 'بودجه‌بندی' }).click()
  await page.locator('.page-heading').getByRole('button', { name: 'تعیین سقف بودجه' }).click()
  dialog = page.getByRole('dialog', { name: 'تعیین سقف بودجه' })
  await dialog.getByLabel('سقف ماهانه (تومان)').fill('500000')
  await dialog.getByRole('button', { name: 'ذخیرهٔ سقف' }).click()
  await expect(page.getByText('سقف ماهانهٔ بودجه ذخیره شد.')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'خوراک و خرید روزانه' })).toBeVisible()
})

test('search filters demo transactions and backup downloads', async ({ page }) => {
  await page.goto('/transactions')
  await page.getByRole('searchbox', { name: 'جست‌وجوی تراکنش' }).fill('حقوق')
  await expect(page.getByRole('button', { name: 'حقوق ماهانه' }).first()).toBeVisible()
  await expect(page.getByRole('button', { name: 'اجارهٔ خانه', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: 'حذف فیلترها' }).click()
  await expect(page.getByRole('button', { name: 'دورهٔ آنلاین زبان', exact: true }).first()).toBeVisible()

  await page.locator('.main-nav').getByRole('link', { name: 'داده‌ها و تنظیمات' }).click()
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: 'دریافت فایل پشتیبان' }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toMatch(/^hesabyar-backup-\d{4}-\d{2}-\d{2}\.json$/)
})

test('mobile navigation works without horizontal overflow', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await page.getByRole('button', { name: 'باز کردن منو' }).click()
  await page.locator('.main-nav').getByRole('link', { name: 'بودجه‌بندی' }).click()
  await expect(page.getByRole('heading', { name: 'بودجه‌بندی', level: 1 })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)).toBe(false)
})
