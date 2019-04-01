package green_green_avk.anotherterm.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EscOsc {
    private static final Pattern PAT = Pattern.compile("^\\e](.*?)(?:\\a|\\e\\\\)$");

    public final String body;
    public final String[] args;

    public EscOsc(String v) throws IllegalArgumentException {
        Matcher m = PAT.matcher(v);
        if (!m.matches()) throw new IllegalArgumentException();
        body = m.group(1);
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
