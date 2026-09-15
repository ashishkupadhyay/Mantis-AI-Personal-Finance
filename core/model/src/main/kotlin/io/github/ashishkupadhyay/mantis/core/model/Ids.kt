package io.github.ashishkupadhyay.mantis.core.model

/**
 * Typed entity ids (UUIDv7 strings, generated on device — doc 02 §5.1). Value classes cost nothing at
 * runtime and stop an AccountId from ever being passed where a CategoryId is expected.
 */
@JvmInline value class AccountId(val value: String)
@JvmInline value class TransactionId(val value: String)
@JvmInline value class SplitId(val value: String)
@JvmInline value class CategoryId(val value: String)
@JvmInline value class TagId(val value: String)
@JvmInline value class BudgetId(val value: String)
@JvmInline value class MerchantId(val value: String)
@JvmInline value class ImportBatchId(val value: String)
@JvmInline value class RecurringSeriesId(val value: String)
@JvmInline value class DeviceId(val value: String)
