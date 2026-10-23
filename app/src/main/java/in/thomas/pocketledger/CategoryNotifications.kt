package `in`.thomas.pocketledger

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

internal fun needsCategoryNotification(entry: Entry) =
    entry.smsKey != null && !entry.deleted && entry.type != "TRANSFER" && entry.category == "Uncategorized"

internal object CategoryNotifications {
    const val EXTRA_ENTRY_ID = "ledger_category_entry"
    private const val CHANNEL = "transaction_categories"

    fun show(context: Context, entry: Entry) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Transaction categories", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Reminders to categorize transactions captured from SMS"
        })
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.Builder().scheme("ledger").authority("category").appendPath(entry.id).build())
            .putExtra(EXTRA_ENTRY_ID, entry.id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Add a category")
            .setContentText("New transaction parsed. Tap to choose its category.")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // A denied notification must never prevent saving a transaction.
        try { manager.notify(entry.id, 0, notification) } catch (_: SecurityException) { }
    }

    fun cancel(context: Context, entryId: String) {
        context.getSystemService(NotificationManager::class.java).cancel(entryId, 0)
    }
}
