package io.github.ashishkupadhyay.mantis.benchmark

import android.content.Intent
import io.github.ashishkupadhyay.mantis.core.domain.message.UserMessages
import io.github.ashishkupadhyay.mantis.core.domain.repository.CategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.DevelopmentGate
import io.github.ashishkupadhyay.mantis.core.domain.repository.DevelopmentRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.PreferencesRepository
import javax.inject.Inject

/**
 * Launch-intent hooks for the `:benchmark` module (NFR-2/NFR-3): `--ei mantis.seed.months N` completes onboarding
 * and seeds N months of sample data, then posts "Seeded …" so the benchmark can wait for it. Only honoured when
 * the [DevelopmentGate] allows developer tools — release builds ignore the extras entirely.
 */
class BenchmarkHooks @Inject constructor(
    private val gate: DevelopmentGate,
    private val preferences: PreferencesRepository,
    private val categories: CategoryRepository,
    private val development: DevelopmentRepository,
    private val messages: UserMessages,
) {
    fun requestedMonths(intent: Intent?): Int? =
        intent?.getIntExtra(EXTRA_SEED_MONTHS, 0)?.takeIf { it > 0 && gate.toolsEnabled }

    suspend fun prepare(months: Int) {
        if (!gate.toolsEnabled) return
        categories.ensureDefaults()
        preferences.update { it.copy(onboardingCompleted = true) }
        development.seedSampleData(months)
            .onSuccess { messages.show("Seeded $it transactions") }
            .onFailure { messages.show("Seeding failed: ${it.message}") }
    }

    companion object {
        const val EXTRA_SEED_MONTHS = "mantis.seed.months"
    }
}
