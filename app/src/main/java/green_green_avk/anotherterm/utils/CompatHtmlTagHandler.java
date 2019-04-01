package green_green_avk.anotherterm.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.text.style.ClickableSpan;
import android.text.style.LeadingMarginSpan;
import android.view.View;
import android.widget.Toast;

import org.xml.sax.XMLReader;

import java.util.Stack;

import green_green_avk.anotherterm.R;

// TODO: correct
// https://stackoverflow.com/questions/44259072/leadingmarginspan-not-indented-correctly-for-multilevel-nested-bullets
// It seems actual for API < 24 only.
public final class CompatHtmlTagHandler implements Html.TagHandler {
    private final Stack<Object> lists = new Stack<>();
    private final Stack<LeadingMarginSpan> lis = new Stack<>();
    private int cbtStart = 0;

    @Override
    public void handleTag(final boolean opening, String tag,
                          final Editable output, final XMLReader xmlReader) {
        tag = tag.toLowerCase();
        if (opening) {
            switch (tag) {
                case "li": {
                    if (output.length() > 0 && output.charAt(output.length() - 1) != '\n') {
                        output.append("\n\n");
                    }
                    final int pos = output.length();
                    final LeadingMarginSpan span = new LeadingMarginSpan.Standard(15);
                    output.setSpan(span, pos, pos, Spanned.SPAN_MARK_MARK);
                    lis.push(span);
                    break;
                }
                case "ul":
                    lists.push(null);
                    break;
                case "ol":
                    lists.push(1);
                    break;
                case "clipboard":
                    cbtStart = output.length();
                    break;
            }
        } else {
            switch (tag) {
                case "li": {
                    if (lis.empty()) break;
                    if (output.length() > 0 && output.charAt(output.length() - 1) != '\n') {
                        output.append("\n\n");
                    }
                    final LeadingMarginSpan span = lis.pop();
                    final int start = output.getSpanStart(span);
                    final int end = output.length();
                    output.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    Object list;
                    if (!lists.empty() && (list = lists.peek()) instanceof Integer) {
                        output.insert(start, list.toString() + ") ");
                        lists.pop();
                        lists.push((int) list + 1);
                    } else {
                        output.setSpan(new BulletSpan(15), start, end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    break;
                }
                case "ul":
                case "ol": {
                    if (lists.empty()) break;
                    final Object list = lists.pop();
                    break;
                }
                case "clipboard": {
                    final String content = output.subSequence(cbtStart, output.length()).toString();
                    output.setSpan(new ClickableSpan() {
                        @Override
                        public void onClick(@NonNull final View widget) {
                            final ClipboardManager clipboard =
                                    (ClipboardManager) widget.getContext()
                                            .getSystemService(Context.CLIPBOARD_SERVICE);
                            if (clipboard == null) return;
                            clipboard.setPrimaryClip(ClipData.newPlainText(
                                    null, content));
                            Toast.makeText(widget.getContext(), R.string.msg_copied_to_clipboard,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }, cbtStart, output.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                }
            }
        }
    }
}
