package green_green_avk.anotherterm;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

public final class LinksProvider extends ContentProvider {
    private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".linksprovider";
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    private static final int CODE_LINK_HTML = 1;

    static {
        MATCHER.addURI(AUTHORITY, "/html/*", CODE_LINK_HTML);
    }

    private String contentTitle = null;
    private String contentFilename = null;
    private String contentFmt = null;

    @Override
    public boolean onCreate() {
        contentTitle = getContext().getString(R.string.terminal_link_s);
        contentFilename = contentTitle + ".html";
        contentFmt =
                "<html><body><p>" + contentTitle + "</p><p><a href='%2$s'>%2$s</a></p></body></html>";
        return true;
    }

    public static Uri getHtmlWithLink(@NonNull final Uri uri) {
        return getHtmlWithLink(uri.toString());
    }

    public static Uri getHtmlWithLink(@NonNull final String link) {
        return Uri.parse("content://" + AUTHORITY + "/html/" + Uri.encode(link));
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "text/html";
    }

    @Nullable
    @Override
    public String[] getStreamTypes(@NonNull Uri uri, @NonNull String mimeTypeFilter) {
        return new String[]{"text/html"};
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @NonNull
    private Uri getTargetUri(@NonNull final Uri uri) {
        return Uri.parse(uri.getLastPathSegment());
    }

    @NonNull
    private byte[] buildContent(@NonNull final Uri uri) {
        return String.format(Locale.getDefault(), contentFmt, uri.getQueryParameter("name"),
                uri.toString()).getBytes();
    }

    private final PipeDataWriter<String> streamWriter = new PipeDataWriter<String>() {
        @Override
        public void writeDataToPipe(@NonNull ParcelFileDescriptor output, @NonNull Uri uri, @NonNull String mimeType, @Nullable Bundle opts, @Nullable String args) {
            final FileOutputStream s = new FileOutputStream(output.getFileDescriptor());
            try {
                s.write(buildContent(getTargetUri(uri)));
            } catch (IOException ignored) {
            }
        }
    };

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
//        Log.d("QUERY", String.format(Locale.ROOT, "[%s] %s", uri, mode));
        switch (MATCHER.match(uri)) {
            case CODE_LINK_HTML: {
                return openPipeHelper(uri, "text/html", null, null, streamWriter);
            }
        }
        return super.openFile(uri, mode);
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
//        Log.d("QUERY", String.format(Locale.ROOT, "[%s] {%s}", uri, Arrays.toString(projection)));
        switch (MATCHER.match(uri)) {
            case CODE_LINK_HTML: {
                final Uri targetUri = getTargetUri(uri);
                final MatrixCursor cursor =
                        new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                                1); // Bluetooth should be happy
                cursor.addRow(new Object[]{
                        String.format(Locale.getDefault(), contentFilename, targetUri.getQueryParameter("name")),
                        buildContent(targetUri).length
                });
                return cursor;
            }
        }
        return null;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
