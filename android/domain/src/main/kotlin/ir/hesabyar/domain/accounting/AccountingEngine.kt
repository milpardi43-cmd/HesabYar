package ir.hesabyar.domain.accounting

import ir.hesabyar.core.common.MAX_MONEY
import ir.hesabyar.core.common.Quantity
import ir.hesabyar.core.common.Toman
import ir.hesabyar.core.common.requireValid
import ir.hesabyar.core.common.safeAdd
import ir.hesabyar.core.common.safeMul
import ir.hesabyar.core.common.safeSub
import ir.hesabyar.core.validation.Validators
import ir.hesabyar.domain.model.ProductSnapshot
import ir.hesabyar.domain.model.TransactionType
import java.math.BigInteger

/**
 * Pure accounting core. UI and database code must not duplicate these rules.
 * Money is whole Toman; quantities are whole units. Weighted-average cost is derived from
 * inventoryValue / stockQuantity, and purchase direct costs are capitalized into inventory.
 */
object AccountingEngine {

    fun computeSale(command: SaleCommand, products: Map<Long, ProductSnapshot>): SaleResult {
        requireValid(command.operationId.isNotBlank(), "شناسهٔ عملیات الزامی است.")
        requireValid(command.lines.isNotEmpty(), "حداقل یک کالا باید انتخاب شود.")
        var gross = 0L
        var discount = 0L
        var cogs = 0L
        val lines = command.lines.map { line ->
            Validators.quantity(line.quantity)
            Validators.money(line.unitPrice, allowZero = true, label = "قیمت فروش")
            Validators.money(line.discount, allowZero = true, label = "تخفیف")
            val product = products[line.productId]
            requireValid(product != null, "کالای انتخاب‌شده پیدا نشد.")
            requireValid(line.quantity <= product!!.stockQuantity, "موجودی کالا کافی نیست.")
            val lineGross = line.quantity.safeMul(line.unitPrice)
            requireValid(line.discount <= lineGross, "تخفیف نمی‌تواند بیشتر از مبلغ کالا باشد.")
            val unitCost = if (product.stockQuantity > 0) product.inventoryValue / product.stockQuantity else 0L
            val lineCogs = line.quantity.safeMul(unitCost)
            gross = gross.safeAdd(lineGross)
            discount = discount.safeAdd(line.discount)
            cogs = cogs.safeAdd(lineCogs)
            SaleLineResult(
                productId = line.productId,
                quantity = line.quantity,
                unitPrice = line.unitPrice,
                unitCostSnapshot = unitCost,
                discount = line.discount,
                totalAmount = lineGross.safeSub(line.discount),
                cogs = lineCogs,
            )
        }
        val net = gross.safeSub(discount)
        Validators.money(command.paidAmount, allowZero = true, label = "مبلغ دریافتی")
        requireValid(command.paidAmount <= net, "مبلغ دریافتی نمی‌تواند بیشتر از مبلغ فروش باشد.")
        val remaining = net.safeSub(command.paidAmount)
        if (remaining > 0) requireValid(command.contactId != null, "برای فروش نسیه باید مشتری بدهکار ثبت شود.")
        if (command.paidAmount > 0) requireValid(command.accountId != null, "برای مبلغ دریافتی باید حساب دریافت‌کننده انتخاب شود.")
        val type = when {
            remaining == 0L -> TransactionType.CASH_SALE
            command.paidAmount == 0L -> TransactionType.CREDIT_SALE
            else -> TransactionType.MIXED_SALE
        }
        return SaleResult(type, gross, discount, net, command.paidAmount, remaining, cogs, net.safeSub(cogs), lines)
    }

    fun computePurchase(command: PurchaseCommand): PurchaseResult {
        requireValid(command.operationId.isNotBlank(), "شناسهٔ عملیات الزامی است.")
        requireValid(command.lines.isNotEmpty(), "حداقل یک کالا باید انتخاب شود.")
        Validators.money(command.directCost, allowZero = true, label = "هزینهٔ مستقیم خرید")
        var gross = 0L
        var discount = 0L
        val baseLines = command.lines.map { line ->
            Validators.quantity(line.quantity)
            Validators.money(line.unitPrice, allowZero = true, label = "قیمت خرید")
            Validators.money(line.discount, allowZero = true, label = "تخفیف")
            val lineGross = line.quantity.safeMul(line.unitPrice)
            requireValid(line.discount <= lineGross, "تخفیف نمی‌تواند بیشتر از مبلغ کالا باشد.")
            gross = gross.safeAdd(lineGross)
            discount = discount.safeAdd(line.discount)
            line to lineGross.safeSub(line.discount)
        }
        val goodsNet = gross.safeSub(discount)
        val total = goodsNet.safeAdd(command.directCost)
        Validators.money(command.paidAmount, allowZero = true, label = "مبلغ پرداختی")
        requireValid(command.paidAmount <= total, "مبلغ پرداختی نمی‌تواند بیشتر از مبلغ خرید باشد.")
        val remaining = total.safeSub(command.paidAmount)
        if (remaining > 0) requireValid(command.supplierId > 0, "برای خرید نسیه باید تأمین‌کننده ثبت شود.")
        if (command.paidAmount > 0) requireValid(command.accountId != null, "برای مبلغ پرداختی باید حساب پرداخت‌کننده انتخاب شود.")

        // Capitalize direct costs proportionally by goods net; remainder goes to the last line.
        var allocatedSoFar = 0L
        val lines = baseLines.mapIndexed { index, (line, lineNet) ->
            val allocated = if (index == baseLines.lastIndex) {
                total.safeSub(allocatedSoFar)
            } else if (goodsNet == 0L) 0L else {
                BigInteger.valueOf(total).multiply(BigInteger.valueOf(lineNet))
                    .divide(BigInteger.valueOf(goodsNet)).longValueExact()
            }
            allocatedSoFar = allocatedSoFar.safeAdd(allocated)
            PurchaseLineResult(line.productId, line.quantity, line.unitPrice, line.discount, lineNet, allocated)
        }
        return PurchaseResult(gross, discount, goodsNet, command.directCost, total, command.paidAmount, remaining, lines)
    }

    fun computeExpense(command: ExpenseCommand): ExpenseResult {
        requireValid(command.operationId.isNotBlank(), "شناسهٔ عملیات الزامی است.")
        Validators.requiredName(command.title, "عنوان هزینه")
        val amount = Validators.money(command.amount, label = "مبلغ هزینه")
        Validators.money(command.paidAmount, allowZero = true, label = "مبلغ پرداختی")
        requireValid(command.paidAmount <= amount, "مبلغ پرداختی نمی‌تواند بیشتر از هزینه باشد.")
        val remaining = amount.safeSub(command.paidAmount)
        if (command.paidAmount > 0) requireValid(command.accountId != null, "حساب پرداخت‌کننده الزامی است.")
        if (remaining > 0) requireValid(command.contactId != null, "برای هزینهٔ پرداخت‌نشده باید شخص بستانکار ثبت شود.")
        return ExpenseResult(amount, command.paidAmount, remaining)
    }

    fun validatePayment(command: PaymentCommand) {
        requireValid(command.operationId.isNotBlank(), "شناسهٔ عملیات الزامی است.")
        requireValid(command.contactId > 0, "شخص الزامی است.")
        requireValid(command.accountId > 0, "حساب الزامی است.")
        Validators.money(command.amount, label = if (command.direction == PaymentDirection.RECEIVE) "مبلغ دریافتی" else "مبلغ پرداختی")
    }

    fun validateTransfer(command: TransferCommand) {
        requireValid(command.operationId.isNotBlank(), "شناسهٔ عملیات الزامی است.")
        requireValid(command.fromAccountId != command.toAccountId, "حساب مبدأ و مقصد باید متفاوت باشند.")
        requireValid(command.fromAccountId > 0 && command.toAccountId > 0, "حساب مبدأ و مقصد الزامی است.")
        Validators.money(command.amount, label = "مبلغ انتقال")
    }

    fun validateStockAdjustment(command: StockAdjustmentCommand, product: ProductSnapshot) {
        requireValid(command.operationId.isNotBlank(), "شناسهٔ عملیات الزامی است.")
        requireValid(command.reason.isNotBlank(), "دلیل اصلاح موجودی الزامی است.")
        requireValid(command.delta != 0L, "تعداد اصلاح باید غیرصفر باشد.")
        val newQuantity = product.stockQuantity.safeAdd(command.delta)
        requireValid(newQuantity >= 0, "موجودی کالا نمی‌تواند منفی شود.")
        if (command.unitCost != null) Validators.money(command.unitCost!!, allowZero = true, label = "بهای واحد")
    }

    fun validateAccountAdjustment(command: AccountAdjustmentCommand) {
        requireValid(command.operationId.isNotBlank(), "شناسهٔ عملیات الزامی است.")
        requireValid(command.reason.isNotBlank(), "دلیل اصلاح صندوق الزامی است.")
        requireValid(command.delta != 0L, "مبلغ اصلاح باید غیرصفر باشد.")
        requireValid(kotlin.math.abs(command.delta) <= MAX_MONEY, "مبلغ اصلاح خارج از محدوده است.")
    }

    fun computeSaleReturn(
        command: SaleReturnCommand,
        originalItems: List<ir.hesabyar.domain.model.SaleItemSnapshot>,
    ): SaleReturnResult {
        requireValid(command.operationId.isNotBlank(), "شناسهٔ عملیات الزامی است.")
        requireValid(command.reason.isNotBlank(), "دلیل برگشت فروش الزامی است.")
        requireValid(command.lines.isNotEmpty(), "حداقل یک قلم مرجوعی باید انتخاب شود.")
        val itemsById = originalItems.associateBy { it.id }
        var gross = 0L
        var discount = 0L
        var cogsReversed = 0L
        val lines = command.lines.map { line ->
            Validators.quantity(line.quantity)
            val item = itemsById[line.saleItemId]
            requireValid(item != null, "قلم فروش پیدا نشد.")
            val available = item!!.quantity - item.returnedQuantity
            requireValid(line.quantity <= available, "تعداد مرجوعی بیشتر از تعداد فروش‌رفته است.")
            val lineGross = line.quantity.safeMul(item.unitPrice)
            val discountShare = if (line.quantity == available) {
                item.discountAmount.safeSub(item.returnedDiscountAmount)
            } else {
                BigInteger.valueOf(item.discountAmount).multiply(BigInteger.valueOf(line.quantity))
                    .divide(BigInteger.valueOf(item.quantity)).longValueExact()
            }
            val net = lineGross.safeSub(discountShare)
            val cogs = if (line.sellable) line.quantity.safeMul(item.unitCostSnapshot) else 0L
            gross = gross.safeAdd(lineGross)
            discount = discount.safeAdd(discountShare)
            cogsReversed = cogsReversed.safeAdd(cogs)
            SaleReturnLineResult(item.id, item.productId, line.quantity, item.unitPrice, item.unitCostSnapshot, discountShare, net, cogs, line.sellable)
        }
        val net = gross.safeSub(discount)
        Validators.money(command.refundAmount, allowZero = true, label = "مبلغ بازپرداخت")
        requireValid(command.refundAmount <= net, "بازپرداخت نمی‌تواند بیشتر از مبلغ مرجوعی باشد.")
        if (command.refundAmount > 0) requireValid(command.accountId != null, "حساب بازپرداخت الزامی است.")
        val receivableReduction = net.safeSub(command.refundAmount)
        return SaleReturnResult(gross, discount, net, command.refundAmount, receivableReduction, cogsReversed, lines)
    }

}
