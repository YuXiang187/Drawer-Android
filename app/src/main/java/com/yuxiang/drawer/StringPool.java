package com.yuxiang.drawer;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class StringPool {
    static ArrayList<String> initPool;
    static ArrayList<String> pool;
    Random random;

    SharedPreferences poolPreferences;
    SharedPreferences initPoolPreferences;

    public StringPool(Context context) {
        initPoolPreferences = context.getSharedPreferences("init", MODE_PRIVATE);
        poolPreferences = context.getSharedPreferences("pool", MODE_PRIVATE);

        initPool = new ArrayList<>(Arrays.asList(initPoolPreferences.getString("init", "Item1,Item2,Item3,Item4,Item5").split(",")));
        pool = new ArrayList<>(Arrays.asList(poolPreferences.getString("pool", String.join(",", initPool)).split(",")));
        random = new Random();

        if (pool.get(0).isEmpty()) {
            reset();
        }
    }

    public String get() {
        if (pool.isEmpty()) {
            reset();
        }
        return pool.get(random.nextInt(pool.size()));
    }

    public static void reset() {
        pool = new ArrayList<>(initPool);
    }

    public void remove(String value) {
        pool.remove(value);
    }

    public void save() {
        poolPreferences.edit().putString("pool", String.join(",", pool)).apply();
    }
}