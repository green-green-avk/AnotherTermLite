package green_green_avk.anotherterm.utils;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.text.HtmlCompat;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.QuoteSpan;

import green_green_avk.anotherterm.R;

public final class CustomHtml {
    public static Spanned fromHtml(@NonNull final String source, @NonNull final Context ctx) {
        final Spanned r = HtmlCompat.fromHtml(source, HtmlCompat.FROM_HTML_MODE_LEGACY,
                null, new CompatHtmlTagHandler());
        if (r instanceof Spannable) {
            for (final QuoteSpan qs : r.getSpans(0, source.length(), QuoteSpan.class)) {
                ((Spannable) r).setSpan(
                        new CustomQuoteSpan(ctx.getResources().getColor(R.color.colorAccent)),
                        r.getSpanStart(qs), r.getSpanEnd(qs), r.getSpanFlags(qs));
                ((Spannable) r).removeSpan(qs);
            }
        }
        return r;
    }
}
