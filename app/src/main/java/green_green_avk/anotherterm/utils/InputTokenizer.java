package green_green_avk.anotherterm.utils;

import android.support.annotation.NonNull;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InputTokenizer implements Iterator<InputTokenizer.Token>, Iterable<InputTokenizer.Token> {
    private static final int SEQ_MAXLEN = 256;
    private static final Pattern CTL_PAT = Pattern.compile("[\\x00-\\x1F\\x7F]");
    private static final Pattern CSI_END_PAT = Pattern.compile("[@A-Z\\\\^_`a-z{|}~]");
    private static final Pattern OSC_END_PAT = Pattern.compile("\\a|\\e\\\\");

    public static final class Token {
        public enum Type {
            TEXT, CTL, ESC, CSI, OSC
        }

        public final Type type;
        public final String value;

        public Token(@NonNull final Type t, @NonNull final String v) {
            type = t;
            value = v;
        }
    }

    private String mStr = "";
    private int mPos = 0;
    private String mToken = null;
    private Token.Type mType = Token.Type.TEXT;
    private Boolean mGotNext = false;

    private void getSequence(int pos, final Pattern pat) {
        final Matcher m = pat.matcher(mStr);
        m.region(pos, mStr.length());
        if (!m.find()) {
            if ((mStr.length() - pos) > SEQ_MAXLEN) {
                mType = Token.Type.TEXT;
                mToken = mStr.substring(mPos, pos);
                mPos = pos;
                return;
            }
            mToken = null;
            return;
        }
        pos = m.end();
        mToken = mStr.substring(mPos, pos);
        mPos = pos;
    }

    private void getNext() {
        if (mStr.length() == mPos) {
            mToken = null;
            return;
        }
        final Matcher m;
        int pos;
        m = CTL_PAT.matcher(mStr);
        m.region(mPos, mStr.length());
        if (!m.find()) {
            mType = Token.Type.TEXT;
            mToken = mStr.substring(mPos);
            mPos = mStr.length();
            return;
        }
        pos = m.start();
        if (pos > mPos) {
            mType = Token.Type.TEXT;
            mToken = mStr.substring(mPos, pos);
            mPos = pos;
            return;
        }
        if (mStr.charAt(pos) != '\u001B') {
            mType = Token.Type.CTL;
            mToken = mStr.substring(pos, pos + 1);
            mPos = pos + 1;
            return;
        }
        mType = Token.Type.ESC;
        ++pos;
        try {
            switch (mStr.charAt(pos)) {
                case '[':
                    mType = Token.Type.CSI;
                    getSequence(pos + 1, CSI_END_PAT);
                    return;
                case ']':
                    mType = Token.Type.OSC;
                    getSequence(pos + 1, OSC_END_PAT);
                    return;
                case ' ':
                case '#':
                case '%':
                case '(':
                case ')':
                case '*':
                case '+':
                case '-':
                case '.':
                case '/':
                    pos += 2;
                    mToken = mStr.substring(mPos, pos);
                    mPos = pos;
                    return;
                default:
                    ++pos;
                    mToken = mStr.substring(mPos, pos);
                    mPos = pos;
                    return;
            }
        } catch (final IndexOutOfBoundsException e) {
            mToken = null;
            return;
        }
    }

    @Override
    public Token next() {
        if (!mGotNext) getNext();
        if (mToken == null) throw new NoSuchElementException();
        mGotNext = false;
        return new Token(mType, mToken);
    }

    @Override
    public boolean hasNext() {
        if (!mGotNext) getNext();
        mGotNext = true;
        return mToken != null;
    }

    @NonNull
    @Override
    public Iterator<Token> iterator() {
        return this;
    }

    public void tokenize(@NonNull final char[] buf, final int start, final int len) {
        tokenize(new String(buf, start, len));
    }

    public void tokenize(@NonNull final CharSequence v) {
        tokenize(v.toString());
    }

    public void tokenize(@NonNull final String v) {
        if (mStr.length() == mPos) {
            mStr = v;
        } else {
            mStr = mStr.substring(mPos) + v;
        }
        mPos = 0;
        mGotNext = false;
    }
}
