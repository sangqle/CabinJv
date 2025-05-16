package com.cabin.express.debug;

import com.cabin.express.loggger.CabinLogger;

import java.util.HashMap;
import java.util.Set;

/**
 * A debug map implementation that logs all operations to help diagnose thread safety issues.
 */
public class DebugHashMap<K, V> extends HashMap<K, V> {
    private final String mapName;
    
    public DebugHashMap(String mapName) {
        this.mapName = mapName;
    }
    
    @Override
    public V put(K key, V value) {
        try {
            CabinLogger.debug(mapName + " PUT: " + key + " by thread " + Thread.currentThread().getName());
            return super.put(key, value);
        } catch (Exception e) {
            CabinLogger.error(mapName + " Error in PUT: " + key + " by thread " + Thread.currentThread().getName(), e);
            throw e;
        }
    }
    
    @Override
    public Set<Entry<K, V>> entrySet() {
        CabinLogger.debug(mapName + " ENTRYSET accessed by thread " + Thread.currentThread().getName());
        return super.entrySet();
    }
    
    // Add similar overrides for other methods as needed
}