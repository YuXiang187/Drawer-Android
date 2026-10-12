package com.yuxiang.drawer;

import android.content.Context;
import android.content.SharedPreferences;

public final class SettingsStore {
    private static final String PREFS_NAME = "settings";

    // the main screen listens for changes of exactly these two keys
    public static final String KEY_FLOAT_STATE = "float_state";
    public static final String KEY_BACKGROUND_STATE = "background_state";

    private static final String KEY_START_ON_BOOT = "boot_state";
    private static final String KEY_REMEMBER_LOCATION = "is_remember_location";
    private static final String KEY_THEME = "theme";
    private static final String KEY_WELCOME_GUIDE_SHOWN = "guide_main_shown";
    private static final String KEY_DRAG_GUIDE_SHOWN = "guide_drag_shown";
    private static final String KEY_EDIT_GUIDE_SHOWN = "guide_edit_shown";

    private static final int THEME_SYSTEM = 0;

    private static volatile SettingsStore instance;

    private final SharedPreferences preferences;

    public static SettingsStore get(Context context) {
        if (instance == null) {
            synchronized (SettingsStore.class) {
                if (instance == null) {
                    instance = new SettingsStore(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private SettingsStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isStartOnBoot() {
        return preferences.getBoolean(KEY_START_ON_BOOT, false);
    }

    public void setStartOnBoot(boolean value) {
        preferences.edit().putBoolean(KEY_START_ON_BOOT, value).apply();
    }

    public boolean isFloatButtonEnabled() {
        return preferences.getBoolean(KEY_FLOAT_STATE, false);
    }

    public void setFloatButtonEnabled(boolean value) {
        preferences.edit().putBoolean(KEY_FLOAT_STATE, value).apply();
    }

    public boolean isBackgroundEnabled() {
        return preferences.getBoolean(KEY_BACKGROUND_STATE, false);
    }

    public void setBackgroundEnabled(boolean value) {
        preferences.edit().putBoolean(KEY_BACKGROUND_STATE, value).apply();
    }

    // The button is only kept alive in the background when both switches are on
    public boolean isBackgroundKeepAliveRequested() {
        return isFloatButtonEnabled() && isBackgroundEnabled();
    }

    public boolean isRememberLocation() {
        return preferences.getBoolean(KEY_REMEMBER_LOCATION, false);
    }

    public void setRememberLocation(boolean value) {
        preferences.edit().putBoolean(KEY_REMEMBER_LOCATION, value).apply();
    }

    public int getThemeMode() {
        return preferences.getInt(KEY_THEME, THEME_SYSTEM);
    }

    public void setThemeMode(int mode) {
        preferences.edit().putInt(KEY_THEME, mode).apply();
    }

    public boolean isWelcomeGuideShown() {
        return preferences.getBoolean(KEY_WELCOME_GUIDE_SHOWN, false);
    }

    public void setWelcomeGuideShown(boolean value) {
        preferences.edit().putBoolean(KEY_WELCOME_GUIDE_SHOWN, value).apply();
    }

    public boolean isDragGuideShown() {
        return preferences.getBoolean(KEY_DRAG_GUIDE_SHOWN, false);
    }

    public void setDragGuideShown(boolean value) {
        preferences.edit().putBoolean(KEY_DRAG_GUIDE_SHOWN, value).apply();
    }

    public boolean isEditGuideShown() {
        return preferences.getBoolean(KEY_EDIT_GUIDE_SHOWN, false);
    }

    public void setEditGuideShown(boolean value) {
        preferences.edit().putBoolean(KEY_EDIT_GUIDE_SHOWN, value).apply();
    }

    public void registerChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
