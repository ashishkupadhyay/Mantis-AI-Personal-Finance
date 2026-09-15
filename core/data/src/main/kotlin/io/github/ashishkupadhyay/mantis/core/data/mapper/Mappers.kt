package io.github.ashishkupadhyay.mantis.core.data.mapper

import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.database.dao.TransactionWithRelations
import io.github.ashishkupadhyay.mantis.core.database.entity.AccountEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.BudgetEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.ImportBatchEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.CategoryEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.CategoryRuleEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.SyncColumns
import io.github.ashishkupadhyay.mantis.core.database.entity.TagEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.TransactionEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.TransactionSplitEntity
import io.github.ashishkupadhyay.mantis.core.model.Account
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.Budget
import io.github.ashishkupadhyay.mantis.core.model.BudgetId
import io.github.ashishkupadhyay.mantis.core.model.Category
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.CategoryRule
import io.github.ashishkupadhyay.mantis.core.model.DeviceId
import io.github.ashishkupadhyay.mantis.core.model.ImportBatch
import io.github.ashishkupadhyay.mantis.core.model.ImportBatchId
import io.github.ashishkupadhyay.mantis.core.model.MerchantId
import io.github.ashishkupadhyay.mantis.core.model.RecurringSeriesId
import io.github.ashishkupadhyay.mantis.core.model.RuleField
import io.github.ashishkupadhyay.mantis.core.model.RuleId
import io.github.ashishkupadhyay.mantis.core.model.RuleMatchType
import io.github.ashishkupadhyay.mantis.core.model.Split
import io.github.ashishkupadhyay.mantis.core.model.SplitId
import io.github.ashishkupadhyay.mantis.core.model.SyncMeta
import io.github.ashishkupadhyay.mantis.core.model.Tag
import io.github.ashishkupadhyay.mantis.core.model.TagId
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import io.github.ashishkupadhyay.mantis.core.database.entity.RuleField as EntityRuleField
import io.github.ashishkupadhyay.mantis.core.database.entity.RuleMatchType as EntityRuleMatchType

/*
 * Entity ↔ model mapping (doc 02 §3: `core:data` is the only module that sees both). Money is (minor, currency
 * code), dates are ISO strings and epoch millis in storage; the domain gets java.time and Money.
 */

fun SyncColumns.toModel(): SyncMeta = SyncMeta(updatedAt, version, deletedAt, dirty, DeviceId(deviceId))

fun SyncMeta.toColumns(): SyncColumns = SyncColumns(updatedAt, version, deletedAt, dirty, deviceId.value)

fun AccountEntity.toModel(): Account = Account(
    id = AccountId(id),
    name = name,
    type = type,
    currency = Currency.of(currency),
    openingBalance = Money(openingBalanceMinor, Currency.of(currency)),
    openingDate = LocalDate.parse(openingDate),
    institutionId = institutionId,
    last4 = last4,
    colorSeed = colorSeed,
    icon = icon,
    statementDay = statementDay,
    dueDay = dueDay,
    isArchived = isArchived,
    sortOrder = sortOrder,
    meta = sync.toModel(),
)

fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id.value,
    name = name,
    type = type,
    institutionId = institutionId,
    last4 = last4,
    currency = currency.code,
    openingBalanceMinor = openingBalance.minor,
    openingDate = openingDate.toString(),
    colorSeed = colorSeed,
    icon = icon,
    statementDay = statementDay,
    dueDay = dueDay,
    isArchived = isArchived,
    sortOrder = sortOrder,
    sync = meta.toColumns(),
)

fun CategoryEntity.toModel(): Category = Category(
    id = CategoryId(id),
    name = name,
    kind = kind,
    parentId = parentId?.let(::CategoryId),
    key = key,
    icon = icon,
    colorSeed = colorSeed,
    isSystem = isSystem,
    isHidden = isHidden,
    sortOrder = sortOrder,
    meta = sync.toModel(),
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id.value,
    parentId = parentId?.value,
    key = key,
    name = name,
    icon = icon,
    colorSeed = colorSeed,
    kind = kind,
    isSystem = isSystem,
    isHidden = isHidden,
    sortOrder = sortOrder,
    sync = meta.toColumns(),
)

fun CategoryRuleEntity.toModel(): CategoryRule = CategoryRule(
    id = RuleId(id),
    matchType = RuleMatchType.valueOf(matchType.name),
    pattern = pattern,
    field = RuleField.valueOf(field.name),
    categoryId = CategoryId(categoryId),
    priority = priority,
    createdFromTransactionId = createdFromTransactionId?.let(::TransactionId),
    hitCount = hitCount,
    meta = sync.toModel(),
)

fun CategoryRule.toEntity(): CategoryRuleEntity = CategoryRuleEntity(
    id = id.value,
    matchType = EntityRuleMatchType.valueOf(matchType.name),
    pattern = pattern,
    field = EntityRuleField.valueOf(field.name),
    categoryId = categoryId.value,
    priority = priority,
    createdFromTransactionId = createdFromTransactionId?.value,
    hitCount = hitCount,
    sync = meta.toColumns(),
)

fun TagEntity.toModel(): Tag = Tag(TagId(id), name, colorSeed, emoji, description, sync.toModel())

fun Tag.toEntity(): TagEntity = TagEntity(id.value, name, normalizedName, colorSeed, emoji, description, meta.toColumns())

fun TransactionWithRelations.toModel(): Transaction = transaction.toModel(
    splits = splits.sortedBy { it.sortOrder }.map { it.toModel(transaction.currency) },
    tagIds = tags.map { TagId(it.id) }.toSet(),
)

fun TransactionEntity.toModel(splits: List<Split> = emptyList(), tagIds: Set<TagId> = emptySet()): Transaction = Transaction(
    id = TransactionId(id),
    accountId = AccountId(accountId),
    type = type,
    amount = Money(amountMinor, Currency.of(currency)),
    postedAt = Instant.ofEpochMilli(postedAt),
    postedLocalDate = LocalDate.parse(postedLocalDate),
    descriptionRaw = descriptionRaw,
    merchantNormalized = merchantNormalized,
    merchantId = merchantId?.let(::MerchantId),
    categoryId = categoryId?.let(::CategoryId),
    categorySource = categorySource,
    categoryConfidence = categoryConfidence,
    notes = notes,
    isExcluded = isExcluded,
    isPending = isPending,
    needsReview = needsReview,
    transferPairId = transferPairId?.let(::TransactionId),
    recurringSeriesId = recurringSeriesId?.let(::RecurringSeriesId),
    importBatchId = importBatchId?.let(::ImportBatchId),
    fingerprint = fingerprint,
    anomalyScore = anomalyScore,
    entrySource = entrySource,
    tags = tagIds,
    splits = splits,
    meta = sync.toModel(),
)

private fun TransactionSplitEntity.toModel(currencyCode: String): Split =
    Split(SplitId(id), CategoryId(categoryId), Money(amountMinor, Currency.of(currencyCode)), note, origin)

/** [tzOffsetMinutes] is the user's zone offset at write time (doc 02 §5.1). */
fun Transaction.toEntity(tzOffsetMinutes: Int): TransactionEntity = TransactionEntity(
    id = id.value,
    accountId = accountId.value,
    type = type,
    amountMinor = amount.minor,
    currency = amount.currency.code,
    postedAt = postedAt.toEpochMilli(),
    postedLocalDate = postedLocalDate.toString(),
    tzOffsetMinutes = tzOffsetMinutes,
    descriptionRaw = descriptionRaw,
    merchantNormalized = merchantNormalized,
    merchantId = merchantId?.value,
    categoryId = categoryId?.value,
    categorySource = categorySource,
    categoryConfidence = categoryConfidence,
    notes = notes,
    isExcluded = isExcluded,
    isPending = isPending,
    needsReview = needsReview,
    transferPairId = transferPairId?.value,
    recurringSeriesId = recurringSeriesId?.value,
    importBatchId = importBatchId?.value,
    fingerprint = fingerprint,
    anomalyScore = anomalyScore,
    entrySource = entrySource,
    sync = meta.toColumns(),
)

fun Transaction.splitEntities(): List<TransactionSplitEntity> = splits.mapIndexed { index, split ->
    TransactionSplitEntity(split.id.value, id.value, split.categoryId.value, split.amount.minor, split.note, split.origin, index)
}

private val json = Json { ignoreUnknownKeys = true }
private val intList = ListSerializer(Int.serializer())

fun ImportBatchEntity.toModel(): ImportBatch = ImportBatch(
    id = ImportBatchId(id),
    accountId = AccountId(accountId),
    sourceName = sourceName,
    presetId = presetId,
    fileHash = fileHash,
    rowCount = rowCount,
    importedCount = importedCount,
    duplicateCount = duplicateCount,
    mergedCount = mergedCount,
    errorCount = errorCount,
    createdAt = Instant.ofEpochMilli(createdAt),
    undoneAt = undoneAt?.let(Instant::ofEpochMilli),
)

fun ImportBatch.toEntity(): ImportBatchEntity = ImportBatchEntity(
    id = id.value,
    accountId = accountId.value,
    sourceName = sourceName,
    presetId = presetId,
    fileHash = fileHash,
    rowCount = rowCount,
    importedCount = importedCount,
    duplicateCount = duplicateCount,
    mergedCount = mergedCount,
    errorCount = errorCount,
    createdAt = createdAt.toEpochMilli(),
    undoneAt = undoneAt?.toEpochMilli(),
)

fun BudgetEntity.toModel(categoryIds: Set<CategoryId>, accountIds: Set<AccountId>): Budget = Budget(
    id = BudgetId(id),
    name = name,
    amount = Money(amountMinor, Currency.of(currency)),
    period = period,
    customStart = customStart?.let(LocalDate::parse),
    customEnd = customEnd?.let(LocalDate::parse),
    rollover = rollover,
    thresholds = runCatching { json.decodeFromString(intList, thresholdsJson) }.getOrDefault(Budget.DEFAULT_THRESHOLDS),
    alertOnProjectedOverspend = alertOnProjectedOverspend,
    categoryIds = categoryIds,
    accountIds = accountIds,
    snoozedUntilPeriodKey = snoozedUntilPeriodKey,
    startsOn = startsOn?.let(LocalDate::parse),
    meta = sync.toModel(),
)

fun Budget.toEntity(): BudgetEntity = BudgetEntity(
    id = id.value,
    name = name,
    amountMinor = amount.minor,
    currency = amount.currency.code,
    period = period,
    customStart = customStart?.toString(),
    customEnd = customEnd?.toString(),
    rollover = rollover,
    thresholdsJson = json.encodeToString(intList, thresholds),
    alertOnProjectedOverspend = alertOnProjectedOverspend,
    snoozedUntilPeriodKey = snoozedUntilPeriodKey,
    startsOn = startsOn?.toString(),
    sync = meta.toColumns(),
)
