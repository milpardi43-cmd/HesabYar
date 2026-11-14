package ir.hesabyar.feature

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.hesabyar.core.design.HesabYarTheme
import ir.hesabyar.domain.model.Business
import ir.hesabyar.domain.repository.AppSettings
import ir.hesabyar.domain.repository.AppTheme
import ir.hesabyar.feature.accounts.AccountsScreen
import ir.hesabyar.feature.contacts.ContactDetailScreen
import ir.hesabyar.feature.contacts.ContactFormScreen
import ir.hesabyar.feature.contacts.ContactsScreen
import ir.hesabyar.feature.dashboard.DashboardScreen
import ir.hesabyar.feature.navigation.Routes
import ir.hesabyar.feature.onboarding.OnboardingScreen
import ir.hesabyar.feature.products.ProductDetailScreen
import ir.hesabyar.feature.products.ProductFormScreen
import ir.hesabyar.feature.products.ProductsScreen
import ir.hesabyar.feature.reminders.RemindersScreen
import ir.hesabyar.feature.reports.ReportsScreen
import ir.hesabyar.feature.sales.ExpenseScreen
import ir.hesabyar.feature.sales.PaymentScreen
import ir.hesabyar.feature.sales.PurchaseScreen
import ir.hesabyar.feature.sales.ReturnScreen
import ir.hesabyar.feature.sales.SaleScreen
import ir.hesabyar.feature.settings.AboutScreen
import ir.hesabyar.feature.settings.BackupScreen
import ir.hesabyar.feature.settings.SettingsScreen
import ir.hesabyar.feature.settings.SubscriptionScreen
import ir.hesabyar.feature.transactions.TransactionDetailScreen
import ir.hesabyar.feature.transactions.TransactionsScreen

@Composable
fun HesabYarApp(viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dark = when (state.settings.theme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    HesabYarTheme(darkTheme = dark, fontScalePercent = state.settings.fontScalePercent) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.business == null -> OnboardingScreen()
                    else -> MainScaffold(state.business!!, state.settings)
                }
            }
        }
    }
}

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun MainScaffold(business: Business, settings: AppSettings) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val items = listOf(
        BottomItem(Routes.DASHBOARD, "خانه", Icons.Filled.Home),
        BottomItem(Routes.TRANSACTIONS, "تراکنش‌ها", Icons.Filled.ReceiptLong),
        BottomItem(Routes.CONTACTS, "اشخاص", Icons.Filled.People),
        BottomItem(Routes.PRODUCTS, "کالاها", Icons.Filled.Inventory2),
        BottomItem(Routes.REPORTS, "گزارش‌ها", Icons.Filled.BarChart),
    )
    val showBottom = currentRoute in items.map { it.route }
    Scaffold(
        bottomBar = {
            if (showBottom) {
                NavigationBar {
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        AppNavHost(navController, business, settings, Modifier.padding(padding))
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, business: Business, settings: AppSettings, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = Routes.DASHBOARD, modifier = modifier) {
        composable(Routes.DASHBOARD) { DashboardScreen(business, settings, navController) }
        composable(Routes.TRANSACTIONS) { TransactionsScreen(business.id, settings, navController) }
        composable(Routes.CONTACTS) { ContactsScreen(business.id, settings, navController) }
        composable(Routes.PRODUCTS) { ProductsScreen(business.id, settings, navController) }
        composable(Routes.REPORTS) { ReportsScreen(business.id, settings, navController) }
        composable(Routes.SETTINGS) { SettingsScreen(business, settings, navController) }
        composable(Routes.SALE) { SaleScreen(business.id, settings, navController) }
        composable(Routes.PURCHASE) { PurchaseScreen(business.id, settings, navController) }
        composable(Routes.EXPENSE) { ExpenseScreen(business.id, settings, navController) }
        composable(Routes.ACCOUNTS) { AccountsScreen(business.id, settings, navController) }
        composable(Routes.REMINDERS) { RemindersScreen(business.id, settings, navController) }
        composable(Routes.BACKUP) { BackupScreen(settings, navController) }
        composable(Routes.SUBSCRIPTION) { SubscriptionScreen(business.id, navController) }
        composable(Routes.ABOUT) { AboutScreen(navController) }
        composable(Routes.PAYMENT, arguments = listOf(navArgument("contactId") { type = NavType.LongType }, navArgument("direction") { type = NavType.StringType })) { entry ->
            PaymentScreen(entry.arguments!!.getLong("contactId"), entry.arguments!!.getString("direction")!!, settings, navController)
        }
        composable(Routes.RETURN, arguments = listOf(navArgument("transactionId") { type = NavType.LongType })) { entry ->
            ReturnScreen(entry.arguments!!.getLong("transactionId"), settings, navController)
        }
        composable(Routes.TRANSACTION_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            TransactionDetailScreen(entry.arguments!!.getLong("id"), settings, navController)
        }
        composable(Routes.CONTACT_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            ContactDetailScreen(entry.arguments!!.getLong("id"), settings, navController)
        }
        composable(Routes.CONTACT_FORM, arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L })) { entry ->
            ContactFormScreen(business.id, entry.arguments!!.getLong("id").takeIf { it > 0 }, settings, navController)
        }
        composable(Routes.PRODUCT_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            ProductDetailScreen(entry.arguments!!.getLong("id"), settings, navController)
        }
        composable(Routes.PRODUCT_FORM, arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L })) { entry ->
            ProductFormScreen(business.id, entry.arguments!!.getLong("id").takeIf { it > 0 }, settings, navController)
        }
    }
}
