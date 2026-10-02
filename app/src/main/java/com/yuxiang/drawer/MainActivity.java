package com.yuxiang.drawer;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.appbar.MaterialToolbar;

public class MainActivity extends AppCompatActivity {
    SharedPreferences settingsPreferences;
    PasswordManager passwordManager;
    ActivityResultLauncher<Intent> overlayPermissionLauncher;
    ActivityResultLauncher<String> notificationPermissionLauncher;
    ActivityResultLauncher<Intent> accessibilitySettingsLauncher;

    FloatView floatView;
    MaterialSwitch bootSwitch;
    MaterialSwitch floatSwitch;
    MaterialSwitch backgroundSwitch;
    MaterialSwitch locationSwitch;
    Button drawButton;
    Button editButton;
    Button statButton;
    ImageButton resetLocationButton;
    ImageButton backgroundInfoButton;

    private PopupWindow richTooltip;

    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener =
            (preferences, key) -> {
                if ("float_state".equals(key)) {
                    floatSwitch.setChecked(preferences.getBoolean(key, false));
                } else if ("background_state".equals(key)) {
                    backgroundSwitch.setChecked(preferences.getBoolean(key, false));
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settingsPreferences = getSharedPreferences("settings", MODE_PRIVATE);

        int themeIndex = settingsPreferences.getInt("theme", 0);
        AppCompatDelegate.setDefaultNightMode(themeIndex == 0 ? AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM : themeIndex);

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                moveTaskToBack(true);
            }
        });

        // Check permission when user returns from settings
        overlayPermissionLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (Settings.canDrawOverlays(this)) {
                            Toast.makeText(this, getString(R.string.text_get_permission), Toast.LENGTH_SHORT).show();
                            floatView.showFloatButton();
                            FloatService.sync(this);
                        } else {
                            Toast.makeText(this, getString(R.string.text_no_permission), Toast.LENGTH_SHORT).show();
                            floatSwitch.setChecked(false);
                            settingsPreferences.edit().putBoolean("float_state", false).apply();
                        }
                    }
                }
        );

        notificationPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!granted) {
                        Toast.makeText(this, getString(R.string.text_no_notification_permission), Toast.LENGTH_LONG).show();
                    }
                    askForAccessibilityService();
                }
        );

        // the user comes back from the system accessibility settings
        accessibilitySettingsLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (FloatAccessibilityService.isEnabled(this) || FloatAccessibilityService.isConnected()) {
                        Toast.makeText(this, getString(R.string.text_accessibility_permission), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, getString(R.string.text_no_accessibility_permission), Toast.LENGTH_LONG).show();
                    }
                    // The accessibility service itself hands the button over when it connects
                    FloatService.sync(this);
                }
        );

        passwordManager = new PasswordManager(this);
        // the overlay windows are not owned by this Activity
        floatView = FloatView.getInstance(this);

        bootSwitch = findViewById(R.id.start_on_boot_switch);
        bootSwitch.setChecked(settingsPreferences.getBoolean("boot_state", false));
        bootSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            settingsPreferences.edit().putBoolean("boot_state", b).apply();
            if (b && isInstalledOnExternalStorage()) {
                // Apps on SD cards cannot auto-start after boot
                new MaterialAlertDialogBuilder(this)
                        .setMessage(R.string.text_boot_external_storage)
                        .setNegativeButton(R.string.ok, null)
                        .show();
            }
        });

        floatSwitch = findViewById(R.id.float_switch);
        floatSwitch.setChecked(settingsPreferences.getBoolean("float_state", false));
        floatSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            settingsPreferences.edit().putBoolean("float_state", b).apply();
            if (b) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.canDrawOverlays(this)) {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        overlayPermissionLauncher.launch(intent);
                    } else {
                        floatView.showFloatButton();
                    }
                } else {
                    floatView.showFloatButton();
                }
            } else {
                floatView.hideFloatButton();
            }
            FloatService.sync(this);
        });

        backgroundInfoButton = findViewById(R.id.background_info_button);
        TooltipCompat.setTooltipText(backgroundInfoButton, getString(R.string.action_background_info));
        backgroundInfoButton.setOnClickListener(view -> showFloatModeTooltip());

        backgroundSwitch = findViewById(R.id.background_switch);
        backgroundSwitch.setChecked(settingsPreferences.getBoolean("background_state", false));
        backgroundSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            settingsPreferences.edit().putBoolean("background_state", b).apply();
            if (b) {
                // Start with the foreground service; offer the accessibility alternative right after
                FloatService.sync(this);
                if (!requestNotificationPermission()) {
                    askForAccessibilityService();
                }
            } else {
                FloatService.sync(this);
                if (floatView.usesAccessibilityOverlay()) {
                    backgroundInfoButton.post(this::showAccessibilityStillRunningTooltip);
                }
            }
        });

        locationSwitch = findViewById(R.id.location_switch);
        locationSwitch.setChecked(settingsPreferences.getBoolean("is_remember_location", false));
        locationSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            settingsPreferences.edit().putBoolean("is_remember_location", b).apply();
            if (b) {
                floatView.saveLocation();
            }
        });

        settingsPreferences.registerOnSharedPreferenceChangeListener(preferenceListener);

        resetLocationButton = findViewById(R.id.reset_location_button);
        TooltipCompat.setTooltipText(resetLocationButton, getString(R.string.action_reset_location));
        resetLocationButton.setOnClickListener(view -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_reset_location)
                .setMessage(R.string.dialog_reset_location_text)
                .setPositiveButton(R.string.confirm, (dialogInterface, i) -> floatView.resetFloatButtonLocation())
                .setNegativeButton(R.string.cancel, null)
                .show());

        editButton = findViewById(R.id.edit_btn);
        editButton.setOnClickListener(view -> passwordManager.enterPassword());

        statButton = findViewById(R.id.stat_btn);
        statButton.setOnClickListener(view -> {
            StringBuilder result = new StringBuilder();
            for (String item : StringPool.initPool) {
                result.append(item).append(", ");
            }
            if (result.length() > 0) {
                result.setLength(result.length() - 2);
            }
            String message = getString(R.string.dialog_number_text, StringPool.initPool.size()) + "\n" + getString(R.string.dialog_name_text, result);
            ScrollView scrollView = new ScrollView(this);
            scrollView.setPadding(64, 16, 64, 0);
            TextView textView = new TextView(this);
            textView.setText(message);
            scrollView.addView(textView);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.lists_statistics)
                    .setView(scrollView)
                    .setNegativeButton(R.string.ok, null)
                    .show();
        });

        drawButton = findViewById(R.id.draw_btn);
        drawButton.setOnClickListener(view -> onDrawClicked());

        if (settingsPreferences.getBoolean("float_state", false)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                floatView.showFloatButton();
            }
        }

        FloatService.sync(this);

        Intent isBack = getIntent();
        if (isBack.getBooleanExtra("is_back", false)) {
            moveTaskToBack(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        floatSwitch.setChecked(settingsPreferences.getBoolean("float_state", false));
        backgroundSwitch.setChecked(settingsPreferences.getBoolean("background_state", false));
        FloatService.sync(this);
    }

    @Override
    protected void onDestroy() {
        dismissRichTooltip();
        settingsPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        if (isFinishing() && !FloatService.shouldKeepAlive(this)) {
            floatView.hideFloatButton();
            floatView.hideFloatText();
        }
        super.onDestroy();
    }

    private boolean isInstalledOnExternalStorage() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0;
    }

    private boolean requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return true;
        }
        return false;
    }

    private void askForAccessibilityService() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (!settingsPreferences.getBoolean("float_state", false)
                || !settingsPreferences.getBoolean("background_state", false)) {
            return;
        }
        if (FloatAccessibilityService.isEnabled(this) || FloatAccessibilityService.isConnected()) {
            // accessibility service enabled
            FloatService.sync(this);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_accessibility_title)
                .setMessage(R.string.dialog_accessibility_text)
                .setPositiveButton(R.string.dialog_accessibility_open, (dialogInterface, i) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    accessibilitySettingsLauncher.launch(intent);
                })
                .setNegativeButton(R.string.dialog_accessibility_skip, null)
                .show();
    }

    private void showFloatModeTooltip() {
        if (!settingsPreferences.getBoolean("float_state", false)) {
            showRichTooltip(backgroundInfoButton, getString(R.string.tooltip_float_mode_off_title),
                    getString(R.string.tooltip_float_mode_off_text), null, null);
        } else if (floatView.usesAccessibilityOverlay()) {
            showRichTooltip(backgroundInfoButton, getString(R.string.tooltip_float_mode_accessibility_title),
                    getString(R.string.tooltip_float_mode_accessibility_text), null, null);
        } else if (settingsPreferences.getBoolean("background_state", false)) {
            showRichTooltip(backgroundInfoButton, getString(R.string.tooltip_float_mode_service_title),
                    getString(R.string.tooltip_float_mode_service_text), null, null);
        } else {
            showRichTooltip(backgroundInfoButton, getString(R.string.tooltip_float_mode_activity_title),
                    getString(R.string.tooltip_float_mode_activity_text), null, null);
        }
    }

    // app cannot stop the accessibility service, only the system settings can
    private void showAccessibilityStillRunningTooltip() {
        if (isFinishing() || isDestroyed() || !floatView.usesAccessibilityOverlay()) {
            return;
        }
        showRichTooltip(backgroundInfoButton,
                getString(R.string.tooltip_accessibility_running_title),
                getString(R.string.tooltip_accessibility_running_text),
                getString(R.string.tooltip_accessibility_running_action),
                () -> accessibilitySettingsLauncher.launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    }

    private void showRichTooltip(View anchor, CharSequence title, CharSequence text,
                                 CharSequence actionText, Runnable action) {
        dismissRichTooltip();

        View content = LayoutInflater.from(this).inflate(R.layout.view_rich_tooltip, null);
        ((TextView) content.findViewById(R.id.tooltip_title)).setText(title);
        ((TextView) content.findViewById(R.id.tooltip_text)).setText(text);
        MaterialButton actionButton = content.findViewById(R.id.tooltip_action);
        if (actionText != null && action != null) {
            actionButton.setText(actionText);
            actionButton.setVisibility(View.VISIBLE);
            actionButton.setOnClickListener(view -> {
                dismissRichTooltip();
                action.run();
            });
        }

        PopupWindow tooltip = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        // transparent background can dismiss it
        tooltip.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        tooltip.setOutsideTouchable(true);
        tooltip.setFocusable(true);
        richTooltip = tooltip;

        int screenWidth = anchor.getResources().getDisplayMetrics().widthPixels;
        int margin = getResources().getDimensionPixelSize(R.dimen.spacing_s);
        content.measure(View.MeasureSpec.makeMeasureSpec(screenWidth - 2 * margin, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        int overflow = anchorLocation[0] + content.getMeasuredWidth() - (screenWidth - margin);
        tooltip.showAsDropDown(anchor, overflow > 0 ? -overflow : 0, 0);
    }

    private void dismissRichTooltip() {
        if (richTooltip != null) {
            richTooltip.dismiss();
            richTooltip = null;
        }
    }

    private void hideFloatButton() {
        settingsPreferences.edit().putBoolean("float_state", false).apply();
        floatView.hideFloatButton();
        floatView.hideFloatText();
        // hand the windows back to the layer the app owns
        floatView.useRegularOverlay();
    }

    private void onDrawClicked() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            // reuse the floating window
            floatView.run();
            return;
        }

        // without the overlay permission, display the drawn name in an in-app dialog
        String name = floatView.draw();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.text_is_null, Toast.LENGTH_SHORT).show();
        } else {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(name)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_app_bar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_theme) {
            new MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle(R.string.menu_theme)
                    .setSingleChoiceItems(new String[]{getString(R.string.theme_system), getString(R.string.theme_white), getString(R.string.theme_black)}, settingsPreferences.getInt("theme", 0), (dialogInterface, i) -> {
                        settingsPreferences.edit().putInt("theme", i).apply();
                        dialogInterface.dismiss();
                        AppCompatDelegate.setDefaultNightMode(i == 0 ? AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM : i);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else if (id == R.id.menu_close) {
            moveTaskToBack(true);
        } else if (id == R.id.menu_exit) {
            hideFloatButton();
            FloatService.stopNow(this);
            finishAffinity();
        } else if (id == R.id.menu_about) {
            LayoutInflater inflater = getLayoutInflater();
            View aboutDialog = inflater.inflate(R.layout.about_dialog, null);
            new MaterialAlertDialogBuilder(this)
                    .setView(aboutDialog)
                    .show();
        }
        return super.onOptionsItemSelected(item);
    }
}