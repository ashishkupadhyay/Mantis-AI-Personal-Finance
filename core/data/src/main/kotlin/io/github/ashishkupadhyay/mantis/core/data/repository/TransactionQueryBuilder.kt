package io.github.ashishkupadhyay.mantis.core.data.repository

import androidx.room3.RoomRawQuery
import io.github.ashishkupadhyay.mantis.core.model.TransactionFilter
import io.github.ashishkupadhyay.mantis.core.model.TransactionSort

/**
 * Turns a [TransactionFilter] into a parameterised SQL query (NFR-20c): every user value is bound, never
 * concatenated, and free text reaches FTS only as sanitised prefix terms (see [ftsMatchExpression]).
 */
internal object TransactionQueryBuilder {

    fun build(filter: TransactionFilter, limit: Int? = null): RoomRawQuery {
        val (from, where, args) = clauses(filter)
        val orderBy = when (filter.sort) {
            TransactionSort.DATE_DESC -> "transactions.postedAt DESC, transactions.id DESC"
            TransactionSort.DATE_ASC -> "transactions.postedAt ASC, transactions.id ASC"
            TransactionSort.AMOUNT_DESC -> "ABS(transactions.amountMinor) DESC, transactions.id DESC"
            TransactionSort.AMOUNT_ASC -> "ABS(transactions.amountMinor) ASC, transactions.id ASC"
        }
        val limited = if (limit != null) " LIMIT ?" else ""
        val bound = if (limit != null) args + limit.toLong() else args
        return rawQuery("SELECT transactions.* FROM $from WHERE $where ORDER BY $orderBy$limited", bound)
    }

    /** Same rows as [build], collapsed to one signed total and count per calendar day (transfers and excluded rows skipped). */
    fun buildDayTotals(filter: TransactionFilter): RoomRawQuery {
        val (from, where, args) = clauses(filter)
        val counted = "$where AND transactions.type != 'TRANSFER' AND transactions.isExcluded = 0"
        return rawQuery(
            "SELECT transactions.postedLocalDate AS day, SUM(transactions.amountMinor) AS totalMinor, COUNT(*) AS count " +
                "FROM $from WHERE $counted GROUP BY transactions.postedLocalDate ORDER BY day DESC",
            args,
        )
    }

    private data class Clauses(val from: String, val where: String, val args: List<Any>)

    private fun clauses(filter: TransactionFilter): Clauses {
        val where = mutableListOf("transactions.deletedAt IS NULL")
        val args = mutableListOf<Any>()

        fun inClause(column: String, values: Collection<String>) {
            if (values.isEmpty()) return
            where += "transactions.$column IN (${placeholders(values.size)})"
            args.addAll(values)
        }

        inClause("accountId", filter.accountIds.map { it.value })
        inClause("categoryId", filter.categoryIds.map { it.value })
        inClause("type", filter.types.map { it.name })
        filter.dateRange?.let { range ->
            where += "transactions.postedLocalDate BETWEEN ? AND ?"
            args += range.start.toString()
            args += range.endInclusive.toString()
        }
        if (!filter.includeExcluded) where += "transactions.isExcluded = 0"
        if (filter.needsReviewOnly) where += "transactions.needsReview = 1"
        if (filter.uncategorizedOnly) where += "transactions.categoryId IS NULL"
        filter.minAmountMinor?.let { where += "ABS(transactions.amountMinor) >= ?"; args += it }
        filter.maxAmountMinor?.let { where += "ABS(transactions.amountMinor) <= ?"; args += it }
        if (filter.tagIds.isNotEmpty()) {
            where += "transactions.id IN (SELECT transactionId FROM transaction_tags WHERE tagId IN (${placeholders(filter.tagIds.size)}))"
            args.addAll(filter.tagIds.map { it.value })
        }
        val match = ftsMatchExpression(filter.query)
        val join = if (match != null) {
            where += "transactions_fts MATCH ?"
            args += match
            " JOIN transactions_fts ON transactions.rowid = transactions_fts.rowid"
        } else {
            ""
        }
        return Clauses(from = "transactions$join", where = where.joinToString(" AND "), args = args)
    }

    private fun rawQuery(sql: String, args: List<Any>): RoomRawQuery =
        RoomRawQuery(sql) { statement ->
            args.forEachIndexed { index, arg ->
                when (arg) {
                    is Long -> statement.bindLong(index + 1, arg)
                    else -> statement.bindText(index + 1, arg.toString())
                }
            }
        }

    /**
     * Free text → FTS4 expression: keep letters and digits only, each term as a prefix (`swig*`), implicit AND.
     * Returns null when nothing searchable remains, so the query falls back to a plain list.
     */
    fun ftsMatchExpression(query: String): String? {
        val terms = query.split(WHITESPACE)
            .map { it.filter(Char::isLetterOrDigit) }
            .filter { it.isNotEmpty() }
            .take(MAX_TERMS)
        if (terms.isEmpty()) return null
        return terms.joinToString(" ") { "$it*" }
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(",")

    private val WHITESPACE = Regex("\\s+")
    private const val MAX_TERMS = 8
}
