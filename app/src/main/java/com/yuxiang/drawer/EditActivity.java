package com.yuxiang.drawer;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.WindowManager;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class EditActivity extends AppCompatActivity {
    EditText editText;
    EditText addInput;
    TextView totalText;
    TextView selectedText;
    ListView listView;
    ArrayAdapter<String> listAdapter;
    List<String> list = new ArrayList<>();

    SettingsStore settings;

    GroupStore groupStore;
    MaterialAutoCompleteTextView groupDropdown;
    List<String> groupIds = new ArrayList<>();
    String currentGroupId;
    String loadedContent = "";

    GuideSequence activeGuide;

    int findIndex = -1;

    TextInputLayout findInputLayout;
    TextInputEditText findInput;

    ActivityResultLauncher<String[]> importLauncher;
    ActivityResultLauncher<String> exportLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit);

        settings = SettingsStore.get(this);

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
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    if (inputStream == null) {
                        Toast.makeText(this, R.string.text_is_null, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Scanner scanner = new Scanner(inputStream, "UTF-8").useDelimiter("\\A");
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
                                    String merged = current.isEmpty()
                                            ? imported
                                            : current + (current.endsWith(",") ? "" : ",") + imported;
                                    editText.setText(merged);
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
                    OutputStream outputStream = getContentResolver().openOutputStream(uri);
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

        addInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(50)});

        // Material CheckBox List
        listAdapter = new ArrayAdapter<>(this, R.layout.item_edit_list, R.id.item_checkbox, list);
        listView.setAdapter(listAdapter);

        // Group selector row
        groupStore = GroupStore.get(this);
        groupDropdown = findViewById(R.id.edit_group_dropdown);
        MaterialButton groupAddButton = findViewById(R.id.group_add_btn);
        MaterialButton groupRenameButton = findViewById(R.id.group_rename_btn);
        MaterialButton groupDeleteButton = findViewById(R.id.group_delete_btn);

        groupDropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= groupIds.size()) {
                return;
            }
            String selected = groupIds.get(position);
            if (selected.equals(currentGroupId)) {
                return;
            }
            withDiscardConfirmation(() -> loadGroup(selected));
        });
        groupAddButton.setOnClickListener(v -> withDiscardConfirmation(() -> showGroupNameDialog(
                R.string.group_new_title, "", null,
                name -> {
                    loadGroup(groupStore.addGroup(name));
                    refreshGroupDropdown();
                    Toast.makeText(this, R.string.group_created_hint, Toast.LENGTH_SHORT).show();
                })));
        groupRenameButton.setOnClickListener(v -> showGroupNameDialog(
                R.string.group_rename_title, groupStore.getGroupName(currentGroupId), currentGroupId,
                name -> {
                    groupStore.renameGroup(currentGroupId, name);
                    refreshGroupDropdown();
                }));
        groupDeleteButton.setOnClickListener(v -> withDiscardConfirmation(this::showDeleteGroupDialog));
        TooltipCompat.setTooltipText(groupAddButton, getString(R.string.group_add));
        TooltipCompat.setTooltipText(groupRenameButton, getString(R.string.group_rename));
        TooltipCompat.setTooltipText(groupDeleteButton, getString(R.string.group_delete));

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

        MaterialButton addButton = findViewById(R.id.add_btn);
        addButton.setOnClickListener(v -> addItem());
        TooltipCompat.setTooltipText(addButton, getString(R.string.content_desc_add));

        MaterialButton removeButton = findViewById(R.id.remove_btn);
        removeButton.setOnClickListener(v -> removeSelected());
        TooltipCompat.setTooltipText(removeButton, getString(R.string.content_desc_remove));
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

        currentGroupId = groupStore.getActiveGroupId();
        refreshGroupDropdown();
        loadGroup(currentGroupId);

        if (savedInstanceState != null) {
            restoreEditorState(savedInstanceState);
        }

        if (!settings.isEditGuideShown()) {
            showWelcomeGuide();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // The editor state is not derived from the preferences, it has to survive a recreation
        outState.putString("state_group_id", currentGroupId);
        outState.putString("state_editor_text", editText.getText().toString());
        outState.putString("state_loaded_content", loadedContent);
    }

    // Restores the group and the unsaved edits after the Activity was recreated
    private void restoreEditorState(@NonNull Bundle savedState) {
        String groupId = savedState.getString("state_group_id");
        if (groupId == null || groupId.isEmpty() || !groupStore.getGroupIds().contains(groupId)) {
            return;
        }
        currentGroupId = groupId;
        editText.setText(savedState.getString("state_editor_text", ""));
        addInput.setText("");
        refreshList();
        loadedContent = savedState.getString("state_loaded_content", editText.getText().toString());
        refreshGroupDropdown();
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
        findIndex = -1;
        totalText.setText(getString(R.string.text_total, list.size()));
        selectedText.setText(getString(R.string.text_selected, 0));
    }

    // Reloads the group list and keeps the edited group selected
    private void refreshGroupDropdown() {
        // The edited group can disappear when it is removed, fall back to the active one
        if (!groupStore.getGroupIds().contains(currentGroupId)) {
            loadGroup(groupStore.getActiveGroupId());
        }
        groupIds = new ArrayList<>(groupStore.getGroupIds());
        String[] names = new String[groupIds.size()];
        for (int i = 0; i < groupIds.size(); i++) {
            names[i] = groupStore.getGroupName(groupIds.get(i));
        }
        groupDropdown.setSimpleItems(names);
        int index = groupIds.indexOf(currentGroupId);
        if (index >= 0) {
            groupDropdown.setText(names[index], false);
        }
    }

    // Loads a group's list into the editor
    private void loadGroup(String groupId) {
        if (groupId == null || groupId.isEmpty()) {
            return;
        }
        currentGroupId = groupId;
        editText.setText(String.join(",", groupStore.getInit(groupId)));
        addInput.setText("");
        refreshList();
        // remember the loaded content so later edits can be detected
        loadedContent = editText.getText().toString();
    }

    // True when the editor differs from the content that was loaded
    private boolean isListModified() {
        return !editText.getText().toString().equals(loadedContent);
    }

    // Runs the action when the editor holds no unsaved edits, otherwise asks first.
    // The dropdown label has to be resynced on every dismissal: the Material dropdown
    // already moved its text to the tapped item, while the edit itself may not happen.
    private void withDiscardConfirmation(Runnable action) {
        if (!isListModified()) {
            action.run();
            return;
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.group_switch_title)
                .setMessage(getString(R.string.group_switch_text, groupStore.getGroupName(currentGroupId)))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialogInterface, i) -> action.run())
                .create();
        dialog.setOnDismissListener(dialogInterface -> refreshGroupDropdown());
        dialog.show();
    }

    private void showDeleteGroupDialog() {
        if (groupStore.getGroupCount() <= 1) {
            Toast.makeText(this, R.string.group_last_cannot_delete, Toast.LENGTH_SHORT).show();
            return;
        }
        String name = groupStore.getGroupName(currentGroupId);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.group_delete_title)
                .setMessage(getString(R.string.group_delete_text, name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialogInterface, i) -> {
                    groupStore.deleteGroup(currentGroupId);
                    loadGroup(groupStore.getActiveGroupId());
                    refreshGroupDropdown();
                })
                .show();
    }

    // Shared input dialog for creating and renaming a group.
    // excludeId is the group that may keep its current name, null for a new group
    private void showGroupNameDialog(int titleId, String initialName, String excludeId, GroupNameCallback callback) {
        TextInputLayout inputLayout = new TextInputLayout(this, null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        inputLayout.setHint(getString(R.string.group_name_hint));
        TextInputEditText input = new TextInputEditText(inputLayout.getContext());
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(30)});
        input.setText(initialName);
        input.setSelection(input.getText().length());
        inputLayout.addView(input);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int sidePadding = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(sidePadding, 0, sidePadding, 0);
        container.addView(inputLayout);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(titleId)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, null)
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                inputLayout.setError(getString(R.string.group_name_empty));
                return;
            }
            String existing = groupStore.findGroupIdByName(name);
            if (existing != null && !existing.equals(excludeId)) {
                inputLayout.setError(getString(R.string.group_name_exists));
                return;
            }
            dialog.dismiss();
            callback.onConfirm(name);
        });

        input.requestFocus();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private interface GroupNameCallback {
        void onConfirm(String name);
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

        if (findInputLayout == null) {
            findInputLayout = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
            findInputLayout.setHint(getString(R.string.find_hint));
            findInput = new TextInputEditText(findInputLayout.getContext());
            findInput.setSingleLine(true);
            findInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            findInputLayout.addView(findInput);
        }
        if (findInputLayout.getParent() != null) {
            ((ViewGroup) findInputLayout.getParent()).removeView(findInputLayout);
        }
        container.addView(findInputLayout);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_find)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialogInterface, i) -> {
                    String query = findInput.getText().toString().trim();
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
                        for (int index = 0; index <= findIndex && index < list.size(); index++) {
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

        findInput.requestFocus();
        findInput.setSelection(findInput.getText().length());
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
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

    private void showWelcomeGuide() {
        if (activeGuide != null && activeGuide.isRunning()) {
            return;
        }
        activeGuide = new GuideSequence(this)
                .add(R.id.bottom_import_btn, R.string.action_import, R.string.guide_import_text)
                .add(R.id.bottom_password_btn, R.string.password, R.string.guide_password_text)
                .add(R.id.menu_save, R.string.action_save, R.string.guide_save_text)
                .setOnFinishListener(() -> settings.setEditGuideShown(true));
        activeGuide.start();
    }

    private void showHelpGuide() {
        if (activeGuide != null && activeGuide.isRunning()) {
            return;
        }
        activeGuide = new GuideSequence(this)
                .add(R.id.bottom_import_btn, R.string.action_import, R.string.guide_import_text)
                .add(R.id.bottom_password_btn, R.string.password, R.string.guide_password_text)
                .add(R.id.group_row, R.string.group_label, R.string.guide_group_text)
                .add(R.id.edit_text_area, R.string.guide_edit_area_title, R.string.guide_edit_area_text)
                .add(R.id.bottom_button_bar, R.string.guide_function_buttons_title, R.string.guide_function_buttons_text)
                .add(R.id.menu_save, R.string.action_save, R.string.guide_save_text);
        activeGuide.start();
    }

    private void showBackDialog() {
        if (!isListModified()) {
            // nothing was edited, there is nothing to discard
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.back)
                .setMessage(getString(R.string.text_is_discard, groupStore.getGroupName(currentGroupId)))
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

        groupStore.setInit(currentGroupId, cleanList);
        groupStore.setActiveGroup(currentGroupId);

        StringBuilder result = new StringBuilder();
        for (String item : cleanList) {
            result.append(item).append(", ");
        }
        if (result.length() > 0) {
            result.setLength(result.length() - 2);
        }
        String message = getString(R.string.dialog_group_text, groupStore.getGroupName(currentGroupId)) + "\n"
                + getString(R.string.dialog_number_text, cleanList.size()) + "\n"
                + getString(R.string.dialog_name_text, result);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setPadding(64, 16, 64, 0);
        TextView textView = new TextView(this);
        textView.setText(message);
        scrollView.addView(textView);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.lists_statistics)
                .setView(scrollView)
                .setPositiveButton(R.string.ok, null)
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
            showHelpGuide();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}