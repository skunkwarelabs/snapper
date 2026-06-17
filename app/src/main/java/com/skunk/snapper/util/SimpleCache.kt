package com.skunk.snapper.util

/**
 * Tiny thread-safe in-memory cache: LRU eviction past [maxSize], plus a per-entry
 * TTL so stale data eventually refreshes. Lives for the process lifetime.
 */
class SimpleCache<K, V>(
    private val maxSize: Int = 64,
    private val ttlMillis: Long = 24 * 60 * 60 * 1000L
) {
    private data class Entry<V>(val value: V, val expiresAt: Long)

    // access-order = true makes this an LRU map.
    private val map = object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>): Boolean =
            size > maxSize
    }

    @Synchronized
    fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (entry.expiresAt < System.currentTimeMillis()) {
            map.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: K, value: V) {
        map[key] = Entry(value, System.currentTimeMillis() + ttlMillis)
    }
}
