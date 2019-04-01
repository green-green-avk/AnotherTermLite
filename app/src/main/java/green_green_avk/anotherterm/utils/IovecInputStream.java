package green_green_avk.anotherterm.utils;

import android.support.annotation.NonNull;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;

public class IovecInputStream extends InputStream {
    protected final Queue<byte[]> mIovec = new LinkedList<>();
    protected int mPos = 0;

    public void add(@NonNull final byte[] v) {
        if (v.length > 0) mIovec.add(v);
    }

    @Override
    public int available() {
        int r = -mPos;
        for (final byte[] buf : mIovec) r += buf.length;
        return r;
    }

    @Override
    public int read() {
        final byte[] buf = mIovec.peek();
        if (buf == null) return -1;
        final int r = ((int) buf[mPos]) & 0xFF;
        ++mPos;
        if (mPos == buf.length) {
            mIovec.remove();
            mPos = 0;
        }
        return r;
    }

    @Override
    public int read(@NonNull final byte[] b, final int off, final int len) {
        int r = 0;
        while (true) {
            final byte[] buf = mIovec.peek();
            if (buf == null)
                return r;
            final int a = buf.length - mPos;
            final int l = len - r;
            if (l < a) {
                if (l > 0) {
                    System.arraycopy(buf, mPos, b, off + r, l);
                    mPos += l;
                    r += l;
                }
                return r;
            }
            System.arraycopy(buf, mPos, b, off + r, a);
            r += a;
            mIovec.remove();
            mPos = 0;
        }
    }

    @Override
    public long skip(final long n) {
        int t = (int) n + mPos;
        while (true) {
            final byte[] buf = mIovec.peek();
            if (buf == null) return n + mPos - t;
            if (t < buf.length) break;
            t -= buf.length;
            mIovec.remove();
        }
        mPos = t;
        return n;
    }
}
