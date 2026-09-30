package com.yuxiang.drawer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

public class FloatView {
    int defaultColor;
    boolean isTextViewAdded = false;
    static boolean isButtonViewAdded = false;
    boolean isRunning = false;
    int rollingStep = 0;
    private static final long COUNTDOWN_INTERVAL_MS = 12;
    private static final long ROLLING_INTERVAL_MS = 60;
    private static final int ROLLING_STEPS = 8;

    Context context;
    Handler handler;
    StringPool stringPool;
    SharedPreferences locationPreferences;

    private final WindowManager windowManager;
    private final View floatButtonView;
    private final WindowManager.LayoutParams buttonParams;
    private final View floatWindowView;
    private final WindowManager.LayoutParams textParams;
    TextView textView;
    LinearProgressIndicator linearProgressIndicator;
    FloatingActionButton fab;
    int progressValue = 100;

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

    public FloatView(Context context) {
        this.context = context;
        stringPool = new StringPool(context);
        handler = new Handler(Looper.getMainLooper());
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        locationPreferences = context.getSharedPreferences("location", Context.MODE_PRIVATE);

        // Init button layout
        LayoutInflater buttonInflater = LayoutInflater.from(context);
        floatButtonView = buttonInflater.inflate(R.layout.float_button, new FrameLayout(context), false);

        buttonParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.RGBA_8888);

        buttonParams.gravity = Gravity.TOP | Gravity.START;
        resetLocation();

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

        // Init text layout
        LayoutInflater windowInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        floatWindowView = windowInflater.inflate(R.layout.float_window, new FrameLayout(context), false);
        textView = floatWindowView.findViewById(R.id.text);
        linearProgressIndicator = floatWindowView.findViewById(R.id.progress);
        defaultColor = textView.getCurrentTextColor();

        textParams = new WindowManager.LayoutParams(
                context.getResources().getDimensionPixelSize(R.dimen.float_window_width),
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.RGBA_8888);
        textParams.gravity = Gravity.CENTER;
    }

    private void resetLocation() {
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        buttonParams.x = locationPreferences.getInt("locationX", size.x - 105);
        buttonParams.y = locationPreferences.getInt("locationY", size.y - 175);
    }

    public void resetFloatButtonLocation() {
        locationPreferences.edit().remove("locationX").remove("locationY").apply();
        resetLocation();
        if (isButtonViewAdded) {
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
        if (!isTextViewAdded) {
            windowManager.addView(floatWindowView, textParams);
            isTextViewAdded = true;
        }
    }

    public void showFloatButton() {
        if (!isButtonViewAdded) {
            resetLocation();
            windowManager.addView(floatButtonView, buttonParams);
            isButtonViewAdded = true;
        }
    }

    public void hideFloatText() {
        stopAnimation();
        if (isTextViewAdded) {
            windowManager.removeView(floatWindowView);
            isTextViewAdded = false;
        }
    }

    public void hideFloatButton(boolean isNotification) {
        if (isButtonViewAdded) {
            if (floatButtonView.getWindowToken() != null) {
                windowManager.removeView(floatButtonView);
                isButtonViewAdded = false;
            } else {
                if (isNotification) {
                    Toast.makeText(context, R.string.text_restart_app, Toast.LENGTH_SHORT).show();
                }
            }
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
                    Toast.makeText(context, R.string.text_is_null, Toast.LENGTH_SHORT).show();
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