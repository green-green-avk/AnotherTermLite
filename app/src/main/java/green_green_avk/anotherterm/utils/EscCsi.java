package green_green_avk.anotherterm.utils;

import android.support.annotation.NonNull;

import java.nio.CharBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EscCsi {
    private static final Pattern PAT = Pattern.compile(
            "^\\e\\[([?>!]?)(.*?)([@A-Z\\\\^_`a-z{|}~])$", Pattern.DOTALL);

    public final char type;
    public final char prefix;
    public final String body;
    public final String[] args;

    public EscCsi(@NonNull final CharBuffer v) throws IllegalArgumentException {
        final Matcher m = PAT.matcher(v);
        if (!m.matches()) throw new IllegalArgumentException("len=" + v.length());
        type = m.group(3).charAt(0);
        body = m.group(2);
        prefix = (m.group(1).isEmpty()) ? 0 : m.group(1).charAt(0);
        args = body.split(";");
    }

    public int getIntArg(final int n, final int def) {
        if (args.length <= n) return def;
        try {
            return Integer.parseInt(args[n]);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
