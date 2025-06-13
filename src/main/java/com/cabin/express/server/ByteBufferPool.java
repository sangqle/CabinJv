package com.cabin.express.server;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A two-tier ByteBuffer pool with proper resource management:
 *  - Fast path: each thread has a bounded ThreadLocal cache with cleanup support.
 *  - Slow path: a global concurrent deque with bounded size.
 *  - Monitoring: tracks pool statistics and provides cleanup capabilities.
 */
public class ByteBufferPool {
    private final int bufferSize;
    private final int maxGlobalPoolSize;
    private final int maxThreadLocalSize;
    
    // Statistics tracking
    private final AtomicInteger totalBuffersCreated = new AtomicInteger(0);
    private final AtomicInteger activeThreadLocalCaches = new AtomicInteger(0);

    // Global pool (lock-free)
    private final ConcurrentLinkedDeque<ByteBuffer> globalPool = new ConcurrentLinkedDeque<>();

    // Per-thread cache with cleanup support
    private final ThreadLocal<BoundedDeque> localCache = new ThreadLocal<BoundedDeque>() {
        @Override
        protected BoundedDeque initialValue() {
            activeThreadLocalCaches.incrementAndGet();
            return new BoundedDeque(maxThreadLocalSize);
        }
        
        @Override
        public void remove() {
            BoundedDeque cache = get();
            if (cache != null) {
                // Return buffers to global pool before cleanup
                cache.drainTo(globalPool, maxGlobalPoolSize);
                activeThreadLocalCaches.decrementAndGet();
            }
            super.remove();
        }
    };

    /**
     * Bounded deque that enforces size limits
     */
    private static class BoundedDeque {
        private final Deque<ByteBuffer> deque = new ArrayDeque<>();
        private final int maxSize;
        
        BoundedDeque(int maxSize) {
            this.maxSize = maxSize;
        }
        
        boolean offerFirst(ByteBuffer buffer) {
            if (deque.size() >= maxSize) {
                return false;
            }
            deque.offerFirst(buffer);
            return true;
        }
        
        ByteBuffer pollFirst() {
            return deque.pollFirst();
        }
        
        int size() {
            return deque.size();
        }
        
        void drainTo(ConcurrentLinkedDeque<ByteBuffer> target, int maxTargetSize) {
            while (!deque.isEmpty() && target.size() < maxTargetSize) {
                ByteBuffer buffer = deque.pollFirst();
                if (buffer != null) {
                    target.offerFirst(buffer);
                }
            }
        }
    }

    /**
     * @param bufferSize         size of each ByteBuffer (in bytes)
     * @param maxGlobalPoolSize  total # of buffers to keep globally
     * @param maxThreadLocalSize # of buffers each thread can cache
     */
    public ByteBufferPool(int bufferSize, int maxGlobalPoolSize, int maxThreadLocalSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive: " + bufferSize);
        }
        if (maxGlobalPoolSize < 0) {
            throw new IllegalArgumentException("Max global pool size cannot be negative: " + maxGlobalPoolSize);
        }
        if (maxThreadLocalSize < 0) {
            throw new IllegalArgumentException("Max thread local size cannot be negative: " + maxThreadLocalSize);
        }
        
        this.bufferSize = bufferSize;
        this.maxGlobalPoolSize = maxGlobalPoolSize;
        this.maxThreadLocalSize = maxThreadLocalSize;
    }

    /**
     * Acquire a buffer.
     * Try thread-local first, then global, else allocate a new direct buffer.
     */
    public ByteBuffer acquire() {
        // Fast path: thread-local cache
        BoundedDeque cache = localCache.get();
        ByteBuffer buf = cache.pollFirst();
        if (buf != null) {
            return buf;
        }

        // Slow path: global pool
        buf = globalPool.pollFirst();
        if (buf != null) {
            return buf;
        }

        // Fallback: allocate new direct buffer
        totalBuffersCreated.incrementAndGet();
        return ByteBuffer.allocateDirect(bufferSize);
    }

    /**
     * Return a buffer to the pool.
     * Validates buffer size and clears it, then tries thread-local first, then global.
     */
    public void release(ByteBuffer buf) {
        if (buf == null) {
            return;
        }
        
        // Validate buffer size to prevent corruption
        if (buf.capacity() != bufferSize) {
            throw new IllegalArgumentException(
                "Buffer size mismatch: expected " + bufferSize + ", got " + buf.capacity());
        }
        
        buf.clear();

        // Try thread-local first
        BoundedDeque cache = localCache.get();
        if (cache.offerFirst(buf)) {
            return;
        }
        
        // Try global pool if thread-local is full
        if (globalPool.size() < maxGlobalPoolSize) {
            globalPool.offerFirst(buf);
        }
        // Otherwise, let GC reclaim the buffer
    }

    /**
     * Clear the current thread's local cache, returning buffers to the global pool.
     * Useful for cleanup in thread pool environments.
     */
    public void clearThreadLocalCache() {
        localCache.remove();
    }

    /**
     * Clear all caches and reset the pool.
     * WARNING: This should only be called when no other threads are using the pool.
     */
    public void clear() {
        globalPool.clear();
        // Note: Cannot safely clear all thread-local caches from here
        // Each thread should call clearThreadLocalCache() before termination
    }

    /**
     * Get current pool statistics.
     */
    public PoolStats getStats() {
        return new PoolStats(
            globalPool.size(),
            activeThreadLocalCaches.get(),
            totalBuffersCreated.get(),
            bufferSize,
            maxGlobalPoolSize,
            maxThreadLocalSize
        );
    }

    /**
     * Pool statistics snapshot
     */
    public static class PoolStats {
        public final int globalPoolSize;
        public final int activeThreadLocalCaches;
        public final int totalBuffersCreated;
        public final int bufferSize;
        public final int maxGlobalPoolSize;
        public final int maxThreadLocalSize;

        PoolStats(int globalPoolSize, int activeThreadLocalCaches, int totalBuffersCreated,
                 int bufferSize, int maxGlobalPoolSize, int maxThreadLocalSize) {
            this.globalPoolSize = globalPoolSize;
            this.activeThreadLocalCaches = activeThreadLocalCaches;
            this.totalBuffersCreated = totalBuffersCreated;
            this.bufferSize = bufferSize;
            this.maxGlobalPoolSize = maxGlobalPoolSize;
            this.maxThreadLocalSize = maxThreadLocalSize;
        }

        @Override
        public String toString() {
            return String.format(
                "PoolStats{globalPoolSize=%d, activeThreadLocalCaches=%d, totalBuffersCreated=%d, " +
                "bufferSize=%d, maxGlobalPoolSize=%d, maxThreadLocalSize=%d}",
                globalPoolSize, activeThreadLocalCaches, totalBuffersCreated,
                bufferSize, maxGlobalPoolSize, maxThreadLocalSize);
        }
    }

    /**
     * Estimate total memory usage by this pool (in bytes).
     * This is an approximation as it doesn't account for object overhead.
     */
    public long estimateMemoryUsage() {
        PoolStats stats = getStats();
        // Estimate: (global buffers + estimated thread-local buffers) * buffer size
        int estimatedThreadLocalBuffers = stats.activeThreadLocalCaches * (stats.maxThreadLocalSize / 2);
        return (long) (stats.globalPoolSize + estimatedThreadLocalBuffers) * stats.bufferSize;
    }
}