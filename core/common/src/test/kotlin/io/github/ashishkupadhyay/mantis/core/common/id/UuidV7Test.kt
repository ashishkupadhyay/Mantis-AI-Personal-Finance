package io.github.ashishkupadhyay.mantis.core.common.id

import io.kotest.matchers.collections.shouldBeSorted
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class UuidV7Test {

    @Test
    fun `ids are version 7, RFC variant and embed the timestamp`() {
        val now = 1_757_700_000_000L
        val gen = UuidV7(nowMillis = { now })
        val id = gen.next()
        id.version() shouldBe 7
        id.variant() shouldBe 2
        UuidV7.timestampMillis(id) shouldBe now
        UuidV7.isV7(id) shouldBe true
        UuidV7.isV7(UUID.randomUUID()) shouldBe false
    }

    @Test
    fun `ids generated in the same millisecond are strictly increasing and unique`() {
        val gen = UuidV7(nowMillis = { 1_757_700_000_000L })
        val ids = List(5_000) { gen.next().toString() }
        ids.toSet() shouldHaveSize ids.size
        ids.shouldBeSorted() // lexical order == generation order for UUIDv7 strings
    }

    @Test
    fun `a clock that goes backwards never produces a smaller id`() {
        var t = 1_757_700_000_000L
        val gen = UuidV7(nowMillis = { t })
        val first = gen.next()
        t -= 10_000
        val second = gen.next()
        (second.toString() > first.toString()) shouldBe true
    }

    @Test
    fun `ids across milliseconds sort by time`() {
        var t = 1_757_700_000_000L
        val gen = UuidV7(nowMillis = { t })
        val ids = (1..200).map { t += 3; gen.next().toString() }
        ids.shouldBeSorted()
    }
}
