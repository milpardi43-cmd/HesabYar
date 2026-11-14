package ir.hesabyar.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ir.hesabyar.core.design.*

@Composable
fun AboutScreen(navController: NavController) {
    val context = LocalContext.current
    HyScreen("درباره و راهنما", navController) {
        HyCard {
            Text("حساب‌یار", style = MaterialTheme.typography.titleLarge)
            Text("نسخه 0.1.0", style = MaterialTheme.typography.bodySmall)
            Text("حساب‌وکتاب فروشگاهت، همیشه همراهت", style = MaterialTheme.typography.bodyMedium)
        }
        HyCard {
            Text("راهنمای کوتاه", style = MaterialTheme.typography.titleMedium)
            Text("۱. فروش نقدی صندوق را زیاد می‌کند؛ فروش نسیه فقط بدهی مشتری را زیاد می‌کند.", style = MaterialTheme.typography.bodySmall)
            Text("۲. دریافت طلب درآمد جدید نیست؛ فقط بدهی را کم و صندوق را زیاد می‌کند.", style = MaterialTheme.typography.bodySmall)
            Text("۳. خرید کالا هزینه عملیاتی نیست؛ به موجودی اضافه می‌شود و هنگام فروش به بهای تمام‌شده تبدیل می‌شود.", style = MaterialTheme.typography.bodySmall)
            Text("۴. تراکنش تأییدشده حذف نمی‌شود؛ برای اصلاح از «برگشت و ثبت مجدد» استفاده کن.", style = MaterialTheme.typography.bodySmall)
            Text("۵. همه مبالغ تومان و تمام‌عدد هستند؛ تاریخ‌ها شمسی نمایش داده می‌شوند.", style = MaterialTheme.typography.bodySmall)
        }
        HyOutlinedButton("پشتیبانی در گیت‌هاب", {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/milpardi43-cmd/HesabYar/issues")))
        })
    }
}
