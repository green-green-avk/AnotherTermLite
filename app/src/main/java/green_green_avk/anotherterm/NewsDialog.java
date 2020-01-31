package green_green_avk.anotherterm;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;

public final class NewsDialog {
    private NewsDialog() {
    }

    public static boolean getSeen(@NonNull final Context ctx) {
        final SharedPreferences ps = PreferenceManager.getDefaultSharedPreferences(ctx);
        return ps.getBoolean("news_seen", false);
    }

    public static void setSeen(@NonNull final Context ctx, final boolean v) {
        final SharedPreferences ps = PreferenceManager.getDefaultSharedPreferences(ctx);
        final SharedPreferences.Editor e = ps.edit();
        e.putBoolean("news_seen", v);
        e.apply();
    }

    public static void show(@NonNull final Context ctx) {
        new AlertDialog.Builder(ctx).setView(R.layout.dialog_news).setCancelable(false)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setSeen(ctx, true);
                    }
                }).show();
    }

    public static void showUnseen(@NonNull final Context ctx) {
        if (!getSeen(ctx)) show(ctx);
    }
}
