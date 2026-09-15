package io.github.ashishkupadhyay.mantis.core.common.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Injectable time source. `java.time` is used throughout (available from API 26; minSdk is 28), which keeps
 * period arithmetic (month-start day, salary-day months) on a mature API without an extra dependency.
 */
interface Clock {
    fun now(): Instant
    val zone: ZoneId

    // Java 8 form on purpose: LocalDate.ofInstant is Java 9 (Android 34+) and this module is not seen by Android Lint.
    fun today(): LocalDate = now().atZone(zone).toLocalDate()
    fun nowLocal(): LocalDateTime = LocalDateTime.ofInstant(now(), zone)
    fun epochMillis(): Long = now().toEpochMilli()
}

class SystemClock(override val zone: ZoneId = ZoneId.systemDefault()) : Clock {
    override fun now(): Instant = Instant.now()
}

/** Deterministic clock for tests and previews. */
class FixedClock(private var instant: Instant, override val zone: ZoneId = ZoneId.of("Asia/Kolkata")) : Clock {
    override fun now(): Instant = instant
    fun set(instant: Instant) { this.instant = instant }
    fun advanceMillis(millis: Long) { instant = instant.plusMillis(millis) }
}
