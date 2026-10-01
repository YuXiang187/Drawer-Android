package com.yuxiang.drawer;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

public class FloatView {
    private static final long COUNTDOWN_INTERVAL_MS = 12;
    private static final long ROLLING_INTERVAL_MS = 60;
    private static final int ROLLING_STEPS = 8;

    private static FloatView instance;

    private final Context appContext;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringPool stringPool;
    private final SharedPreferences locationPreferences;
    private final WindowManager.LayoutParams buttonParams;
    private final WindowManager.LayoutParams textParams;

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

    /**
     * Returns the one and only overlay controller of this process.
     *
     * @param context any context; only its configuration (light/dark) is taken from it, never the
     *                context itself, so no Activity can be leaked by the long living windows.
     */
    public static synchronized FloatView getInstance(Context context) {
        int nightMode = nightModeOf(context);
        if (instance == null) {
            instance = new FloatView(context.getApplicationContext(), nightMode);
        } else {
            instance.applyNightMode(nightMode);
        }
        return instance;
    }

    private FloatView(Context appContext, int nightMode) {
        this.appContext = appContext;
        stringPool = new StringPool(appContext);
        windowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
        locationPreferences = appContext.getSharedPreferences("location", Context.MODE_PRIVATE);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;

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
                        if (MainActivity.isRememberLocation) {
                            saveLocation();
                        }
                        // Detect if it is a click event (you can determine if it is a click based on the distance moved)
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

    // The windows are inflated from the application context, with the theme the app shows.
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

    // Rebuilds both windows when the in app theme (or the system theme) changed.
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

    // True while the floating button window is registered with the window manager.
    public boolean isFloatButtonShown() {
        return floatButtonView.getParent() != null;
    }

    private boolean isFloatTextShown() {
        return floatWindowView.getParent() != null;
    }

    private void addButtonWindow() {
        windowManager.addView(floatButtonView, buttonParams);
    }

    private void removeButtonWindow() {
        windowManager.removeView(floatButtonView);
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
        windowManager.addView(floatWindowView, textParams);
    }

    public void showFloatButton() {
        if (isFloatButtonShown()) {
            return;
        }
        resetLocation();
        addButtonWindow();
    }

    public void hideFloatText() {
        stopAnimation();
        if (isFloatTextShown()) {
            windowManager.removeView(floatWindowView);
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