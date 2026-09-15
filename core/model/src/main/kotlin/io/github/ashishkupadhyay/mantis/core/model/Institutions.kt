package io.github.ashishkupadhyay.mantis.core.model

/** A bank, card issuer or wallet from the bundled Indian list (FR-ACC-1). [brandColor] is ARGB. */
data class Institution(val id: String, val name: String, val brandColor: Long, val kinds: Set<AccountType>)

/** Bundled institutions with brand colours; user-entered names remain possible for anything not listed. */
object Institutions {
    private val bank = setOf(AccountType.BANK, AccountType.CREDIT_CARD, AccountType.LOAN)
    private val wallet = setOf(AccountType.WALLET)

    val all: List<Institution> = listOf(
        Institution("hdfc", "HDFC Bank", 0xFF004C8F, bank),
        Institution("icici", "ICICI Bank", 0xFFB02A30, bank),
        Institution("sbi", "State Bank of India", 0xFF22409A, bank),
        Institution("axis", "Axis Bank", 0xFF97144D, bank),
        Institution("kotak", "Kotak Mahindra Bank", 0xFFED1C24, bank),
        Institution("yes", "YES Bank", 0xFF00539F, bank),
        Institution("idfc", "IDFC FIRST Bank", 0xFF9C1D26, bank),
        Institution("indusind", "IndusInd Bank", 0xFF9B2335, bank),
        Institution("pnb", "Punjab National Bank", 0xFFA20E37, bank),
        Institution("bob", "Bank of Baroda", 0xFFF26A21, bank),
        Institution("canara", "Canara Bank", 0xFF0A6EB4, bank),
        Institution("union", "Union Bank of India", 0xFF1E3D8F, bank),
        Institution("federal", "Federal Bank", 0xFF0B4F9C, bank),
        Institution("rbl", "RBL Bank", 0xFF1F3B73, bank),
        Institution("au", "AU Small Finance Bank", 0xFFF37021, bank),
        Institution("amex", "American Express", 0xFF006FCF, setOf(AccountType.CREDIT_CARD)),
        Institution("onecard", "OneCard", 0xFF111111, setOf(AccountType.CREDIT_CARD)),
        Institution("paytm", "Paytm", 0xFF00BAF2, wallet),
        Institution("phonepe", "PhonePe", 0xFF5F259F, wallet),
        Institution("gpay", "Google Pay", 0xFF1A73E8, wallet),
        Institution("amazonpay", "Amazon Pay", 0xFFFF9900, wallet),
        Institution("mobikwik", "MobiKwik", 0xFF0F5BD7, wallet),
        Institution("zerodha", "Zerodha", 0xFF387ED1, setOf(AccountType.INVESTMENT)),
        Institution("groww", "Groww", 0xFF00D09C, setOf(AccountType.INVESTMENT)),
        Institution("ppf", "PPF / EPF", 0xFF2E7D32, setOf(AccountType.INVESTMENT)),
    )

    fun byId(id: String?): Institution? = id?.let { key -> all.firstOrNull { it.id == key } }

    fun forType(type: AccountType): List<Institution> = all.filter { type in it.kinds }
}
