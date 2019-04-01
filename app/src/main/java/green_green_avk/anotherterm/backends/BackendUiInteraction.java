package green_green_avk.anotherterm.backends;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public interface BackendUiInteraction {
    @Nullable
    String promptPassword(@NonNull final String message) throws InterruptedException;

    boolean promptYesNo(@NonNull final String message) throws InterruptedException;

    void showMessage(@NonNull final String message);

    void showToast(@NonNull final String message);

    byte[] promptContent(@NonNull final String message, @NonNull final String mimeType) throws InterruptedException;

    boolean promptPermissions(@NonNull final String[] perms) throws InterruptedException;
}
