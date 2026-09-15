package io.github.ashishkupadhyay.mantis.feature.transactions.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ashishkupadhyay.mantis.core.domain.message.UserMessage
import io.github.ashishkupadhyay.mantis.core.domain.message.UserMessages
import io.github.ashishkupadhyay.mantis.core.domain.repository.AccountRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.CategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.TagRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.TransactionRepository
import io.github.ashishkupadhyay.mantis.core.model.Account
import io.github.ashishkupadhyay.mantis.core.model.Category
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.Tag
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the detail screen shows, resolved (doc 05 §4.3). */
data class TransactionDetailUiState(
    val transaction: Transaction? = null,
    val category: Category? = null,
    val categoryGroup: Category? = null,
    val account: Account? = null,
    val pairAccount: Account? = null,
    val tags: ImmutableList<Tag> = persistentListOf(),
    val splitCategories: ImmutableList<Category> = persistentListOf(),
    val categories: ImmutableList<Category> = persistentListOf(),
    /** After a correction: "Always categorize <pattern> as <category>?" (FR-CAT-6). */
    val ruleOffer: RuleOffer? = null,
    val loaded: Boolean = false,
) {
    val missing: Boolean get() = loaded && transaction == null
}

/** The rule the detail screen offers to create after the user corrects a category. */
data class RuleOffer(val pattern: String, val categoryId: CategoryId, val categoryName: String)

sealed interface TransactionDetailEvent {
    /** "This is wrong" / choose category — always recorded as the user's decision (FR-TXN-12). */
    data class SetCategory(val categoryId: CategoryId?) : TransactionDetailEvent
    data object DismissRuleOffer : TransactionDetailEvent
    data class SetExcluded(val excluded: Boolean) : TransactionDetailEvent
    data object Delete : TransactionDetailEvent
}

sealed interface TransactionDetailEffect {
    data object Deleted : TransactionDetailEffect
}

@HiltViewModel(assistedFactory = TransactionDetailViewModel.Factory::class)
class TransactionDetailViewModel @AssistedInject constructor(
    @Assisted private val transactionId: String,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    accounts: AccountRepository,
    tags: TagRepository,
    private val messages: UserMessages,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(transactionId: String): TransactionDetailViewModel
    }

    private val id = TransactionId(transactionId)
    private val ruleOffer = MutableStateFlow<RuleOffer?>(null)

    val state: StateFlow<TransactionDetailUiState> = combine(
        transactions.observeTransaction(id),
        categories.observeCategories(),
        accounts.observeAccounts(includeArchived = true),
        tags.observeTags(),
        ruleOffer,
    ) { transaction, cats, accs, allTags, offer ->
        if (transaction == null) return@combine TransactionDetailUiState(loaded = true, categories = cats.toImmutableList())
        val byId = cats.associateBy { it.id }
        val category = transaction.categoryId?.let(byId::get)
        val pair = transaction.transferPairId?.let { transactions.getTransaction(it) }
        TransactionDetailUiState(
            transaction = transaction,
            category = category,
            categoryGroup = category?.parentId?.let(byId::get),
            account = accs.firstOrNull { it.id == transaction.accountId },
            pairAccount = pair?.let { p -> accs.firstOrNull { it.id == p.accountId } },
            tags = allTags.filter { it.id in transaction.tags }.toImmutableList(),
            splitCategories = transaction.splits.mapNotNull { byId[it.categoryId] }.toImmutableList(),
            categories = cats.toImmutableList(),
            ruleOffer = offer,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TransactionDetailUiState())

    private val _effects = Channel<TransactionDetailEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: TransactionDetailEvent) {
        when (event) {
            is TransactionDetailEvent.SetCategory -> viewModelScope.launch {
                transactions.setCategory(id, event.categoryId, CategorySource.USER)
                ruleOffer.value = offerFor(event.categoryId)
            }
            TransactionDetailEvent.DismissRuleOffer -> ruleOffer.value = null
            is TransactionDetailEvent.SetExcluded -> viewModelScope.launch { transactions.setExcluded(id, event.excluded) }
            TransactionDetailEvent.Delete -> viewModelScope.launch {
                transactions.delete(id)
                messages.show(UserMessage("Transaction deleted", actionLabel = "Undo", action = { transactions.restore(id) }, long = true))
                _effects.send(TransactionDetailEffect.Deleted)
            }
        }
    }

    /** The merchant when known, otherwise the first words of the narration — enough to make a `contains` rule. */
    private suspend fun offerFor(categoryId: CategoryId?): RuleOffer? {
        val category = categoryId?.let { categories.getCategory(it) } ?: return null
        val transaction = transactions.getTransaction(id) ?: return null
        val pattern = transaction.merchantNormalized
            ?: transaction.descriptionRaw.split(WHITESPACE).filter { it.any(Char::isLetter) }.take(PATTERN_WORDS).joinToString(" ")
        if (pattern.isBlank()) return null
        return RuleOffer(pattern, category.id, category.name)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val PATTERN_WORDS = 2
        val WHITESPACE = Regex("\\s+")
    }
}
