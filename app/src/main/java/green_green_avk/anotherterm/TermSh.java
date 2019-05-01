package green_green_avk.anotherterm;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import java.io.DataInputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.nio.CharBuffer;
import java.util.Map;

import green_green_avk.anotherterm.backends.BackendException;
import green_green_avk.anotherterm.backends.BackendModule;
import green_green_avk.anotherterm.backends.usbUart.UsbUartModule;
import green_green_avk.anotherterm.utils.BinaryGetOpts;
import green_green_avk.anotherterm.utils.Misc;

public final class TermSh {
    private static final String NOTIFICATION_CHANNEL_ID = TermSh.class.getName();

    private static final class UiBridge {
        private final Context ctx;
        private final Handler handler;

        private int notificationId = 0;

        @UiThread
        private UiBridge(@NonNull final Context context) {
            ctx = context;
            handler = new Handler();
        }

        private void runOnUiThread(@NonNull final Runnable runnable) {
            handler.post(runnable);
        }

        private int getNextNotificationId() {
            return notificationId++;
        }

        private void makeNotification(final String message, final int id) {
            final int _id = id & C.NOTIFICATION_SUBID_MASK | C.NOTIFICATION_TERMSH_GROUP;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    final Notification n = new NotificationCompat.Builder(
                            ctx.getApplicationContext(), NOTIFICATION_CHANNEL_ID)
                            .setContentTitle(message)
                            .setSmallIcon(R.drawable.ic_stat_serv)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true)
                            .build();
                    NotificationManagerCompat.from(ctx).notify(_id, n);
                }
            });
        }
    }

    private static final class UiServer implements Runnable {
        private static final BinaryGetOpts.Options NOTIFY_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("id", new String[]{"-i", "--id"},
                                BinaryGetOpts.Option.Type.INT)
                });

        private final UiBridge ui;
        private LocalServerSocket serverSocket;

        private UiServer(@NonNull final UiBridge ui) {
            this.ui = ui;
        }

        private static final class ParseException extends Exception {
            public ParseException(final String message) {
                super(message);
            }
        }

        private static final class CmdIO {
            private static final int ARGLEN_MAX = 1024 * 1024;
            private static final byte[][] NOARGS = new byte[0][];

            private boolean closed = false;
            private final Object closeLock = new Object();
            private final LocalSocket socket;
            private final InputStream cis;
            private final FileDescriptor[] ioFds;
            public final InputStream stdIn;
            public final OutputStream stdOut;
            public final OutputStream stdErr;
            public final byte[][] args;

            private final Thread cth = new Thread("TermShServer.Control") {
                @Override
                public void run() {
                    try {
                        while (true) {
                            final int r = cis.read();
                            if (r < 0) break;
                        }
                    } catch (final IOException e) {
                        Log.e("TermShServer", "Request", e);
                    }
                    close();
                }
            };

            private void close() {
                synchronized (closeLock) {
                    if (closed) return;
                    closed = true;
                    try {
                        if (stdIn != null) stdIn.close();
                    } catch (final IOException ignored) {
                    }
                    try {
                        if (stdOut != null) stdOut.close();
                    } catch (final IOException ignored) {
                    }
                    try {
                        if (stdErr != null) stdErr.close();
                    } catch (final IOException ignored) {
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                        if (ioFds != null)
                            for (final FileDescriptor fd : ioFds)
                                if (fd.valid()) {
                                    try {
                                        Os.close(fd); // For API >= 28
                                    } catch (final ErrnoException e) {
                                        Log.e("TermShServer", "Request", e);
                                    }
                                }
                    try {
                        socket.close();
                    } catch (final IOException ignored) {
                    }
                }
            }

            /*
             * Technically, creating an FD owner stream is the best solution here...
             * Na-ah... It seems, I need to make my own
             * TODO: implementation of the whole LocalServerSocket / LocalSocket
             * / FileDescriptor / File*Stream / AsynchronousCloseMonitor
             * to work it around in API versions >= 28.
             * Android has no API to implement low-level extensions.
             */

            private static FileInputStream wrapInputFD(final FileDescriptor fd) {
                try {
                    return FileInputStream.class
                            .getConstructor(FileDescriptor.class, boolean.class)
                            .newInstance(fd, true);
                } catch (final IllegalAccessException e) {
                    return new FileInputStream(fd);
                } catch (final InstantiationException e) {
                    return new FileInputStream(fd);
                } catch (final InvocationTargetException e) {
                    return new FileInputStream(fd);
                } catch (final NoSuchMethodException e) {
                    return new FileInputStream(fd);
                }
            }

            private static FileOutputStream wrapOutputFD(final FileDescriptor fd) {
                try {
                    return FileOutputStream.class
                            .getConstructor(FileDescriptor.class, boolean.class)
                            .newInstance(fd, true);
                } catch (final IllegalAccessException e) {
                    return new FileOutputStream(fd);
                } catch (final InstantiationException e) {
                    return new FileOutputStream(fd);
                } catch (final InvocationTargetException e) {
                    return new FileOutputStream(fd);
                } catch (final NoSuchMethodException e) {
                    return new FileOutputStream(fd);
                }
            }

            @NonNull
            private static byte[][] parseArgs(@NonNull final InputStream is)
                    throws IOException, ParseException {
                final int argc = is.read();
                if (argc <= 0) return NOARGS;
                final byte[][] args = new byte[argc][];
                final DataInputStream dis = new DataInputStream(is);
                for (int i = 0; i < argc; ++i) {
                    final int l = dis.readInt();
                    if (l < 0 || l > ARGLEN_MAX) throw new ParseException("Arguments parse error");
                    args[i] = new byte[l];
                    dis.readFully(args[i]);
                }
                return args;
            }

            public void exit(int status) {
                try {
                    socket.getOutputStream().write(new byte[]{(byte) status});
                } catch (final IOException ignored) {
                }
                close();
            }

            private CmdIO(@NonNull final LocalSocket socket) throws IOException, ParseException {
                this.socket = socket;
                cis = socket.getInputStream();
                args = parseArgs(cis);
                ioFds = socket.getAncillaryFileDescriptors();
                if (ioFds == null || ioFds.length != 3)
                    throw new ParseException("No file descriptors");
                stdIn = wrapInputFD(ioFds[0]);
                stdOut = wrapOutputFD(ioFds[1]);
                stdErr = wrapOutputFD(ioFds[2]);
                cth.start();
            }
        }

        @SuppressLint("StaticFieldLeak")
        private final class ClientTask extends AsyncTask<Object, Object, Object> {
            @Override
            protected Object doInBackground(final Object[] objects) {
                final LocalSocket socket = (LocalSocket) objects[0];
                final CmdIO shellCmd;
                try {
                    if (Process.myUid() != socket.getPeerCredentials().getUid())
                        throw new ParseException("Spoofing detected!");
                    shellCmd = new CmdIO(socket);
                } catch (final IOException | ParseException e) {
                    Log.e("TermShServer", "Request", e);
                    try {
                        socket.close();
                    } catch (final IOException ignored) {
                    }
                    return null;
                }
                try {
                    if (shellCmd.args.length < 1) throw new ParseException("No command specified");
                    final String command = Misc.fromUTF8(shellCmd.args[0]);
                    switch (command) {
                        case "help":
                            shellCmd.stdOut.write(Misc.toUTF8(ui.ctx.getString(
                                    R.string.desc_termsh_help)));
                            break;
                        case "notify": {
                            final BinaryGetOpts.Parser ap = new BinaryGetOpts.Parser(shellCmd.args);
                            ap.skip();
                            final Map<String, ?> opts = ap.parse(NOTIFY_OPTS);
                            final Integer _id = (Integer) opts.get("id");
                            final int id = _id == null ? ui.getNextNotificationId() : _id;
                            final String msg;
                            switch (shellCmd.args.length - ap.position) {
                                case 1:
                                    msg = Misc.fromUTF8(shellCmd.args[ap.position]);
                                    break;
                                case 0: {
                                    final Reader reader = new InputStreamReader(shellCmd.stdIn, Misc.UTF8);
                                    final CharBuffer buf = CharBuffer.allocate(8192);
                                    String m = "";
                                    while (true) {
                                        ui.makeNotification(m, id);
                                        if (reader.read(buf) < 0) break;
                                        if (buf.remaining() < 2) { // TODO: correct
                                            buf.position(buf.limit() / 2);
                                            buf.compact();
                                        }
                                        m = buf.duplicate().flip().toString();
                                    }
                                    msg = m;
                                    break;
                                }
                                default:
                                    throw new ParseException("Bad arguments");
                            }
                            ui.makeNotification(msg, id);
                            break;
                        }
                        case "serial": {
                            if (shellCmd.args.length != 2)
                                throw new ParseException("Wrong number of arguments");
                            final Map<String, ?> params
                                    = UsbUartModule.meta.fromUri(Uri.parse(
                                    "uart:/" + Misc.fromUTF8(shellCmd.args[1])));
                            final BackendModule be = new UsbUartModule();
                            be.setContext(ui.ctx);
                            be.setOnMessageListener(new BackendModule.OnMessageListener() {
                                @Override
                                public void onMessage(final Object msg) {
                                    if (msg instanceof Throwable) {
                                        try {
                                            shellCmd.stdErr.write(Misc.toUTF8(((Throwable) msg)
                                                    .getMessage() + "\n"));
                                        } catch (final IOException ignored) {
                                        }
                                    }
                                }
                            });
                            // be.setUi(); // TODO: Maybe...
                            be.setOutputStream(shellCmd.stdOut);
                            final OutputStream toBe = be.getOutputStream();
                            try {
                                be.setParameters(params);
                                be.connect();
                                final byte[] buf = new byte[8192];
                                try {
                                    while (true) {
                                        final int r = shellCmd.stdIn.read(buf);
                                        if (r < 0) break;
                                        toBe.write(buf, 0, r);
                                    }
                                } catch (final IOException | BackendException e) {
                                    try {
                                        be.disconnect();
                                    } catch (final BackendException ignored) {
                                    }
                                    throw new IOException(e);
                                }
                                be.disconnect();
                            } catch (final BackendException e) {
                                throw new IOException(e);
                            }
                            break;
                        }
                        default:
                            throw new ParseException("Unknown command");
                    }
                    shellCmd.exit(0);
                } catch (final IOException | ParseException | BinaryGetOpts.ParseException e) {
                    try {
                        shellCmd.stdErr.write(Misc.toUTF8(e.getMessage() + "\n"));
                        shellCmd.exit(1);
                    } catch (final IOException ignored) {
                    }
                }
                return null;
            }
        }

        @Override
        public void run() {
            try {
                serverSocket = new LocalServerSocket(ui.ctx.getPackageName() + ".termsh");
                while (!Thread.interrupted()) {
                    final LocalSocket socket = serverSocket.accept();
                    ui.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            new ClientTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, socket);
                        }
                    });
                }
            } catch (final InterruptedIOException ignored) {
            } catch (final IOException e) {
                Log.e("TermShServer", "IO", e);
            }
        }
    }

    private final UiBridge ui;
    private final UiServer uiServer;
    private final Thread lth;

    @UiThread
    public TermSh(@NonNull final Context context) {

        final NotificationChannel nc;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nc = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.app_name) + " Shell",
                    NotificationManager.IMPORTANCE_HIGH);
            ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                    .createNotificationChannel(nc);
        }

        ui = new UiBridge(context);
        uiServer = new UiServer(ui);
        lth = new Thread(uiServer, "TermShServer");
        lth.setDaemon(true);
        lth.start();
    }

    @Override
    protected void finalize() throws Throwable {
        lth.interrupt();
        super.finalize();
    }
}
