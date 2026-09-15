package io.github.ashishkupadhyay.mantis.feature.transactions.list

import io.github.ashishkupadhyay.mantis.core.model.Account
import io.github.ashishkupadhyay.mantis.core.model.Category
import io.github.ashishkupadhyay.mantis.core.model.DateRange
import io.github.ashishkupadhyay.mantis.core.model.Tag
import io.github.ashishkupadhyay.mantis.core.model.TransactionFilter
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import java.time.LocalDate

/**
 * The compact `key=value&key=value` filter language behind `mantis://transactions?filter=…` links, widgets and
 * notifications (doc 02 §4.2). Unknown keys and unresolvable values are ignored rather than rejected, so an old
 * link still opens a sensible list.
 *
 * Keys: `account` (ids), `category` (keys or ids), `tag` (names or ids), `type` (EXPENSE|INCOME|TRANSFER),
 * `from`/`to` (ISO dates), `q` (search text), `review=1`, `uncategorized=1`, `excluded=0`. Lists are comma-separated.
 */
object FilterQuery {

    fun parse(raw: String, categories: List<Category>, accounts: List<Account>, tags: List<Tag>): TransactionFilter {
        val params = raw.split('&').mapNotNull { pair ->
            val key = pair.substringBefore('=').trim()
            if (key.isEmpty()) null else key to pair.substringAfter('=', "").trim()
        }.groupBy({ it.first }, { it.second })
        fun values(key: String) = params[key].orEmpty().flatMap { it.split(',') }.map(String::trim).filter { it.isNotEmpty() }
        fun flag(key: String) = params[key]?.lastOrNull() == "1"

        val from = values("from").firstOrNull()?.let(::parseDate)
        val to = values("to").firstOrNull()?.let(::parseDate)
        val range = when {
            from != null && to != null && !to.isBefore(from) -> DateRange(from, to)
            from != null && to == null -> DateRange(from, OPEN_END)
            from == null && to != null -> DateRange(OPEN_START, to)
            else -> null
        }
        return TransactionFilter(
            accountIds = values("account").mapNotNull { id -> accounts.firstOrNull { it.id.value == id }?.id }.toSet(),
            categoryIds = values("category").mapNotNull { ref ->
                categories.firstOrNull { it.key == ref || it.id.value == ref }?.id
            }.toSet(),
            tagIds = values("tag").mapNotNull { ref ->
                tags.firstOrNull { it.id.value == ref || it.normalizedName == Tag.normalize(ref) }?.id
            }.toSet(),
            types = values("type").mapNotNull { name ->
                TransactionType.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            }.toSet(),
            dateRange = range,
            query = values("q").joinToString(" "),
            includeExcluded = params["excluded"]?.lastOrNull() != "0",
            needsReviewOnly = flag("review"),
            uncategorizedOnly = flag("uncategorized"),
        )
    }

    private fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

    /** Open-ended ranges are expressed with far-away bounds; `postedLocalDate` is an ISO string in SQL. */
    @Suppress("MagicNumber")
    private val OPEN_END: LocalDate = LocalDate.of(2999, 12, 31)
    private val OPEN_START: LocalDate = LocalDate.ofEpochDay(0)
}
