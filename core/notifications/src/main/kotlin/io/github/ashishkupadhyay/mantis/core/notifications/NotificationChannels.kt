package io.github.ashishkupadhyay.mantis.core.notifications

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat

/**
 * The five channels of FR-NTF-2. Creating a channel is idempotent, so every poster calls [ensure] before it
 * notifies; the user toggles each one in the system settings (Settings › Notifications links there).
 */
object NotificationChannels {
    const val BUDGET_ALERTS = "budget_alerts"
    const val INSIGHTS = "insights"
    const val BILLS = "bills"
    const val SYNC_IMPORT = "sync_import"
    const val ASSISTANT = "assistant"

    fun ensure(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannelsCompat(
            listOf(
                channel(context, BUDGET_ALERTS, R.string.channel_budget_alerts, R.string.channel_budget_alerts_description, NotificationManagerCompat.IMPORTANCE_HIGH),
                channel(context, INSIGHTS, R.string.channel_insights, R.string.channel_insights_description, NotificationManagerCompat.IMPORTANCE_DEFAULT),
                channel(context, BILLS, R.string.channel_bills, R.string.channel_bills_description, NotificationManagerCompat.IMPORTANCE_DEFAULT),
                channel(context, SYNC_IMPORT, R.string.channel_sync_import, R.string.channel_sync_import_description, NotificationManagerCompat.IMPORTANCE_LOW),
                channel(context, ASSISTANT, R.string.channel_assistant, R.string.channel_assistant_description, NotificationManagerCompat.IMPORTANCE_DEFAULT),
            ),
        )
    }

    private fun channel(context: Context, id: String, name: Int, description: Int, importance: Int) =
        NotificationChannelCompat.Builder(id, importance)
            .setName(context.getString(name))
            .setDescription(context.getString(description))
            .build()
}
