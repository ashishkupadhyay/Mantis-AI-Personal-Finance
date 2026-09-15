package io.github.ashishkupadhyay.mantis.core.common.log

/**
 * Logging facade (doc 02 §9): the only logging API allowed outside this module (Konsist forbids `android.util.Log`).
 * Messages are lambdas so release builds pay nothing; the [Redact] helpers keep amounts, descriptions and
 * identifiers out of logs by construction (NFR-17).
 */
interface Logger {
    fun v(tag: String, message: () -> String)
    fun d(tag: String, message: () -> String)
    fun i(tag: String, message: () -> String)
    fun w(tag: String, throwable: Throwable? = null, message: () -> String)
    fun e(tag: String, throwable: Throwable? = null, message: () -> String)
}

/** Discards everything — the release default until an opt-in reporter is wired (FR-PRV-5). */
object NoOpLogger : Logger {
    override fun v(tag: String, message: () -> String) = Unit
    override fun d(tag: String, message: () -> String) = Unit
    override fun i(tag: String, message: () -> String) = Unit
    override fun w(tag: String, throwable: Throwable?, message: () -> String) = Unit
    override fun e(tag: String, throwable: Throwable?, message: () -> String) = Unit
}

/** Prints to stdout — for JVM tests and tooling. Android debug builds bind a Logcat implementation in `app`. */
class PrintLogger(private val minLevel: Level = Level.DEBUG) : Logger {
    enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR }

    private fun log(level: Level, tag: String, throwable: Throwable?, message: () -> String) {
        if (level.ordinal < minLevel.ordinal) return
        println("${level.name.first()}/$tag: ${message()}")
        throwable?.printStackTrace()
    }

    override fun v(tag: String, message: () -> String) = log(Level.VERBOSE, tag, null, message)
    override fun d(tag: String, message: () -> String) = log(Level.DEBUG, tag, null, message)
    override fun i(tag: String, message: () -> String) = log(Level.INFO, tag, null, message)
    override fun w(tag: String, throwable: Throwable?, message: () -> String) = log(Level.WARN, tag, throwable, message)
    override fun e(tag: String, throwable: Throwable?, message: () -> String) = log(Level.ERROR, tag, throwable, message)
}

/** Redaction helpers for log messages. Use them instead of interpolating raw values. */
object Redact {
    private const val KEEP = 4

    /** `"a1b2c3d4-…"` — enough of an id to correlate, not enough to identify. */
    fun id(value: String?): String = value?.take(KEEP + KEEP)?.plus("…") ?: "null"

    /** Amounts never appear in logs; only their sign and order of magnitude. */
    fun amount(minor: Long): String {
        val digits = kotlin.math.abs(minor).toString().length
        val sign = if (minor < 0) "-" else "+"
        return "${sign}~1e$digits"
    }

    /** Free text (descriptions, notes, merchant names) is replaced by its length. */
    fun text(value: String?): String = value?.let { "<text:${it.length}>" } ?: "null"

    /** Keys and tokens: only a trailing fragment, never the value. */
    fun secret(value: String?): String = value?.let { "…${it.takeLast(KEEP)}" } ?: "null"
}
