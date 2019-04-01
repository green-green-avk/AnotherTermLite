package green_green_avk.anotherterm.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EscCsi {
    private static final Pattern PAT = Pattern.compile("^\\e\\[([?>!]?)(.*?)([@A-Z\\\\^_`a-z{|}~])$");

    public final char type;
    public final char prefix;
    public final String body;
    public final String[] args;

    public EscCsi(String v) throws IllegalArgumentException {
        Matcher m = PAT.matcher(v);
        if (!m.matches()) throw new IllegalArgumentException();
        type = m.group(3).charAt(0);
        body = m.group(2);
        prefix = (m.group(1).isEmpty()) ? 0 : m.group(1).charAt(0);
        args = body.split(";");
    }

    public int getIntArg(int n, int def) {
        if (args.length <= n) return def;
        try {
            return Integer.parseInt(args[n]);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
