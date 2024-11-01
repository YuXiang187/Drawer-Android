package com.yuxiang.drawer;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
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

import java.util.Objects;

public class PasswordManager {
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
        passwordImage.setImageResource(R.drawable.password);

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
    }

    public boolean isPasswordCurrent(String password) {
        if (password.equals(passwordPreferences.getString("password", "123456"))) {
            Toast.makeText(context, R.string.password_correct, Toast.LENGTH_SHORT).show();
            return true;
        } else {
            Toast.makeText(context, R.string.password_incorrect, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    public void enterPassword() {
        refreshLayout(customTitle(R.string.dialog_enter_password));
        new MaterialAlertDialogBuilder(context)
                .setView(layout)
                .setNeutralButton(R.string.change, (dialogInterface, i) -> {
                    String password = Objects.requireNonNull(editText.getText()).toString().trim();
                    if (isPasswordCurrent(password)) {
                        changePassword();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialogInterface, i) -> {
                    String password = Objects.requireNonNull(editText.getText()).toString().trim();
                    if (isPasswordCurrent(password)) {
                        Intent intent = new Intent(context, EditActivity.class);
                        context.startActivity(intent);
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
                    passwordPreferences.edit().putString("password", password).apply();
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
        title.setGravity(android.view.Gravity.CENTER);
        title.setTextSize(24);
        title.setPadding(0, 0, 0, 20);
        return title;
    }
}