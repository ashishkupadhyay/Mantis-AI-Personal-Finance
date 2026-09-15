package io.github.ashishkupadhyay.mantis.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.ashishkupadhyay.mantis.core.common.log.Logger
import io.github.ashishkupadhyay.mantis.core.common.money.MoneyFormatter
import io.github.ashishkupadhyay.mantis.core.domain.budget.BudgetAlert
import io.github.ashishkupadhyay.mantis.core.domain.budget.BudgetAlertNotifier
import io.github.ashishkupadhyay.mantis.core.domain.repository.PreferencesRepository
import io.github.ashishkupadhyay.mantis.core.model.BudgetId
import io.github.ashishkupadhyay.mantis.core.model.BudgetStatus
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts a budget alert as a local notification (FR-BUD-4, FR-NTF-1/5; doc 05 §7): "Food & Dining · 84 % used" /
 * "₹1,300 left · 12 days to go", expanding to the projection, with *View budget* (deep link `mantis://budget/{id}`)
 * and *Snooze this period* actions. Built `VISIBILITY_PRIVATE` with a public version that names only the budget
 * (NFR-20h), and it honours *hide amounts* by quoting percentages instead of money.
 */
@Singleton
class BudgetAlertPoster @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesRepository,
    private val logger: Logger,
) : BudgetAlertNotifier {

    override suspend fun notify(alert: BudgetAlert) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            logger.i(TAG) { "budget alert for ${alert.status.budget.id.value} dropped: notifications disabled" }
            return
        }
        NotificationChannels.ensure(context)
        val prefs = preferences.preferences.first()
        val formatter = MoneyFormatter(prefs.numberStyle)
        val status = alert.status
        val text = BudgetAlertText.of(context, alert, formatter, hideAmounts = prefs.hideAmounts)
        val id = status.budget.id

        val public = NotificationCompat.Builder(context, NotificationChannels.BUDGET_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_mantis)
            .setContentTitle(status.budget.name)
            .setContentText(context.getString(R.string.budget_alert_public))
            .build()
        val notification = NotificationCompat.Builder(context, NotificationChannels.BUDGET_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_mantis)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setGroup(GROUP_PREFIX + id.value)
            .setAutoCancel(true)
            .setContentIntent(viewBudget(id))
            .addAction(0, context.getString(R.string.budget_action_view), viewBudget(id))
            .addAction(0, context.getString(R.string.budget_action_snooze), snooze(status))
            .build()
        // Same id per budget: a later threshold replaces the earlier card instead of stacking.
        manager.notify(notificationId(id), notification)
    }

    /** `mantis://budget/{id}`, pinned to our own package so no other app can claim the link (intent security). */
    private fun viewBudget(id: BudgetId): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, "$DEEP_LINK_PREFIX${id.value}".toUri()).setPackage(context.packageName)
        return PendingIntent.getActivity(context, requestCode(id, VIEW_CODE), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun snooze(status: BudgetStatus): PendingIntent {
        val intent = BudgetSnoozeReceiver.intent(context, status.budget.id, status.period.key)
        return PendingIntent.getBroadcast(
            context, requestCode(status.budget.id, SNOOZE_CODE), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun requestCode(id: BudgetId, kind: Int): Int = id.value.hashCode() * REQUEST_STRIDE + kind

    companion object {
        const val DEEP_LINK_PREFIX = "mantis://budget/"
        private const val GROUP_PREFIX = "budget:"
        private const val TAG = "BudgetAlerts"
        private const val VIEW_CODE = 1
        private const val SNOOZE_CODE = 2
        private const val REQUEST_STRIDE = 4

        fun notificationId(id: BudgetId): Int = (GROUP_PREFIX + id.value).hashCode()
    }
}
