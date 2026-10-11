package com.yuxiang.drawer;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupStore {
    private static final String PREFS_NAME = "groups";
    private static final String KEY_IDS = "group_ids";
    private static final String KEY_ACTIVE = "active_group";
    private static final String KEY_NEXT_ID = "next_id";
    private static final String PREFIX_NAME = "name_";
    private static final String PREFIX_INIT = "init_";
    private static final String PREFIX_POOL = "pool_";

    private static final String LEGACY_INIT_PREFS = "init";
    private static final String LEGACY_POOL_PREFS = "pool";
    private static final String LEGACY_INIT_KEY = "init";
    private static final String LEGACY_POOL_KEY = "pool";

    private final Context context;
    private final SharedPreferences groupPreferences;

    public GroupStore(Context context) {
        this.context = context.getApplicationContext();
        groupPreferences = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        migrateLegacyListIfNeeded();
    }

    // Copies the old single list into the first group once
    private void migrateLegacyListIfNeeded() {
        if (!getGroupIds().isEmpty()) {
            return;
        }

        SharedPreferences legacyInit = context.getSharedPreferences(LEGACY_INIT_PREFS, Context.MODE_PRIVATE);
        SharedPreferences legacyPool = context.getSharedPreferences(LEGACY_POOL_PREFS, Context.MODE_PRIVATE);

        ArrayList<String> init = split(legacyInit.getString(LEGACY_INIT_KEY, StringPool.DEFAULT_INIT));
        if (init.isEmpty()) {
            init = split(StringPool.DEFAULT_INIT);
        }
        ArrayList<String> pool = split(legacyPool.getString(LEGACY_POOL_KEY, String.join(",", init)));
        if (pool.isEmpty()) {
            pool = new ArrayList<>(init);
        }

        String id = "g0";
        groupPreferences.edit()
                .putString(KEY_IDS, id)
                .putString(KEY_ACTIVE, id)
                .putInt(KEY_NEXT_ID, 1)
                .putString(PREFIX_NAME + id, context.getString(R.string.group_default))
                .putString(PREFIX_INIT + id, String.join(",", init))
                .putString(PREFIX_POOL + id, String.join(",", pool))
                .apply();
    }

    // The ids in display order
    public List<String> getGroupIds() {
        return split(groupPreferences.getString(KEY_IDS, ""));
    }

    public String getGroupName(String id) {
        return groupPreferences.getString(PREFIX_NAME + id, "");
    }

    // The id used for drawing, guaranteed to point at an existing group
    public String getActiveGroupId() {
        List<String> ids = getGroupIds();
        if (ids.isEmpty()) {
            return "";
        }
        String active = groupPreferences.getString(KEY_ACTIVE, "");
        if (!ids.contains(active)) {
            active = ids.get(0);
            groupPreferences.edit().putString(KEY_ACTIVE, active).apply();
        }
        return active;
    }

    public void setActiveGroup(String id) {
        if (getGroupIds().contains(id)) {
            groupPreferences.edit().putString(KEY_ACTIVE, id).apply();
        }
    }

    public List<String> getInit(String id) {
        return split(groupPreferences.getString(PREFIX_INIT + id, ""));
    }

    // Writes the name list and rebuilds the pool so that new names are appended at the front
    public void setInit(String id, List<String> names) {
        ArrayList<String> clean = new ArrayList<>(names);
        ArrayList<String> pool = syncPool(clean, getPool(id));
        groupPreferences.edit()
                .putString(PREFIX_INIT + id, String.join(",", clean))
                .putString(PREFIX_POOL + id, String.join(",", pool))
                .apply();
    }

    public List<String> getPool(String id) {
        return split(groupPreferences.getString(PREFIX_POOL + id, ""));
    }

    public void setPool(String id, List<String> pool) {
        groupPreferences.edit().putString(PREFIX_POOL + id, String.join(",", pool)).apply();
    }

    public int getGroupCount() {
        return getGroupIds().size();
    }

    // Creates a group seeded with the default names, returns its id
    public String addGroup(String name) {
        int next = groupPreferences.getInt(KEY_NEXT_ID, 1);
        String id = "g" + next;

        ArrayList<String> ids = new ArrayList<>(getGroupIds());
        ids.add(id);

        ArrayList<String> init = split(StringPool.DEFAULT_INIT);
        groupPreferences.edit()
                .putString(KEY_IDS, String.join(",", ids))
                .putInt(KEY_NEXT_ID, next + 1)
                .putString(PREFIX_NAME + id, name)
                .putString(PREFIX_INIT + id, String.join(",", init))
                .putString(PREFIX_POOL + id, String.join(",", init))
                .apply();
        return id;
    }

    public void renameGroup(String id, String name) {
        if (getGroupIds().contains(id)) {
            groupPreferences.edit().putString(PREFIX_NAME + id, name).apply();
        }
    }

    // Removes a group. Refuses to remove the last remaining one, returns true when removed
    public boolean deleteGroup(String id) {
        ArrayList<String> ids = new ArrayList<>(getGroupIds());
        if (ids.size() <= 1 || !ids.remove(id)) {
            return false;
        }

        SharedPreferences.Editor editor = groupPreferences.edit()
                .putString(KEY_IDS, String.join(",", ids))
                .remove(PREFIX_NAME + id)
                .remove(PREFIX_INIT + id)
                .remove(PREFIX_POOL + id);
        if (id.equals(groupPreferences.getString(KEY_ACTIVE, ""))) {
            editor.putString(KEY_ACTIVE, ids.get(0));
        }
        editor.apply();
        return true;
    }

    // The id of the group with this exact name, or null
    public String findGroupIdByName(String name) {
        for (String id : getGroupIds()) {
            if (getGroupName(id).equals(name)) {
                return id;
            }
        }
        return null;
    }

    // Keeps the draw history order of the names that are still listed, appends new names first
    static ArrayList<String> syncPool(List<String> init, List<String> pool) {
        Map<String, Integer> required = new HashMap<>();
        for (String name : init) {
            required.put(name, count(required, name) + 1);
        }

        ArrayList<String> history = new ArrayList<>();
        Map<String, Integer> kept = new HashMap<>();
        for (String name : pool) {
            if (count(kept, name) < count(required, name)) {
                history.add(name);
                kept.put(name, count(kept, name) + 1);
            }
        }

        ArrayList<String> fresh = new ArrayList<>();
        Map<String, Integer> added = new HashMap<>();
        for (String name : init) {
            if (count(kept, name) + count(added, name) < count(required, name)) {
                fresh.add(name);
                added.put(name, count(added, name) + 1);
            }
        }

        ArrayList<String> merged = new ArrayList<>(fresh);
        merged.addAll(history);
        return merged;
    }

    private static int count(Map<String, Integer> map, String key) {
        Integer value = map.get(key);
        return value == null ? 0 : value;
    }

    static ArrayList<String> split(String value) {
        ArrayList<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        for (String part : value.split(",")) {
            if (!part.isEmpty()) {
                result.add(part);
            }
        }
        return result;
    }
}