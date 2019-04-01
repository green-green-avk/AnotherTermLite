package green_green_avk.anotherterm;

import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Map;

import green_green_avk.anotherterm.ui.HtmlTextView;

public final class InfoActivity extends AppCompatActivity {

    private static final Map<String, Integer> res = new HashMap<>();

    static {
        res.put("keymap_escapes", R.string.desc_keymap_escapes);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        final HtmlTextView v = findViewById(R.id.desc);
        final Uri uri = getIntent().getData();
        if (uri != null) {
            if ("infores".equals(uri.getScheme())) {
                final Integer id = res.get(uri.getSchemeSpecificPart());
                if (id != null)
                    v.setHtmlText(getString(id));
            }
        }
    }
}
