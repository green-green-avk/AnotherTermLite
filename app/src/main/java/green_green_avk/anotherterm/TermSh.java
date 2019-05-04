package green_green_avk.anotherterm;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.FileProvider;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
import java.util.concurrent.atomic.AtomicInteger;

import green_green_avk.anotherterm.backends.BackendException;
import green_green_avk.anotherterm.backends.BackendModule;
import green_green_avk.anotherterm.backends.usbUart.UsbUartModule;
import green_green_avk.anotherterm.utils.BinaryGetOpts;
import green_green_avk.anotherterm.utils.BlockingSync;
import green_green_avk.anotherterm.utils.Misc;

public final class TermSh {
    private static final String USER_NOTIFICATION_CHANNEL_ID =
            TermSh.class.getName() + ".user";
    private static final String REQUEST_NOTIFICATION_CHANNEL_ID =
            TermSh.class.getName() + ".request";

    private static File getFileWithCWD(@NonNull final String cwd, @NonNull final String fn) {
        final File f = new File(fn);
        if (f.isAbsolute()) return f;
        return new File(cwd, fn);
    }

    private static void checkFile(@NonNull final File file) throws FileNotFoundException {
        try {
            if (!file.exists())
                throw new FileNotFoundException("No such file");
            if (file.isDirectory())
                throw new FileNotFoundException("File is a directory");
        } catch (final SecurityException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    private static final class UiBridge {
        private final Context ctx;
        private final Handler handler;

        private final AtomicInteger notificationId = new AtomicInteger(0);

        @UiThread
        private UiBridge(@NonNull final Context context) {
            ctx = context;
            handler = new Handler();
        }

        private void runOnUiThread(@NonNull final Runnable runnable) {
            handler.post(runnable);
        }

        private int getNextNotificationId() {
            return notificationId.getAndIncrement();
        }

        private void postNotification(final String message, final int id) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    final Notification n = new NotificationCompat.Builder(
                            ctx.getApplicationContext(), USER_NOTIFICATION_CHANNEL_ID)
                            .setContentTitle(message)
                            .setSmallIcon(R.drawable.ic_stat_serv)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true)
                            .build();
                    NotificationManagerCompat.from(ctx).notify(C.TERMSH_USER_TAG, id, n);
                }
            });
        }

        private void removeNotification(final int id) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    NotificationManagerCompat.from(ctx).cancel(C.TERMSH_USER_TAG, id);
                }
            });
        }
    }

    private static final class UiServer implements Runnable {
        private static final BinaryGetOpts.Options NOTIFY_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("id", new String[]{"-i", "--id"},
                                BinaryGetOpts.Option.Type.INT),
                        new BinaryGetOpts.Option("remove", new String[]{"-r", "--remove"},
                                BinaryGetOpts.Option.Type.NONE)
                });
        private static final BinaryGetOpts.Options GET_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("notify", new String[]{"-n", "--notify"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("mime", new String[]{"-m", "--mime"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("title", new String[]{"-t", "--title"},
                                BinaryGetOpts.Option.Type.STRING),
                });
        private static final BinaryGetOpts.Options OPEN_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("notify", new String[]{"-n", "--notify"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("mime", new String[]{"-m", "--mime"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("title", new String[]{"-t", "--title"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("uri", new String[]{"-u", "--uri"},
                                BinaryGetOpts.Option.Type.NONE),
                });

        private final UiBridge ui;
        private LocalServerSocket serverSocket;

        private UiServer(@NonNull final UiBridge ui) {
            this.ui = ui;
        }

        private static final class ParseException extends Exception {
            private ParseException(final String message) {
                super(message);
            }
        }

        private static final class ShellCmdIO {
            private static final int ARGLEN_MAX = 1024 * 1024;
            private static final byte[][] NOARGS = new byte[0][];

            private boolean closed = false;
            private final Object closeLock = new Object();
            private final LocalSocket socket;
            private final InputStream cis;
            private final FileDescriptor[] ioFds;
            private final InputStream stdIn;
            private final OutputStream stdOut;
            private final OutputStream stdErr;
            private final String currDir;
            private final byte[][] args;
            private volatile Runnable onTerminate = null;

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
                    final Runnable ot = onTerminate;
                    if (ot != null) ot.run();
                    close();
                }
            };

            // It seems, android.system.Os class is trying to be linked by Dalvik even when inside
            // appropriate if statement and raises java.lang.VerifyError on the constructor call...
            // API 19 is affected at least.
            // Moving to separate class to work it around.
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            private static final class Utils21 {
                private Utils21() {
                }

                private static void close(final FileDescriptor[] fds) {
                    if (fds != null) {
                        for (final FileDescriptor fd : fds) {
                            if (fd.valid()) {
                                try {
                                    Os.close(fd);
                                } catch (final ErrnoException e) {
                                    Log.e("TermShServer", "Request", e);
                                }
                            }
                        }
                    }
                }
            }

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
                        Utils21.close(ioFds); // For API >= 28
                    try {
                        socket.close();
                    } catch (final IOException ignored) {
                    }
                }
            }

            // TODO: ParcelFileDescriptor?

            @NonNull
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

            @NonNull
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
            private static String parsePwd(@NonNull final InputStream is)
                    throws IOException, ParseException {
                final DataInputStream dis = new DataInputStream(is);
                final int l = dis.readInt();
                if (l < 0 || l > ARGLEN_MAX) throw new ParseException("Current dir parse error");
                final byte[] buf = new byte[l];
                dis.readFully(buf);
                return Misc.fromUTF8(buf);
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

            private void exit(final int status) {
                try {
                    socket.getOutputStream().write(new byte[]{(byte) status});
                } catch (final IOException ignored) {
                }
                close();
            }

            private ShellCmdIO(@NonNull final LocalSocket socket)
                    throws IOException, ParseException {
                this.socket = socket;
                cis = socket.getInputStream();
                currDir = parsePwd(cis);
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
                final ShellCmdIO shellCmd;
                try {
                    if (Process.myUid() != socket.getPeerCredentials().getUid())
                        throw new ParseException("Spoofing detected!");
                    shellCmd = new ShellCmdIO(socket);
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
                            if (opts.containsKey("remove")) {
                                if (_id == null)
                                    throw new ParseException("What exactly to remove?");
                                ui.removeNotification(_id);
                                break;
                            }
                            final int id = _id == null ? ui.getNextNotificationId() : _id;
                            final String msg;
                            switch (shellCmd.args.length - ap.position) {
                                case 1:
                                    msg = Misc.fromUTF8(shellCmd.args[ap.position]);
                                    break;
                                case 0: {
                                    final Reader reader =
                                            new InputStreamReader(shellCmd.stdIn, Misc.UTF8);
                                    final CharBuffer buf = CharBuffer.allocate(8192);
                                    String m = "";
                                    while (true) {
                                        ui.postNotification(m, id);
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
                            ui.postNotification(msg, id);
                            break;
                        }
                        case "view":
                        case "edit": {
                            final boolean writeable = "edit".equals(command);
                            final BinaryGetOpts.Parser ap = new BinaryGetOpts.Parser(shellCmd.args);
                            ap.skip();
                            final Map<String, ?> opts = ap.parse(OPEN_OPTS);
                            String mime = (String) opts.get("mime");
                            String title = (String) opts.get("title");
                            if (title == null) title = "Pick application";
                            if (shellCmd.args.length - ap.position == 1) {
                                final String filename =
                                        Misc.fromUTF8(shellCmd.args[ap.position]);
                                final Uri uri;
                                if (opts.containsKey("uri")) {
                                    uri = Uri.parse(filename);
                                } else {
                                    final File file = getFileWithCWD(shellCmd.currDir, filename);
                                    checkFile(file);
                                    try {
                                        uri = FileProvider.getUriForFile(ui.ctx,
                                                BuildConfig.APPLICATION_ID + ".fileprovider",
                                                file);
                                    } catch (final IllegalArgumentException e) {
                                        throw new FileNotFoundException(e.getMessage());
                                    }
                                }
                                final Intent i = new Intent(writeable ?
                                        Intent.ACTION_EDIT : Intent.ACTION_VIEW);
                                i.setDataAndType(uri, mime);
                                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        | (writeable ?
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION : 0));
                                if (opts.containsKey("notify"))
                                    RequesterActivity.showAsNotification(ui.ctx,
                                            Intent.createChooser(i, title),
                                            ui.ctx.getString(R.string.title_shell_of_s,
                                                    ui.ctx.getString(R.string.app_name)),
                                            title + " (" + filename + ")",
                                            REQUEST_NOTIFICATION_CHANNEL_ID,
                                            NotificationCompat.PRIORITY_HIGH);
                                else
                                    ui.ctx.startActivity(Intent.createChooser(i, title)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            } else {
                                throw new ParseException("Bad arguments");
                            }
                            break;
                        }
                        case "send": {
                            final BinaryGetOpts.Parser ap = new BinaryGetOpts.Parser(shellCmd.args);
                            ap.skip();
                            final Map<String, ?> opts = ap.parse(OPEN_OPTS);
                            String mime = (String) opts.get("mime");
                            if (mime == null) mime = "*/*";
                            String title = (String) opts.get("title");
                            if (title == null) title = "Pick destination";
                            final String name;
                            final Uri uri;
                            final BlockingSync<Object> result = new BlockingSync<>();
                            switch (shellCmd.args.length - ap.position) {
                                case 1: {
                                    result.set(null);
                                    name = Misc.fromUTF8(shellCmd.args[ap.position]);
                                    if (opts.containsKey("uri")) {
                                        uri = Uri.parse(name);
                                    } else {
                                        final File file = getFileWithCWD(shellCmd.currDir, name);
                                        checkFile(file);
                                        try {
                                            uri = FileProvider.getUriForFile(ui.ctx,
                                                    BuildConfig.APPLICATION_ID + ".fileprovider",
                                                    file);
                                        } catch (final IllegalArgumentException e) {
                                            throw new FileNotFoundException(e.getMessage());
                                        }
                                    }
                                    break;
                                }
                                case 0: {
                                    name = "Stream";
                                    uri = StreamProvider.getUri(shellCmd.stdIn, mime,
                                            new StreamProvider.OnResult() {
                                                @Override
                                                public void onResult(final Object msg) {
                                                    result.set(null);
                                                }
                                            });
                                    break;
                                }
                                default:
                                    throw new ParseException("Bad arguments");
                            }
                            final Intent i = new Intent(Intent.ACTION_SEND);
                            i.setType(mime);
                            i.putExtra(Intent.EXTRA_STREAM, uri);
                            if (opts.containsKey("notify"))
                                RequesterActivity.showAsNotification(ui.ctx,
                                        Intent.createChooser(i, title),
                                        ui.ctx.getString(R.string.title_shell_of_s,
                                                ui.ctx.getString(R.string.app_name)),
                                        title + " (" + name + ")",
                                        REQUEST_NOTIFICATION_CHANNEL_ID,
                                        NotificationCompat.PRIORITY_HIGH);
                            else
                                ui.ctx.startActivity(Intent.createChooser(i, title)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            // Wait here
                            shellCmd.onTerminate = new Runnable() {
                                @Override
                                public void run() {
                                    result.set(null);
                                }
                            };
                            result.get();
                            shellCmd.onTerminate = null;
                            // ===
                            break;
                        }
                        case "get": {
                            final BinaryGetOpts.Parser ap = new BinaryGetOpts.Parser(shellCmd.args);
                            ap.skip();
                            final Map<String, ?> opts = ap.parse(GET_OPTS);
                            String mime = (String) opts.get("mime");
                            if (mime == null) mime = "*/*";
                            String title = (String) opts.get("title");
                            if (title == null) title = "Pick document";
                            final BlockingSync<Intent> r = new BlockingSync<>();
                            final Intent i = new Intent(Intent.ACTION_GET_CONTENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE).setType(mime);
                            final RequesterActivity.OnResult onResult = new RequesterActivity.OnResult() {
                                @Override
                                public void onResult(@Nullable Intent result) {
                                    r.setIfIsNotSet(result);
                                }
                            };
                            final RequesterActivity.Request request = opts.containsKey("notify") ?
                                    RequesterActivity.request(
                                            ui.ctx, Intent.createChooser(i, title), onResult,
                                            ui.ctx.getString(R.string.title_shell_of_s,
                                                    ui.ctx.getString(R.string.app_name)),
                                            title, REQUEST_NOTIFICATION_CHANNEL_ID,
                                            NotificationCompat.PRIORITY_HIGH) :
                                    RequesterActivity.request(
                                            ui.ctx, Intent.createChooser(i, title), onResult);
                            // Wait here
                            shellCmd.onTerminate = new Runnable() {
                                @Override
                                public void run() {
                                    request.cancel();
                                }
                            };
                            final Intent ri = r.get();
                            shellCmd.onTerminate = null;
                            // ===
                            if (ri != null) {
                                final Uri uri = ri.getData();
                                if (uri != null) {
                                    shellCmd.stdOut.write(Misc.toUTF8(uri.toString()));
                                    break;
                                }
                            }
                            shellCmd.exit(1);
                            return null;
                        }
                        case "cat": {
                            if (shellCmd.args.length != 2)
                                throw new ParseException("Wrong number of arguments");
                            final Uri uri = Uri.parse(Misc.fromUTF8(shellCmd.args[1]));
                            final InputStream is =
                                    ui.ctx.getContentResolver().openInputStream(uri);
                            if (is == null) {
                                // Asset
                                throw new FileNotFoundException("Something not found");
                            }
                            final byte[] buf = new byte[8192];
                            try {
                                while (true) {
                                    final int r = is.read(buf);
                                    if (r < 0) break;
                                    shellCmd.stdOut.write(buf, 0, r);
                                }
                            } finally {
                                is.close();
                            }
                            break;
                        }
                        case "serial": {
                            if (shellCmd.args.length > 2)
                                throw new ParseException("Wrong number of arguments");
                            final Map<String, ?> params
                                    = shellCmd.args.length == 2
                                    ? UsbUartModule.meta.fromUri(Uri.parse(
                                    "uart:/" + Misc.fromUTF8(shellCmd.args[1])))
                                    : null;
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
                                if (params != null) be.setParameters(params);
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
                } catch (final InterruptedException |
                        IOException | ParseException | BinaryGetOpts.ParseException e) {
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
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private final UiBridge ui;
    private final UiServer uiServer;
    private final Thread lth;

    @UiThread
    public TermSh(@NonNull final Context context) {

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            final NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(
                    USER_NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.title_shell_of_s,
                            context.getString(R.string.app_name)),
                    NotificationManager.IMPORTANCE_HIGH
            ));
            nm.createNotificationChannel(new NotificationChannel(
                    REQUEST_NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.title_shell_script_request_of_s,
                            context.getString(R.string.app_name)),
                    NotificationManager.IMPORTANCE_HIGH
            ));
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
