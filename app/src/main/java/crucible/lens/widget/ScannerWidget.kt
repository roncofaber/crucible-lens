package crucible.lens.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import crucible.lens.MainActivity
import crucible.lens.R

/**
 * Simple QR scanner widget - tapping it opens the app directly to the scanner
 */
class ScannerWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update each widget instance
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Widget added to home screen for the first time
    }

    override fun onDisabled(context: Context) {
        // Last widget removed from home screen
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    // Create intent to launch scanner
    val intent = Intent(context, MainActivity::class.java).apply {
        action = "crucible.lens.OPEN_SCANNER"
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Set up widget layout
    val views = RemoteViews(context.packageName, R.layout.widget_scanner).apply {
        setOnClickPendingIntent(R.id.widget_icon, pendingIntent)
    }

    // Update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}
