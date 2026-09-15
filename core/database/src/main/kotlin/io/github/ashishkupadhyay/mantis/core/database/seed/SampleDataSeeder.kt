package io.github.ashishkupadhyay.mantis.core.database.seed

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.common.time.Clock
import io.github.ashishkupadhyay.mantis.core.database.MantisDatabase
import io.github.ashishkupadhyay.mantis.core.database.entity.AccountEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.BudgetEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.MerchantAliasEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.MerchantEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.MerchantSource
import io.github.ashishkupadhyay.mantis.core.database.entity.MerchantType
import io.github.ashishkupadhyay.mantis.core.database.entity.SyncColumns
import io.github.ashishkupadhyay.mantis.core.database.entity.TagEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.TransactionEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.TransactionTagCrossRef
import io.github.ashishkupadhyay.mantis.core.model.AccountType
import io.github.ashishkupadhyay.mantis.core.model.BudgetPeriodType
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.DefaultTaxonomy
import io.github.ashishkupadhyay.mantis.core.model.EntrySource
import io.github.ashishkupadhyay.mantis.core.model.TransactionFingerprint
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.core.model.defaultIcon
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import kotlin.random.Random

/**
 * Deterministic synthetic history for debug builds, benchmarks and screenshots (WP-0.5): [months] of activity
 * across four accounts — salary and rent monthly, groceries weekly, food delivery, cabs, subscriptions, utility
 * bills, shopping, card-payment transfer pairs — plus merchants with aliases, two tags and two budgets.
 * The same [seed] always produces the same rows, so screenshots and benchmarks are reproducible.
 */
@Suppress("MagicNumber", "TooManyFunctions", "CyclomaticComplexMethod", "LongMethod", "MaxLineLength")
class SampleDataSeeder @Inject constructor(
    private val database: MantisDatabase,
    private val clock: Clock,
    private val ids: UuidV7,
    private val categorySeeder: DefaultCategorySeeder,
) {

    data class Result(val accounts: Int, val merchants: Int, val transactions: Int, val budgets: Int)

    suspend fun seed(deviceId: String = "seed-device", months: Int = 18, seed: Int = 42): Result {
        categorySeeder.seed(deviceId)
        val random = Random(seed)
        val now = clock.epochMillis()
        val sync = SyncColumns(updatedAt = now, dirty = false, deviceId = deviceId)
        val categoryIds = database.categoryDao().all().associate { it.key to it.id }
        fun category(key: String): String = categoryIds.getValue(key)

        val accounts = sampleAccounts(sync)
        val merchants = sampleMerchants(sync, ::category)
        val tags = listOf(
            TagEntity(ids.nextString(), "Goa trip", "goa-trip", 2, "🏖️", null, sync),
            TagEntity(ids.nextString(), "Reimbursable", "reimbursable", 5, null, "Claim from work", sync),
        )
        val plan = TransactionPlan(accounts, merchants, tags, ::category, random, deviceId, now)
        val end = clock.today()
        val start = end.minusMonths(months.toLong()).withDayOfMonth(1)
        val transactions = plan.generate(start, end)
        val budgets = listOf(
            BudgetEntity(
                id = ids.nextString(), name = "Monthly essentials", amountMinor = 45_000_00, currency = "INR",
                period = BudgetPeriodType.MONTHLY, customStart = null, customEnd = null, rollover = false,
                thresholdsJson = "[50,80,100]", alertOnProjectedOverspend = true, snoozedUntilPeriodKey = null,
                startsOn = start.toString(), sync = sync,
            ),
            BudgetEntity(
                id = ids.nextString(), name = "Eating out", amountMinor = 8_000_00, currency = "INR",
                period = BudgetPeriodType.MONTHLY, customStart = null, customEnd = null, rollover = true,
                thresholdsJson = "[80,100]", alertOnProjectedOverspend = true, snoozedUntilPeriodKey = null,
                startsOn = start.toString(), sync = sync,
            ),
        )

        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                database.accountDao().upsertAll(accounts)
                database.merchantDao().upsertAll(merchants.map { it.entity })
                database.merchantDao().upsertAliases(merchants.flatMap { m -> m.aliases.map { MerchantAliasEntity(it, m.entity.id) } })
                tags.forEach { database.tagDao().upsert(it) }
                transactions.chunked(500).forEach { database.transactionDao().upsertAll(it.map { t -> t.entity }) }
                database.transactionDao().upsertTagRefs(
                    transactions.flatMap { t -> t.tagIds.map { TransactionTagCrossRef(t.entity.id, it) } },
                )
                database.budgetDao().replace(budgets[0], emptySet(), emptySet())
                database.budgetDao().replace(
                    budgets[1],
                    setOf(category("food.restaurants"), category("food.delivery"), category("food.cafe")),
                    emptySet(),
                )
            }
        }
        return Result(accounts.size, merchants.size, transactions.size, budgets.size)
    }

    private fun sampleAccounts(sync: SyncColumns): List<AccountEntity> = listOf(
        account("HDFC Savings", AccountType.BANK, "hdfc", "4412", 1_25_000_00, 0, sync),
        account("ICICI Credit Card", AccountType.CREDIT_CARD, "icici", "8831", 0, 1, sync, statementDay = 18, dueDay = 6),
        account("Cash", AccountType.CASH, null, null, 3_500_00, 2, sync),
        account("Paytm Wallet", AccountType.WALLET, "paytm", null, 1_200_00, 3, sync),
    )

    @Suppress("LongParameterList")
    private fun account(
        name: String,
        type: AccountType,
        institution: String?,
        last4: String?,
        openingMinor: Long,
        order: Int,
        sync: SyncColumns,
        statementDay: Int? = null,
        dueDay: Int? = null,
    ) = AccountEntity(
        id = ids.nextString(), name = name, type = type, institutionId = institution, last4 = last4, currency = "INR",
        openingBalanceMinor = openingMinor, openingDate = "2024-01-01", colorSeed = order, icon = type.defaultIcon,
        statementDay = statementDay, dueDay = dueDay, isArchived = false, sortOrder = order, sync = sync,
    )

    private class SeedMerchant(val entity: MerchantEntity, val aliases: List<String>, val categoryKey: String)

    private fun sampleMerchants(sync: SyncColumns, category: (String) -> String): List<SeedMerchant> {
        fun m(name: String, type: MerchantType, key: String, vararg aliases: String) = SeedMerchant(
            MerchantEntity(
                id = ids.nextString(), canonicalName = name.lowercase(), displayName = name, defaultCategoryId = category(key),
                merchantType = type, logoKey = null, source = MerchantSource.BUNDLED, sync = sync,
            ),
            aliases.toList(),
            key,
        )
        return listOf(
            m("Blinkit", MerchantType.QUICK_COMMERCE, "food.groceries", "blinkit", "grofers"),
            m("BigBasket", MerchantType.GROCERY, "food.groceries", "bigbasket", "bb daily"),
            m("Swiggy", MerchantType.FOOD_DELIVERY, "food.delivery", "swiggy", "bundl technologies"),
            m("Zomato", MerchantType.FOOD_DELIVERY, "food.delivery", "zomato"),
            m("Third Wave Coffee", MerchantType.RESTAURANT, "food.cafe", "third wave coffee", "twc"),
            m("Uber", MerchantType.TRANSPORT, "transport.cab", "uber", "uber india"),
            m("Rapido", MerchantType.TRANSPORT, "transport.cab", "rapido"),
            m("HP Petrol Pump", MerchantType.FUEL, "transport.fuel", "hpcl", "hp petrol"),
            m("Amazon", MerchantType.ECOMMERCE, "shopping.online", "amazon", "amazon pay"),
            m("Myntra", MerchantType.ECOMMERCE, "shopping.clothing", "myntra"),
            m("Netflix", MerchantType.ENTERTAINMENT, "entertainment.subscriptions", "netflix"),
            m("Spotify", MerchantType.ENTERTAINMENT, "entertainment.subscriptions", "spotify"),
            m("BESCOM", MerchantType.UTILITY, "bills.electricity", "bescom", "bangalore electricity"),
            m("Airtel", MerchantType.UTILITY, "bills.mobile_internet", "airtel", "bharti airtel"),
            m("Apollo Pharmacy", MerchantType.PHARMACY, "health.pharmacy", "apollo pharmacy", "apollo"),
            m("Cult.fit", MerchantType.OTHER, "health.fitness", "cult fit", "cultfit"),
            m("PVR Cinemas", MerchantType.ENTERTAINMENT, "entertainment.events", "pvr", "pvr inox"),
        )
    }

    private class SeedTransaction(val entity: TransactionEntity, val tagIds: Set<String>)

    /** Generates rows month by month; amounts are rupees × 100 and always negative for expenses. */
    private inner class TransactionPlan(
        private val accounts: List<AccountEntity>,
        private val merchants: List<SeedMerchant>,
        private val tags: List<TagEntity>,
        private val category: (String) -> String,
        private val random: Random,
        private val deviceId: String,
        private val now: Long,
    ) {
        private val bank = accounts[0]
        private val card = accounts[1]
        private val cash = accounts[2]
        private val wallet = accounts[3]
        private val byName = merchants.associateBy { it.entity.displayName }

        fun generate(start: LocalDate, end: LocalDate): List<SeedTransaction> {
            val rows = mutableListOf<SeedTransaction>()
            var month = start
            while (!month.isAfter(end)) {
                rows += monthlyFixed(month, end)
                rows += cardPayment(month, end)
                month = month.plusMonths(1)
            }
            var day = start
            while (!day.isAfter(end)) {
                rows += daily(day)
                day = day.plusDays(1)
            }
            return rows.sortedBy { it.entity.postedAt }
        }

        private fun monthlyFixed(month: LocalDate, end: LocalDate): List<SeedTransaction> = buildList {
            fun on(dayOfMonth: Int) = month.withDayOfMonth(dayOfMonth.coerceAtMost(month.lengthOfMonth())).takeIf { !it.isAfter(end) }
            on(1)?.let { add(income(bank, it, 1_45_000_00, "SALARY CREDIT ACME TECH", "income.salary")) }
            on(3)?.let { add(expense(bank, it, 32_000_00, "NEFT RENT MR SHARMA", "bills.rent", merchant = null)) }
            on(5)?.let { add(expense(card, it, 649_00, "NETFLIX.COM", "entertainment.subscriptions", byName["Netflix"])) }
            on(7)?.let { add(expense(card, it, 119_00, "SPOTIFY INDIA", "entertainment.subscriptions", byName["Spotify"])) }
            on(10)?.let {
                add(expense(bank, it, 1_500_00 + random.nextLong(0, 2_500_00), "BESCOM BILL PAYMENT", "bills.electricity", byName["BESCOM"]))
            }
            on(12)?.let { add(expense(bank, it, 999_00, "AIRTEL POSTPAID", "bills.mobile_internet", byName["Airtel"])) }
            on(15)?.let { add(expense(bank, it, 2_990_00, "CULT FIT MEMBERSHIP", "health.fitness", byName["Cult.fit"])) }
            on(20)?.let { add(expense(bank, it, 10_000_00, "UPI/SIP/GROWW", "finance.investments", merchant = null)) }
        }

        /** Credit-card bill paid from the bank: a linked transfer pair (FR-ACC-4). */
        private fun cardPayment(month: LocalDate, end: LocalDate): List<SeedTransaction> {
            val date = month.withDayOfMonth(6).takeIf { !it.isAfter(end) } ?: return emptyList()
            val amount = 18_000_00 + random.nextLong(0, 12_000_00)
            val outId = ids.nextString()
            val inId = ids.nextString()
            val transfer = DefaultTaxonomy.KEY_TRANSFER
            val out = transaction(outId, bank, date, -amount, TransactionType.TRANSFER, "CC PAYMENT ICICI 8831", transfer, null, pairId = inId)
            val inn = transaction(inId, card, date, amount, TransactionType.TRANSFER, "PAYMENT RECEIVED - THANK YOU", transfer, null, pairId = outId)
            return listOf(out, inn)
        }

        private fun daily(day: LocalDate): List<SeedTransaction> = buildList {
            val weekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
            if (day.dayOfWeek == DayOfWeek.SATURDAY) {
                val grocer = if (random.nextBoolean()) byName["BigBasket"] else byName["Blinkit"]
                add(expense(card, day, 1_200_00 + random.nextLong(0, 2_800_00), "${grocer!!.entity.displayName.uppercase()} ORDER", "food.groceries", grocer))
            }
            if (random.nextInt(100) < 18) add(expense(wallet, day, 150_00 + random.nextLong(0, 450_00), "BLINKIT", "food.groceries", byName["Blinkit"]))
            if (random.nextInt(100) < if (weekend) 55 else 30) {
                val app = if (random.nextBoolean()) byName["Swiggy"] else byName["Zomato"]
                add(expense(card, day, 220_00 + random.nextLong(0, 680_00), "${app!!.entity.displayName.uppercase()} ORDER", "food.delivery", app))
            }
            if (!weekend && random.nextInt(100) < 45) {
                val cab = if (random.nextInt(100) < 70) byName["Uber"] else byName["Rapido"]
                add(expense(wallet, day, 80_00 + random.nextLong(0, 320_00), "UPI ${cab!!.entity.displayName.uppercase()}", "transport.cab", cab))
            }
            if (!weekend && random.nextInt(100) < 25) add(expense(cash, day, 40_00 + random.nextLong(0, 260_00), "COFFEE", "food.cafe", byName["Third Wave Coffee"]))
            if (random.nextInt(100) < 6) add(expense(card, day, 2_000_00 + random.nextLong(0, 1_500_00), "HPCL FUEL", "transport.fuel", byName["HP Petrol Pump"]))
            if (random.nextInt(100) < 7) add(expense(card, day, 400_00 + random.nextLong(0, 6_000_00), "AMAZON.IN", "shopping.online", byName["Amazon"]))
            if (random.nextInt(100) < 2) add(expense(card, day, 900_00 + random.nextLong(0, 3_500_00), "MYNTRA", "shopping.clothing", byName["Myntra"]))
            if (random.nextInt(100) < 4) add(expense(wallet, day, 120_00 + random.nextLong(0, 900_00), "APOLLO PHARMACY", "health.pharmacy", byName["Apollo Pharmacy"]))
            if (weekend && random.nextInt(100) < 12) add(expense(card, day, 500_00 + random.nextLong(0, 1_200_00), "PVR INOX", "entertainment.events", byName["PVR Cinemas"]))
            if (random.nextInt(1000) < 8) add(expense(cash, day, 50_00 + random.nextLong(0, 500_00), "CASH SPEND", "system.uncategorized", null, categorized = false))
            // A week in Goa each January: tagged, so the tag dimension has data.
            if (day.monthValue == 1 && day.dayOfMonth in 8..14 && random.nextInt(100) < 60) {
                add(expense(card, day, 800_00 + random.nextLong(0, 4_000_00), "GOA ${listOf("SHACK", "SCOOTER RENTAL", "HOTEL").random(random)}", "travel.trip", null, tagIds = setOf(tags[0].id)))
            }
        }

        @Suppress("LongParameterList")
        private fun expense(
            account: AccountEntity,
            date: LocalDate,
            amountMinor: Long,
            description: String,
            categoryKey: String,
            merchant: SeedMerchant?,
            categorized: Boolean = true,
            tagIds: Set<String> = emptySet(),
        ) = transaction(ids.nextString(), account, date, -amountMinor, TransactionType.EXPENSE, description, categoryKey, merchant, categorized = categorized, tagIds = tagIds)

        private fun income(account: AccountEntity, date: LocalDate, amountMinor: Long, description: String, categoryKey: String) =
            transaction(ids.nextString(), account, date, amountMinor, TransactionType.INCOME, description, categoryKey, null)

        @Suppress("LongParameterList")
        private fun transaction(
            id: String,
            account: AccountEntity,
            date: LocalDate,
            amountMinor: Long,
            type: TransactionType,
            description: String,
            categoryKey: String,
            merchant: SeedMerchant?,
            pairId: String? = null,
            categorized: Boolean = true,
            tagIds: Set<String> = emptySet(),
        ): SeedTransaction {
            val postedAt = date.atTime(9 + random.nextInt(12), random.nextInt(60)).toEpochSecond(IST) * 1000
            val source = when {
                !categorized -> CategorySource.NONE
                merchant != null -> CategorySource.MERCHANT_DB
                type == TransactionType.TRANSFER -> CategorySource.USER
                else -> CategorySource.ON_DEVICE_MODEL
            }
            val entity = TransactionEntity(
                id = id,
                accountId = account.id,
                type = type,
                amountMinor = amountMinor,
                currency = "INR",
                postedAt = postedAt,
                postedLocalDate = date.toString(),
                tzOffsetMinutes = IST_OFFSET_MINUTES,
                descriptionRaw = description,
                merchantNormalized = merchant?.entity?.canonicalName,
                merchantId = merchant?.entity?.id,
                categoryId = if (categorized) category(categoryKey) else null,
                categorySource = source,
                categoryConfidence = when (source) {
                    CategorySource.MERCHANT_DB -> 0.95f
                    CategorySource.ON_DEVICE_MODEL -> 0.8f + random.nextFloat() * 0.15f
                    else -> null
                },
                notes = null,
                isExcluded = false,
                isPending = false,
                needsReview = false,
                transferPairId = pairId,
                recurringSeriesId = null,
                importBatchId = null,
                fingerprint = TransactionFingerprint.of(account.id, date, amountMinor, description),
                anomalyScore = null,
                entrySource = if (account.type == AccountType.CASH) EntrySource.MANUAL else EntrySource.IMPORT,
                sync = SyncColumns(updatedAt = now, dirty = false, deviceId = deviceId),
            )
            return SeedTransaction(entity, tagIds)
        }
    }

    private companion object {
        val IST: ZoneOffset = ZoneOffset.ofHoursMinutes(5, 30)
        const val IST_OFFSET_MINUTES = 330
    }
}
