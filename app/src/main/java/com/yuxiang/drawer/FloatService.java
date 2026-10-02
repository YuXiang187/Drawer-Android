package com.yuxiang.drawer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

/**
 * Keeps the floating button alive in the background.
 *
 * <p>It is optional and only runs when background mode is enabled without the accessibility
 * service. If the accessibility service is available, it keeps the button alive instead.
 *
 * <p>Handles foreground service requirements across Android versions.
 */
public class FloatService extends Service {
    private static final String TAG = "FloatService";
    private static final String CHANNEL_ID = "float_button";
    private static final int NOTIFICATION_ID = 1;
    private static final String ACTION_HIDE = "com.yuxiang.drawer.action.HIDE_FLOAT_BUTTON";

    // starts or stops the service so that it matches the two settings
    public static void sync(Context context) {
        if (shouldRun(context)) {
            start(context);
        } else {
            stopNow(context);
        }
    }

    public static boolean shouldKeepAlive(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("settings", MODE_PRIVATE);
        return preferences.getBoolean("float_state", false)
                && preferences.getBoolean("background_state", false)
                && canDrawOverlays(context);
    }

    public static boolean shouldRun(Context context) {
        return shouldKeepAlive(context) && !FloatAccessibilityService.isConnected();
    }

    // used by the Exit menu entry
    public static void stopNow(Context context) {
        context.stopService(new Intent(context, FloatService.class));
    }

    private static boolean canDrawOverlays(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
    }

    private static void start(Context context) {
        Intent intent = new Intent(context, FloatService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot start the background service", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Every start (including a restart by the system, which delivers a null Intent) has to
        // post the notification quickly, also on the way out, otherwise the platform kills us.
        if (!showNotification()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && ACTION_HIDE.equals(intent.getAction())) {
            getSharedPreferences("settings", MODE_PRIVATE)
                    .edit().putBoolean("float_state", false).apply();
            FloatView floatView = FloatView.getInstance(this);
            floatView.hideFloatButton();
            floatView.hideFloatText();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!shouldRun(this)) {
            // Nothing to keep alive
            stopSelf();
            return START_NOT_STICKY;
        }

        FloatView.getInstance(this).showFloatButton();
        // Try to restore the service if the system interrupts it
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // return false when the platform refused the foreground notification
    private boolean showNotification() {
        Notification notification = buildNotification();
        try {
            // API 34+ require the specialUse type
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "startForeground failed", e);
            return false;
        }
    }

    private Notification buildNotification() {
        // bring the existing task to the front
        Intent openIntent = new Intent(this, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openIntent, pendingIntentFlags());

        Intent hideIntent = new Intent(this, FloatService.class).setAction(ACTION_HIDE);
        PendingIntent hidePendingIntent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? PendingIntent.getForegroundService(this, 1, hideIntent, pendingIntentFlags())
                : PendingIntent.getService(this, 1, hideIntent, pendingIntentFlags());

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(contentIntent)
                .addAction(0, getString(R.string.notification_action_hide), hidePendingIntent)
                .setOngoing(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.notification_channel_description));
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private static int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
}