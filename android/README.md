# حساب‌یار — نسخهٔ Android Native

این پوشه نسخهٔ بومی اندروید حساب‌یار است: Kotlin + Jetpack Compose + Room + Hilt + WorkManager + DataStore + Coroutines/Flow و Gradle Kotlin DSL. هیچ WebView یا جایگزین HTML/JS برای اپ بومی استفاده نشده است.

## ساختار ماژول‌ها
- `app` — پوستهٔ برنامه، Hilt Application، MainActivity، FileProvider و تم Splash.
- `core/common` — نتیجهٔ عملیات، خطاها، پول Long/تومان، Dispatcherها.
- `core/calendar` — تبدیل میلادی/شمسی؛ ذخیره‌سازی تاریخ استاندارد و نمایش شمسی.
- `core/currency` — پارس و قالب‌بندی تومان، ارقام فارسی، جداکنندهٔ هزارگان.
- `core/validation` — اعتبارسنجی نام، تلفن، مبلغ و تعداد.
- `core/design` — تم Material3، فونت وزیرمتن، کامپوننت‌های مشترک.
- `core/database` — Entityها، DAOها، Room Database و کلیدهای خارجی/ایندکس‌ها.
- `domain` — مدل‌ها، فرمان‌ها، AccountingEngine خالص و قراردادهای Repository.
- `data` — پیاده‌سازی Room/DataStore/WorkManager، پشتیبان‌گیری، خروجی CSV/XLSX.
- `feature` — صفحه‌های Compose: onboarding، dashboard، transactions، sales/purchase/expense/return/payment، contacts، products، reports، accounts، reminders، settings.
- `billing` — گیت‌وی پرداخت بازار؛ در این نسخه پیکربندی نشده و موفقیت ساختگی تولید نمی‌کند.

## قواعد حسابداری
- همهٔ مبالغ `Long` و به تومان هستند؛ Float/Double در هستهٔ مالی استفاده نشده است.
- فروش نسیه صندوق را زیاد نمی‌کند؛ فقط بدهی مشتری را زیاد می‌کند.
- دریافت طلب درآمد جدید نیست؛ پرداخت بدهی هزینهٔ جدید نیست.
- خرید کالا هزینهٔ عملیاتی فوری نیست؛ به موجودی اضافه می‌شود و هنگام فروش با میانگین موزون متحرک به بهای تمام‌شده تبدیل می‌شود.
- هزینهٔ مستقیم خرید مثل حمل به بهای موجودی سرمایه‌ای می‌شود و از هزینهٔ عملیاتی جداست.
- انتقال بین حساب‌های خود کسب‌وکار درآمد/هزینه نیست.
- ماندهٔ اشخاص علامت‌دار است: مثبت = طلب از مشتری، منفی = بدهی به تأمین‌کننده.
- تراکنش تأییدشده حذف فیزیکی نمی‌شود؛ اصلاح از مسیر برگشت (Reversal) و ثبت مجدد انجام می‌شود.
- هر عملیات چندبخشی داخل یک تراکنش اتمیک Room نوشته می‌شود و `operationId` یکتا از ثبت تکراری جلوگیری می‌کند.
- برگشت فروش فقط برای کالای سالم موجودی را برمی‌گرداند و بهای تمام‌شده را کاهش می‌دهد؛ کالای غیرقابل‌فروش به موجودی برنمی‌گردد و بهای آن به‌عنوان زیان در بهای تمام‌شده باقی می‌ماند.

## اجرا و ساخت
پیش‌نیاز: JDK 17 و Android SDK 35.

```bash
cd android
./gradlew :app:assembleDebug
./gradlew :app:bundleRelease
```

خروجی‌ها:
- APK دیباگ: `app/build/outputs/apk/debug/app-debug.apk`
- AAB انتشار: `app/build/outputs/bundle/release/app-release.aab`

در CI همین شاخه، GitHub Actions فایل‌ها را می‌سازد و به‌عنوان Artifact و Release asset منتشر می‌کند. AAB انتشار بدون کلید آپلود، امضای فروشگاهی ندارد؛ برای بازار باید با کلید آپلود واقعی امضا شود.

### امضای انتشار
کلید خصوصی را در مخزن commit نکنید. هنگام ساخت محلی/CI این متغیرها را بدهید:

```bash
export HY_KEYSTORE_PATH=/secure/hesabyar-upload.jks
export HY_KEYSTORE_PASSWORD=...
export HY_KEY_ALIAS=...
export HY_KEY_PASSWORD=...
./gradlew :app:bundleRelease :app:assembleRelease
```

## تست‌ها
```bash
./gradlew :core:calendar:test :core:currency:test :domain:test
./gradlew :data:testDebugUnitTest
```

تست‌های واحد هستهٔ مالی و تست‌های یکپارچهٔ Room/Robolectric سناریوهای اجباری را پوشش می‌دهند: فروش نقدی/نسیه/ترکیبی، دریافت طلب، خرید، بهای تمام‌شده و سود، پرداخت بدهی، ماندگاری پس از restart، عملیات آفلاین، جلوگیری از ثبت تکراری، میانگین موزون، انتقال، مرجوعی و اصلاح.

## پشتیبان‌گیری و خروجی
- پشتیبان محلی فایل SQLite را با checkpoint و SHA-256 ذخیره می‌کند.
- بازیابی پیش از جایگزینی، header، `integrity_check`، `foreign_key_check` و جدول‌های لازم را اعتبارسنجی می‌کند و به تأیید کاربر نیاز دارد.
- خروجی CSV با BOM UTF-8 و خروجی Excel واقعی `xlsx` ساخته می‌شود.
- پشتیبان‌گیری خودکار روزانه با WorkManager قابل فعال‌سازی است.

## محدودیت‌های صادقانهٔ این نسخه
- پرداخت درون‌برنامه‌ای بازار هنوز متصل نیست؛ هیچ خرید/اشتراک موفق ساختگی ثبت نمی‌شود.
- پشتیبان‌گیری ابری، همگام‌سازی و چندکاربره نیازمند سرویس بیرونی هستند و در این نسخه فعال نیستند.
- برای انتشار در بازار، امضای release با کلید آپلود واقعی و پیکربندی پرداخت بازار لازم است.
