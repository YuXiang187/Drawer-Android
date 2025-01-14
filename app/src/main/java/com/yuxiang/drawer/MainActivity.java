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
import android.widget.RadioGroup;
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
import com.google.android.material.radiobutton.MaterialRadioButton;

public class MainActivity extends AppCompatActivity {
    static boolean isRememberLocation;

    SharedPreferences settingsPreferences;
    PasswordManager passwordManager;
    ActivityResultLauncher<Intent> overlayPermissionLauncher;

    FloatView floatView;
    MaterialSwitch bootSwitch;
    MaterialSwitch floatSwitch;
    RadioGroup radioGroup;
    MaterialSwitch locationSwitch;
    Button editButton;
    Button statButton;

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
                        } else {
                            Toast.makeText(this, getString(R.string.text_no_permission), Toast.LENGTH_SHORT).show();
                            floatSwitch.setChecked(false);
                            settingsPreferences.edit().putBoolean("float_state", false).apply();
                        }
                    }
                }
        );

        passwordManager = new PasswordManager(this);
        floatView = new FloatView(this);

        bootSwitch = findViewById(R.id.start_on_boot_switch);
        bootSwitch.setChecked(settingsPreferences.getBoolean("boot_state", false));
        bootSwitch.setOnCheckedChangeListener((compoundButton, b) -> settingsPreferences.edit().putBoolean("boot_state", b).apply());

        floatSwitch = findViewById(R.id.float_switch);
        floatSwitch.setChecked(settingsPreferences.getBoolean("float_state", false));
        floatSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
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
                floatView.hideFloatButton(true);
                floatView.locationPreferences.edit().remove("locationX").apply();
                floatView.locationPreferences.edit().remove("locationY").apply();
            }
            settingsPreferences.edit().putBoolean("float_state", b).apply();
        });

        radioGroup = findViewById(R.id.radio_group);
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.common_radio_btn) {
                floatView.isCommonButton(true);
                settingsPreferences.edit().putBoolean("is_common_button", true).apply();
            } else if (checkedId == R.id.image_radio_btn) {
                floatView.isCommonButton(false);
                settingsPreferences.edit().putBoolean("is_common_button", false).apply();
            }
        });

        isRememberLocation = settingsPreferences.getBoolean("is_remember_location", false);
        locationSwitch = findViewById(R.id.location_switch);
        locationSwitch.setChecked(isRememberLocation);
        locationSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            isRememberLocation = b;
            settingsPreferences.edit().putBoolean("is_remember_location", b).apply();
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

        if (settingsPreferences.getBoolean("float_state", false)) {
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

        MaterialRadioButton commonRadioButton = findViewById(R.id.common_radio_btn);
        MaterialRadioButton imageRadioButton = findViewById(R.id.image_radio_btn);
        if (settingsPreferences.getBoolean("is_common_button", true)) {
            commonRadioButton.setChecked(true);
        } else {
            imageRadioButton.setChecked(true);
        }

        Intent isBack = getIntent();
        if (isBack.getBooleanExtra("is_back", false)) {
            moveTaskToBack(true);
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
            floatView.hideFloatButton(false);
            floatView.hideFloatText();
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