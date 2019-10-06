package green_green_avk.anotherterm.utils;

import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import org.xml.sax.XMLReader;

import java.util.Stack;

// TODO: correct
// https://stackoverflow.com/questions/44259072/leadingmarginspan-not-indented-correctly-for-multilevel-nested-bullets
// It seems actual for API < 24 only...
// ...and sorry guys, it seems, devices with API >= 24 also have some problems with lists...
// Compat tags lic, ulc, olc are added.
public final class CompatHtmlTagHandler implements Html.TagHandler {

    private final Stack<Object> lists = new Stack<>();
    private final Stack<Object> spans = new Stack<>();

    private void startNullSpan() {
        spans.push(null);
    }

    private void startSpan(@NonNull final Editable output, @NonNull final Object span, final int pos) {
        output.setSpan(span, pos, pos, Spanned.SPAN_MARK_MARK);
        spans.push(span);
    }

    private Object endSpan(@NonNull final Editable output, final int pos) {
        final Object span = spans.pop();
        if (span != null) {
            final int start = output.getSpanStart(span);
            if (pos > start) output.setSpan(span, start, pos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            else output.removeSpan(span);
        }
        return span;
    }

    private void startParagraph(@NonNull final Editable output) {
        if (output.length() > 0 && output.charAt(output.length() - 1) != '\n')
            output.append("\n\n");
    }

    @Override
    public void handleTag(final boolean opening, String tag,
                          final Editable output, final XMLReader xmlReader) {
        tag = tag.toLowerCase();
        if (opening) {
            switch (tag) {
                case "code":
                    startSpan(output, new TypefaceSpan("monospace"), output.length());
                    startSpan(output, new BackgroundColorSpan(0x40808080), output.length());
                    break;
                case "lic":
                case "li": {
                    startParagraph(output);
                    Object list = null;
                    if (!lists.empty() && (list = lists.peek()) instanceof Integer) {
                        output.append(list.toString()).append(") ");
                        lists.pop();
                        lists.push((int) list + 1);
                        startNullSpan();
                    } else if (list instanceof Character && list.equals('*')) {
                        startSpan(output, new BulletSpan(15), output.length());
                    } else {
                        startNullSpan();
                    }
                    break;
                }
                case "dt":
                    startParagraph(output);
                    startSpan(output, new StyleSpan(Typeface.BOLD), output.length());
                    break;
                case "dd":
                    startParagraph(output);
                    startSpan(output, new LeadingMarginSpan.Standard(15), output.length());
                    break;
                case "dl": {
                    startParagraph(output);
                    startSpan(output, new LeadingMarginSpan.Standard(15), output.length());
                    lists.push(null);
                    break;
                }
                case "ulc":
                case "ul": {
                    startParagraph(output);
                    startSpan(output, new LeadingMarginSpan.Standard(15), output.length());
                    lists.push('*');
                    break;
                }
                case "olc":
                case "ol": {
                    startParagraph(output);
                    startSpan(output, new LeadingMarginSpan.Standard(15), output.length());
                    lists.push(1);
                    break;
                }
                case "clipboard":
                    startSpan(output, new ClipboardSpan(), output.length());
                    break;
            }
        } else {
            switch (tag) {
                case "code":
                    endSpan(output, output.length());
                    endSpan(output, output.length());
                    break;
                case "lic":
                case "li":
                case "dt":
                case "dd":
                    startParagraph(output);
                    endSpan(output, output.length());
                    break;
                case "dl":
                case "ulc":
                case "olc":
                case "ul":
                case "ol": {
                    startParagraph(output);
                    endSpan(output, output.length());
                    lists.pop();
                    break;
                }
                case "clipboard": {
                    final ClipboardSpan span = (ClipboardSpan) endSpan(output, output.length());
                    final String content = output.subSequence(output.getSpanStart(span),
                            output.getSpanEnd(span)).toString();
                    span.setContent(content);
                    output.append('\u2398');
                    break;
                }
            }
        }
    }
}
