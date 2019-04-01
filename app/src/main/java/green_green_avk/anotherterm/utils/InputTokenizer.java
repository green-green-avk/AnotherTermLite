package green_green_avk.anotherterm.utils;

import android.support.annotation.NonNull;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InputTokenizer implements Iterator<InputTokenizer.Token>, Iterable<InputTokenizer.Token> {
    protected static final int SEQ_MAXLEN = 256;
    public static final Pattern CTL_PAT = Pattern.compile("[\\x00-\\x1F\\x7F]");
    public static final Pattern CSI_END_PAT = Pattern.compile("[@A-Z\\\\^_`a-z{|}~]");
    public static final Pattern OSC_END_PAT = Pattern.compile("\\a|\\e\\\\");

    public static final class Token {
        public enum Type {
            TEXT, CTL, ESC, CSI, OSC
        }

        public final Type type;
        public final String value;

        public Token(Type t, String v) {
            type = t;
            value = v;
        }
    }

    protected String mStr = "";
    protected int mPos = 0;
    protected String mToken = null;
    protected Token.Type mType = Token.Type.TEXT;
    protected Boolean mGotNext = false;

    protected void getSequence(int pos, Pattern pat) {
        Matcher m = pat.matcher(mStr);
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

    protected void getNext() {
        if (mStr.length() == mPos) {
            mToken = null;
            return;
        }
        Matcher m;
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
        } catch (IndexOutOfBoundsException e) {
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

    public void tokenize(char[] buf, int start, int len) {
        tokenize(new String(buf, start, len));
    }

    public void tokenize(CharSequence v) {
        tokenize(v.toString());
    }

    public void tokenize(String v) {
        if (mStr.length() == mPos) {
            mStr = v;
        } else {
            mStr = mStr.substring(mPos) + v;
        }
        mPos = 0;
        mGotNext = false;
    }
}
