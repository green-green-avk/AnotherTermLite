package green_green_avk.anotherterm.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;

import java.util.UUID;

public abstract class Requester {
    @SuppressLint("ValidFragment")
    protected static class UiFragment extends Fragment {
        private static final String TAG = "UiFragment";

        protected static int generateRequestCode() {
            return (int) UUID.randomUUID().getLeastSignificantBits() & 0xFFFF;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);
        }

        @UiThread
        protected void recycle() {
            getActivity().getSupportFragmentManager().beginTransaction().remove(this).commit();
        }
    }

    @UiThread
    @NonNull
    protected static <T extends UiFragment> T prepare(@NonNull final Context ctx, @NonNull final T fragment) {
        final FragmentActivity a = (FragmentActivity) ctx;
        final FragmentManager m = a.getSupportFragmentManager();
        m.beginTransaction().add(fragment, UiFragment.TAG).commitNow();
        return fragment;
    }
}
