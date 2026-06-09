package com.thebeyond.isleweaver.compat.unusualend;

import it.unimi.dsi.fastutil.ints.IntList;

import java.util.HashMap;
import java.util.Map;

// single-chunk reef-Y cache; lives outside the mixin package so the Mixin classloader will load it
public final class ChunkColumnReefCache {
    public long currentChunk = Long.MIN_VALUE;
    public final Map<Long, IntList> reefYsByColumn = new HashMap<>();

    public IntList get(long chunkKey, long colKey) {
        return chunkKey != currentChunk ? null : reefYsByColumn.get(colKey);
    }

    public void put(long chunkKey, long colKey, IntList reefYs) {
        if (chunkKey != currentChunk) {
            currentChunk = chunkKey;
            reefYsByColumn.clear();
        }
        reefYsByColumn.put(colKey, reefYs);
    }
}
