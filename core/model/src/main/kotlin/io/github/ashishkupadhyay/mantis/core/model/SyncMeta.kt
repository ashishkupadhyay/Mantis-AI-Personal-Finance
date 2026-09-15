package io.github.ashishkupadhyay.mantis.core.model

/**
 * Sync bookkeeping carried by every synced entity (doc 02 §5.1, doc 03 §6).
 *
 * @property updatedAt hybrid logical clock value of the last local change.
 * @property version server-acknowledged version; a push carries it as `base_version`.
 * @property deletedAt tombstone timestamp (HLC) or null when live.
 * @property dirty true while a local change is waiting in the outbox.
 * @property deviceId the device that produced the last change.
 */
data class SyncMeta(
    val updatedAt: Long,
    val version: Int = 0,
    val deletedAt: Long? = null,
    val dirty: Boolean = true,
    val deviceId: DeviceId,
) {
    val isDeleted: Boolean get() = deletedAt != null

    companion object {
        /** Fresh local entity: version 0, dirty, not yet seen by the server. */
        fun local(hlc: Long, deviceId: DeviceId): SyncMeta = SyncMeta(updatedAt = hlc, deviceId = deviceId)

        /**
         * A fresh entity created above the data layer (use-cases, features), which cannot know the device id:
         * every repository re-stamps `updatedAt`, `dirty` and `deviceId` on save, so the placeholder never persists.
         */
        fun unstamped(): SyncMeta = SyncMeta(updatedAt = 0L, deviceId = UNSTAMPED_DEVICE)

        val UNSTAMPED_DEVICE: DeviceId = DeviceId("")
    }
}
