package green_green_avk.anotherterm;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public final class C {

    private C() {
    }

    public static final String PKG_NAME = C.class.getName().replaceFirst("\\..*?$", "");
    public static final String APP_ID = BuildConfig.APPLICATION_ID;
    public static final String IFK_MSG_NEW = BuildConfig.APPLICATION_ID + ".MSG_NEW";
    public static final String IFK_MSG_NAME = BuildConfig.APPLICATION_ID + ".MSG_NAME";
    public static final String IFK_MSG_SESS_KEY = BuildConfig.APPLICATION_ID + ".MSG_SESS_KEY";
    public static final String IFK_MSG_SESS_TAIL = BuildConfig.APPLICATION_ID + ".MSG_SESS_TAIL";
    public static final List<String> charsetList = new ArrayList<>(Charset.availableCharsets().keySet());
}
