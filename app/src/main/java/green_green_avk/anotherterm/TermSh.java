package green_green_avk.anotherterm;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.OpenableColumns;
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
import java.io.DataOutputStream;
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
import green_green_avk.anotherterm.ui.BackendUiShell;
import green_green_avk.anotherterm.utils.BinaryGetOpts;
import green_green_avk.anotherterm.utils.BlockingSync;
import green_green_avk.anotherterm.utils.ChrootedFile;
import green_green_avk.anotherterm.utils.Misc;
import green_green_avk.ptyprocess.PtyProcess;

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
        if (!file.exists())
            throw new FileNotFoundException("No such file");
        if (file.isDirectory())
            throw new FileNotFoundException("File is a directory");
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
        private static final BinaryGetOpts.Options COPY_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("force", new String[]{"-f", "--force"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("from-path", new String[]{"-fp", "--from-path"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("from-uri", new String[]{"-fu", "--from-uri"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("to-path", new String[]{"-tp", "--to-path"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("to-uri", new String[]{"-tu", "--to-uri"},
                                BinaryGetOpts.Option.Type.STRING)
                });
        private static final BinaryGetOpts.Options NOTIFY_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("id", new String[]{"-i", "--id"},
                                BinaryGetOpts.Option.Type.INT),
                        new BinaryGetOpts.Option("remove", new String[]{"-r", "--remove"},
                                BinaryGetOpts.Option.Type.NONE)
                });
        private static final BinaryGetOpts.Options OPEN_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("mime", new String[]{"-m", "--mime"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("notify", new String[]{"-n", "--notify"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("title", new String[]{"-t", "--title"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("uri", new String[]{"-u", "--uri"},
                                BinaryGetOpts.Option.Type.NONE)
                });
        private static final BinaryGetOpts.Options PICK_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("force", new String[]{"-f", "--force"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("mime", new String[]{"-m", "--mime"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("notify", new String[]{"-n", "--notify"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("title", new String[]{"-t", "--title"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("uri", new String[]{"-u", "--uri"},
                                BinaryGetOpts.Option.Type.NONE)
                });
        private static final BinaryGetOpts.Options SEND_OPTS =
                new BinaryGetOpts.Options(new BinaryGetOpts.Option[]{
                        new BinaryGetOpts.Option("mime", new String[]{"-m", "--mime"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("notify", new String[]{"-n", "--notify"},
                                BinaryGetOpts.Option.Type.NONE),
                        new BinaryGetOpts.Option("size", new String[]{"-s", "--size"},
                                BinaryGetOpts.Option.Type.INT),
                        new BinaryGetOpts.Option("title", new String[]{"-t", "--title"},
                                BinaryGetOpts.Option.Type.STRING),
                        new BinaryGetOpts.Option("uri", new String[]{"-u", "--uri"},
                                BinaryGetOpts.Option.Type.NONE)
                });

        private final UiBridge ui;
        private LocalServerSocket serverSocket;

        private UiServer(@NonNull final UiBridge ui) {
            this.ui = ui;
        }

        private static final class ParseException extends RuntimeException {
            private ParseException(final String message) {
                super(message);
            }
        }

        private static final class ShellCmdIO {
            private static final byte CMD_EXIT = 0;
            private static final byte CMD_OPEN = 1;

            private static final int ARGLEN_MAX = 1024 * 1024;
            private static final byte[][] NOARGS = new byte[0][];

            private boolean closed = false;
            private final Object closeLock = new Object();
            private final LocalSocket socket;
            private final InputStream cis;
            @NonNull
            private final InputStream stdIn;
            @NonNull
            private final OutputStream stdOut;
            @NonNull
            private final OutputStream stdErr;
            @NonNull
            private final InputStream ctlIn;
            @NonNull
            private final String currDir;
            private final byte[][] args;
            private volatile Runnable onTerminate = null;

            private final Thread cth = new Thread("TermShServer.Control") {
                @Override
                public void run() {
                    try {
                        while (true) {
                            final int r = ctlIn.read();
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

                private static void close(@NonNull final FileDescriptor fd) throws IOException {
                    if (fd.valid()) {
                        try {
                            Os.close(fd);
                        } catch (final ErrnoException e) {
                            throw new IOException(e.getMessage(), e);
                        }
                    }
                }
            }

            private static void close(@NonNull final FileDescriptor fd) throws IOException {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Utils21.close(fd);
                } else {
                    final int _fd;
                    try {
                        _fd = (int) FileDescriptor.class.getMethod("getInt$").invoke(fd);
                    } catch (final IllegalAccessException e) {
                        throw new IOException("Cannot close temporary socket: workaround failed");
                    } catch (final InvocationTargetException e) {
                        throw new IOException("Cannot close temporary socket: workaround failed");
                    } catch (final NoSuchMethodException e) {
                        throw new IOException("Cannot close temporary socket: workaround failed");
                    }
                    // I see no reason to implement a native method here.
                    ParcelFileDescriptor.adoptFd(_fd).close();
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
                }
            }

            @NonNull
            private static ParcelFileDescriptor wrapFD(final FileDescriptor fd)
                    throws IOException {
                final ParcelFileDescriptor pfd = ParcelFileDescriptor.dup(fd);
                try {
                    close(fd);
                } catch (final IOException ignored) {
                }
                return pfd;
            }

            // TODO: better remove fallbacks

            @NonNull
            private static FileInputStream wrapInputFD(final FileDescriptor fd) {
                try {
                    final ParcelFileDescriptor pfd = wrapFD(fd);
                    try {
                        return new PtyProcess.InterruptableFileInputStream(pfd);
                    } catch (final IOException e) {
                        Log.e("TermShServer", "Request", e);
                    }
                    return new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                } catch (final IOException e) {
                    Log.e("TermShServer", "Request", e);
                }
                return new FileInputStream(fd);
            }

            @NonNull
            private static FileOutputStream wrapOutputFD(final FileDescriptor fd) {
                try {
                    final ParcelFileDescriptor pfd = wrapFD(fd);
                    return new ParcelFileDescriptor.AutoCloseOutputStream(pfd);
                } catch (final IOException e) {
                    Log.e("TermShServer", "Request", e);
                }
                return new FileOutputStream(fd);
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

            private class ShellErrnoException extends IOException {
                public final int errno;

                public ShellErrnoException(final String message, final int errno) {
                    super(message);
                    this.errno = errno;
                }
            }

            private void exit(final int status) {
                try {
                    socket.getOutputStream().write(new byte[]{CMD_EXIT, (byte) status});
                } catch (final IOException ignored) {
                }
                close();
            }

            @NonNull
            private ParcelFileDescriptor open(@NonNull final String name, final int flags)
                    throws ParseException, IOException {
                final int errno;
                try {
                    final DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                    dos.writeByte(CMD_OPEN);
                    dos.writeInt(flags);
                    final byte[] _name = Misc.toUTF8(name);
                    dos.writeInt(_name.length);
                    dos.write(_name);
                    final DataInputStream dis = new DataInputStream(socket.getInputStream());
                    final byte result = dis.readByte();
                    if (result == 0) {
                        final FileDescriptor[] fds = socket.getAncillaryFileDescriptors();
                        if (fds.length != 1) throw new ParseException("Invalid descriptors");
                        return wrapFD(fds[0]);
                    }
                    errno = dis.readInt();
                } catch (final IOException e) {
                    throw new ParseException(e.getMessage());
                }
                switch (errno) {
                    case PtyProcess.ENOENT:
                        throw new FileNotFoundException(name + ": No such file or directory");
                    default:
                        throw new ShellErrnoException(name + ": open() fails with errno=" + errno, errno);
                }
            }

            private final ChrootedFile.Ops ops = new ChrootedFile.Ops() {
                @Override
                public ParcelFileDescriptor open(final String path, final int flags)
                        throws IOException, ParseException {
                    return ShellCmdIO.this.open(path, flags);
                }
            };

            @NonNull
            private ChrootedFile getOriginal(@NonNull final String name)
                    throws ParseException {
                return new ChrootedFile(ops, name);
            }

            @NonNull
            private File getOriginalFile(@NonNull final String name)
                    throws ParseException, IOException {
                return new File(PtyProcess.getPathByFd(open(name, PtyProcess.O_PATH).getFd()));
            }

            private ShellCmdIO(@NonNull final LocalSocket socket)
                    throws IOException, ParseException {
                this.socket = socket;
                cis = socket.getInputStream();
                currDir = parsePwd(cis);
                args = parseArgs(cis);
                final FileDescriptor[] ioFds = socket.getAncillaryFileDescriptors();
                if (ioFds == null || ioFds.length != 4)
                    throw new ParseException("Bad descriptors");
                stdIn = wrapInputFD(ioFds[0]);
                stdOut = wrapOutputFD(ioFds[1]);
                stdErr = wrapOutputFD(ioFds[2]);
                ctlIn = wrapInputFD(ioFds[3]);
                cth.start();
            }
        }

        @NonNull
        private InputStream openInputStream(@NonNull final Uri uri) throws FileNotFoundException {
            final InputStream is = ui.ctx.getContentResolver().openInputStream(uri);
            if (is == null) {
                // Asset
                throw new FileNotFoundException(uri.toString() + " does not exist");
            }
            return is;
        }

        @NonNull
        private OutputStream openOutputStream(@NonNull final Uri uri) throws FileNotFoundException {
            final OutputStream os = ui.ctx.getContentResolver().openOutputStream(uri);
            if (os == null) {
                // Asset
                throw new FileNotFoundException(uri.toString() + " does not exist");
            }
            return os;
        }

        @Nullable
        private String deduceName(@NonNull final Uri uri) {
            return uri.getLastPathSegment();
        }

        @Nullable
        private String getName(@NonNull final Uri uri) {
            final Cursor c = ui.ctx.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (c == null) return deduceName(uri);
            try {
                c.moveToFirst();
                return c.getString(c.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            } catch (final Throwable e) {
                return deduceName(uri);
            } finally {
                c.close();
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
                    int exitStatus = 0;
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
                                    throw new ParseException("`id' argument is mandatory");
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
                                    final File file = shellCmd.getOriginalFile(filename);
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
                            final Map<String, ?> opts = ap.parse(SEND_OPTS);
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
                                        final File file = shellCmd.getOriginalFile(name);
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
                                    uri = StreamProvider.obtainUri(shellCmd.stdIn, mime,
                                            null, (Integer) opts.get("size"),
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
                                    StreamProvider.releaseUri(uri);
                                    result.set(null);
                                }
                            };
                            result.get();
                            shellCmd.onTerminate = null;
                            // ===
                            break;
                        }
                        case "pick": {
                            final BinaryGetOpts.Parser ap = new BinaryGetOpts.Parser(shellCmd.args);
                            ap.skip();
                            final Map<String, ?> opts = ap.parse(PICK_OPTS);
                            String mime = (String) opts.get("mime");
                            if (mime == null) mime = "*/*";
                            String title = (String) opts.get("title");
                            if (title == null) title = "Pick document";

                            OutputStream output;
                            final ChrootedFile outputFile;
                            switch (shellCmd.args.length - ap.position) {
                                case 0:
                                    output = shellCmd.stdOut;
                                    outputFile = null;
                                    break;
                                case 1: {
                                    output = null;
                                    final String name = Misc.fromUTF8(shellCmd.args[ap.position]);
                                    final ChrootedFile f = shellCmd.getOriginal(name);
                                    if (f.isDirectory()) {
                                        outputFile = f;
                                    } else if (f.exists()) {
                                        if (!opts.containsKey("force")) {
                                            throw new ParseException("File already exists");
                                        }
                                        outputFile = f;
                                    } else {
                                        final ChrootedFile pf = f.getParent();
                                        if (pf == null || !pf.isDirectory()) {
                                            throw new ParseException("Directory does not exist");
                                        }
                                        outputFile = pf;
                                    }
                                    if (!outputFile.canWrite()) {
                                        throw new ParseException("Directory write access denied");
                                    }
                                    break;
                                }
                                default:
                                    throw new ParseException("Bad arguments");
                            }

                            final BlockingSync<Intent> r = new BlockingSync<>();
                            final Intent i = new Intent(Intent.ACTION_GET_CONTENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE).setType(mime);
                            final RequesterActivity.OnResult onResult =
                                    new RequesterActivity.OnResult() {
                                        @Override
                                        public void onResult(@Nullable final Intent result) {
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
                            final Uri uri;
                            if (ri == null || (uri = ri.getData()) == null) {
                                shellCmd.exit(1);
                                return null;
                            }
                            if (output == null) {
                                if (outputFile.isDirectory()) {
                                    String filename = getName(uri);
                                    if (filename == null) {
                                        shellCmd.stdErr.write(
                                                Misc.toUTF8("Cannot deduce file name\n"));
                                        filename = C.UNNAMED_FILE_NAME;
                                        exitStatus = 2;
                                    }
                                    final ChrootedFile of = outputFile.getChild(filename);
                                    if (!opts.containsKey("force") && of.isFile())
                                        throw new ParseException("File already exists");
                                    output = new FileOutputStream(of.create().getOriginalFile());
                                } else {
                                    output = new FileOutputStream(outputFile.getOriginalFile());
                                }
                            }
                            if (opts.containsKey("uri")) {
                                output.write(Misc.toUTF8(uri.toString()));
                            } else {
                                final InputStream is = openInputStream(uri);
                                try {
                                    Misc.copy(output, is);
                                } finally {
                                    try {
                                        is.close();
                                    } finally {
                                        output.close();
                                    }
                                }
                            }
                            break;
                        }
                        case "copy": {
                            final BinaryGetOpts.Parser ap = new BinaryGetOpts.Parser(shellCmd.args);
                            ap.skip();
                            final Map<String, ?> opts = ap.parse(COPY_OPTS);
                            if (shellCmd.args.length - ap.position != 0)
                                throw new ParseException("Wrong number of arguments");
                            final InputStream is;
                            final OutputStream os;
                            String name;
                            Uri fromUri = null;
                            File fromFile = null;
                            if ((name = (String) opts.get("from-uri")) != null) {
                                fromUri = Uri.parse(name);
                                is = openInputStream(fromUri);
                            } else if ((name = (String) opts.get("from-path")) != null) {
                                fromFile = shellCmd.getOriginalFile(name);
                                is = new FileInputStream(fromFile);
                            } else {
                                is = shellCmd.stdIn;
                            }
                            if ((name = (String) opts.get("to-uri")) != null) {
                                os = openOutputStream(Uri.parse(name));
                            } else if ((name = (String) opts.get("to-path")) != null) {
                                ChrootedFile of = shellCmd.getOriginal(name);
                                if (of.isDirectory()) {
                                    String filename = null;
                                    if (fromUri != null) {
                                        filename = getName(fromUri);
                                    } else if (fromFile != null) {
                                        filename = fromFile.getName();
                                    }
                                    if (filename == null) {
                                        shellCmd.stdErr.write(Misc.toUTF8("Cannot deduce file name\n"));
                                        filename = C.UNNAMED_FILE_NAME;
                                        exitStatus = 2;
                                    }
                                    of = of.getChild(filename);
                                }
                                if (!opts.containsKey("force") && of.isFile())
                                    throw new ParseException("File already exists");
                                os = new FileOutputStream(of.create().getOriginalFile());
                            } else {
                                os = shellCmd.stdOut;
                            }
                            try {
                                Misc.copy(os, is);
                            } finally {
                                try {
                                    is.close();
                                } finally {
                                    os.close();
                                }
                            }
                            break;
                        }
                        case "cat": {
                            if (!PtyProcess.isatty(shellCmd.stdIn) || shellCmd.args.length < 2) {
                                Misc.copy(shellCmd.stdOut, shellCmd.stdIn);
                            }
                            for (int i = 1; i < shellCmd.args.length; ++i) {
                                final Uri uri = Uri.parse(Misc.fromUTF8(shellCmd.args[i]));
                                final InputStream is = openInputStream(uri);
                                try {
                                    Misc.copy(shellCmd.stdOut, is);
                                } finally {
                                    is.close();
                                }
                            }
                            break;
                        }
                        case "name": {
                            if (shellCmd.args.length != 2)
                                throw new ParseException("Wrong number of arguments");
                            final Uri uri = Uri.parse(Misc.fromUTF8(shellCmd.args[1]));
                            final String name = getName(uri);
                            if (name == null) {
                                shellCmd.stdOut.write(Misc.toUTF8(C.UNNAMED_FILE_NAME));
                                exitStatus = 2;
                            } else
                                shellCmd.stdOut.write(Misc.toUTF8(name));
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
                                public void onMessage(@NonNull final Object msg) {
                                    if (msg instanceof Throwable) {
                                        try {
                                            shellCmd.stdErr.write(Misc.toUTF8(((Throwable) msg)
                                                    .getMessage() + "\n"));
                                        } catch (final IOException ignored) {
                                        }
                                    }
                                }
                            });
                            final BackendUiShell ui = new BackendUiShell();
                            ui.setIO(shellCmd.stdIn, shellCmd.stdOut, shellCmd.stdErr);
                            be.setUi(ui);
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
                    shellCmd.exit(exitStatus);
                } catch (final InterruptedException | SecurityException |
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
