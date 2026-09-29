package com.yuxiang.drawer;

import android.content.Context;
import android.content.SharedPreferences;
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
    private static final long COUNTDOWN_INTERVAL_MS = 14;

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
                            locationPreferences.edit().putInt("locationX", buttonParams.x).apply();
                            locationPreferences.edit().putInt("locationY", buttonParams.y).apply();
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
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.RGBA_8888);
        textParams.gravity = Gravity.CENTER;
        // Let the window span the display cutout (notch/punch-hole).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            textParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    private void resetLocation() {
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        buttonParams.x = locationPreferences.getInt("locationX", size.x - 105);
        buttonParams.y = locationPreferences.getInt("locationY", size.y - 175);
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
        handler.removeCallbacks(countdownRunnable);
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

    public void run() {
        String result = stringPool.draw();
        if (result.isEmpty()) {
            Toast.makeText(context, R.string.text_is_null, Toast.LENGTH_SHORT).show();
            return;
        }

        stringPool.save();

        showFloatText();
        textView.setTextColor(defaultColor);
        textView.setText(result);

        handler.removeCallbacks(countdownRunnable);
        progressValue = 100;
        linearProgressIndicator.setProgressCompat(100, false);
        handler.postDelayed(countdownRunnable, COUNTDOWN_INTERVAL_MS);
    }
}