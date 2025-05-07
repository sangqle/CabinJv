package com.cabin.express.stream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class SimpleChunkedOutputStream extends OutputStream {
    private final OutputStream out;

    public SimpleChunkedOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte)b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (len <= 0) return;
        // 1) Write chunk size in hex + CRLF
        out.write(Integer.toHexString(len).getBytes(StandardCharsets.US_ASCII));
        out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        // 2) Write the data
        out.write(b, off, len);
        // 3) Chunk terminator
        out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
    }

    /** Call at end to signal zero‐length chunk **/
    public void finish() throws IOException {
        out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }
}
