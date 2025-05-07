package com.cabin.express.stream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * @version 11
 * @since 2025-05-06
 * @author Sang Le
 */
public class NonBlockingOutputStream extends OutputStream {
    private final SocketChannel channel;
    private volatile boolean isClosed = false;

    public NonBlockingOutputStream(SocketChannel channel) {
        this.channel = channel;
    }

    @Override
    public void write(int b) throws IOException {
        if (isClosed) {
            throw new IOException("Stream closed");
        }

        ByteBuffer buf = ByteBuffer.allocate(1);
        buf.put((byte)b).flip();
        try {
            // loop until all data is sent, even if write() returns 0
            while (buf.hasRemaining() && !isClosed) {
                int bytesWritten = channel.write(buf);
                // If no bytes were written in multiple attempts, likely connection issue
                if (bytesWritten == 0) {
                    // Small sleep to prevent CPU spinning
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while writing", e);
                    }
                }
            }
        } catch (IOException e) {
            isClosed = true;
            // Rethrow connection reset errors with clear message
            if (e.getMessage() != null &&
                    (e.getMessage().contains("Connection reset") ||
                            e.getMessage().contains("Broken pipe"))) {
                throw new IOException("Connection reset by peer - client disconnected", e);
            }
            throw e;
        }
    }

    @Override
    public void write(byte[] data, int off, int len) throws IOException {
        if (isClosed) {
            throw new IOException("Stream closed");
        }

        if (data == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || off + len > data.length) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return;
        }

        ByteBuffer buf = ByteBuffer.wrap(data, off, len);
        try {
            while (buf.hasRemaining() && !isClosed) {
                int bytesWritten = channel.write(buf);
                // If no bytes were written in multiple attempts, likely connection issue
                if (bytesWritten == 0) {
                    // Small sleep to prevent CPU spinning
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while writing", e);
                    }
                }
            }
        } catch (IOException e) {
            isClosed = true;
            // Rethrow connection reset errors with clear message
            if (e.getMessage() != null &&
                    (e.getMessage().contains("Connection reset") ||
                            e.getMessage().contains("Broken pipe"))) {
                throw new IOException("Connection reset by peer - client disconnected", e);
            }
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        isClosed = true;
        // No need to close the channel here as it's managed by the server
    }
}
