package com.yuxiang.drawer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.appbar.MaterialToolbar;

public class MainActivity extends AppCompatActivity {
    SharedPreferences bootPreferences;
    SharedPreferences switchPreferences;
    SharedPreferences themePreferences;

    PasswordManager passwordManager;
    ActivityResultLauncher<Intent> overlayPermissionLauncher;

    FloatView floatView;
    MaterialSwitch bootSwitch;
    MaterialSwitch floatSwitch;
    Button editButton;
    Button statButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        themePreferences = getSharedPreferences("theme", MODE_PRIVATE);
        bootPreferences = getSharedPreferences("boot_state", MODE_PRIVATE);
        switchPreferences = getSharedPreferences("switch_state", MODE_PRIVATE);

        int themeIndex = themePreferences.getInt("theme", 0);
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
                        } else {
                            Toast.makeText(this, getString(R.string.text_no_permission), Toast.LENGTH_SHORT).show();
                            floatSwitch.setChecked(false);
                            switchPreferences.edit().putBoolean("switch_state", false).apply();
                        }
                    }
                }
        );

        passwordManager = new PasswordManager(this);
        floatView = new FloatView(this);

        bootSwitch = findViewById(R.id.start_on_boot_switch);
        bootSwitch.setChecked(bootPreferences.getBoolean("boot_state", false));
        bootSwitch.setOnCheckedChangeListener((compoundButton, b) -> bootPreferences.edit().putBoolean("boot_state", b).apply());

        floatSwitch = findViewById(R.id.float_switch);
        floatSwitch.setChecked(switchPreferences.getBoolean("switch_state", false));
        floatSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.canDrawOverlays(this)) {
                        requestPermission(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true);
                    } else {
                        floatView.showFloatButton();
                    }
                } else {
                    floatView.showFloatButton();
                }
            } else {
                floatView.hideFloatButton(true);
            }
            switchPreferences.edit().putBoolean("switch_state", b).apply();
        });

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
                    .setTitle(R.string.lists_statisticians)
                    .setView(scrollView)
                    .setNegativeButton(R.string.ok, null)
                    .show();
        });

        Button batteryButton = findViewById(R.id.battery_btn);
        batteryButton.setOnClickListener(view -> new MaterialAlertDialogBuilder(MainActivity.this)
                .setTitle(R.string.optimization_battery)
                .setMessage(R.string.optimization_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermission(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, false);
                    }
                })
                .show());

        if (switchPreferences.getBoolean("switch_state", false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this) && !FloatView.isButtonViewAdded) {
                    floatView.showFloatButton();
                }
            } else {
                if (!FloatView.isButtonViewAdded) {
                    floatView.showFloatButton();
                }
            }
        }

        Intent isBack = getIntent();
        if (isBack.getBooleanExtra("is_back", false)) {
            moveTaskToBack(true);
        }
    }

    private void requestPermission(String action, boolean isUri) {
        Intent intent = new Intent();
        intent.setAction(action);
        if (isUri) {
            intent.setData(Uri.parse("package:" + getPackageName()));
            overlayPermissionLauncher.launch(intent);
        } else {
            startActivity(intent);
        }
    }

    @Override
    protected void onDestroy() {
        floatView.hideFloatButton(false);
        floatView.hideFloatText();
        super.onDestroy();
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
        if (id == R.id.menu_run) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    floatView.run();
                } else {
                    Toast.makeText(this, R.string.text_no_permission, Toast.LENGTH_SHORT).show();
                }
            } else {
                floatView.run();
            }
        } else if (id == R.id.menu_theme) {
            new MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle(R.string.menu_theme)
                    .setSingleChoiceItems(new String[]{getString(R.string.theme_system), getString(R.string.theme_white), getString(R.string.theme_black)}, themePreferences.getInt("theme", 0), (dialogInterface, i) -> {
                        themePreferences.edit().putInt("theme", i).apply();
                        dialogInterface.dismiss();
                        AppCompatDelegate.setDefaultNightMode(i == 0 ? AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM : i);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else if (id == R.id.menu_close) {
            moveTaskToBack(true);
        } else if (id == R.id.menu_exit) {
            new MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle(R.string.menu_exit)
                    .setMessage(R.string.text_is_exit)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.confirm, (dialogInterface, i) -> finishAffinity())
                    .show();
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