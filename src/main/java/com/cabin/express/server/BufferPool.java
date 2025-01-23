package com.cabin.express.server;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A buffer pool for managing ByteBuffer instances
 * @since 2025-01-23
 * @version 1.0.1
 * @author Sang Le
 * @see ByteBuffer
 */
public class BufferPool {
    private final Deque<ByteBuffer> buffers = new ArrayDeque<>();
    private final int bufferSize;
    private final int maxPoolSize;

    BufferPool(int bufferSize, int maxPoolSize) {
        this.bufferSize = bufferSize;
        this.maxPoolSize = maxPoolSize;
    }

    synchronized ByteBuffer getBuffer() {
        if (buffers.isEmpty()) {
            return ByteBuffer.allocate(bufferSize);
        }
        return buffers.pollFirst();
    }

    synchronized void returnBuffer(ByteBuffer buffer) {
        if (buffers.size() < maxPoolSize) {
            buffer.clear();
            buffers.offerFirst(buffer);
        }
    }

    private ByteBuffer getDynamicBuffer(int expectedSize) {
        return ByteBuffer.allocate(Math.max(expectedSize, 1024)); // Minimum size of 1024 bytes
    }
}
