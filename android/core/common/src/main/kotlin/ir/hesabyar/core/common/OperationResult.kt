package ir.hesabyar.core.common

/** Financial operations never throw raw database errors into the UI. */
sealed interface OperationResult<out T> {
    data class Success<T>(val value: T) : OperationResult<T>
    data class Failure(val error: AppError) : OperationResult<Nothing>
}

sealed interface AppError {
    data class Validation(val message: String) : AppError
    data class NotFound(val message: String) : AppError
    data class Conflict(val message: String) : AppError
    data class Storage(val message: String, val cause: Throwable? = null) : AppError
}

inline fun <T> runOperation(block: () -> T): OperationResult<T> = try {
    OperationResult.Success(block())
} catch (e: ValidationException) {
    OperationResult.Failure(AppError.Validation(e.message ?: "ورودی نامعتبر است."))
} catch (e: NotFoundException) {
    OperationResult.Failure(AppError.NotFound(e.message ?: "موردی پیدا نشد."))
} catch (e: ConflictException) {
    OperationResult.Failure(AppError.Conflict(e.message ?: "این عملیات قبلاً ثبت شده است."))
} catch (e: Exception) {
    OperationResult.Failure(AppError.Storage("خطا در ذخیره‌سازی اطلاعات.", e))
}

class ValidationException(message: String) : IllegalArgumentException(message)
class NotFoundException(message: String) : NoSuchElementException(message)
class ConflictException(message: String) : IllegalStateException(message)

fun requireValid(condition: Boolean, message: String) {
    if (!condition) throw ValidationException(message)
}
