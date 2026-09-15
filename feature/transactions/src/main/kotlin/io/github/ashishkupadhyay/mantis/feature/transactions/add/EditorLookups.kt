package io.github.ashishkupadhyay.mantis.feature.transactions.add

import io.github.ashishkupadhyay.mantis.core.common.time.Clock
import io.github.ashishkupadhyay.mantis.core.domain.repository.AccountRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.CategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.SpendingRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.TagRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.TransactionRepository
import io.github.ashishkupadhyay.mantis.core.model.Account
import io.github.ashishkupadhyay.mantis.core.model.Category
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Everything the add/edit sheet reads (never writes); keeps the ViewModel's constructor small. */
class EditorLookups @Inject constructor(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val tags: TagRepository,
    private val spending: SpendingRepository,
    private val clock: Clock,
) {
    suspend fun accounts(): List<Account> = accounts.observeAccounts(includeArchived = false).first()

    suspend fun categories(): List<Category> = categories.observeCategories().first().filter { !it.isHidden && !it.meta.isDeleted }

    suspend fun transaction(id: TransactionId): Transaction? = transactions.getTransaction(id)

    /** The other leg of a transfer, if [transaction] is one. */
    suspend fun pairOf(transaction: Transaction): Transaction? = transaction.transferPairId?.let { transactions.getTransaction(it) }

    suspend fun tagNames(transaction: Transaction): List<String> = tags.tags(transaction.tags).map { it.name }

    /** Most-used categories of [type] in the last [LIKELY_DAYS] days, most frequent first. */
    suspend fun likely(type: TransactionType, limit: Int): List<CategoryId> =
        spending.frequentCategories(type, clock.today().minusDays(LIKELY_DAYS), limit)

    private companion object {
        const val LIKELY_DAYS = 90L
    }
}
