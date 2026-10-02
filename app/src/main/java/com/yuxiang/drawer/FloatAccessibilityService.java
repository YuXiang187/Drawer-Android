package com.yuxiang.drawer;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import java.util.List;

/**
 * Optional alternative to {@link FloatService} for keeping the floating button alive in the
 * background without a foreground service notification.
 *
 * <p>The system keeps the accessibility service alive and allows it to display floating windows
 * over system UI. This service does not inspect the screen or process accessibility events; it
 * only keeps the floating button visible.
 *
 * <p>{@link MainActivity} lets the user choose between the two methods. If accessibility access
 * is not granted, {@link FloatService} is used instead.
 */
public class FloatAccessibilityService extends AccessibilityService {
    private static final String TAG = "FloatAccessibility";

    private static boolean connected = false;
    // used to ask for the window manager of the accessibility layer
    private static FloatAccessibilityService instance;

    public static boolean isConnected() {
        return connected;
    }

    @Nullable
    public static WindowManager getAccessibilityWindowManager() {
        FloatAccessibilityService service = instance;
        // null while this service is not connected
        if (!connected || service == null) {
            return null;
        }
        return (WindowManager) service.getSystemService(WINDOW_SERVICE);
    }

    // True when the user enables this service in accessibility settings
    public static boolean isEnabled(Context context) {
        AccessibilityManager manager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null || !manager.isEnabled()) {
            return false;
        }
        List<AccessibilityServiceInfo> services =
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        String className = FloatAccessibilityService.class.getName();
        for (AccessibilityServiceInfo info : services) {
            ResolveInfo resolveInfo = info.getResolveInfo();
            ServiceInfo serviceInfo = resolveInfo == null ? null : resolveInfo.serviceInfo;
            if (serviceInfo == null || !context.getPackageName().equals(serviceInfo.packageName)) {
                continue;
            }
            if (className.equals(serviceInfo.name)
                    || FloatAccessibilityService.class.getSimpleName().equals(serviceInfo.name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        instance = this;
        Log.i(TAG, "Service connected");

        FloatView floatView = FloatView.peekInstance();
        if (floatView != null) {
            // Overlay the button over restricted system areas
            floatView.useAccessibilityOverlay(getAccessibilityWindowManager());
        }

        // Only take the floating button over when it is supposed to stay alive in the background
        SharedPreferences preferences = getSharedPreferences("settings", MODE_PRIVATE);
        if (!preferences.getBoolean("float_state", false)
                || !preferences.getBoolean("background_state", false)) {
            return;
        }

        // Apply the saved theme before inflating the overlay views
        int themeIndex = preferences.getInt("theme", 0);
        AppCompatDelegate.setDefaultNightMode(themeIndex == 0
                ? AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM : themeIndex);

        FloatView.getInstance(this).showFloatButton();
        FloatService.sync(this);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        connected = false;
        instance = null;
        Log.i(TAG, "Service unbound");
        FloatView floatView = FloatView.peekInstance();
        if (floatView != null) {
            // fallback to the regular overlay when the window token is invalid
            floatView.useRegularOverlay();
        }
        FloatService.sync(this);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        connected = false;
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // The service only hosts the floating button and never evaluates screen content
    }

    @Override
    public void onInterrupt() {
        // no feedback and no event handling is in progress
    }
}