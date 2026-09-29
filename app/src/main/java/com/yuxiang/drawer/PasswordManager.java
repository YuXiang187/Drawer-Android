package com.yuxiang.drawer;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Objects;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordManager {
    private static final String KEY_HASH = "password_hash";
    private static final String KEY_SALT = "password_salt";
    private static final String LEGACY_KEY = "password";
    private static final String DEFAULT_PASSWORD = "123456";
    private static final int PBKDF2_ITERATIONS = 100000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;

    Context context;
    LinearLayout layout;
    ImageView passwordImage;
    TextInputLayout textInputLayout;
    TextInputEditText editText;
    TextView limitTextView;
    SharedPreferences passwordPreferences;

    public PasswordManager(Context context) {
        this.context = context;
        passwordPreferences = context.getSharedPreferences("password", MODE_PRIVATE);

        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 16);

        passwordImage = new ImageView(context);
        passwordImage.setImageResource(R.drawable.ic_password);

        textInputLayout = new TextInputLayout(context);
        textInputLayout.setHint(R.string.password);
        textInputLayout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);

        editText = new TextInputEditText(context);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});

        limitTextView = new TextView(context);
        limitTextView.setGravity(Gravity.END);
        limitTextView.setText(context.getString(R.string.password_limit, editText.length()));

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                limitTextView.setText(context.getString(R.string.password_limit, editText.length()));
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        textInputLayout.addView(editText);

        migrateLegacyPassword();
    }

    public boolean isPasswordCurrent(String password) {
        String storedHash = passwordPreferences.getString(KEY_HASH, null);
        if (storedHash == null) {
            // No custom password has ever been set; the built-in default applies.
            if (password.equals(DEFAULT_PASSWORD)) {
                return true;
            }
        } else {
            String storedSalt = passwordPreferences.getString(KEY_SALT, null);
            if (storedSalt != null) {
                String candidate = hashPassword(password, Base64.decode(storedSalt, Base64.NO_WRAP));
                if (candidate != null && constantTimeEquals(candidate, storedHash)) {
                    return true;
                }
            }
        }
        Toast.makeText(context, R.string.password_incorrect, Toast.LENGTH_SHORT).show();
        return false;
    }

    // Derives a PBKDF2 hash of the given password using the supplied salt, or null on failure.
    private static String hashPassword(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return Base64.encodeToString(factory.generateSecret(spec).getEncoded(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        } finally {
            spec.clearPassword();
        }
    }

    // Hashes and stores the password, replacing any legacy plain-text entry.
    private void storePassword(String password) {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        new SecureRandom().nextBytes(salt);
        String hash = hashPassword(password, salt);
        if (hash == null) {
            return;
        }
        passwordPreferences.edit()
                .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_HASH, hash)
                .remove(LEGACY_KEY)
                .apply();
    }

    // Migrates a plain-text password saved by an older version into a salted hash.
    private void migrateLegacyPassword() {
        if (passwordPreferences.contains(KEY_HASH) || !passwordPreferences.contains(LEGACY_KEY)) {
            return;
        }
        String legacy = passwordPreferences.getString(LEGACY_KEY, null);
        if (legacy == null || legacy.isEmpty()) {
            passwordPreferences.edit().remove(LEGACY_KEY).apply();
            return;
        }
        storePassword(legacy);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public void enterPassword() {
        refreshLayout(customTitle(R.string.dialog_enter_password));
        new MaterialAlertDialogBuilder(context)
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialogInterface, i) -> {
                    String password = Objects.requireNonNull(editText.getText()).toString().trim();
                    if (isPasswordCurrent(password)) {
                        if (password.equals(DEFAULT_PASSWORD)) {
                            new MaterialAlertDialogBuilder(context)
                                    .setTitle(R.string.dialog_password_warning_title)
                                    .setMessage(R.string.dialog_password_warning_text)
                                    .setNegativeButton(R.string.ok, null)
                                    .setOnDismissListener(dialog -> {
                                        Intent intent = new Intent(context, EditActivity.class);
                                        context.startActivity(intent);
                                    })
                                    .show();
                        } else {
                            Intent intent = new Intent(context, EditActivity.class);
                            context.startActivity(intent);
                        }
                    }
                })
                .setOnDismissListener(dialogInterface -> editText.setText(""))
                .show();
    }

    public void changePassword() {
        refreshLayout(customTitle(R.string.dialog_change_password));
        new MaterialAlertDialogBuilder(context)
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialogInterface, i) -> {
                    String password = Objects.requireNonNull(editText.getText()).toString().trim();
                    if (password.isEmpty()) {
                        Toast.makeText(context, R.string.password_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    storePassword(password);
                    Toast.makeText(context, R.string.password_changed, Toast.LENGTH_SHORT).show();
                })
                .setOnDismissListener(dialogInterface -> editText.setText(""))
                .show();
    }

    private void refreshLayout(View title) {
        if (layout.getParent() != null) {
            ((ViewGroup) layout.getParent()).removeView(layout);
        }
        layout.removeAllViews();
        layout.addView(passwordImage);
        layout.addView(title);
        layout.addView(textInputLayout);
        layout.addView(limitTextView);
    }

    private TextView customTitle(int text) {
        TextView title = new TextView(context);
        title.setText(text);
        title.setGravity(Gravity.CENTER);
        title.setTextSize(24);
        title.setPadding(0, 0, 0, 20);
        return title;
    }
}