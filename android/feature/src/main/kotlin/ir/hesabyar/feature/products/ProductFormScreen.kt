package ir.hesabyar.feature.products

import androidx.compose.foundation.layout.*
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
import ir.hesabyar.domain.repository.AppSettings
import ir.hesabyar.domain.repository.ProductCategory
import ir.hesabyar.domain.repository.ProductCommand
import ir.hesabyar.domain.repository.ProductRepository
import ir.hesabyar.feature.common.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductFormState(
    val id: Long = 0, val name: String = "", val code: String = "", val categoryId: Long? = null,
    val purchaseInput: String = "", val sellingInput: String = "", val openingInput: String = "", val minimumInput: String = "",
    val description: String = "", val categories: List<ProductCategory> = emptyList(),
    val loading: Boolean = false, val error: String? = null, val saved: Boolean = false,
)

@HiltViewModel
class ProductFormViewModel @Inject constructor(private val repository: ProductRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProductFormState())
    val state = _state.asStateFlow()
    private var loaded = false
    fun load(businessId: Long, id: Long?) {
        if (loaded) return; loaded = true
        viewModelScope.launch { repository.observeCategories(businessId).collect { c -> _state.value = _state.value.copy(categories = c) } }
        if (id != null) viewModelScope.launch { repository.getById(id)?.let { p -> _state.value = _state.value.copy(id = p.id, name = p.name, code = p.code, categoryId = p.categoryId, purchaseInput = p.purchasePrice.toString(), sellingInput = p.sellingPrice.toString(), minimumInput = p.minimumStock.toString(), description = p.description) } }
    }
    fun update(block: (ProductFormState) -> ProductFormState) { _state.value = block(_state.value) }
    fun addCategory(businessId: Long, name: String) { viewModelScope.launch { repository.addCategory(businessId, name).handle({}, { m -> update { it.copy(error = m) } }) } }
    fun submit() {
        val s = _state.value; update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val command = ProductCommand(id = s.id, name = s.name, code = s.code, categoryId = s.categoryId, purchasePrice = parseMoneyInput(s.purchaseInput) ?: 0, sellingPrice = parseMoneyInput(s.sellingInput) ?: 0, openingStock = parseMoneyInput(s.openingInput) ?: 0, minimumStock = parseMoneyInput(s.minimumInput) ?: 0, description = s.description)
            val result = if (s.id == 0L) repository.add(command) else repository.update(command)
            result.handle({ update { it.copy(loading = false, saved = true) } }, { m -> update { it.copy(loading = false, error = m) } })
        }
    }
}

@Composable
fun ProductFormScreen(businessId: Long, id: Long?, settings: AppSettings, navController: NavController, viewModel: ProductFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(businessId, id) { viewModel.load(businessId, id) }
    LaunchedEffect(state.saved) { if (state.saved) navController.popBackStack() }
    var categoryPicker by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf(false) }
    var categoryName by remember { mutableStateOf("") }
    HyScreen(if (id == null) "افزودن کالا" else "ویرایش کالا", navController) {
        HyErrorBanner(state.error) { viewModel.update { it.copy(error = null) } }
        HyField(state.name, { v -> viewModel.update { it.copy(name = v) } }, "نام کالا *")
        HyField(state.code, { v -> viewModel.update { it.copy(code = v) } }, "کد کالا")
        OutlinedButton(onClick = { categoryPicker = true }, modifier = Modifier.fillMaxWidth()) { Text("دسته: ${state.categories.firstOrNull { it.id == state.categoryId }?.name ?: "بدون دسته"}") }
        MoneyField(state.purchaseInput, { v -> viewModel.update { it.copy(purchaseInput = v) } }, "قیمت خرید *")
        MoneyField(state.sellingInput, { v -> viewModel.update { it.copy(sellingInput = v) } }, "قیمت فروش *")
        if (id == null) MoneyField(state.openingInput, { v -> viewModel.update { it.copy(openingInput = v) } }, "موجودی اولیه")
        MoneyField(state.minimumInput, { v -> viewModel.update { it.copy(minimumInput = v) } }, "حداقل موجودی هشدار")
        HyField(state.description, { v -> viewModel.update { it.copy(description = v) } }, "توضیح", singleLine = false)
        HyButton(if (state.loading) "در حال ذخیره…" else "ذخیره", { viewModel.submit() }, enabled = !state.loading)
    }
    if (categoryPicker) SelectorDialog("دسته کالا", listOf<ProductCategory?>(null) + state.categories, { it?.name ?: "بدون دسته" }, { categoryPicker = false }, footer = { TextButton(onClick = { categoryPicker = false; newCategory = true }) { Text("دسته جدید") } }) { c -> viewModel.update { it.copy(categoryId = c?.id) }; categoryPicker = false }
    if (newCategory) AlertDialog(onDismissRequest = { newCategory = false }, title = { Text("دسته جدید") }, text = { HyField(categoryName, { categoryName = it }, "نام دسته") }, confirmButton = { TextButton(onClick = { viewModel.addCategory(businessId, categoryName); categoryName = ""; newCategory = false }) { Text("ذخیره") } }, dismissButton = { TextButton(onClick = { newCategory = false }) { Text("انصراف") } })
}
