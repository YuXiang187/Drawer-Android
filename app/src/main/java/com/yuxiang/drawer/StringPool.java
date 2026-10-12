package com.yuxiang.drawer;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class StringPool {
    private static final double STDDEV_SCALING_FACTOR = 3.0;
    static final String DEFAULT_INIT = "Item1,Item2,Item3,Item4,Item5";

    private final GroupStore groupStore;
    private final Random random = new Random();

    public StringPool(Context context) {
        groupStore = GroupStore.get(context);
    }

    // Draws one name from the active group using the Gaussian model
    public String draw() {
        String groupId = groupStore.getActiveGroupId();
        ArrayList<String> init = new ArrayList<>(groupStore.getInit(groupId));
        if (init.isEmpty()) {
            return "";
        }

        ArrayList<String> pool = GroupStore.syncPool(init, groupStore.getPool(groupId));

        int index = pickIndex(pool);
        String value = pool.get(index);

        pool.remove(value);
        pool.add(value);
        groupStore.setPool(groupId, pool);

        return value;
    }

    // used by the rolling animation
    public String get() {
        String groupId = groupStore.getActiveGroupId();
        ArrayList<String> pool = new ArrayList<>(groupStore.getPool(groupId));
        if (pool.isEmpty()) {
            pool = new ArrayList<>(groupStore.getInit(groupId));
        }
        if (pool.isEmpty()) {
            return "";
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private int pickIndex(List<String> pool) {
        for (; ; ) {
            double stddev = pool.size() / STDDEV_SCALING_FACTOR;
            double sample = Math.abs(random.nextGaussian() * stddev);

            if (sample < pool.size()) {
                return (int) sample;
            }
        }
    }
}