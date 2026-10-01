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

import androidx.appcompat.app.AppCompatDelegate;

import java.util.List;

/**
 * Optional alternative to {@link FloatService}: instead of a foreground service (which needs an
 * ongoing notification) the floating button can be hosted by the accessibility service the user
 * enabled in the system settings.
 *
 * <p>An accessibility service is bound by the system, which keeps this process - and therefore the
 * floating overlay windows of {@link FloatView} - alive, and it is allowed to show
 * {@code TYPE_ACCESSIBILITY_OVERLAY} windows. Those windows carry the window token of this service
 * and are not treated as system alert windows, which is what keeps the button visible over system
 * UI such as the settings or the notification shade. The service itself never inspects the screen:
 * it asks for no window content, ignores every event it receives and only shows the floating
 * button.
 *
 * <p>The user chooses between both ways in {@link MainActivity} when "Run in background" is turned
 * on. When the accessibility permission is not granted, {@link FloatService} keeps the button
 * alive as before.
 */
public class FloatAccessibilityService extends AccessibilityService {
    private static final String TAG = "FloatAccessibility";

    // The accessibility service is bound in the same process as the rest of the app, so the state
    // can simply be shared through a static field.
    private static boolean connected = false;

    /**
     * True while the platform has this service bound, i.e. while the floating button can be shown
     * on the accessibility layer and the foreground service is not needed any more.
     */
    public static boolean isConnected() {
        return connected;
    }

    /**
     * True when the user turned this service on in the system accessibility settings. The service
     * is normally bound immediately afterwards, but the connection may lag behind a little, which
     * is why both states are checked separately.
     */
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
            // The name may be stored in its short form, for example ".FloatAccessibilityService".
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
        Log.i(TAG, "Service connected");

        // Only take the floating button over when it is supposed to stay alive in the background.
        SharedPreferences preferences = getSharedPreferences("settings", MODE_PRIVATE);
        if (!preferences.getBoolean("float_state", false)
                || !preferences.getBoolean("background_state", false)) {
            return;
        }

        // The accessibility service can be the only entry point of this process (the system binds it
        // again after a restart), so the saved theme has to be applied before the overlay views are
        // inflated, exactly like MainActivity does when it starts the app.
        int themeIndex = preferences.getInt("theme", 0);
        AppCompatDelegate.setDefaultNightMode(themeIndex == 0
                ? AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM : themeIndex);

        FloatView floatView = FloatView.getInstance(this);
        // Only the window manager of this service carries the accessibility overlay window token,
        // which is what makes the button a real accessibility overlay instead of a regular overlay
        // that the system hides over its own UI.
        floatView.useAccessibilityOverlay((WindowManager) getSystemService(WINDOW_SERVICE));
        floatView.showFloatButton();
        // The accessibility service keeps this process alive, so the foreground service - and with
        // it the ongoing notification - can go away.
        FloatService.sync(this);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        connected = false;
        Log.i(TAG, "Service unbound");
        FloatView floatView = FloatView.peekInstance();
        if (floatView != null) {
            // The window token of this service is not valid any more, so the button has to go back
            // to the regular overlay layer.
            floatView.useRegularOverlay();
        }
        // The accessibility permission is gone: fall back to the foreground service. The start may
        // be refused while the app is in the background on Android 12+; MainActivity then restarts
        // the service as soon as it is visible again.
        FloatService.sync(this);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        connected = false;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // The service only hosts the floating button and never evaluates screen content.
    }

    @Override
    public void onInterrupt() {
        // Nothing to interrupt: no feedback and no event handling is in progress.
    }
}