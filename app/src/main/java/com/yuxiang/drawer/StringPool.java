package com.yuxiang.drawer;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class StringPool {
    private static final double STDDEV_SCALING_FACTOR = 3.0;
    static final String DEFAULT_INIT = "Item1,Item2,Item3,Item4,Item5";

    static ArrayList<String> initPool = new ArrayList<>();
    static ArrayList<String> pool = new ArrayList<>();

    private final Random random = new Random();

    final SharedPreferences poolPreferences;
    final SharedPreferences initPoolPreferences;

    public StringPool(Context context) {
        initPoolPreferences = context.getSharedPreferences("init", MODE_PRIVATE);
        poolPreferences = context.getSharedPreferences("pool", MODE_PRIVATE);

        ArrayList<String> loadedInit = split(initPoolPreferences.getString("init", DEFAULT_INIT));
        ArrayList<String> loadedPool = split(poolPreferences.getString("pool", String.join(",", loadedInit)));

        setState(loadedInit, loadedPool);
    }

    // Draws one name using the Gaussian model
    public String draw() {
        if (initPool.isEmpty()) {
            return "";
        }

        syncPool();

        int index = pickIndex();
        String value = pool.get(index);

        int first = pool.indexOf(value);
        pool.remove(first);
        pool.add(value);

        return value;
    }

    private int pickIndex() {
        for (; ; ) {
            double stddev = pool.size() / STDDEV_SCALING_FACTOR;
            double sample = Math.abs(random.nextGaussian() * stddev);

            if (sample < pool.size()) {
                return (int) sample;
            }
        }
    }

    static void setNames(List<String> names) {
        initPool = new ArrayList<>(names);
        syncPool();
    }

    static void setState(List<String> init, List<String> drawPool) {
        initPool = new ArrayList<>(init);
        pool = new ArrayList<>(drawPool);
        syncPool();
    }

    void save() {
        initPoolPreferences.edit().putString("init", String.join(",", initPool)).apply();
        poolPreferences.edit().putString("pool", String.join(",", pool)).apply();
    }

    private static void syncPool() {
        Map<String, Integer> required = new HashMap<>();
        for (String name : initPool) {
            required.put(name, count(required, name) + 1);
        }

        // Keep the draw history order of the names that are still listed.
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
        for (String name : initPool) {
            if (count(kept, name) + count(added, name) < count(required, name)) {
                fresh.add(name);
                added.put(name, count(added, name) + 1);
            }
        }

        ArrayList<String> merged = new ArrayList<>(fresh);
        merged.addAll(history);
        pool = merged;
    }

    private static int count(Map<String, Integer> map, String key) {
        Integer value = map.get(key);
        return value == null ? 0 : value;
    }

    private static ArrayList<String> split(String value) {
        ArrayList<String> result = new ArrayList<>();
        for (String part : value.split(",")) {
            if (!part.isEmpty()) {
                result.add(part);
            }
        }
        return result;
    }
}