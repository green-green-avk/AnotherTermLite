package green_green_avk.anotherterm.utils;

import android.support.annotation.NonNull;

import java.io.IOException;
import java.nio.CharBuffer;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InputTokenizer implements Iterator<InputTokenizer.Token>, Iterable<InputTokenizer.Token> {
    private static final int SEQ_MAXLEN = 256;
    private static final Pattern CSI_END_PAT = Pattern.compile("[@A-Z\\\\^_`a-z{|}~]");
    private static final Pattern OSC_END_PAT = Pattern.compile("\\a|\\e\\\\");

    public static final class Token {
        public enum Type {
            TEXT, CTL, ESC, CSI, OSC
        }

        public Type type;
        public CharBuffer value;
    }

    private final Token ownToken = new Token();

    private final CharBuffer mBuf = (CharBuffer) CharBuffer.allocate(8192).flip();
    private final char[] mBufArr = mBuf.array();
    private int mPos = 0;
    private CharBuffer mToken = null;
    private Token.Type mType = Token.Type.TEXT;
    private Boolean mGotNext = false;

    private void getSequence(int pos, @NonNull final Pattern pat) {
        final Matcher m = pat.matcher(mBuf);
        m.region(pos, mBuf.limit());
        if (!m.find()) {
            if ((mBuf.limit() - pos) > SEQ_MAXLEN) {
                mType = Token.Type.TEXT;
                mToken = mBuf.subSequence(mPos, pos);
                mPos = pos;
                return;
            }
            mToken = null;
            return;
        }
        pos = m.end();
        mToken = mBuf.subSequence(mPos, pos);
        mPos = pos;
    }

    private void getNext() {
        if (mBuf.limit() <= mPos) {
            mToken = null;
            return;
        }
        int pos = mPos;
        for (; mBufArr[pos] > 0x1F && mBufArr[pos] != 0x7F; ++pos) {
            if (pos >= mBuf.limit()) {
                mType = Token.Type.TEXT;
                mToken = mBuf.subSequence(mPos, mBuf.limit());
                mPos = mBuf.limit();
                return;
            }
        }
        if (pos > mPos) {
            mType = Token.Type.TEXT;
            mToken = mBuf.subSequence(mPos, pos);
            mPos = pos;
            return;
        }
        if (mBufArr[pos] != '\u001B') {
            mType = Token.Type.CTL;
            mToken = mBuf.subSequence(pos, pos + 1);
            mPos = pos + 1;
            return;
        }
        mType = Token.Type.ESC;
        ++pos;
        if (mBuf.limit() <= pos) {
            mToken = null;
            return;
        }
        switch (mBufArr[pos]) {
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
                if (mBuf.limit() < pos) {
                    mToken = null;
                    return;
                }
                mToken = mBuf.subSequence(mPos, pos);
                mPos = pos;
                return;
            default:
                ++pos;
                mToken = mBuf.subSequence(mPos, pos);
                mPos = pos;
                return;
        }
    }

    @NonNull
    @Override
    public Token next() {
        if (!mGotNext) getNext();
        if (mToken == null) throw new NoSuchElementException();
        mGotNext = false;
        ownToken.type = mType;
        ownToken.value = mToken;
        return ownToken;
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

    public void tokenize(@NonNull final Readable v) throws IOException {
        mBuf.position(mPos);
        mBuf.compact();
        try {
            v.read(mBuf);
        } finally {
            mBuf.flip();
            mPos = 0;
            mGotNext = false;
        }
    }
}
