package io.github.ashishkupadhyay.mantis.core.common.flags

/**
 * Feature flags for work that ships dark or behind a rollout (doc 06 §13). Values come from build config
 * and remote config later; defaults here are the ones the MVP is built against.
 */
enum class Flag(val default: Boolean) {
    /** Bank/UPI app notification capture (FR-IMP-12) — Play-policy sensitive, ships off. */
    NOTIFICATION_CAPTURE(false),

    /** On-device LLM providers: AICore / MediaPipe (FR-LLM-7). */
    ON_DEVICE_LLM(false),

    /** Android 16+ AppFunctions service (FR-AFN). */
    APP_FUNCTIONS(false),

    /** Receipt scanning epic (FR-RCP), v1.1. */
    RECEIPT_SCANNING(false),

    /** Google sign-in + sync (FR-ONB-3, FR-SYN) — enabled once the backend exists (M4). */
    ACCOUNT_SYNC(false),

    /** BYOK LLM layer (FR-LLM) — enabled in M3. */
    LLM_PROVIDERS(false),

    /** Debug-only tooling such as the motion catalogue and the sample-data seeder. */
    DEBUG_TOOLS(false),
}

interface FeatureFlags {
    fun isEnabled(flag: Flag): Boolean
}

/** Static flags: defaults plus explicit overrides (build type, remote config, tests). */
class StaticFeatureFlags(private val overrides: Map<Flag, Boolean> = emptyMap()) : FeatureFlags {
    override fun isEnabled(flag: Flag): Boolean = overrides[flag] ?: flag.default
}
