package io.github.ashishkupadhyay.mantis.core.domain.security

import io.github.ashishkupadhyay.mantis.core.common.time.FixedClock
import io.github.ashishkupadhyay.mantis.core.model.preferences.AppLockPreferences
import io.github.ashishkupadhyay.mantis.core.model.preferences.LockTimeout
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/** FR-ONB-6 (timeouts) and NFR-20h (locked on process start). */
class DefaultAppLockManagerTest {

    private val clock = FixedClock(Instant.parse("2026-09-13T10:00:00Z"))
    private val manager = DefaultAppLockManager(clock)
    private val enabled = AppLockPreferences(enabled = true, timeout = LockTimeout.ONE_MINUTE)

    @Test
    fun `NFR_20h starts locked and stays locked when App Lock is enabled`() {
        manager.state.value shouldBe LockState.LOCKED
        manager.applyPreferences(enabled)
        manager.state.value shouldBe LockState.LOCKED
        manager.isUnlockedForeground shouldBe false
    }

    @Test
    fun `disabled App Lock never blocks content`() {
        manager.applyPreferences(AppLockPreferences(enabled = false))
        manager.onForeground()
        manager.state.value shouldBe LockState.DISABLED
        manager.isUnlockedForeground shouldBe true
    }

    @Test
    fun `FR_ONB_6 relocks only after the configured background timeout`() {
        manager.applyPreferences(enabled)
        manager.onForeground()
        manager.unlock()
        manager.state.value shouldBe LockState.UNLOCKED

        manager.onBackground()
        clock.advanceMillis(30_000)
        manager.onForeground()
        manager.state.value shouldBe LockState.UNLOCKED

        manager.onBackground()
        clock.advanceMillis(60_000)
        manager.onForeground()
        manager.state.value shouldBe LockState.LOCKED
    }

    @Test
    fun `immediate timeout locks on every background`() {
        manager.applyPreferences(enabled.copy(timeout = LockTimeout.IMMEDIATELY))
        manager.onForeground()
        manager.unlock()
        manager.onBackground()
        manager.onForeground()
        manager.state.value shouldBe LockState.LOCKED
    }

    @Test
    fun `enabling the lock from Settings does not lock the current session`() {
        manager.applyPreferences(AppLockPreferences(enabled = false))
        manager.onForeground()
        manager.applyPreferences(enabled)
        manager.state.value shouldBe LockState.UNLOCKED
        manager.lock()
        manager.state.value shouldBe LockState.LOCKED
    }
}
