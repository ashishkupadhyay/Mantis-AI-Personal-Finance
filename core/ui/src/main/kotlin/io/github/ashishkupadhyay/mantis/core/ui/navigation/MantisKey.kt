package io.github.ashishkupadhyay.mantis.core.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every destination in the app (doc 02 §4.2). Keys are the whole navigation contract between features: a feature
 * navigates by pushing another feature's key and never imports that feature. Keys are serialized into saved state
 * (config changes, process death), so they carry ids, not objects.
 */
@Serializable
sealed interface MantisKey : NavKey

// Top-level destinations (each owns a back stack — see MantisNavigationState).
@Serializable data object Home : MantisKey
@Serializable data object Transactions : MantisKey
@Serializable data object Budgets : MantisKey
@Serializable data object Reports : MantisKey
@Serializable data object Settings : MantisKey

// Transactions
@Serializable data class TransactionDetail(val id: String, val openReview: Boolean = false) : MantisKey
@Serializable data class TransactionsFiltered(val filter: String) : MantisKey
/** The add/edit sheet: [editId] set = edit that transaction, otherwise a new one seeded from [prefill]. */
@Serializable data class AddTransaction(val prefill: TransactionPrefill? = null, val editId: String? = null) : MantisKey
@Serializable data object ReviewQueue : MantisKey

/** Values a caller (widget, assistant, receipt flow) can pre-fill in the add sheet. All optional. */
@Serializable
data class TransactionPrefill(
    val amountMinor: Long? = null,
    val merchant: String? = null,
    val categoryKey: String? = null,
    val accountId: String? = null,
    val note: String? = null,
    /** "EXPENSE", "INCOME" or "TRANSFER"; defaults to expense. */
    val type: String? = null,
)

// Budgets
@Serializable data class BudgetDetail(val id: String) : MantisKey

// Reports & views
@Serializable data object Insights : MantisKey
@Serializable data object Recurring : MantisKey
@Serializable data class ViewEditor(val savedViewId: String? = null) : MantisKey
@Serializable data class SavedView(val id: String) : MantisKey

// Categories & rules (Settings → Categories & rules; the rule editor is also offered after a correction, FR-CAT-6)
@Serializable data object Categories : MantisKey
/** Create (`id == null`) or edit a category; [parentId] preselects the group for a new leaf. */
@Serializable data class CategoryEditor(val id: String? = null, val parentId: String? = null) : MantisKey
@Serializable data object CategoryRules : MantisKey
/** Create (`id == null`) or edit a rule; the other fields pre-fill a new rule from a corrected transaction. */
@Serializable data class RuleEditor(
    val id: String? = null,
    val pattern: String? = null,
    val categoryId: String? = null,
    val fromTransactionId: String? = null,
) : MantisKey

// Accounts
@Serializable data object Accounts : MantisKey
@Serializable data class AccountDetail(val id: String) : MantisKey
/** Create (`id == null`) or edit an account. */
@Serializable data class AccountEditor(val id: String? = null) : MantisKey

// Import, receipts, assistant
@Serializable data class ImportWizard(val uri: String? = null) : MantisKey
@Serializable data class ScanReceipt(val attachToTransactionId: String? = null) : MantisKey
@Serializable data class ReceiptReview(val draftId: String) : MantisKey
@Serializable data object Assistant : MantisKey

// Settings sub-screens. AiSettings is reachable from outside the feature (deep links, notifications).
@Serializable data object AiSettings : MantisKey

enum class SettingsSectionKind { APPEARANCE, MONEY, SECURITY, PRIVACY, ABOUT, DEVELOPER }

@Serializable data class SettingsSection(val kind: SettingsSectionKind) : MantisKey

/** Design-system catalogue (doc 05 §11); gated to debug builds by the Settings feature. */
@Serializable data object DesignCatalogue : MantisKey
