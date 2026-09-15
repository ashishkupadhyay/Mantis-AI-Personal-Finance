package io.github.ashishkupadhyay.mantis.core.importer.preset

import io.github.ashishkupadhyay.mantis.core.domain.imports.ColumnMapping
import io.github.ashishkupadhyay.mantis.core.domain.imports.ColumnRole
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportPreset

/**
 * Launch presets (FR-IMP-3). Signatures are the *normalised* header cells (lower-case, letters and digits only) in
 * column order; [PresetMatcher] accepts an exact match or ≥ 80 % token overlap. Bank exports change without notice,
 * so every preset is *beta* until verified against a real statement — a mismatch simply falls back to the mapping
 * wizard (FR-IMP-4). A remotely updated copy of this list is a later milestone.
 */
object BundledPresets {

    fun normalise(header: String): String = header.lowercase().filter { it.isLetterOrDigit() }

    private fun preset(id: String, name: String, headers: List<String>, dateFormat: String, vararg roles: Pair<Int, ColumnRole>) =
        ImportPreset(id, name, headers.map(::normalise), ColumnMapping(roles.toMap(), dateFormat))

    val all: List<ImportPreset> = listOf(
        preset(
            "hdfc", "HDFC Bank",
            listOf("Date", "Narration", "Chq./Ref.No.", "Value Dt", "Withdrawal Amt.", "Deposit Amt.", "Closing Balance"),
            "dd/MM/yy",
            0 to ColumnRole.DATE, 1 to ColumnRole.DESCRIPTION, 2 to ColumnRole.REFERENCE, 4 to ColumnRole.DEBIT,
            5 to ColumnRole.CREDIT, 6 to ColumnRole.BALANCE,
        ),
        preset(
            "icici", "ICICI Bank",
            listOf(
                "S No.", "Value Date", "Transaction Date", "Cheque Number", "Transaction Remarks",
                "Withdrawal Amount (INR )", "Deposit Amount (INR )", "Balance (INR )",
            ),
            "dd/MM/yyyy",
            2 to ColumnRole.DATE, 3 to ColumnRole.REFERENCE, 4 to ColumnRole.DESCRIPTION, 5 to ColumnRole.DEBIT,
            6 to ColumnRole.CREDIT, 7 to ColumnRole.BALANCE,
        ),
        preset(
            "sbi", "State Bank of India",
            listOf("Txn Date", "Value Date", "Description", "Ref No./Cheque No.", "Debit", "Credit", "Balance"),
            "d MMM yyyy",
            0 to ColumnRole.DATE, 2 to ColumnRole.DESCRIPTION, 3 to ColumnRole.REFERENCE, 4 to ColumnRole.DEBIT,
            5 to ColumnRole.CREDIT, 6 to ColumnRole.BALANCE,
        ),
        preset(
            "axis", "Axis Bank",
            listOf("Tran Date", "CHQNO", "PARTICULARS", "DR", "CR", "BAL", "SOL"),
            "dd-MM-yyyy",
            0 to ColumnRole.DATE, 1 to ColumnRole.REFERENCE, 2 to ColumnRole.DESCRIPTION, 3 to ColumnRole.DEBIT,
            4 to ColumnRole.CREDIT, 5 to ColumnRole.BALANCE,
        ),
        preset(
            "kotak", "Kotak Mahindra Bank",
            listOf("Sl. No.", "Date", "Description", "Chq / Ref number", "Amount", "Dr / Cr", "Balance"),
            "dd/MM/yyyy",
            1 to ColumnRole.DATE, 2 to ColumnRole.DESCRIPTION, 3 to ColumnRole.REFERENCE, 4 to ColumnRole.AMOUNT,
            5 to ColumnRole.DR_CR, 6 to ColumnRole.BALANCE,
        ),
        preset(
            "yes", "YES Bank",
            listOf("Transaction Date", "Value Date", "Description", "Reference No", "Debit", "Credit", "Balance"),
            "dd/MM/yyyy",
            0 to ColumnRole.DATE, 2 to ColumnRole.DESCRIPTION, 3 to ColumnRole.REFERENCE, 4 to ColumnRole.DEBIT,
            5 to ColumnRole.CREDIT, 6 to ColumnRole.BALANCE,
        ),
        preset(
            "idfc", "IDFC FIRST Bank",
            listOf("Transaction Date", "Value Date", "Particulars", "Cheque No.", "Debit", "Credit", "Balance"),
            "dd-MMM-yyyy",
            0 to ColumnRole.DATE, 2 to ColumnRole.DESCRIPTION, 3 to ColumnRole.REFERENCE, 4 to ColumnRole.DEBIT,
            5 to ColumnRole.CREDIT, 6 to ColumnRole.BALANCE,
        ),
        preset(
            "paytm", "Paytm Payments Bank",
            listOf("Date", "Transaction Details", "Reference No", "Debit", "Credit", "Balance"),
            "dd/MM/yyyy",
            0 to ColumnRole.DATE, 1 to ColumnRole.DESCRIPTION, 2 to ColumnRole.REFERENCE, 3 to ColumnRole.DEBIT,
            4 to ColumnRole.CREDIT, 5 to ColumnRole.BALANCE,
        ),
        preset(
            "generic-debit-credit", "Generic (Date / Description / Debit / Credit)",
            listOf("Date", "Description", "Debit", "Credit"),
            "dd/MM/yyyy",
            0 to ColumnRole.DATE, 1 to ColumnRole.DESCRIPTION, 2 to ColumnRole.DEBIT, 3 to ColumnRole.CREDIT,
        ),
        preset(
            "generic-amount", "Generic (Date / Description / Amount)",
            listOf("Date", "Description", "Amount"),
            "dd/MM/yyyy",
            0 to ColumnRole.DATE, 1 to ColumnRole.DESCRIPTION, 2 to ColumnRole.AMOUNT,
        ),
    )
}

/** Exact or ≥ [MIN_OVERLAP] Jaccard match between a file's normalised headers and a preset signature (doc 02 §7 step 2). */
object PresetMatcher {
    private const val MIN_OVERLAP = 0.8

    fun match(headers: List<String>, presets: List<ImportPreset> = BundledPresets.all): ImportPreset? {
        val normalised = headers.map(BundledPresets::normalise).filter { it.isNotEmpty() }
        if (normalised.isEmpty()) return null
        presets.firstOrNull { it.headerSignature == normalised }?.let { return it }
        val tokens = normalised.toSet()
        return presets
            .map { preset -> preset to jaccard(tokens, preset.headerSignature.toSet()) }
            .filter { (_, score) -> score >= MIN_OVERLAP }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double =
        if (a.isEmpty() && b.isEmpty()) 1.0 else (a intersect b).size.toDouble() / (a union b).size
}

/** Guesses column roles from header keywords when no preset matches (FR-IMP-4). */
object MappingInference {
    private val ORDERED: List<Pair<ColumnRole, List<String>>> = listOf(
        ColumnRole.DESCRIPTION to listOf("narration", "description", "particulars", "details", "remarks", "activity"),
        ColumnRole.DEBIT to listOf("withdrawal", "debit", "moneyout", "paidout", "spent", "dr"),
        ColumnRole.CREDIT to listOf("deposit", "credit", "moneyin", "paidin", "received", "cr"),
        ColumnRole.AMOUNT to listOf("amount", "amt"),
        ColumnRole.BALANCE to listOf("balance", "bal"),
        ColumnRole.REFERENCE to listOf("ref", "chq", "cheque", "reference", "utr"),
        ColumnRole.CATEGORY to listOf("category"),
    )

    private val INDICATORS = setOf("drcr", "crdr", "type")

    /** Prefer a transaction date over a value date when both exist. */
    private fun dateColumn(cells: List<String>): Int? =
        cells.indexOfFirst { it.contains("date") && !it.contains("value") }.takeIf { it >= 0 }
            ?: cells.indexOfFirst { it.contains("date") || it == "dt" || it.startsWith("posted") }.takeIf { it >= 0 }

    /** Short keywords ("dr", "cr") must match the whole cell; longer ones may appear inside it. */
    private fun matches(cell: String, keywords: List<String>): Boolean =
        keywords.any { keyword -> cell == keyword || (keyword.length > 2 && cell.contains(keyword)) }

    fun infer(headers: List<String>, dateFormat: String): ColumnMapping {
        val cells = headers.map { BundledPresets.normalise(it) }
        val roles = mutableMapOf<Int, ColumnRole>()
        dateColumn(cells)?.let { roles[it] = ColumnRole.DATE }
        ORDERED.forEach { (role, keywords) ->
            val index = cells.indices.firstOrNull { i -> i !in roles && matches(cells[i], keywords) }
            if (index != null) roles[index] = role
        }
        // "Dr / Cr" indicator columns sit next to an unsigned amount.
        cells.indices.firstOrNull { i -> i !in roles && cells[i] in INDICATORS }?.let { roles[it] = ColumnRole.DR_CR }
        if (ColumnRole.AMOUNT in roles.values && (ColumnRole.DEBIT in roles.values || ColumnRole.CREDIT in roles.values)) {
            roles.entries.removeAll { it.value == ColumnRole.AMOUNT }
        }
        return ColumnMapping(roles, dateFormat)
    }
}
