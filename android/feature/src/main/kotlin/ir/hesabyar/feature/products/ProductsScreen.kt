package ir.hesabyar.feature.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hesabyar.core.design.*
import ir.hesabyar.domain.model.Product
import ir.hesabyar.domain.repository.AppSettings
import ir.hesabyar.domain.repository.ProductRepository
import ir.hesabyar.feature.common.*
import ir.hesabyar.feature.navigation.Routes
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductsState(val products: List<Product> = emptyList(), val query: String = "", val lowOnly: Boolean = false)

@HiltViewModel
class ProductsViewModel @Inject constructor(private val repository: ProductRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProductsState())
    val state = _state.asStateFlow()
    private var loaded = false
    fun load(businessId: Long) { if (loaded) return; loaded = true; viewModelScope.launch { repository.observeProducts(businessId).collect { p -> _state.value = _state.value.copy(products = p) } } }
    fun update(block: (ProductsState) -> ProductsState) { _state.value = block(_state.value) }
}

@Composable
fun ProductsScreen(businessId: Long, settings: AppSettings, navController: NavController, viewModel: ProductsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(businessId) { viewModel.load(businessId) }
    val filtered = state.products.filter { it.name.contains(state.query, true) || it.code.contains(state.query, true) }.filter { !state.lowOnly || it.isLowStock }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("کالاها", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { navController.navigate(Routes.productForm()) }) { Icon(Icons.Filled.Add, contentDescription = "افزودن کالا") }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HyField(state.query, { v -> viewModel.update { it.copy(query = v) } }, "جستجوی نام یا کد")
            FilterChip(selected = state.lowOnly, onClick = { viewModel.update { it.copy(lowOnly = !it.lowOnly) } }, label = { Text("فقط کم‌موجودها") })
        }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (filtered.isEmpty()) item { HyEmptyState("کالایی ثبت نشده", "کالا اضافه کن تا فروش و موجودی را مدیریت کنی.", "افزودن کالا") { navController.navigate(Routes.productForm()) } }
            items(filtered, key = { it.id }) { p ->
                HyCard(Modifier.clickable { navController.navigate(Routes.product(p.id)) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("موجودی ${formatNumber(p.stockQuantity, settings)}", style = MaterialTheme.typography.bodySmall, color = if (p.isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("خرید ${formatMoney(p.purchasePrice, settings)} | فروش ${formatMoney(p.sellingPrice, settings)}", style = MaterialTheme.typography.bodySmall)
                    if (p.code.isNotEmpty()) Text("کد: ${p.code}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
