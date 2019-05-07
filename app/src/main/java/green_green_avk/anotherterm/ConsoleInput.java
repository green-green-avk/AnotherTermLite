package green_green_avk.anotherterm;

import android.graphics.Color;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseBooleanArray;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;

import green_green_avk.anotherterm.backends.EventBasedBackendModuleWrapper;
import green_green_avk.anotherterm.utils.BinderInputStream;
import green_green_avk.anotherterm.utils.BytesSink;
import green_green_avk.anotherterm.utils.EscCsi;
import green_green_avk.anotherterm.utils.EscOsc;
import green_green_avk.anotherterm.utils.InputTokenizer;
import green_green_avk.anotherterm.utils.Misc;

public final class ConsoleInput implements BytesSink {
    private static final boolean LOG_UNKNOWN_ESC = false; // BuildConfig.DEBUG

    public ConsoleOutput consoleOutput = null;
    public EventBasedBackendModuleWrapper backendModule = null;
    public ConsoleScreenBuffer currScrBuf;
    private final ConsoleScreenBuffer mainScrBuf;
    private final ConsoleScreenBuffer altScrBuf;
    private final ConsoleScreenCharAttrs mCurrAttrs = new ConsoleScreenCharAttrs();
    private final BinderInputStream mInputBuf = new BinderInputStream(1024);
    private InputStreamReader mStrConv;
    private final InputTokenizer mInputTokenizer = new InputTokenizer();

    public boolean insertMode = false;
    public boolean cursorVisibility = true; // DECTECM
    public int defaultTabLength = 8;
    public final TreeSet<Integer> tabPositions = new TreeSet<>();

    private void sendBack(final String v) {
        if (consoleOutput != null)
            consoleOutput.feed(v);
    }

    public ConsoleInput() {
        mainScrBuf = new ConsoleScreenBuffer(80, 24, 1000);
        altScrBuf = new ConsoleScreenBuffer(80, 24, 24);
        currScrBuf = mainScrBuf;
        setCharset(Charset.defaultCharset());
    }

    public void setCharset(final Charset ch) {
        mStrConv = new InputStreamReader(mInputBuf, ch);
    }

    public interface OnInvalidateSink {
        void onInvalidateSink(Rect rect);
    }

    private Set<OnInvalidateSink> mOnInvalidateSink = Collections.newSetFromMap(new WeakHashMap<OnInvalidateSink, Boolean>());

    public void addOnInvalidateSink(@NonNull final OnInvalidateSink h) {
        mOnInvalidateSink.add(h);
    }

    public void removeOnInvalidateSink(@NonNull final OnInvalidateSink h) {
        mOnInvalidateSink.remove(h);
    }

    public void invalidateSink() {
        for (final OnInvalidateSink h : mOnInvalidateSink)
            h.onInvalidateSink(null);
    }

    public void reset() {
        mCurrAttrs.reset();
        currScrBuf.clear();
        currScrBuf.setDefaultAttrs(mCurrAttrs);
        currScrBuf.setCurrentAttrs(mCurrAttrs);
    }

    public void resize(final int w, final int h) {
        mainScrBuf.resize(w, h);
        altScrBuf.resize(w, h, h);
        if (backendModule != null)
            backendModule.resize(w, h, w * 16, h * 16);
    }

    public boolean isAltBuf() {
        return currScrBuf == altScrBuf;
    }

    private void cr() {
        currScrBuf.setPosX(0);
    }

    private void lf() {
        currScrBuf.moveScrollPosY(1);
    }

    private void ri() {
        currScrBuf.setCurrentAttrs(mCurrAttrs);
        currScrBuf.scroll(-1);
    }

    private void bs() {
        currScrBuf.movePosX(-1);
    }

    private void tab(int v) {
        if (v == 0) return;
        int pos = currScrBuf.getPosX();
        final int d;
        final NavigableSet<Integer> tt;
        if (v > 0) {
            d = defaultTabLength;
            tt = tabPositions.tailSet(pos, false);
        } else {
            d = -defaultTabLength;
            tt = tabPositions.headSet(pos, false).descendingSet();
            v = -v;
        }
        // Standard Java and Android libraries does not have a proper algorithm for it...
        // As long as Apache Commons...
        // TODO: TreeList with random access by order index should be implemented.
        if (v < tt.size()) {
            final Iterator<Integer> i = tt.iterator();
            while (v > 0) {
                pos = i.next();
                --v;
            }
        } else {
            if (tt.size() > 0) {
                pos = tt.last();
                v -= tt.size();
            }
            if (v != 0) {
                pos += d * v;
                pos -= pos % d;
            }
        }
        currScrBuf.setPosX(pos);
    }

    private void tabClear(final int pos) {
        if (pos < 0) {
            tabPositions.clear();
            return;
        }
        tabPositions.remove(pos);
    }

    private void tabSet(final int pos) {
        tabPositions.add(pos);
    }

    @Override
    public void feed(@NonNull final ByteBuffer v) {
        if (v.remaining() == 0) return;
        mInputBuf.bind(v);
        try {
            while (mStrConv.ready()) {
                mInputTokenizer.tokenize(mStrConv);
                for (final InputTokenizer.Token t : mInputTokenizer) {
//                    Log.v("CtrlSeq/Note", t.type + ": " + t.value.toString());
                    switch (t.type) {
                        case OSC: {
                            EscOsc osc = new EscOsc(t.value);
                            if (osc.args.length == 2) {
                                switch (osc.getIntArg(0, -1)) {
                                    case 0:
                                    case 2:
                                        currScrBuf.windowTitle = osc.args[1];
                                        break;
                                }
                                break;
                            }
                            if (LOG_UNKNOWN_ESC)
                                Log.w("CtrlSeq", "OSC: " + t.value.toString());
                            break;
                        }
                        case CSI:
                            parseCsi(new EscCsi(t.value));
                            break;
                        case ESC:
                            switch (t.value.charAt(1)) {
                                case 'H': // HTS
                                    tabSet(currScrBuf.getPosX());
                                    break;
                                case 'M': // RI (Reverse linefeed)
                                    ri();
                                    break;
                                case 'c':
                                    reset();
                                    break;
                                case '7':
                                    currScrBuf.savePos();
                                    break;
                                case '8':
                                    currScrBuf.restorePos();
                                    break;
                                case '>':
                                    if (consoleOutput != null)
                                        consoleOutput.appNumKeys = false;
                                    break;
                                case '=':
                                    if (consoleOutput != null)
                                        consoleOutput.appNumKeys = true;
                                    break;
                                case '(':
                                case ')':
                                    mCurrAttrs.reset();
                                    break;
                                default:
                                    if (LOG_UNKNOWN_ESC)
                                        Log.w("CtrlSeq", "ESC: " +
                                                t.value.subSequence(1, t.value.remaining()).toString());
                            }
                            break;
                        case CTL:
                            switch (t.value.charAt(0)) {
                                case '\r':
                                    cr();
                                    break;
                                case '\n':
                                case '\u000B': // VT
                                    currScrBuf.setCurrentAttrs(mCurrAttrs);
                                    lf();
                                    break;
                                case '\t':
                                    tab(1);
                                    break;
                                case '\b':
                                    bs();
                                    break;
                                case '\u0007':
                                    // TODO: Bell
                                    break;
                                default:
                                    if (LOG_UNKNOWN_ESC)
                                        Log.w("CtrlSeq", "CTL: " + (int) t.value.charAt(0));
                            }
                            break;
                        case TEXT:
                            currScrBuf.setCurrentAttrs(mCurrAttrs);
                            if (insertMode) currScrBuf.insertChars(t.value.length());
                            currScrBuf.setChars(t.value);
                            break;
                    }
                }
            }
        } catch (final IOException e) {
            if (BuildConfig.DEBUG)
                Log.e("-", "Strange IO error", e);
        } finally {
            mInputBuf.release();
        }
    }

    private void parseCsi(@NonNull final EscCsi csi) {
        switch (csi.prefix) {
            case 0:
                switch (csi.type) {
                    case 'c':
                        sendBack("\u001B[?6c"); // TODO: VT102 yet...
//                        Log.i("CtrlSeq", "Terminal type request");
                        return;
                    case 'n':
                        if (csi.args.length == 0) break;
                        switch (csi.args[0]) {
                            case "5":
                                sendBack("\u001B[0n"); // Terminal OK
                                return;
                            case "6":
                                sendBack("\u001B[" + (currScrBuf.getPosY() + 1) + ";" + (currScrBuf.getPosX() + 1) + "R");
                                return;
                        }
                        break;
                    case 'A':
                        currScrBuf.movePosY(-csi.getIntArg(0, 1));
                        return;
                    case 'B':
                        currScrBuf.movePosY(csi.getIntArg(0, 1));
                        return;
                    case 'C':
                        currScrBuf.movePosX(csi.getIntArg(0, 1));
                        return;
                    case 'D':
                        currScrBuf.movePosX(-csi.getIntArg(0, 1));
                        return;
                    case 'E':
                        currScrBuf.movePosY(csi.getIntArg(0, 1));
                        cr();
                        return;
                    case 'F':
                        currScrBuf.movePosY(-csi.getIntArg(0, 1));
                        cr();
                        return;
                    case 'G':
                        currScrBuf.setPosX(csi.getIntArg(0, 1) - 1);
                        return;
                    case 'H':
                    case 'f':
                        currScrBuf.setPosY(csi.getIntArg(0, 1) - 1);
                        currScrBuf.setPosX(csi.getIntArg(1, 1) - 1);
                        return;
                    case 'I': // CHT
                        tab(csi.getIntArg(0, 1));
                        return;
                    case 'Z': // CBT
                        tab(-csi.getIntArg(0, 1));
                        return;
                    case 'g': // TBC
                        switch (csi.getIntArg(0, 0)) {
                            case 0:
                                tabClear(currScrBuf.getPosX());
                                return;
                            case 3:
                                tabClear(-1);
                                return;
                        }
                        break;
                    case 'J':
                        currScrBuf.setCurrentAttrs(mCurrAttrs);
                        switch (csi.getIntArg(0, 0)) {
                            case 0:
                                currScrBuf.eraseBelow();
                                return;
                            case 1:
                                currScrBuf.eraseAbove();
                                return;
                            case 2:
                                currScrBuf.eraseAll();
                                return;
                        }
                        break;
                    case 'K':
                        currScrBuf.setCurrentAttrs(mCurrAttrs);
                        switch (csi.getIntArg(0, 0)) {
                            case 0:
                                currScrBuf.eraseLineRight();
                                return;
                            case 1:
                                currScrBuf.eraseLineLeft();
                                return;
                            case 2:
                                currScrBuf.eraseLineAll();
                                return;
                        }
                        break;
                    case 'm': {
                        int i = 0;
                        ConsoleScreenCharAttrs aa = mCurrAttrs;
                        if (csi.args.length == 0) {
                            aa.reset();
                            return;
                        }
                        while (i < csi.args.length) {
                            final int a;
                            try {
                                a = Integer.parseInt(csi.args[i++]);
                            } catch (NumberFormatException e) {
                                continue;
                            }
                            switch (a) {
                                case 0:
                                    aa.reset();
                                    break;
                                case 1:
                                    aa.bold = true;
                                    break;
                                case 3:
                                    aa.italic = true;
                                    break;
                                case 4:
                                    aa.underline = true;
                                    break;
                                case 5:
                                case 6:
                                    aa.blinking = true;
                                    break;
                                case 7:
                                    aa.inverse = true;
                                    break;
                                case 22:
                                    aa.bold = false;
                                    break;
                                case 23:
                                    aa.italic = false;
                                    break;
                                case 24:
                                    aa.underline = false;
                                    break;
                                case 25:
                                    aa.blinking = false;
                                    break;
                                case 27:
                                    aa.inverse = false;
                                    break;
                                case 39:
                                    aa.resetFg();
                                    break;
                                case 49:
                                    aa.resetBg();
                                    break;
                                default:
                                    if ((a >= 30) && (a <= 37)) {
                                        int v = a - 30;
                                        aa.fgColor = Color.rgb(
                                                Misc.bitsAs(v, 1, 255),
                                                Misc.bitsAs(v, 2, 255),
                                                Misc.bitsAs(v, 4, 255)
                                        );
                                        break;
                                    }
                                    if ((a >= 40) && (a <= 47)) {
                                        int v = a - 40;
                                        aa.bgColor = Color.rgb(
                                                Misc.bitsAs(v, 1, 127),
                                                Misc.bitsAs(v, 2, 127),
                                                Misc.bitsAs(v, 4, 127)
                                        );
                                        break;
                                    }
                                    if (LOG_UNKNOWN_ESC)
                                        Log.w("CtrlSeq", "Attr: " + a);
                            }
                        }
                        return;
                    }
                    case 'L':
                        currScrBuf.setCurrentAttrs(mCurrAttrs);
                        currScrBuf.scrollRegion(-csi.getIntArg(0, 1));
                        return;
                    case 'M':
                        currScrBuf.setCurrentAttrs(mCurrAttrs);
                        currScrBuf.scrollRegion(csi.getIntArg(0, 1));
                        return;
                    case '@':
                        currScrBuf.setCurrentAttrs(mCurrAttrs);
                        currScrBuf.insertChars(csi.getIntArg(0, 1));
                        return;
                    case 'P':
                        currScrBuf.setCurrentAttrs(mCurrAttrs);
                        currScrBuf.deleteChars(csi.getIntArg(0, 1));
                        return;
                    case 'h':
                    case 'l': {
                        final boolean value = csi.type == 'h';
                        switch (csi.getIntArg(0, 0)) {
                            case 4:
                                insertMode = value;
                                return;
                        }
                        break;
                    }
                    case 'r':
                        currScrBuf.setPos(0, 0);
                        currScrBuf.setScrollRegion(csi.getIntArg(0, 1) - 1,
                                csi.getIntArg(1, currScrBuf.getHeight()) - 1);
                        return;
                    case 's':
                        currScrBuf.savePos();
                        return;
                    case 'u':
                        currScrBuf.restorePos();
                        return;
                    default:
                }
                break;
            case '?':
                switch (csi.type) {
                    case 'h':
                    case 'l': {
                        final int opt = csi.getIntArg(0, -1);
                        if (opt < 0) break;
                        decPrivateMode.set(opt, csi.type == 'h');
                        return;
                    }
                    case 'r': {
                        final int opt = csi.getIntArg(0, -1);
                        if (opt < 0) break;
                        decPrivateMode.restore(opt);
                        return;
                    }
                    case 's': {
                        final int opt = csi.getIntArg(0, -1);
                        if (opt < 0) break;
                        decPrivateMode.save(opt);
                        return;
                    }
                }
                break;
        }
        if (LOG_UNKNOWN_ESC)
            Log.w("CtrlSeq", "ESC[" + ((csi.prefix == 0) ? "" : csi.prefix) + csi.body + csi.type);
    }

    private class DecPrivateMode {
        private final SparseBooleanArray current = new SparseBooleanArray();
        private final SparseBooleanArray saved = new SparseBooleanArray();

        private void apply(final int opt, final boolean value) {
            switch (opt) {
                case 1:
                    if (consoleOutput != null)
                        consoleOutput.appCursorKeys = value;
                    return;
                case 7:
                    mainScrBuf.wrap = value;
                    altScrBuf.wrap = value;
                    return;
                case 8:
                    if (consoleOutput != null)
                        consoleOutput.keyAutorepeat = value;
                    return;
                case 9:
                    if (consoleOutput != null)
                        consoleOutput.mouseX10 = value;
                    return;
                case 25:
                    cursorVisibility = value;
                    return;
                case 1000:
                    if (consoleOutput != null)
                        consoleOutput.mouseX11 = value;
                    return;
                case 1001:
                    if (consoleOutput != null)
                        consoleOutput.mouseHighlight = value;
                    return;
                case 1002:
                    if (consoleOutput != null)
                        consoleOutput.mouseButtonEvent = value;
                    return;
                case 1003:
                    if (consoleOutput != null)
                        consoleOutput.mouseAnyEvent = value;
                    return;
                case 1005:
                    if (consoleOutput != null)
                        consoleOutput.mouseUTF8 = value;
                    return;
                case 1006:
                    if (consoleOutput != null)
                        consoleOutput.mouseSGR = value;
                    return;
                case 1015:
                    if (consoleOutput != null)
                        consoleOutput.mouseURXVT = value;
                    return;
                case 1047:
                    if (value) {
                        altScrBuf.setPos(currScrBuf);
                        currScrBuf = altScrBuf;
                        currScrBuf.clear();
                    } else {
                        mainScrBuf.setPos(currScrBuf);
                        currScrBuf = mainScrBuf;
                    }
                    currScrBuf.getCurrentAttrs(mCurrAttrs);
                    return;
                case 1048:
                    if (value) {
                        currScrBuf.savePos();
                    } else {
                        currScrBuf.restorePos();
                    }
                    currScrBuf.getCurrentAttrs(mCurrAttrs);
                    return;
                case 1049:
                    if (value) {
                        currScrBuf.savePos();
                        altScrBuf.setPos(currScrBuf);
                        currScrBuf = altScrBuf;
                        currScrBuf.clear();
                    } else {
                        mainScrBuf.setPos(currScrBuf);
                        currScrBuf = mainScrBuf;
                        currScrBuf.restorePos();
                    }
                    currScrBuf.getCurrentAttrs(mCurrAttrs);
                    return;
                case 2004:
                    if (consoleOutput != null)
                        consoleOutput.bracketedPasteMode = value;
                    return;
            }
            if (LOG_UNKNOWN_ESC)
                Log.w("CtrlSeq", "DecPrivateMode: " + opt + " = " + value);
        }

        public void set(final int opt, final boolean value) {
            current.put(opt, value);
            apply(opt, value);
        }

        public void save(final int opt) {
            final int i = current.indexOfKey(opt);
            if (i < 0) return;
            saved.put(opt, current.valueAt(i));
        }

        public void restore(final int opt) {
            final int i = saved.indexOfKey(opt);
            if (i < 0) return;
            final boolean v = saved.valueAt(i);
            current.put(opt, v);
            apply(opt, v);
        }

        public Iterable<Integer> getState() {
            return Misc.getTrueKeysIterable(current);
        }
    }

    private final DecPrivateMode decPrivateMode = new DecPrivateMode();
}
