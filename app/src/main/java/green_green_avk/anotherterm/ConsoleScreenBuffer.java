package green_green_avk.anotherterm;

import android.graphics.Color;
import android.graphics.Point;
import android.support.annotation.NonNull;
import android.support.v4.math.MathUtils;

import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import green_green_avk.fastarrayutils.FastArrayUtils;

public final class ConsoleScreenBuffer {
    public static final int MAX_BUF_HEIGHT = 100000;
    public static final int MAX_ROW_LEN = 1024;
    public static final int DEF_CHAR_ATTRS = encodeAttrs(new ConsoleScreenCharAttrs());

    protected int mWidth;
    protected int mHeight;
    protected int mBufHeight;

    protected static final class Row {
        public final char[] text = new char[MAX_ROW_LEN];
        public final int[] attrs = new int[MAX_ROW_LEN];

        {
            Arrays.fill(text, ' ');
        }
    }

    protected ArrayList<Row> mRows;

    protected final Point mPos = new Point(0, 0);
    protected final Point mPosSaved = new Point(0, 0);

    protected int mScrollRegionTop = 0;
    protected int mScrollRegionBottom = MAX_BUF_HEIGHT - 1;

    public int defaultAttrs;
    public int currentAttrs;
    public boolean wrap = true;
    public String windowTitle = null;

    private static int encodeColor(int c) {
        return (Color.red(c) << 4) & 0xF00 | Color.green(c) & 0xF0 | (Color.blue(c) >> 4) & 0xF;
    }

    private static int decodeColor(int v) {
        return Color.rgb((v >> 4) & 0xF0, v & 0xF0, (v << 4) & 0xF0);
    }

    public static int encodeAttrs(ConsoleScreenCharAttrs a) {
        return (encodeColor(a.fgColor) << 20)
                | (encodeColor(a.bgColor) << 8)
                | (a.bold ? 1 : 0)
                | (a.italic ? 4 : 0)
                | (a.underline ? 8 : 0)
                | (a.blinking ? 16 : 0)
                | (a.inverse ? 64 : 0);
    }

    public static ConsoleScreenCharAttrs decodeAttrs(int v) {
        ConsoleScreenCharAttrs a = new ConsoleScreenCharAttrs();
        decodeAttrs(v, a);
        return a;
    }

    public static void decodeAttrs(int v, ConsoleScreenCharAttrs a) {
        a.reset();
        a.fgColor = decodeColor(v >> 20);
        a.bgColor = decodeColor(v >> 8);
        a.bold = (v & 1) != 0;
        a.italic = (v & 4) != 0;
        a.underline = (v & 8) != 0;
        a.blinking = (v & 16) != 0;
        a.inverse = (v & 64) != 0;
    }

    private int toBufY(int y) {
        return Math.min(mRows.size(), mHeight) - y - 1;
    }

    private int fromBufY(int by) {
        return toBufY(by);
    }

    public ConsoleScreenBuffer(int w, int h, int bh) {
        this(w, h, bh, DEF_CHAR_ATTRS);
    }

    public ConsoleScreenBuffer(int w, int h, int bh, ConsoleScreenCharAttrs da) {
        this(w, h, bh, encodeAttrs(da));
    }

    public ConsoleScreenBuffer(int w, int h, int bh, int da) {
        if (w < 1 || w > MAX_ROW_LEN || h < 1 || h > bh || bh > MAX_BUF_HEIGHT)
            throw new IllegalArgumentException();
        mWidth = w;
        mHeight = h;
        mBufHeight = bh;
        defaultAttrs = da;
        currentAttrs = da;
        clear();
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getMaxBufferHeight() {
        return mBufHeight;
    }

    public int getBufferHeight() {
        return mRows.size();
    }

    public int getScrollableHeight() {
        return MathUtils.clamp(mRows.size() - mHeight, 0, mBufHeight);
    }

    public int limitX(int v) {
        return MathUtils.clamp(v, 0, mWidth - 1);
    }

    public int limitY(int v) {
        return MathUtils.clamp(v, 0, mHeight - 1);
    }

    public void clear() {
        mRows = new ArrayList<>(mBufHeight);
    }

    public void resize(int w, int h) {
        resize(w, h, mBufHeight);
    }

    public void resize(int w, int h, int bh) {
        final int by = toBufY(mPos.y);
        final int bySaved = toBufY(mPosSaved.y);
        if (w < 1 || w > MAX_ROW_LEN || h < 1 || h > bh || bh > MAX_BUF_HEIGHT) return;
        mWidth = w;
        mHeight = h;
        mBufHeight = bh;
        while (mRows.size() > mBufHeight) {
            mRows.remove(mBufHeight);
        }
        mPos.y = MathUtils.clamp(fromBufY(by), 0, mHeight - 1);
        mPosSaved.y = MathUtils.clamp(fromBufY(bySaved), 0, mHeight - 1);
    }

    public CharSequence getChars(int x, int y, int len) {
        final int by = toBufY(y);
        if (x < 0 || x >= mWidth || by < 0 || by >= mRows.size()) {
            return null;
        }
        final Row row = mRows.get(by);
        return CharBuffer.wrap(row.text, x, len);
    }

    public CharSequence getChars(int x, int y) {
        final int by = toBufY(y);
        if (x < 0 || x >= mWidth || by < 0 || by >= mRows.size()) {
            return null;
        }
        final Row row = mRows.get(by);
        return CharBuffer.wrap(row.text, x, FastArrayUtils.getEqualElementsLength(row.attrs, x, mWidth));
    }

    public char getChar(int x, int y) {
        final int by = toBufY(y);
        if (x < 0 || x >= mWidth || by < 0 || by >= mRows.size()) {
            return ' ';
        }
        final char[] text = mRows.get(by).text;
        if (text == null) return ' ';
        return text[x];
    }

    public ConsoleScreenCharAttrs getAttrs(int x, int y) {
        return decodeAttrs(getAttrsN(x, y));
    }

    public void getAttrs(int x, int y, ConsoleScreenCharAttrs a) {
        decodeAttrs(getAttrsN(x, y), a);
    }

    public int getAttrsN(int x, int y) {
        final int by = toBufY(y);
        if (x < 0 || x >= mWidth || by < 0 || by >= mRows.size()) {
            return defaultAttrs;
        }
        return mRows.get(by).attrs[x];
    }

    public int getPosX() {
        return mPos.x % mWidth;
    }

    public int getPosY() {
        return limitY(mPos.y + mPos.x / mWidth);
    }

    public void setPosX(int x) {
        mPos.x = limitX(x);
    }

    public void setPosY(int y) {
        mPos.y = limitY(y);
    }

    public void setPos(int x, int y) {
        mPos.x = limitX(x);
        mPos.y = limitY(y);
    }

    public void movePosX(int x) {
        mPos.x = limitX(mPos.x + x);
    }

    public void movePosY(int y) {
        mPos.y = limitY(mPos.y + y);
    }

    public void movePos(int x, int y) {
        mPos.x = limitX(mPos.x + x);
        mPos.y = limitY(mPos.y + y);
    }

    public void moveScrollPosY(int y) {
        y += mPos.y;
        if (y < 0) y = 0;
        else if (y >= mHeight) {
            scroll(y - Math.min(mRows.size(), mHeight) + 1);
            y = mHeight - 1;
        }
        mPos.y = y;
    }

    public void savePos() {
        mPosSaved.set(mPos.x, mPos.y);
    }

    public void restorePos() {
        mPos.set(mPosSaved.x, mPosSaved.y);
    }

    public void setPos(@NonNull final ConsoleScreenBuffer csb) {
        mPos.set(csb.mPos.x, csb.mPos.y);
        mPosSaved.set(csb.mPosSaved.x, csb.mPosSaved.y);
    }

    public void setScrollRegion(int top, int bottom) {
        this.mScrollRegionTop = top;
        this.mScrollRegionBottom = bottom;
    }

    public void setDefaultAttrs(ConsoleScreenCharAttrs da) {
        defaultAttrs = encodeAttrs(da);
    }

    public void getCurrentAttrs(ConsoleScreenCharAttrs ca) {
        decodeAttrs(currentAttrs, ca);
    }

    public void setCurrentAttrs(ConsoleScreenCharAttrs ca) {
        currentAttrs = encodeAttrs(ca);
    }

    public void eraseAll() {
        scroll(mHeight);
        setPos(0, 0);
    }

    public void eraseLines(int from, int to) {
        int y2 = toBufY(to);
        if (y2 < 0) {
            scroll(-y2);
            y2 = 0;
        }
        final int y1 = toBufY(from);
        for (int i = y1; i > y2; --i) {
            Row row = mRows.get(i);
            Arrays.fill(row.attrs, currentAttrs);
            Arrays.fill(row.text, ' ');
        }
    }

    public void eraseAbove() {
        eraseLines(0, mPos.y);
    }

    public void eraseBelow() {
        eraseLines(mPos.y, mHeight);
    }

    public void eraseLine(int from, int to, int y) {
        int by = toBufY(y);
        if (by < 0) {
            scroll(-by);
            by = 0;
        }
        Row row = mRows.get(by);
        Arrays.fill(row.attrs, from, to, currentAttrs);
        Arrays.fill(row.text, from, to, ' ');
    }

    public void eraseLineAll() {
        eraseLine(0, mWidth, mPos.y);
    }

    public void eraseLineLeft() {
        eraseLine(0, mPos.x, mPos.y);
    }

    public void eraseLineRight() {
        eraseLine(getPosX(), mWidth, mPos.y);
    }

    public void insertChars(int n) {
        insertChars(mPos.x, mPos.y, n);
    }

    public void insertChars(int x, int y, int n) {
        final int by = arrangeSetPos(x, y);
        if (by < 0) return;
        if (n > mWidth - x) n = mWidth - x;
        final Row row = mRows.get(by);
        System.arraycopy(row.text, x, row.text, x + n, mWidth - x - n);
        Arrays.fill(row.text, x, x + n, ' ');
        System.arraycopy(row.attrs, x, row.attrs, x + n, mWidth - x - n);
        Arrays.fill(row.attrs, x, x + n, currentAttrs);
    }

    public void deleteChars(int n) {
        deleteChars(mPos.x, mPos.y, n);
    }

    public void deleteChars(int x, int y, int n) {
        final int by = arrangeSetPos(x, y);
        if (by < 0) return;
        if (n > mWidth - x) n = mWidth - x;
        final Row row = mRows.get(by);
        System.arraycopy(row.text, x + n, row.text, x, mWidth - x - n);
        Arrays.fill(row.text, mWidth - n, mWidth, ' ');
        System.arraycopy(row.attrs, x + n, row.attrs, x, mWidth - x - n);
        Arrays.fill(row.attrs, mWidth - n, mWidth, currentAttrs);
    }

    public int setChars(String s) {
        return setChars(mPos.x, mPos.y, s, mPos);
    }

    public int setChars(char[] s) {
        return setChars(mPos.x, mPos.y, s, mPos);
    }

    public int setChars(int x, int y, String s, Point endPos) {
        return setChars(x, y, s.toCharArray(), endPos);
    }

    public int setChars(int x, int y, char[] s, Point endPos) {
        y += x / mWidth;
        x %= mWidth;
        int by = arrangeSetPos(x, y);
        if (by < 0) return 0;
        Row row = mRows.get(by);
        int end = Math.min(s.length + x, mWidth);
        int len = end - x;
        System.arraycopy(s, 0, row.text, x, len);
        Arrays.fill(row.attrs, x, end, currentAttrs);
        if (wrap) {
            while (s.length > len) {
                if (by == 0) scroll(1);
                else --by;
                ++y;
                row = mRows.get(by);
                end = Math.min(s.length - len, mWidth);
                System.arraycopy(s, len, row.text, 0, end);
                Arrays.fill(row.attrs, 0, end, currentAttrs);
                len += end;
            }
            x = end;
            if (y >= mHeight) {
                y = mHeight - 1;
                if (x >= mWidth) {
                    scroll(1);
                    --y;
                }
            }
        } else {
            x = end;
            if (x >= mWidth) x = mWidth - 1;
        }
        endPos.x = x;
        endPos.y = y;
        return len;
    }

    public void setChar(int x, int y, char c) {
        setChar(x, y, c, currentAttrs);
    }

    public void setChar(int x, int y, char c, ConsoleScreenCharAttrs a) {
        setChar(x, y, c, encodeAttrs(a));
    }

    public void setChar(int x, int y, char c, int a) {
        y += x / mWidth;
        x %= mWidth;
        final int by = arrangeSetPos(x, y);
        if (by < 0) return;
        mRows.get(by).text[x] = c;
        mRows.get(by).attrs[x] = a;
    }

    private int arrangeSetPos(int x, int y) {
        if (x < 0 || x >= mWidth) {
            return -1;
        }
        return arrangeSetPosY(y);
    }

    private int arrangeSetPosY(int y) {
        int by = toBufY(y);
        if (by >= mRows.size()) {
            return -1;
        }
        if (by < 0) {
            scroll(-by);
            by = 0;
        }
        return by;
    }

    public void scroll(int v) {
        _scroll(v, mBufHeight - 1, 0);
    }

    public void scroll(int v, int top, int bottom) {
        top = MathUtils.clamp(toBufY(top), 0, mBufHeight - 1);
        bottom = MathUtils.clamp(toBufY(bottom), 0, mBufHeight - 1);
        _scroll(v, top, bottom);
    }

    public void scrollRegion(int v) {
        final int top = toBufY(
                MathUtils.clamp(mScrollRegionTop, 0, mHeight - 1)
        );
        final int bottom = toBufY(
                MathUtils.clamp(mScrollRegionBottom, 0, mHeight - 1)
        );
        _scroll(v, top, bottom);
    }

    protected void _scroll(int v, int top, int bottom) {
        if (v < 0) {
            top ^= bottom;
            bottom ^= top;
            top ^= bottom;
            v = -v;
        }
        while (v > 0) {
            Row row;
            if (mRows.size() > top && top >= 0) {
                row = mRows.remove(top);
                Arrays.fill(row.text, ' ');
            } else {
                row = new Row();
            }
            Arrays.fill(row.attrs, (mRows.size() < mHeight) ? defaultAttrs : currentAttrs);
            mRows.add((bottom < 0) ? 0 : bottom, row);
            if (top < 0) {
                ++top;
                ++bottom;
            }
            --v;
        }
    }
}
