package green_green_avk.ptyprocess;

import android.os.ParcelFileDescriptor;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

@Keep
public final class PtyProcess extends Process {
    static {
        System.loadLibrary("ptyprocess");
    }

    @Keep
    private volatile int fdPtm;
    @Keep
    private final int pid;

    @Keep
    private PtyProcess(final int fdPtm, final int pid) {
        this.fdPtm = fdPtm;
        this.pid = pid;
    }

    public int getPtm() {
        return fdPtm;
    }

    public int getPid() {
        return pid;
    }

    @NonNull
    @Keep
    public static native PtyProcess execve(@NonNull final String filename, final String[] args,
                                           final String[] env);

    @NonNull
    public static PtyProcess execve(@NonNull final String filename, final String[] args,
                                    final Map<String, String> env) {
        if (env == null) return execve(filename, args, (String[]) null);
        final String[] _env = new String[env.size()];
        int i = 0;
        for (final Map.Entry<String, String> elt : env.entrySet()) {
            _env[i] = elt.getKey() + "=" + elt.getValue();
            ++i;
        }
        return execve(filename, args, _env);
    }

    @NonNull
    public static PtyProcess execv(@NonNull final String filename, final String[] args) {
        return execve(filename, args, (String[]) null);
    }

    @NonNull
    public static PtyProcess execl(@NonNull final String filename,
                                   final Map<String, String> env, final String... args) {
        return execve(filename, args, env);
    }

    @NonNull
    public static PtyProcess execl(@NonNull final String filename, final String... args) {
        return execv(filename, args);
    }

    @NonNull
    public static PtyProcess system(@Nullable final String command,
                                    @Nullable final Map<String, String> env) {
        if (command == null || command.isEmpty())
            return execl("/system/bin/sh", env, "-sh", "-l");
        return execl("/system/bin/sh", env, "-sh", "-l", "-c", command);
    }

    @NonNull
    public static PtyProcess system(@Nullable final String command) {
        return system(command, null);
    }

    @Override
    public OutputStream getOutputStream() {
        return input;
    }

    @Override
    public InputStream getInputStream() {
        return output;
    }

    @Override
    public InputStream getErrorStream() {
        return null;
    }

    @Override
    public int waitFor() {
        return 0;
    }

    @Override
    public int exitValue() {
        return 0;
    }

    @Override
    @Keep
    public native void destroy();

    @Keep
    public native void resize(int width, int height, int widthPx, int heightPx);

    // TODO: Or ParcelFileDescriptor / File Streams?

    @Keep
    private native int readByte() throws IOException;

    @Keep
    private native int readBuf(byte[] buf, int off, int len) throws IOException;

    @Keep
    private native void writeByte(int b) throws IOException;

    @Keep
    private native void writeBuf(byte[] buf, int off, int len) throws IOException;

    private final InputStream output = new InputStream() {
        @Override
        public int read() throws IOException {
            return readByte();
        }

        @Override
        public int read(@NonNull final byte[] b, final int off, final int len) throws IOException {
            if (b == null) throw new NullPointerException();
            if (off < 0 || len < 0 || off + len > b.length) throw new IndexOutOfBoundsException();
            return readBuf(b, off, len);
        }
    };

    private final OutputStream input = new OutputStream() {
        @Override
        public void write(int b) throws IOException {
            writeByte(b);
        }

        @Override
        public void write(@NonNull final byte[] b, final int off, final int len) throws IOException {
            if (b == null) throw new NullPointerException();
            if (off < 0 || len < 0 || off + len > b.length) throw new IndexOutOfBoundsException();
            writeBuf(b, off, len);
        }
    };

    public static final class InterruptableFileInputStream
            extends ParcelFileDescriptor.AutoCloseInputStream {
        private volatile boolean closed = false;
        private final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        private final ParcelFileDescriptor pfd;

        public InterruptableFileInputStream(final ParcelFileDescriptor pfd) throws IOException {
            super(pfd);
            this.pfd = pfd;
        }

        private void interrupt() throws IOException {
            pipe[1].close();
        }

        private void interruptQuiet() {
            try {
                interrupt();
            } catch (final IOException ignored) {
            }
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
            interruptQuiet();
        }

        private boolean check() throws IOException {
            try {
                return pollForRead(pfd.getFd(), pipe[0].getFd());
            } catch (final IllegalStateException e) {
                throw new IOException(e.getMessage(), e);
            }
        }

        @Override
        public int read() throws IOException {
            try {
                if (check()) return -1;
                return super.read();
            } catch (final IOException e) {
                if (!closed) throw e;
                return -1;
            }
        }

        @Override
        public int read(byte[] b) throws IOException {
            if (check()) return -1;
            try {
                return super.read(b);
            } catch (final IOException e) {
                if (!closed) throw e;
                return -1;
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (check()) return -1;
            try {
                return super.read(b, off, len);
            } catch (final IOException e) {
                if (!closed) throw e;
                return -1;
            }
        }
    }

    // Actual only before API 21
    @Keep
    public static native boolean pollForRead(int fd, int intFd) throws IOException;

    @Keep
    public static native long getArgMax();
}
