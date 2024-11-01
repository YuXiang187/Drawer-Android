package com.yuxiang.drawer;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;

public class EditActivity extends AppCompatActivity {
    EditText editText;
    TextView countTextView;
    SharedPreferences initPoolPreferences;
    SharedPreferences poolPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit);

        initPoolPreferences = getSharedPreferences("init", MODE_PRIVATE);
        poolPreferences = getSharedPreferences("pool", MODE_PRIVATE);

        MaterialToolbar toolbar = findViewById(R.id.edit_topAppBar);
        setSupportActionBar(toolbar);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.edit_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showBackDialog();
            }
        });

        editText = findViewById(R.id.edit_text_area);
        countTextView = findViewById(R.id.count_text_view);

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                int number = editText.getText().toString().split(",").length;
                countTextView.setText(getString(R.string.dialog_number_text, number));
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        editText.setText(String.join(",", StringPool.initPool));
    }

    private void showBackDialog() {
        new MaterialAlertDialogBuilder(EditActivity.this)
                .setTitle(R.string.back)
                .setMessage(R.string.text_is_discard)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialogInterface, i) -> finish())
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.edit_app_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save) {
            if (!editText.getText().toString().isEmpty()) {
                initPoolPreferences.edit().putString("init", editText.getText().toString()).apply();
                poolPreferences.edit().putString("pool", editText.getText().toString()).apply();
                StringPool.initPool = new ArrayList<>(Arrays.asList(editText.getText().toString().split(",")));
                StringPool.reset();
                Toast.makeText(this, R.string.lists_saved, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, R.string.text_is_null, Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.menu_clear) {
            editText.setText("");
        } else if (id == R.id.menu_close) {
            showBackDialog();
        }
        return super.onOptionsItemSelected(item);
    }
}