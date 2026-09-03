// 由 AI 助手產生：帶 TTL 的執行緒安全快取，供高併發服務使用
package com.example.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CacheService<K, V> {

    private final Map<K, Entry<V>> store = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final long ttlMillis;

    public CacheService(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    private static class Entry<V> {
        final V value;
        final long expireAt;
        Entry(V value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }

    public V get(K key) {
        lock.readLock().lock();
        try {
            Entry<V> e = store.get(key);
            if (e == null) {
                return null;
            }
            if (System.currentTimeMillis() > e.expireAt) {
                store.remove(key);   // 在 read lock 內做寫入
                return null;
            }
            return e.value;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            store.put(key, new Entry<>(value, System.currentTimeMillis() + ttlMillis));
        } finally {
            lock.writeLock().unlock();
        }
    }

    // 快取穿透保護：查不到就用 loader 載入
    public V getOrLoad(K key, java.util.function.Supplier<V> loader) {
        V cached = get(key);
        if (cached != null) {
            return cached;
        }
        V loaded = loader.get();
        put(key, loaded);
        return loaded;
    }
}
