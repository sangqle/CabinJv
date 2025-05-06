package com.cabin.express.stream;

import com.cabin.express.loggger.CabinLogger;

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

    public NonBlockingOutputStream(SocketChannel channel) {
        this.channel = channel;
    }

    @Override
    public void write(int b) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(1);
        buf.put((byte)b).flip();
        // loop until all data is sent, even if write() returns 0
        while (buf.hasRemaining()) {
            channel.write(buf);                             // non-blocking write :contentReference[oaicite:0]{index=0}
        }
    }

    @Override
    public void write(byte[] data, int off, int len) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(data, off, len);
        while (buf.hasRemaining()) {
            channel.write(buf);                             // may write < len; loop :contentReference[oaicite:1]{index=1}
        }
    }
}
