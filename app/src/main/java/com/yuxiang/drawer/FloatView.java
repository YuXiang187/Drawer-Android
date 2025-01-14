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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

public class FloatView {
    int defaultColor;
    boolean isRun = false;
    boolean isTextViewAdded = false;
    static boolean isButtonViewAdded = false;

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
    ImageView imageView;

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
        imageView = floatButtonView.findViewById(R.id.float_image);
        imageView.setOnClickListener(v -> run());

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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FORMAT_CHANGED);
        textParams.gravity = Gravity.CENTER;
    }

    public void isCommonButton(boolean isCommon) {
        if (isCommon) {
            fab.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.GONE);
        } else {
            fab.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
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
        if (!isRun) {
            showFloatText();
            isRun = true;
            fab.setEnabled(false);
            linearProgressIndicator.setProgress(100);
            textView.setTextColor(Color.GRAY);
            for (int i = 0; i < 8; i++) {
                final int index = i;
                handler.postDelayed(() -> {
                    textView.setText(stringPool.get());
                    if (index == 7) {
                        textView.setTextColor(defaultColor);
                        stringPool.remove(textView.getText().toString());
                        stringPool.save();
                        unVisible();
                    }
                }, i * 60);
            }
        }
    }

    private void unVisible() {
        for (int i = 0; i <= 100; i++) {
            final int currentProgress = 100 - i;
            handler.postDelayed(() -> {
                linearProgressIndicator.setProgress(currentProgress);
                if (currentProgress == 0) {
                    hideFloatText();
                    fab.setEnabled(true);
                    isRun = false;
                }
            }, i * 18);
        }
    }
}