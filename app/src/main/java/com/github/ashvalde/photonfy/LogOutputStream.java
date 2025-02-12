package com.github.ashvalde.photonfy;

import android.util.Log;
import java.io.OutputStream;

public class LogOutputStream extends OutputStream {
    private final String tag;
    private final StringBuilder buffer = new StringBuilder();

    public LogOutputStream(String tag, int debug) {
        this.tag = tag;
    }
    @Override
    public void write(int b) {
        if (b == '\n') {
            Log.d(tag, buffer.toString());
            buffer.setLength(0);
        } else {
            buffer.append((char) b);
        }
    }
}