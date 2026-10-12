package com.yuxiang.drawer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    // Every single write rewrites the whole preferences file, so the draw history is kept in
    // memory and written back in batches instead of once per draw.
    private static final long POOL_FLUSH_DELAY_MS = 2000L;

    private static volatile GroupStore instance;

    private final Context context;
    private final SharedPreferences groupPreferences;
    private final Map<String, ArrayList<String>> poolCache = new HashMap<>();
    private final Set<String> dirtyPools = new LinkedHashSet<>();
    private final Handler flushHandler = new Handler(Looper.getMainLooper());
    private final Runnable flushRunnable = this::flushPools;
    private boolean flushScheduled;

    // The process wide store. Every caller has to share it, otherwise they would each keep
    // their own copy of the draw history and overwrite each other on the next write.
    public static GroupStore get(Context context) {
        if (instance == null) {
            synchronized (GroupStore.class) {
                if (instance == null) {
                    instance = new GroupStore(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private GroupStore(Context context) {
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
                .commit();
        deleteLegacyPreferences();
    }

    // The old single list files only served this migration once, they are never read again
    private void deleteLegacyPreferences() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.deleteSharedPreferences(LEGACY_INIT_PREFS);
            context.deleteSharedPreferences(LEGACY_POOL_PREFS);
            return;
        }
        // deleteSharedPreferences() only exists from Android 7 on
        context.getSharedPreferences(LEGACY_INIT_PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        context.getSharedPreferences(LEGACY_POOL_PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    // The ids in display order
    public List<String> getGroupIds() {
        return split(groupPreferences.getString(KEY_IDS, ""));
    }

    public String getGroupName(String id) {
        return groupPreferences.getString(PREFIX_NAME + id, "");
    }

    public String getActiveGroupId() {
        List<String> ids = getGroupIds();
        if (ids.isEmpty()) {
            return "";
        }
        String active = groupPreferences.getString(KEY_ACTIVE, "");
        return ids.contains(active) ? active : ids.get(0);
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
        ArrayList<String> pool;
        synchronized (poolCache) {
            pool = syncPool(clean, getPool(id));
            poolCache.put(id, pool);
            dirtyPools.remove(id);
        }
        groupPreferences.edit()
                .putString(PREFIX_INIT + id, String.join(",", clean))
                .putString(PREFIX_POOL + id, String.join(",", pool))
                .apply();
    }

    public List<String> getPool(String id) {
        if (id == null || id.isEmpty()) {
            return new ArrayList<>();
        }
        synchronized (poolCache) {
            ArrayList<String> pool = poolCache.get(id);
            if (pool == null) {
                pool = split(groupPreferences.getString(PREFIX_POOL + id, ""));
                poolCache.put(id, pool);
            }
            return new ArrayList<>(pool);
        }
    }

    public void setPool(String id, List<String> pool) {
        if (id == null || id.isEmpty()) {
            return;
        }
        synchronized (poolCache) {
            poolCache.put(id, new ArrayList<>(pool));
            dirtyPools.add(id);
        }
        schedulePoolFlush();
    }

    // At most one write per interval, so a burst of draws cannot grow the number of writes
    // while still bounding how much history a killed process could lose
    private void schedulePoolFlush() {
        if (flushScheduled) {
            return;
        }
        flushScheduled = true;
        flushHandler.postDelayed(flushRunnable, POOL_FLUSH_DELAY_MS);
    }

    // Writes every changed history back in a single transaction. Called by the batch timer and
    // when an Activity or Service goes away, so the history survives a restart.
    public void flushPools() {
        flushScheduled = false;
        flushHandler.removeCallbacks(flushRunnable);

        Map<String, String> values = new HashMap<>();
        synchronized (poolCache) {
            if (dirtyPools.isEmpty()) {
                return;
            }
            for (String id : dirtyPools) {
                ArrayList<String> pool = poolCache.get(id);
                values.put(PREFIX_POOL + id, pool == null ? "" : String.join(",", pool));
            }
            dirtyPools.clear();
        }

        SharedPreferences.Editor editor = groupPreferences.edit();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            editor.putString(entry.getKey(), entry.getValue());
        }
        editor.apply();
    }

    // Drops the cached history of a group that is gone, so it is never written back
    private void discardPool(String id) {
        synchronized (poolCache) {
            poolCache.remove(id);
            dirtyPools.remove(id);
        }
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
        synchronized (poolCache) {
            poolCache.put(id, new ArrayList<>(init));
            dirtyPools.remove(id);
        }
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

        // Resolved before the list changes, so the stored value is corrected when the group
        // that is really in use disappears
        String active = getActiveGroupId();
        discardPool(id);

        SharedPreferences.Editor editor = groupPreferences.edit()
                .putString(KEY_IDS, String.join(",", ids))
                .remove(PREFIX_NAME + id)
                .remove(PREFIX_INIT + id)
                .remove(PREFIX_POOL + id);
        if (id.equals(active)) {
            editor.putString(KEY_ACTIVE, ids.get(0));
        }
        editor.apply();
        return true;
    }

    // The id of the group with this name, compared without case and surrounding spaces, or null
    public String findGroupIdByName(String name) {
        String wanted = name == null ? "" : name.trim();
        for (String id : getGroupIds()) {
            if (getGroupName(id).trim().equalsIgnoreCase(wanted)) {
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