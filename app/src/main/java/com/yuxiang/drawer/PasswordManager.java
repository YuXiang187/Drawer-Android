package com.yuxiang.drawer;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
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

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordManager {
    private static final String PREFS_NAME = "password";
    private static final String KEY_HASH = "password_hash";
    private static final String KEY_SALT = "password_salt";
    private static final String LEGACY_KEY = "password";
    private static final String DEFAULT_PASSWORD = "123456";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final String ALGORITHM_TAG = "pbkdf2-sha1";
    private static final String FIELD_SEPARATOR = "\\$";
    private static final int PBKDF2_ITERATIONS = 100000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;

    private static final ExecutorService HASH_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    Context context;
    LinearLayout layout;
    ImageView passwordImage;
    TextInputLayout textInputLayout;
    TextInputEditText editText;
    TextView limitTextView;
    LinearProgressIndicator progressIndicator;
    SharedPreferences passwordPreferences;

    public PasswordManager(Context context) {
        this.context = context;
        passwordPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

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

        progressIndicator = new LinearProgressIndicator(context);
        progressIndicator.setIndeterminate(true);
        progressIndicator.setVisibility(View.GONE);

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

    private AlertDialog showDialog() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, null)
                .show();
        dialog.setOnDismissListener(dialogInterface -> {
            editText.setText("");
            editText.setEnabled(true);
            progressIndicator.setVisibility(View.GONE);
        });
        return dialog;
    }

    private void setBusy(AlertDialog dialog, boolean busy) {
        progressIndicator.setVisibility(busy ? View.VISIBLE : View.GONE);
        editText.setEnabled(!busy);
        if (dialog.isShowing()) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!busy);
        }
    }

    private String currentInput() {
        return Objects.requireNonNull(editText.getText()).toString().trim();
    }

    public void enterPassword() {
        refreshLayout(customTitle(R.string.dialog_enter_password));
        final AlertDialog dialog = showDialog();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            final String password = currentInput();
            setBusy(dialog, true);
            // Hashing takes a moment, so the answer arrives on the main thread later
            HASH_EXECUTOR.execute(() -> {
                final boolean accepted = isPasswordCurrent(password);
                MAIN_HANDLER.post(() -> {
                    setBusy(dialog, false);
                    if (!accepted) {
                        Toast.makeText(context, R.string.password_incorrect, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    dialog.dismiss();
                    openEditor(password);
                });
            });
        });
    }

    public void changePassword() {
        refreshLayout(customTitle(R.string.dialog_change_password));
        final AlertDialog dialog = showDialog();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            final String password = currentInput();
            if (password.isEmpty()) {
                Toast.makeText(context, R.string.password_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            setBusy(dialog, true);
            HASH_EXECUTOR.execute(() -> {
                storePassword(password);
                MAIN_HANDLER.post(() -> {
                    setBusy(dialog, false);
                    dialog.dismiss();
                    Toast.makeText(context, R.string.password_changed, Toast.LENGTH_SHORT).show();
                });
            });
        });
    }

    private boolean isPasswordCurrent(String password) {
        String storedHash = passwordPreferences.getString(KEY_HASH, null);
        if (storedHash == null) {
            // No custom password has ever been set; the built-in default applies.
            return password.equals(DEFAULT_PASSWORD);
        }

        StoredHash stored = StoredHash.parse(storedHash, passwordPreferences.getString(KEY_SALT, null));
        if (stored == null) {
            return false;
        }
        String candidate = hashPassword(password, stored.salt, stored.iterations);
        if (candidate == null || !constantTimeEquals(candidate, stored.hash)) {
            return false;
        }
        if (stored.outdated) {
            // The password is correct, so this is the moment to move it to the current format
            storePassword(password);
        }
        return true;
    }

    // Derives a PBKDF2 hash of the given password using the supplied salt, or null on failure.
    private static String hashPassword(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = null;
        try {
            spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return Base64.encodeToString(factory.generateSecret(spec).getEncoded(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        } finally {
            if (spec != null) {
                spec.clearPassword();
            }
        }
    }

    // Hashes and stores the password in the current format, replacing every older entry
    private void storePassword(String password) {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        new SecureRandom().nextBytes(salt);
        String hash = hashPassword(password, salt, PBKDF2_ITERATIONS);
        if (hash == null) {
            return;
        }
        String stored = ALGORITHM_TAG + "$" + PBKDF2_ITERATIONS + "$"
                + Base64.encodeToString(salt, Base64.NO_WRAP) + "$" + hash;
        passwordPreferences.edit()
                .putString(KEY_HASH, stored)
                .remove(KEY_SALT)
                .remove(LEGACY_KEY)
                .apply();
    }

    // Migrates a plain-text password saved by an older version into a salted hash
    private void migrateLegacyPassword() {
        if (passwordPreferences.contains(KEY_HASH) || !passwordPreferences.contains(LEGACY_KEY)) {
            return;
        }
        String legacy = passwordPreferences.getString(LEGACY_KEY, null);
        if (legacy == null || legacy.isEmpty()) {
            passwordPreferences.edit().remove(LEGACY_KEY).apply();
            return;
        }
        // Deriving the hash is slow, so the migration finishes in the background
        HASH_EXECUTOR.execute(() -> {
            if (passwordPreferences.contains(LEGACY_KEY)) {
                storePassword(legacy);
            }
        });
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private void openEditor(String password) {
        if (!password.equals(DEFAULT_PASSWORD)) {
            startEditor();
            return;
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_password_warning_title)
                .setMessage(R.string.dialog_password_warning_text)
                .setNegativeButton(R.string.ok, null)
                .setOnDismissListener(dialog -> startEditor())
                .show();
    }

    private void startEditor() {
        context.startActivity(new Intent(context, EditActivity.class));
    }

    private static class StoredHash {
        final String hash;
        final byte[] salt;
        final int iterations;
        final boolean outdated;

        private StoredHash(String hash, byte[] salt, int iterations, boolean outdated) {
            this.hash = hash;
            this.salt = salt;
            this.iterations = iterations;
            this.outdated = outdated;
        }

        static StoredHash parse(String storedHash, String storedSalt) {
            String[] parts = storedHash.split(FIELD_SEPARATOR);
            if (parts.length == 4 && ALGORITHM_TAG.equals(parts[0])) {
                try {
                    int iterations = Integer.parseInt(parts[1]);
                    if (iterations <= 0) {
                        return null;
                    }
                    return new StoredHash(parts[3], Base64.decode(parts[2], Base64.NO_WRAP),
                            iterations, iterations < PBKDF2_ITERATIONS);
                } catch (RuntimeException e) {
                    return null;
                }
            }
            if (storedSalt == null) {
                return null;
            }
            try {
                return new StoredHash(storedHash, Base64.decode(storedSalt, Base64.NO_WRAP),
                        PBKDF2_ITERATIONS, true);
            } catch (RuntimeException e) {
                return null;
            }
        }
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
        layout.addView(progressIndicator);
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