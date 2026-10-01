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
 * Keeps the process (and therefore the floating button window) alive while the app is not in the
 * foreground. It is optional: the service only runs when the user enabled both "Float button" and
 * "Run in background".
 *
 * <p>Compatibility notes for minSdk 21 / targetSdk 35:
 * <ul>
 *     <li>API 26+: a service started from the background must be started with
 *     {@link Context#startForegroundService(Intent)} and call {@code startForeground()} within a few
 *     seconds, which {@link #onStartCommand} does on every path.</li>
 *     <li>API 28+: the {@code FOREGROUND_SERVICE} permission is required (declared in the
 *     manifest).</li>
 *     <li>API 29+: {@code android:foregroundServiceType} is declared in the manifest; on API 34+
 *     the type must also be passed to {@code startForeground()} together with the matching
 *     {@code FOREGROUND_SERVICE_SPECIAL_USE} permission. On older releases the type is left out so
 *     that the platforms that do not know {@code specialUse} never have to interpret it.</li>
 *     <li>API 33+: the ongoing notification is only visible when {@code POST_NOTIFICATIONS} is
 *     granted; without it the service still runs (the user sees it in the foreground service task
 *     manager).</li>
 *     <li>Android 12+ forbids starting a foreground service from the background; the start from
 *     this app is covered by a visible activity and by the overlay permission, and the floating
 *     button is always added before the service is started (which Android 15 requires explicitly).
 *     A restart of a sticky foreground service by the system is exempt from that rule.</li>
 * </ul>
 */
public class FloatService extends Service {
    private static final String TAG = "FloatService";
    private static final String CHANNEL_ID = "float_button";
    private static final int NOTIFICATION_ID = 1;
    // Notification action: hide the floating button and stop the service.
    private static final String ACTION_HIDE = "com.yuxiang.drawer.action.HIDE_FLOAT_BUTTON";

    // Starts or stops the service so that it matches the two settings.
    public static void sync(Context context) {
        if (shouldRun(context)) {
            start(context);
        } else {
            stopNow(context);
        }
    }

    // True while the service is supposed to keep the process alive
    public static boolean shouldRun(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("settings", MODE_PRIVATE);
        return preferences.getBoolean("float_state", false)
                && preferences.getBoolean("background_state", false)
                && canDrawOverlays(context);
    }

    // Stops the service whatever the settings say (used by the "Exit" menu entry).
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
            // For example ForegroundServiceStartNotAllowedException on Android 12+ when the start
            // is not allowed, or a restricted OEM build. The floating button then simply lives as
            // long as the process does, instead of taking the app down.
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
            // The platform refused to turn us into a foreground service: stop instead of being
            // killed for not posting the notification in time.
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
            // Nothing to keep alive (settings changed or the overlay permission is gone)
            stopSelf();
            return START_NOT_STICKY;
        }

        FloatView.getInstance(this).showFloatButton();
        // Let the system bring the service - and the floating button - back if it kills the process
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

    // return false when the platform refused the foreground notification.
    private boolean showNotification() {
        Notification notification = buildNotification();
        try {
            // ServiceCompat hides the platform differences of this call: the two argument version
            // below API 29, the type masked to the flags those releases understand on API 29-33
            // (they do not know specialUse yet), and the real specialUse type on API 34+, where
            // declaring FOREGROUND_SERVICE_SPECIAL_USE is mandatory.
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            return true;
        } catch (RuntimeException e) {
            // For example SecurityException on API 34+ when the type permission is missing, or a
            // restricted OEM build.
            Log.w(TAG, "startForeground failed", e);
            return false;
        }
    }

    private Notification buildNotification() {
        // Behave like the launcher icon: bring the existing task to the front instead of adding
        // another MainActivity instance on top of it.
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

    // FLAG_IMMUTABLE only exists from API 23 and is mandatory from API 31 on.
    private static int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
}