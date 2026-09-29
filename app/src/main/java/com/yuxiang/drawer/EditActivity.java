package com.yuxiang.drawer;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

public class EditActivity extends AppCompatActivity {

    EditText editText;
    EditText addInput;
    TextView totalText;
    TextView selectedText;
    ListView listView;
    ArrayAdapter<String> listAdapter;
    List<String> list = new ArrayList<>();

    SharedPreferences initPoolPreferences;
    SharedPreferences poolPreferences;

    int findIndex = -1;

    ActivityResultLauncher<String[]> importLauncher;
    ActivityResultLauncher<String> exportLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit);

        initPoolPreferences = getSharedPreferences("init", MODE_PRIVATE);
        poolPreferences = getSharedPreferences("pool", MODE_PRIVATE);

        MaterialToolbar toolbar = findViewById(R.id.edit_topAppBar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> showBackDialog());
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

        // import txt file
        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                try {
                    java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
                    if (inputStream == null) {
                        Toast.makeText(this, R.string.text_is_null, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    java.util.Scanner scanner = new java.util.Scanner(inputStream, "UTF-8").useDelimiter("\\A");
                    String content = scanner.hasNext() ? scanner.next() : "";
                    scanner.close();
                    if (content.trim().isEmpty()) {
                        Toast.makeText(this, R.string.text_is_null, Toast.LENGTH_SHORT).show();
                    } else {
                        String imported = content.trim();
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.import_confirm_title)
                                .setMessage(R.string.import_confirm_message)
                                .setNeutralButton(R.string.cancel, null)
                                .setNegativeButton(R.string.import_append, (dialogInterface, i) -> {
                                    String current = editText.getText().toString();
                                    if (current.isEmpty()) {
                                        editText.setText(imported);
                                    } else {
                                        editText.setText(current + (current.endsWith(",") ? "" : ",") + imported);
                                    }
                                })
                                .setPositiveButton(R.string.import_overwrite, (dialogInterface, i) -> {
                                    editText.setText(imported);
                                })
                                .show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, R.string.text_is_null, Toast.LENGTH_SHORT).show();
                }
            }
        });

        // export txt file
        exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain"), uri -> {
            if (uri != null) {
                try {
                    java.io.OutputStream outputStream = getContentResolver().openOutputStream(uri);
                    if (outputStream == null) {
                        Toast.makeText(this, R.string.text_is_null, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    outputStream.write(editText.getText().toString().getBytes("UTF-8"));
                    outputStream.close();
                    Toast.makeText(this, R.string.lists_saved, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, R.string.text_is_null, Toast.LENGTH_SHORT).show();
                }
            }
        });

        editText = findViewById(R.id.edit_text_area);
        addInput = findViewById(R.id.add_text_input);
        totalText = findViewById(R.id.total_text);
        selectedText = findViewById(R.id.selected_text);
        listView = findViewById(R.id.list_view);

        // Material CheckBox List
        listAdapter = new ArrayAdapter<>(this, R.layout.item_edit_list, R.id.item_checkbox, list);
        listView.setAdapter(listAdapter);

        // Update the selected count when items are checked/unchecked
        listView.setOnItemClickListener((parent, view, position, id) -> {
            SparseBooleanArray checked = listView.getCheckedItemPositions();
            int count = 0;
            if (checked != null) {
                for (int i = 0; i < checked.size(); i++) {
                    if (checked.valueAt(i)) {
                        count++;
                    }
                }
            }
            selectedText.setText(getString(R.string.text_selected, count));
        });

        // Docked Toolbar: import, export, find, clear, password
        MaterialButton importButton = findViewById(R.id.bottom_import_btn);
        importButton.setOnClickListener(v -> importLauncher.launch(new String[]{"text/plain"}));
        TooltipCompat.setTooltipText(importButton, getString(R.string.action_import));

        MaterialButton exportButton = findViewById(R.id.bottom_export_btn);
        exportButton.setOnClickListener(v -> {
            if (editText.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, R.string.text_is_null, Toast.LENGTH_SHORT).show();
            } else {
                exportLauncher.launch("drawer_list.txt");
            }
        });
        TooltipCompat.setTooltipText(exportButton, getString(R.string.action_export));

        MaterialButton findButton = findViewById(R.id.bottom_find_btn);
        findButton.setOnClickListener(v -> showFindDialog());
        TooltipCompat.setTooltipText(findButton, getString(R.string.action_find));

        MaterialButton clearButton = findViewById(R.id.bottom_clear_btn);
        clearButton.setOnClickListener(v -> showClearDialog());
        TooltipCompat.setTooltipText(clearButton, getString(R.string.action_clear));

        MaterialButton passwordButton = findViewById(R.id.bottom_password_btn);
        passwordButton.setOnClickListener(v -> new PasswordManager(this).changePassword());
        TooltipCompat.setTooltipText(passwordButton, getString(R.string.password));

        findViewById(R.id.add_btn).setOnClickListener(v -> addItem());
        findViewById(R.id.remove_btn).setOnClickListener(v -> removeSelected());
        addInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addItem();
                return true;
            }
            return false;
        });

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                String modifiedText = charSequence.toString()
                        .replace("\r", "")
                        .replace("\n", ",")
                        .replace(" ", "")
                        .replace("\t", "");
                if (!charSequence.toString().equals(modifiedText)) {
                    editText.setText(modifiedText);
                    editText.setSelection(modifiedText.length());
                }
                refreshList();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        editText.setText(initPoolPreferences.getString("init", ""));
    }

    private void refreshList() {
        list.clear();
        for (String s : editText.getText().toString().split(",")) {
            if (!s.isEmpty()) {
                list.add(s);
            }
        }
        listAdapter.notifyDataSetChanged();
        listView.clearChoices();
        totalText.setText(getString(R.string.text_total, list.size()));
        selectedText.setText(getString(R.string.text_selected, 0));
    }

    private void addItem() {
        String text = addInput.getText().toString().trim();
        if (text.isEmpty()) {
            addInput.setText("");
            return;
        }
        if (text.contains(",")) {
            Toast.makeText(this, R.string.no_comma, Toast.LENGTH_SHORT).show();
            return;
        }
        String current = editText.getText().toString();
        editText.setText(current.isEmpty() ? text : current + "," + text);
        addInput.setText("");
    }

    private void removeSelected() {
        SparseBooleanArray checked = listView.getCheckedItemPositions();
        if (checked == null || checked.size() == 0) {
            return;
        }
        List<String> remaining = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (!checked.get(i)) {
                remaining.add(list.get(i));
            }
        }
        editText.setText(String.join(",", remaining));
    }

    private void showFindDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int sidePadding = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(sidePadding, 0, sidePadding, 0);

        TextInputLayout inputLayout = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        inputLayout.setHint(getString(R.string.find_hint));
        TextInputEditText input = new TextInputEditText(inputLayout.getContext());
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        inputLayout.addView(input);
        container.addView(inputLayout);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_find)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialogInterface, i) -> {
                    String query = input.getText().toString().trim();
                    if (query.isEmpty()) {
                        return;
                    }
                    int found = -1;
                    for (int index = findIndex + 1; index < list.size(); index++) {
                        if (list.get(index).toLowerCase().contains(query.toLowerCase())) {
                            found = index;
                            break;
                        }
                    }
                    if (found == -1) {
                        for (int index = 0; index <= findIndex; index++) {
                            if (list.get(index).toLowerCase().contains(query.toLowerCase())) {
                                found = index;
                                break;
                            }
                        }
                    }
                    if (found >= 0) {
                        findIndex = found;
                        listView.clearChoices();
                        listView.setItemChecked(found, true);
                        listView.smoothScrollToPosition(found);
                        selectedText.setText(getString(R.string.text_selected, 1));
                    } else {
                        findIndex = -1;
                        Toast.makeText(this, R.string.find_done, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showClearDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_clear)
                .setMessage(R.string.clear_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialogInterface, i) -> {
                    editText.setText("");
                    addInput.setText("");
                    listView.clearChoices();
                })
                .show();
    }

    private void showHelpDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_text)
                .setNegativeButton(R.string.ok, null)
                .show();
    }

    private void showBackDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.back)
                .setMessage(R.string.text_is_discard)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialogInterface, i) -> finish())
                .show();
    }

    private void apply() {
        List<String> cleanList = new ArrayList<>();
        for (String s : list) {
            if (!s.isEmpty()) {
                cleanList.add(s);
            }
        }
        if (cleanList.isEmpty()) {
            Toast.makeText(this, R.string.text_is_null, Toast.LENGTH_SHORT).show();
            return;
        }

        StringPool.setNames(new ArrayList<>(cleanList));
        initPoolPreferences.edit().putString("init", String.join(",", StringPool.initPool)).apply();
        poolPreferences.edit().putString("pool", String.join(",", StringPool.pool)).apply();

        StringBuilder result = new StringBuilder();
        for (String item : cleanList) {
            result.append(item).append(", ");
        }
        if (result.length() > 0) {
            result.setLength(result.length() - 2);
        }
        String message = getString(R.string.dialog_number_text, cleanList.size()) + "\n" + getString(R.string.dialog_name_text, result);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setPadding(64, 16, 64, 0);
        TextView textView = new TextView(this);
        textView.setText(message);
        scrollView.addView(textView);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.lists_statisticians)
                .setView(scrollView)
                .setNegativeButton(R.string.ok, (dialogInterface, i) -> finish())
                .setOnDismissListener(dialogInterface -> finish())
                .show();
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
            apply();
            return true;
        } else if (id == R.id.menu_help) {
            showHelpDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}