package green_green_avk.anotherterm.utils;

import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.text.style.LeadingMarginSpan;

import org.xml.sax.XMLReader;

import java.util.Stack;

// TODO: correct
// https://stackoverflow.com/questions/44259072/leadingmarginspan-not-indented-correctly-for-multilevel-nested-bullets
// It seems actual for API < 24 only...
// ...and sorry guys, it seems, devices with API >= 24 also have some problems with lists...
// Compat tags lic, ulc, olc are added.
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
                case "lic":
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
                case "ulc":
                case "ul":
                    lists.push(null);
                    break;
                case "olc":
                case "ol":
                    lists.push(1);
                    break;
                case "clipboard":
                    cbtStart = output.length();
                    break;
            }
        } else {
            switch (tag) {
                case "lic":
                case "li": {
                    if (lis.empty()) break;
                    if (output.length() > 0 && output.charAt(output.length() - 1) != '\n') {
                        output.append("\n\n");
                    }
                    final LeadingMarginSpan span = lis.pop();
                    final int start = output.getSpanStart(span);
                    final int end;
                    final Object list;
                    if (!lists.empty() && (list = lists.peek()) instanceof Integer) {
                        output.insert(start, list.toString() + ") ");
                        lists.pop();
                        lists.push((int) list + 1);
                        end = output.length();
                    } else {
                        end = output.length();
                        output.setSpan(new BulletSpan(lis.empty() ? 15 : 0), start, end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    output.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                }
                case "ulc":
                case "olc":
                case "ul":
                case "ol": {
                    if (lists.empty()) break;
                    final Object list = lists.pop();
                    break;
                }
                case "clipboard": {
                    final String content = output.subSequence(cbtStart, output.length()).toString();
                    output.setSpan(new ClipboardSpan(content),
                            cbtStart, output.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    output.append('\u2398');
                    break;
                }
            }
        }
    }
}
