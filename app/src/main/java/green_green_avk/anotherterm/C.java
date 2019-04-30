package green_green_avk.anotherterm;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public final class C {

    private C() {
    }

    public static final String JAVA_PKG_NAME = C.class.getName().replaceFirst("\\..*?$", "");
    public static final String APP_ID = BuildConfig.APPLICATION_ID;
    public static final String IFK_MSG_NEW = BuildConfig.APPLICATION_ID + ".MSG_NEW";
    public static final String IFK_MSG_NAME = BuildConfig.APPLICATION_ID + ".MSG_NAME";
    public static final String IFK_MSG_SESS_KEY = BuildConfig.APPLICATION_ID + ".MSG_SESS_KEY";
    public static final String IFK_MSG_SESS_TAIL = BuildConfig.APPLICATION_ID + ".MSG_SESS_TAIL";
    public static final String IFK_MSG_ID = BuildConfig.APPLICATION_ID + ".MSG_ID";
    public static final String IFK_MSG_MIME = BuildConfig.APPLICATION_ID + ".MSG_MIME";
    public static final int NOTIFICATION_GROUP_BITS = 4;
    public static final int NOTIFICATION_SUBID_MASK = (-1) >>> NOTIFICATION_GROUP_BITS;
    public static final int NOTIFICATION_APPDYN_GROUP = 1 << (Integer.SIZE - NOTIFICATION_GROUP_BITS);
    public static final int NOTIFICATION_TERMSH_GROUP = 2 << (Integer.SIZE - NOTIFICATION_GROUP_BITS);
    public static final List<String> charsetList = new ArrayList<>(Charset.availableCharsets().keySet());
}
