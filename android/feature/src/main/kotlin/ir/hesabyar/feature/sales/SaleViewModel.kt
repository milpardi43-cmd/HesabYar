package ir.hesabyar.feature.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hesabyar.domain.accounting.AccountingEngine
import ir.hesabyar.domain.accounting.SaleCommand
import ir.hesabyar.domain.accounting.SaleLineCommand
import ir.hesabyar.domain.accounting.SaleResult
import ir.hesabyar.domain.model.*
import ir.hesabyar.domain.repository.*
import ir.hesabyar.feature.common.handle
import ir.hesabyar.feature.common.newOperationId
import ir.hesabyar.feature.common.parseMoneyInput
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CartLine(val productId: Long, val name: String, val stock: Long, val quantity: Long, val unitPrice: Long, val discount: Long)

data class SaleState(
    val products: List<Product> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val cart: List<CartLine> = emptyList(),
    val contactId: Long? = null,
    val accountId: Long? = null,
    val paidInput: String = "",
    val method: PaymentMethod = PaymentMethod.CASH,
    val description: String = "",
    val dueDate: Long? = null,
    val operationId: String = newOperationId(),
    val preview: SaleResult? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val successId: Long? = null,
)

@HiltViewModel
class SaleViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val contactRepository: ContactRepository,
    private val accountRepository: AccountRepository,
    private val accountingRepository: AccountingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SaleState())
    val state = _state.asStateFlow()
    private var businessId = 0L

    fun load(businessId: Long) {
        if (this.businessId == businessId) return
        this.businessId = businessId
        viewModelScope.launch { productRepository.observeProducts(businessId).collect { p -> _state.value = _state.value.copy(products = p); refreshPreview() } }
        viewModelScope.launch { contactRepository.observeContacts(businessId).collect { c -> _state.value = _state.value.copy(contacts = c) } }
        viewModelScope.launch { accountRepository.observeAccounts(businessId).collect { a -> _state.value = _state.value.copy(accounts = a, accountId = _state.value.accountId ?: a.firstOrNull { it.accountType == AccountType.CASH }?.id ?: a.firstOrNull()?.id) } }
    }

    fun addProduct(product: Product) {
        val cart = _state.value.cart.toMutableList()
        val index = cart.indexOfFirst { it.productId == product.id }
        if (index >= 0) cart[index] = cart[index].copy(quantity = cart[index].quantity + 1)
        else cart.add(CartLine(product.id, product.name, product.stockQuantity, 1, product.sellingPrice, 0))
        _state.value = _state.value.copy(cart = cart); refreshPreview()
    }

    fun updateLine(index: Int, quantity: Long? = null, unitPrice: Long? = null, discount: Long? = null) {
        val cart = _state.value.cart.toMutableList()
        if (index !in cart.indices) return
        cart[index] = cart[index].copy(quantity = quantity ?: cart[index].quantity, unitPrice = unitPrice ?: cart[index].unitPrice, discount = discount ?: cart[index].discount)
        _state.value = _state.value.copy(cart = cart); refreshPreview()
    }

    fun removeLine(index: Int) { val cart = _state.value.cart.toMutableList(); if (index in cart.indices) cart.removeAt(index); _state.value = _state.value.copy(cart = cart); refreshPreview() }
    fun setContact(id: Long?) { _state.value = _state.value.copy(contactId = id); refreshPreview() }
    fun setAccount(id: Long) { _state.value = _state.value.copy(accountId = id); refreshPreview() }
    fun setMethod(method: PaymentMethod) {
        val account = _state.value.accounts.firstOrNull { when (method) { PaymentMethod.CASH -> it.accountType == AccountType.CASH; PaymentMethod.CARD_READER -> it.accountType == AccountType.CARD_READER; PaymentMethod.BANK_TRANSFER -> it.accountType == AccountType.BANK; PaymentMethod.OTHER -> it.accountType == AccountType.OTHER } }
        _state.value = _state.value.copy(method = method, accountId = account?.id ?: _state.value.accountId); refreshPreview()
    }
    fun setPaid(value: String) { _state.value = _state.value.copy(paidInput = value); refreshPreview() }
    fun setDescription(value: String) { _state.value = _state.value.copy(description = value) }
    fun setDueDate(value: Long?) { _state.value = _state.value.copy(dueDate = value) }
    fun payFull() { _state.value = _state.value.copy(paidInput = (_state.value.preview?.netAmount ?: 0).toString()); refreshPreview() }
    fun payNone() { _state.value = _state.value.copy(paidInput = "0"); refreshPreview() }
    fun clearError() { _state.value = _state.value.copy(error = null) }

    fun addCustomer(name: String) {
        viewModelScope.launch {
            contactRepository.add(ContactCommand(name = name, contactType = ContactType.CUSTOMER)).handle(
                onSuccess = { contact -> _state.value = _state.value.copy(contactId = contact.id); refreshPreview() },
                onError = { msg -> _state.value = _state.value.copy(error = msg) },
            )
        }
    }

    private fun refreshPreview() {
        val s = _state.value
        val products = s.products.associate { it.id to ProductSnapshot(it.id, it.stockQuantity, it.inventoryValue, it.purchasePrice) }
        val net = s.cart.sumOf { it.quantity * it.unitPrice - it.discount }
        val paid = (parseMoneyInput(s.paidInput) ?: 0).coerceAtMost(net)
        val preview = runCatching {
            AccountingEngine.computeSale(
                SaleCommand(s.operationId, System.currentTimeMillis(), s.cart.map { SaleLineCommand(it.productId, it.quantity, it.unitPrice, it.discount) }, paid, s.contactId ?: 1L, s.accountId ?: 1L, s.method),
                products,
            )
        }.getOrNull()
        _state.value = s.copy(preview = preview)
    }

    fun submit() {
        val s = _state.value
        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            val command = SaleCommand(
                operationId = s.operationId, occurredAt = System.currentTimeMillis(),
                lines = s.cart.map { SaleLineCommand(it.productId, it.quantity, it.unitPrice, it.discount) },
                paidAmount = parseMoneyInput(s.paidInput) ?: 0,
                contactId = s.contactId, accountId = s.accountId, paymentMethod = s.method,
                description = s.description, dueDate = s.dueDate,
            )
            accountingRepository.recordSale(command).handle(
                onSuccess = { tx -> _state.value = _state.value.copy(loading = false, successId = tx.id) },
                onError = { msg -> _state.value = _state.value.copy(loading = false, error = msg) },
            )
        }
    }
}
