package io.github.ashishkupadhyay.mantis.core.model.preferences

import io.github.ashishkupadhyay.mantis.core.common.money.NumberStyle
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/*
 * The same class is the domain model features read and the proto wire shape of `datastore/user_prefs.pb`
 * (ADR-0001 D17). Field numbers are the wire contract: never reuse or renumber one; add new fields with new
 * numbers and defaults so older files keep decoding. Enum entries carry explicit numbers for the same reason.
 */

@OptIn(ExperimentalSerializationApi::class)
@Serializable
enum class ThemePreference {
    @ProtoNumber(0) SYSTEM,
    @ProtoNumber(1) LIGHT,
    @ProtoNumber(2) DARK,
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
enum class ColorSourcePreference {
    @ProtoNumber(0) DYNAMIC,
    @ProtoNumber(1) SEED,
}

/** Background time before App Lock re-engages (FR-ONB-6). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
enum class LockTimeout(val seconds: Int) {
    @ProtoNumber(0) IMMEDIATELY(0),
    @ProtoNumber(1) THIRTY_SECONDS(30),
    @ProtoNumber(2) ONE_MINUTE(60),
    @ProtoNumber(3) FIVE_MINUTES(300),
}

/** App Lock settings (FR-ONB-6/7, FR-LLM-2 g). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AppLockPreferences(
    @ProtoNumber(1) val enabled: Boolean = false,
    @ProtoNumber(2) val timeout: LockTimeout = LockTimeout.ONE_MINUTE,
    /** Strict vault mode: LLM keys bound to biometric authentication (FR-LLM-2 g). */
    @ProtoNumber(3) val strictVault: Boolean = false,
    @ProtoNumber(4) val requireForAiSettings: Boolean = false,
    @ProtoNumber(5) val hideInWidgets: Boolean = true,
)

/** User preferences (FR-SET-1/2/5, FR-PRV-6, FR-ONB-6..9). Nothing secret lives here. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UserPreferences(
    @ProtoNumber(1) val currencyCode: String = "INR",
    /** Ordinal-encoded `core:common` enum; its order is append-only. */
    @ProtoNumber(2) val numberStyle: NumberStyle = NumberStyle.INDIAN,
    /** Day of month the budget month starts on (1..28), e.g. a salary day (FR-ONB-9). */
    @ProtoNumber(3) val monthStartDay: Int = 1,
    /** ISO day-of-week number: 1 = Monday … 7 = Sunday. */
    @ProtoNumber(4) val firstDayOfWeek: Int = 1,
    @ProtoNumber(5) val theme: ThemePreference = ThemePreference.SYSTEM,
    @ProtoNumber(6) val colorSource: ColorSourcePreference = ColorSourcePreference.DYNAMIC,
    /** Name of the bundled seed (`MantisSeed` entry) used when [colorSource] is [ColorSourcePreference.SEED]. */
    @ProtoNumber(7) val seedName: String = "MANTIS_GREEN",
    @ProtoNumber(8) val amoledBlack: Boolean = false,
    @ProtoNumber(9) val reducedMotion: Boolean = false,
    @ProtoNumber(10) val hideAmounts: Boolean = false,
    @ProtoNumber(11) val onboardingCompleted: Boolean = false,
    @ProtoNumber(12) val appLock: AppLockPreferences = AppLockPreferences(),
    /** `FLAG_SECURE` on screens flagged sensitive (FR-ONB-7). */
    @ProtoNumber(13) val secureSensitiveScreens: Boolean = true,
    @ProtoNumber(14) val localOnlyMode: Boolean = true,
) {
    init {
        require(monthStartDay in 1..MAX_MONTH_START_DAY) { "monthStartDay must be 1..$MAX_MONTH_START_DAY" }
        require(firstDayOfWeek in 1..DAYS_IN_WEEK) { "firstDayOfWeek must be ISO 1..7" }
    }

    companion object {
        const val MAX_MONTH_START_DAY = 28
        const val DAYS_IN_WEEK = 7
    }
}
