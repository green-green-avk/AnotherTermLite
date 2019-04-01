package green_green_avk.fastarrayutils;

import android.support.annotation.Keep;

@Keep
public class FastArrayUtils {
    static {
        System.loadLibrary("fastarrayutils");
    }

    @Keep
    public static native int indexOf(byte[] array, int start, int end, byte value);

    @Keep
    public static native int indexOf(char[] array, int start, int end, char value);

    @Keep
    public static native int indexOf(int[] array, int start, int end, int value);

    @Keep
    public static native int getEqualElementsLength(byte[] array, int start, int end);

    @Keep
    public static native int getEqualElementsLength(char[] array, int start, int end);

    @Keep
    public static native int getEqualElementsLength(int[] array, int start, int end);
}
