package com.yuxiang.drawer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewManager;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

public class FloatView {
    private static final String TAG = "FloatView";
    private static final long COUNTDOWN_INTERVAL_MS = 12;
    private static final long ROLLING_INTERVAL_MS = 60;
    private static final int ROLLING_STEPS = 8;

    @SuppressLint("StaticFieldLeak")
    private static FloatView instance;

    private final Context appContext;
    private final WindowManager appWindowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringPool stringPool;
    private final SharedPreferences locationPreferences;
    private final SharedPreferences settingsPreferences;
    private final WindowManager.LayoutParams buttonParams;
    private final WindowManager.LayoutParams textParams;

    private WindowManager windowManager;
    private WindowManager accessibilityWindowManager;

    private View floatButtonView;
    private View floatWindowView;
    private TextView textView;
    private LinearProgressIndicator linearProgressIndicator;
    private FloatingActionButton fab;
    private int inflatedNightMode = -1;

    private int defaultColor;
    private boolean isRunning = false;
    private int rollingStep = 0;
    private int progressValue = 100;

    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            progressValue--;
            linearProgressIndicator.setProgressCompat(Math.max(progressValue, 0), false);
            if (progressValue <= 0) {
                hideFloatText();
                return;
            }
            handler.postDelayed(this, COUNTDOWN_INTERVAL_MS);
        }
    };

    // Returns the overlay controller for this process
    public static synchronized FloatView getInstance(Context context) {
        int nightMode = nightModeOf(context);
        if (instance == null) {
            instance = new FloatView(context.getApplicationContext(), nightMode);
        } else {
            instance.applyNightMode(nightMode);
        }
        return instance;
    }

    // Returns this process's controller, or null if none exists
    public static synchronized FloatView peekInstance() {
        return instance;
    }

    private FloatView(Context appContext, int nightMode) {
        this.appContext = appContext;
        stringPool = new StringPool(appContext);
        windowManager = appWindowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
        locationPreferences = appContext.getSharedPreferences("location", Context.MODE_PRIVATE);
        settingsPreferences = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE);

        int overlayType = windowType();

        buttonParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.RGBA_8888);
        buttonParams.gravity = Gravity.TOP | Gravity.START;
        resetLocation();

        textParams = new WindowManager.LayoutParams(
                appContext.getResources().getDimensionPixelSize(R.dimen.float_window_width),
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.RGBA_8888);
        textParams.gravity = Gravity.CENTER;

        inflateWindows(nightMode);

        // The accessibility service may already be connected, in that case it hosts the windows
        this.accessibilityWindowManager = FloatAccessibilityService.getAccessibilityWindowManager();
        windowManager = accessibilityWindowManager != null ? accessibilityWindowManager : appWindowManager;
    }

    // Shows both windows on the accessibility layer
    public void useAccessibilityOverlay(WindowManager accessibilityWindowManager) {
        if (this.accessibilityWindowManager == accessibilityWindowManager) {
            return;
        }
        boolean textShown = isFloatTextShown();
        boolean buttonShown = isFloatButtonShown();
        if (textShown) {
            removeWindow(floatWindowView);
        }
        if (buttonShown) {
            removeWindow(floatButtonView);
        }

        this.accessibilityWindowManager = accessibilityWindowManager;
        windowManager = accessibilityWindowManager != null ? accessibilityWindowManager : appWindowManager;

        if (buttonShown) {
            try {
                addButtonWindow();
            } catch (RuntimeException e) {
                Log.w(TAG, "Cannot move the floating button to the other overlay layer", e);
            }
        }
        if (textShown) {
            try {
                showFloatText();
            } catch (RuntimeException e) {
                Log.w(TAG, "Cannot move the floating window to the other overlay layer", e);
            }
        }
    }

    // moves both windows to the accessibility layer while the service is connected
    public void useAccessibilityOverlayIfAvailable() {
        useAccessibilityOverlay(FloatAccessibilityService.getAccessibilityWindowManager());
    }

    // shows both windows as regular overlays again
    public void useRegularOverlay() {
        useAccessibilityOverlay(null);
    }

    // true while both overlay windows are hosted by the accessibility service
    public boolean usesAccessibilityOverlay() {
        return accessibilityWindowManager != null;
    }

    // the window type of both overlay windows, depending on the permission that shows them
    private int windowType() {
        if (accessibilityWindowManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            return WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    // fallback to the regular overlay
    private void addWindow(View view, WindowManager.LayoutParams params) {
        // A view that is still attached to the layer it came from would be refused
        detachFromParent(view);
        try {
            windowManager.addView(view, params);
        } catch (RuntimeException e) {
            if (accessibilityWindowManager == null) {
                throw e;
            }
            Log.w(TAG, "Cannot show the window on the accessibility layer", e);
            accessibilityWindowManager = null;
            windowManager = appWindowManager;
            params.type = windowType();
            params.token = null;
            detachFromParent(view);
            windowManager.addView(view, params);
        }
    }

    // releases a view that the window manager no longer owns
    private void detachFromParent(View view) {
        ViewParent parent = view.getParent();
        if (parent == null) {
            return;
        }
        Log.w(TAG, "Detaching the overlay window from its stale parent");
        try {
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(view);
            } else {
                ((ViewManager) parent).removeView(view);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot detach the overlay window from its stale parent", e);
        }
    }

    private void removeWindow(View view) {
        try {
            windowManager.removeView(view);
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot remove the overlay window", e);
        }
        // The window manager may already have dropped the window without clearing the parent
        detachFromParent(view);
    }

    private void inflateWindows(int nightMode) {
        LayoutInflater inflater = LayoutInflater.from(createOverlayContext(nightMode));

        floatButtonView = inflater.inflate(R.layout.float_button, null);
        fab = floatButtonView.findViewById(R.id.float_button);
        fab.setOnClickListener(v -> run());

        floatButtonView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = buttonParams.x;
                        initialY = buttonParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        buttonParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        buttonParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatButtonView, buttonParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (settingsPreferences.getBoolean("is_remember_location", false)) {
                            saveLocation();
                        }
                        // detect if it is a click event
                        if (Math.abs(event.getRawX() - initialTouchX) < 10 && Math.abs(event.getRawY() - initialTouchY) < 10) {
                            v.performClick();
                        }
                        return true;
                }
                return false;
            }
        });

        floatWindowView = inflater.inflate(R.layout.float_window, null);
        textView = floatWindowView.findViewById(R.id.text);
        linearProgressIndicator = floatWindowView.findViewById(R.id.progress);
        defaultColor = textView.getCurrentTextColor();

        inflatedNightMode = nightMode;
    }

    private Context createOverlayContext(int nightMode) {
        Configuration configuration = new Configuration(appContext.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        return new ContextThemeWrapper(
                appContext.createConfigurationContext(configuration), R.style.Theme_Drawer);
    }

    private static int nightModeOf(Context context) {
        switch (AppCompatDelegate.getDefaultNightMode()) {
            case AppCompatDelegate.MODE_NIGHT_YES:
                return Configuration.UI_MODE_NIGHT_YES;
            case AppCompatDelegate.MODE_NIGHT_NO:
                return Configuration.UI_MODE_NIGHT_NO;
            default:
                return context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        }
    }

    // Rebuilds both windows when the in-app theme changed
    private void applyNightMode(int nightMode) {
        if (nightMode == inflatedNightMode) {
            return;
        }
        boolean buttonShown = isFloatButtonShown();
        hideFloatText();
        if (buttonShown) {
            removeButtonWindow();
        }
        inflateWindows(nightMode);
        if (buttonShown) {
            addButtonWindow();
        }
    }

    private void resetLocation() {
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        buttonParams.x = locationPreferences.getInt("locationX", size.x - 105);
        buttonParams.y = locationPreferences.getInt("locationY", size.y - 175);
    }

    // true while the floating button window is registered with the window manager
    public boolean isFloatButtonShown() {
        return floatButtonView.getParent() != null;
    }

    public View getFloatButtonView() {
        return floatButtonView;
    }

    // lets the guide on top of the button receive the touches instead of the button
    public void setTouchable(boolean touchable) {
        int flags = touchable
                ? buttonParams.flags & ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                : buttonParams.flags | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        if (flags == buttonParams.flags) {
            return;
        }
        buttonParams.flags = flags;
        if (!isFloatButtonShown()) {
            return;
        }
        try {
            windowManager.updateViewLayout(floatButtonView, buttonParams);
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot change the touchability of the floating button", e);
        }
    }

    private boolean isFloatTextShown() {
        return floatWindowView.getParent() != null;
    }

    private void addButtonWindow() {
        buttonParams.type = windowType();
        // A token of another layer must never be carried over into a new window.
        buttonParams.token = null;
        addWindow(floatButtonView, buttonParams);
    }

    private void removeButtonWindow() {
        removeWindow(floatButtonView);
    }

    public void resetFloatButtonLocation() {
        locationPreferences.edit().remove("locationX").remove("locationY").apply();
        resetLocation();
        if (isFloatButtonShown()) {
            windowManager.updateViewLayout(floatButtonView, buttonParams);
        }
    }

    public void saveLocation() {
        locationPreferences.edit()
                .putInt("locationX", buttonParams.x)
                .putInt("locationY", buttonParams.y)
                .apply();
    }

    public void showFloatText() {
        if (isFloatTextShown()) {
            return;
        }
        textParams.type = windowType();
        textParams.token = null;
        addWindow(floatWindowView, textParams);
    }

    public void showFloatButton() {
        if (isFloatButtonShown()) {
            return;
        }
        // accessibility service may have connected while the button was hidden
        useAccessibilityOverlayIfAvailable();
        resetLocation();
        addButtonWindow();
    }

    public void hideFloatText() {
        stopAnimation();
        if (isFloatTextShown()) {
            removeWindow(floatWindowView);
        }
    }

    public void hideFloatButton() {
        if (isFloatButtonShown()) {
            removeButtonWindow();
        }
    }

    public String draw() {
        String result = stringPool.draw();
        if (!result.isEmpty()) {
            stringPool.save();
        }
        return result;
    }

    public void run() {
        if (isRunning) {
            return;
        }
        isRunning = true;
        fab.setEnabled(false);

        showFloatText();
        textView.setTextColor(Color.GRAY);
        progressValue = 100;
        linearProgressIndicator.setProgressCompat(100, false);

        rollingStep = 0;
        handler.removeCallbacks(countdownRunnable);
        handler.post(rollingRunnable);
    }

    private final Runnable rollingRunnable = new Runnable() {
        @Override
        public void run() {
            String text;
            if (rollingStep >= ROLLING_STEPS - 1) {
                // last frame
                text = draw();
                if (text.isEmpty()) {
                    cancelAnimation();
                    Toast.makeText(appContext, R.string.text_is_null, Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                // rolling frames
                text = stringPool.get();
            }
            textView.setText(text);
            rollingStep++;
            if (rollingStep >= ROLLING_STEPS) {
                finishRolling();
            } else {
                handler.postDelayed(this, ROLLING_INTERVAL_MS);
            }
        }
    };

    private void finishRolling() {
        handler.removeCallbacks(rollingRunnable);
        textView.setTextColor(defaultColor);
        stopAnimation();

        progressValue = 100;
        linearProgressIndicator.setProgressCompat(100, false);
        handler.postDelayed(countdownRunnable, COUNTDOWN_INTERVAL_MS);
    }

    private void cancelAnimation() {
        stopAnimation();
        hideFloatText();
    }

    private void stopAnimation() {
        handler.removeCallbacks(rollingRunnable);
        handler.removeCallbacks(countdownRunnable);
        if (isRunning) {
            isRunning = false;
            fab.setEnabled(true);
        }
    }
}