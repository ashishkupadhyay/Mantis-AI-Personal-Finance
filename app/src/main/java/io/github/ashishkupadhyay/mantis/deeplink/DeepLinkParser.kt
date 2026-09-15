package io.github.ashishkupadhyay.mantis.deeplink

import io.github.ashishkupadhyay.mantis.core.ui.navigation.AiSettings
import io.github.ashishkupadhyay.mantis.core.ui.navigation.BudgetDetail
import io.github.ashishkupadhyay.mantis.core.ui.navigation.ImportWizard
import io.github.ashishkupadhyay.mantis.core.ui.navigation.MantisKey
import io.github.ashishkupadhyay.mantis.core.ui.navigation.SavedView
import io.github.ashishkupadhyay.mantis.core.ui.navigation.ScanReceipt
import io.github.ashishkupadhyay.mantis.core.ui.navigation.TopLevelDestination
import io.github.ashishkupadhyay.mantis.core.ui.navigation.TransactionDetail
import io.github.ashishkupadhyay.mantis.core.ui.navigation.TransactionsFiltered
import java.net.URI
import java.net.URLDecoder
import javax.inject.Inject

/** A resolved deep link: the tab to select and the keys to place on top of that tab's root. */
data class DeepLink(val destination: TopLevelDestination, val stack: List<MantisKey> = emptyList())

/**
 * Allow-listing parser for `mantis://` links (doc 02 §4.2, NFR-20f). Every accepted shape is enumerated in
 * [routes]; anything else — other schemes, unknown hosts, extra segments, unexpected query parameters, malformed
 * ids, oversized input — yields `null` and the app simply opens normally. Ids are UUIDv7 strings and are validated
 * as such before they reach a screen.
 */
class DeepLinkParser @Inject constructor() {

    private val routes: Map<String, (segments: List<String>, uri: URI) -> DeepLink?> = mapOf(
        "txn" to { segments, _ -> transaction(segments) },
        "transactions" to { segments, uri -> if (segments.isEmpty()) transactions(uri) else null },
        "budget" to { segments, _ -> singleId(segments) { DeepLink(TopLevelDestination.BUDGETS, listOf(BudgetDetail(it))) } },
        "view" to { segments, _ -> singleId(segments) { DeepLink(TopLevelDestination.REPORTS, listOf(SavedView(it))) } },
        "import" to { segments, _ -> noSegments(segments) { DeepLink(TopLevelDestination.HOME, listOf(ImportWizard())) } },
        "scan" to { segments, _ -> noSegments(segments) { DeepLink(TopLevelDestination.HOME, listOf(ScanReceipt())) } },
        "settings" to { segments, _ ->
            if (segments == listOf("ai")) DeepLink(TopLevelDestination.SETTINGS, listOf(AiSettings)) else null
        },
    )

    fun parse(uri: String?): DeepLink? {
        val parsed = validUri(uri) ?: return null
        // Only the transactions list takes query parameters; a query anywhere else is not a link we issued.
        if (parsed.rawQuery != null && parsed.host != "transactions") return null
        val segments = parsed.path.orEmpty().split('/').filter { it.isNotEmpty() }
        return routes[parsed.host]?.invoke(segments, parsed)
    }

    private fun validUri(uri: String?): URI? {
        if (uri.isNullOrEmpty() || uri.length > MAX_LENGTH) return null
        val parsed = runCatching { URI(uri) }.getOrNull() ?: return null
        return parsed.takeIf { it.scheme == SCHEME && it.host != null }
    }

    private fun transaction(segments: List<String>): DeepLink? {
        val id = segments.firstOrNull()?.takeIf(::isId) ?: return null
        return when {
            segments.size == 1 -> DeepLink(TopLevelDestination.TRANSACTIONS, listOf(TransactionDetail(id)))
            segments.size == 2 && segments[1] == "review" ->
                DeepLink(TopLevelDestination.TRANSACTIONS, listOf(TransactionDetail(id, openReview = true)))
            else -> null
        }
    }

    private fun transactions(uri: URI): DeepLink? {
        val query = queryParams(uri) ?: return null
        val filter = query["filter"] ?: return if (query.isEmpty()) DeepLink(TopLevelDestination.TRANSACTIONS) else null
        if (query.size != 1 || filter.length > MAX_FILTER_LENGTH || filter.any { it.isISOControl() }) return null
        return DeepLink(TopLevelDestination.TRANSACTIONS, listOf(TransactionsFiltered(filter)))
    }

    private inline fun singleId(segments: List<String>, build: (String) -> DeepLink): DeepLink? =
        segments.singleOrNull()?.takeIf(::isId)?.let(build)

    private inline fun noSegments(segments: List<String>, build: () -> DeepLink): DeepLink? =
        if (segments.isEmpty()) build() else null

    private fun isId(value: String): Boolean = ID_PATTERN.matches(value)

    /** Decoded query parameters, or null when any value fails to decode. */
    private fun queryParams(uri: URI): Map<String, String>? {
        val raw = uri.rawQuery ?: return emptyMap()
        return raw.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val value = runCatching { URLDecoder.decode(pair.substringAfter('=', ""), Charsets.UTF_8.name()) }.getOrNull()
                ?: return null
            pair.substringBefore('=') to value
        }
    }

    private companion object {
        const val SCHEME = "mantis"
        const val MAX_LENGTH = 512
        const val MAX_FILTER_LENGTH = 200
        val ID_PATTERN = Regex("[A-Za-z0-9-]{1,64}")
    }
}
