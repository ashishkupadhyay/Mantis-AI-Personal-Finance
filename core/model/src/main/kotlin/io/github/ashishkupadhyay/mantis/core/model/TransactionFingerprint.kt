package io.github.ashishkupadhyay.mantis.core.model

import java.security.MessageDigest
import java.time.LocalDate

/**
 * Stable identity of a money movement for import de-duplication (FR-IMP-5, doc 02 §7): SHA-256 over account,
 * calendar date, signed minor amount and the trimmed, lower-cased raw description. Recomputed whenever any of
 * those change, so it is derived data, never user-visible.
 */
object TransactionFingerprint {
    fun of(accountId: AccountId, date: LocalDate, amountMinor: Long, description: String): String =
        of(accountId.value, date, amountMinor, description)

    fun of(accountId: String, date: LocalDate, amountMinor: Long, description: String): String {
        val input = "$accountId|$date|$amountMinor|${description.trim().lowercase()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
