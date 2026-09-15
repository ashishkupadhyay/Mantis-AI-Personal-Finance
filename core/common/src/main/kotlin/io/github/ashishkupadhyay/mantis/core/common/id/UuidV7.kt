package io.github.ashishkupadhyay.mantis.core.common.id

import java.security.SecureRandom
import java.util.UUID

/**
 * UUIDv7 generator (RFC 9562): 48-bit Unix-millisecond timestamp, version nibble 7, 12 random bits,
 * variant bits, 62 random bits. Time-ordered ids keep B-tree inserts sequential in Room/Postgres and
 * are generated on device, so sync never remaps ids (doc 02 §5.1).
 *
 * Within one millisecond ids are made strictly increasing by using the 12-bit `rand_a` field as a counter.
 */
class UuidV7(
    private val random: SecureRandom = SecureRandom(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private var lastMillis = -1L
    private var counter = 0

    @Synchronized
    fun next(): UUID {
        var millis = nowMillis()
        if (millis < lastMillis) millis = lastMillis // clock went backwards: keep monotonic
        if (millis == lastMillis) {
            counter++
            if (counter > MAX_COUNTER) { // 4096 ids in one ms: roll into the next millisecond
                millis++
                counter = 0
            }
        } else {
            counter = random.nextInt(COUNTER_SEED_RANGE) // random start so ids from different devices don't align
        }
        lastMillis = millis

        val msb = (millis shl TIMESTAMP_SHIFT) or (VERSION_7 shl VERSION_SHIFT) or counter.toLong()
        val lsb = (random.nextLong() and LSB_RANDOM_MASK) or VARIANT_RFC
        return UUID(msb, lsb)
    }

    fun nextString(): String = next().toString()

    companion object {
        private const val TIMESTAMP_SHIFT = 16
        private const val VERSION_SHIFT = 12
        private const val VERSION_7 = 0x7L
        private const val MAX_COUNTER = 0xFFF
        private const val COUNTER_SEED_RANGE = 0x800
        private const val LSB_RANDOM_MASK = 0x3FFF_FFFF_FFFF_FFFFL
        private const val VARIANT_RFC = Long.MIN_VALUE // 0x8000_0000_0000_0000

        /** Extracts the embedded creation time in Unix milliseconds. */
        fun timestampMillis(uuid: UUID): Long = uuid.mostSignificantBits ushr TIMESTAMP_SHIFT

        fun isV7(uuid: UUID): Boolean = uuid.version() == VERSION_7.toInt()
    }
}
