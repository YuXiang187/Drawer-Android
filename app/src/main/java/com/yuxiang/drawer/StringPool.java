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
        ArrayList<String> pool = groupStore.getPoolForUpdate(groupId);
        if (pool.isEmpty()) {
            return "";
        }

        int index = pickIndex(pool);
        String value = pool.get(index);

        pool.remove(index);
        pool.add(value);
        groupStore.markPoolDirty(groupId);

        return value;
    }

    // used by the rolling animation
    public String get() {
        String groupId = groupStore.getActiveGroupId();
        List<String> pool = groupStore.getPoolReference(groupId);
        if (pool.isEmpty()) {
            pool = groupStore.getPoolForUpdate(groupId);
        }
        if (pool.isEmpty()) {
            return "";
        }
        return pool.get(random.nextInt(pool.size()));
    }

    // Up to maxNames entries spread evenly over the active pool, for size estimates
    public List<String> sample(int maxNames) {
        String groupId = groupStore.getActiveGroupId();
        List<String> pool = groupStore.getPoolReference(groupId);
        if (pool.isEmpty()) {
            pool = groupStore.getInit(groupId);
        }
        int size = pool.size();
        int count = Math.min(size, maxNames);
        ArrayList<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(pool.get((int) ((long) i * size / count)));
        }
        return result;
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