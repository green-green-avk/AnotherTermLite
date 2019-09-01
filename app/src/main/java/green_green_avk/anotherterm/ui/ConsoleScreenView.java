package green_green_avk.anotherterm.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.math.MathUtils;
import android.support.v4.view.ViewCompat;
import android.support.v7.content.res.AppCompatResources;
import android.util.AttributeSet;
import android.view.MotionEvent;

import green_green_avk.anotherterm.ConsoleInput;
import green_green_avk.anotherterm.ConsoleOutput;
import green_green_avk.anotherterm.ConsoleScreenCharAttrs;
import green_green_avk.anotherterm.R;
import green_green_avk.anotherterm.utils.WeakHandler;

public class ConsoleScreenView extends ScrollableView implements ConsoleInput.OnInvalidateSink {

    public static class State {
        private PointF scrollPosition = null;
        private float fontSize = 0;
        private boolean resizeBufferXOnUi = true;
        private boolean resizeBufferYOnUi = true;

        public void save(@NonNull final ConsoleScreenView v) {
            scrollPosition = v.scrollPosition;
            resizeBufferXOnUi = v.resizeBufferXOnUi;
            resizeBufferYOnUi = v.resizeBufferYOnUi;
            fontSize = v.getFontSize();
        }

        public void apply(@NonNull final ConsoleScreenView v) {
            if (scrollPosition == null) return;
            v.scrollPosition = scrollPosition;
            v.resizeBufferXOnUi = resizeBufferXOnUi;
            v.resizeBufferYOnUi = resizeBufferYOnUi;
            v.setFontSize(fontSize);
        }
    }

    protected static final int MSG_BLINK = 0;
    protected static final int INTERVAL_BLINK = 500; // ms
    protected ConsoleInput consoleInput = null;
    public final ConsoleScreenCharAttrs charAttrs = new ConsoleScreenCharAttrs();
    protected final Paint fgPaint = new Paint();
    protected final Paint bgPaint = new Paint();
    protected final Paint cursorPaint = new Paint();
    protected final Paint selectionPaint = new Paint();
    protected final Paint paddingMarkupPaint = new Paint();
    protected Drawable selectionMarkerPtr = null;
    protected Drawable attrMarkupBlinking = null;
    protected Drawable paddingMarkup = null;
    protected Typeface[] typefaces = {
            Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL),
            Typeface.create(Typeface.MONOSPACE, Typeface.BOLD),
            Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC),
            Typeface.create(Typeface.MONOSPACE, Typeface.BOLD_ITALIC)
    };
    protected float mFontSize = 16;
    protected float mFontWidth;
    protected float mFontHeight;
    protected ConsoleScreenSelection selection = null;
    protected boolean selectionMode = false;
    protected final Point selectionMarkerFirst = new Point();
    protected final Point selectionMarkerLast = new Point();
    protected Point selectionMarker = selectionMarkerFirst;
    protected boolean mouseMode = false;
    public boolean resizeBufferXOnUi = true;
    public boolean resizeBufferYOnUi = true;

    private boolean mBlinkState = true;
    private WeakHandler mHandler = null;

    public ConsoleScreenView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.consoleScreenViewStyle);
    }

    public ConsoleScreenView(final Context context, final AttributeSet attrs,
                             final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, R.style.AppConsoleScreenViewStyle);
    }

    public ConsoleScreenView(final Context context, final AttributeSet attrs,
                             final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    protected void init(final Context context, final AttributeSet attrs,
                        final int defStyleAttr, final int defStyleRes) {
        final int attrMarkupAlpha;
        final int paddingMarkupAlpha;
        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.ConsoleScreenView, defStyleAttr, defStyleRes);
        try {
//            selectionMarkerPtr = a.getDrawable(R.styleable.ConsoleScreenView_selectionMarker);
            selectionMarkerPtr = AppCompatResources.getDrawable(context,
                    a.getResourceId(R.styleable.ConsoleScreenView_selectionMarker, 0));
            attrMarkupBlinking = AppCompatResources.getDrawable(context,
                    a.getResourceId(R.styleable.ConsoleScreenView_attrMarkupBlinking, 0));
            attrMarkupAlpha = (int) (a.getFloat(R.styleable.ConsoleScreenView_attrMarkupAlpha,
                    0.5f) * 255);
            paddingMarkup = AppCompatResources.getDrawable(context,
                    a.getResourceId(R.styleable.ConsoleScreenView_paddingMarkup, 0));
            paddingMarkupAlpha = (int) (a.getFloat(R.styleable.ConsoleScreenView_paddingMarkupAlpha,
                    0.2f) * 255);
        } finally {
            a.recycle();
        }

        attrMarkupBlinking.setAlpha(attrMarkupAlpha);
        paddingMarkup.setAlpha(paddingMarkupAlpha);

        cursorPaint.setColor(Color.argb(127, 255, 255, 255));
        cursorPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        selectionPaint.setColor(Color.argb(127, 0, 255, 0));
        selectionPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        paddingMarkupPaint.setColor(getResources().getColor(R.color.colorMiddle));
        paddingMarkupPaint.setStrokeWidth(3);
        paddingMarkupPaint.setStyle(Paint.Style.STROKE);
        paddingMarkupPaint.setAlpha(paddingMarkupAlpha);
        paddingMarkupPaint.setPathEffect(new DashPathEffect(new float[]{10, 20}, 0));
        if (Build.VERSION.SDK_INT >= 21) {
            // At least, devices with Android 4.4.2 can have monospace font width glitches with these settings.
            fgPaint.setHinting(Paint.HINTING_ON);
            fgPaint.setFlags(fgPaint.getFlags()
                    | Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
            fgPaint.setElegantTextHeight(true);
        }
        applyFont();
        applyCharAttrs();

        mGestureDetector.setOnDoubleTapListener(null);
//        mGestureDetector.setContextClickListener(null);
        mGestureDetector.setIsLongpressEnabled(false);
    }

    protected void resizeBuffer(int cols, int rows) {
        if (consoleInput == null) return;
        if (cols <= 0 || !resizeBufferXOnUi) cols = consoleInput.currScrBuf.getWidth();
        if (rows <= 0 || !resizeBufferYOnUi) rows = consoleInput.currScrBuf.getHeight();
        consoleInput.resize(cols, rows);
    }

    protected void resizeBuffer() {
        resizeBuffer(getCols(), getRows());
    }

    /**
     * Define fixed or variable (if dimension <= 0) screen size.
     */
    public void setScreenSize(int cols, int rows) {
        resizeBufferXOnUi = cols <= 0;
        resizeBufferYOnUi = rows <= 0;
        if (resizeBufferXOnUi) cols = getCols();
        if (resizeBufferYOnUi) rows = getRows();
        consoleInput.resize(cols, rows);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mHandler = new WeakHandler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_BLINK:
                        mBlinkState = !mBlinkState;
                        if (consoleInput != null)
                            invalidate(getBufferDrawRect(
                                    consoleInput.currScrBuf.getAbsPosX(),
                                    consoleInput.currScrBuf.getAbsPosY()
                            ));
                        sendEmptyMessageDelayed(MSG_BLINK, INTERVAL_BLINK);
                        break;
                }
            }
        };
        mHandler.sendEmptyMessage(MSG_BLINK);
    }

    @Override
    protected void onDetachedFromWindow() {
        mHandler.removeMessages(MSG_BLINK);
        mHandler = null;
        super.onDetachedFromWindow();
    }

    @Override
    protected float getTopScrollLimit() {
        return (consoleInput == null) ? 0 : Math.min(
                consoleInput.currScrBuf.getHeight() - getRows(),
                -consoleInput.currScrBuf.getScrollableHeight());
    }

    @Override
    protected float getBottomScrollLimit() {
        return (consoleInput == null) ? 0 : Math.max(
                consoleInput.currScrBuf.getHeight() - getRows(),
                -consoleInput.currScrBuf.getScrollableHeight());
    }

    @Override
    protected float getRightScrollLimit() {
        return (consoleInput == null) ? 0 : Math.max(
                consoleInput.currScrBuf.getWidth() - getCols(),
                0);
    }

    protected void applyFont() {
        final Paint p = new Paint();
        p.setTypeface(typefaces[0]);
        p.setTextSize(mFontSize);
        mFontHeight = p.getFontSpacing();
        mFontWidth = p.measureText("A");
        fgPaint.setTextSize(mFontSize);
        setScrollScale(mFontWidth, mFontHeight);
    }

    protected void _setFont(@NonNull final Typeface[] tfs) {
        typefaces = tfs;
    }

    public void setFont(@NonNull final Typeface[] tfs) {
        _setFont(tfs);
        applyFont();
        resizeBuffer();
        ViewCompat.postInvalidateOnAnimation(this);
    }

    public float getFontSize() {
        return mFontSize;
    }

    public void setFontSize(final float size) {
        mFontSize = size;
        applyFont();
        resizeBuffer();
        ViewCompat.postInvalidateOnAnimation(this);
    }

    public boolean getSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(final boolean mode) {
        selectionMode = mode;
        scrollDisabled = mode || mouseMode;
        if (mode) {
            if (selection == null) {
                selection = new ConsoleScreenSelection();
                selection.first.x = selection.last.x = getCols() / 2;
                selection.first.y = selection.last.y = getRows() / 2;
            }
            getCenter(selection.first.x, selection.first.y, selectionMarkerFirst);
            getCenter(selection.last.x, selection.last.y, selectionMarkerLast);
        }
        ViewCompat.postInvalidateOnAnimation(this);
    }

    public boolean getSelectionIsRect() {
        return selection != null && selection.isRectangular;
    }

    public void setSelectionIsRect(final boolean v) {
        if (selection != null) {
            selection.isRectangular = v;
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    public boolean getSelectionMarker() {
        return selectionMarker == selectionMarkerLast;
    }

    public void setSelectionMarker(final boolean last) {
        selectionMarker = last ? selectionMarkerLast : selectionMarkerFirst;
    }

    public boolean getMouseMode() {
        return mouseMode;
    }

    public void setMouseMode(final boolean mode) {
        mouseMode = mode;
        scrollDisabled = mode || selectionMode;
    }

    protected void getCenter(final int x, final int y, @NonNull final Point r) {
        final Rect p = getBufferDrawRect(x, y);
        r.x = (p.left + p.right) / 2;
        r.y = (p.top + p.bottom) / 2;
    }

    protected Rect getBufferDrawRect(final int x, final int y) {
        return getBufferDrawRect(x, y, x + 1, y + 1);
    }

    protected void getBufferDrawRect(final int x, final int y, @NonNull final Rect rect) {
        getBufferDrawRect(x, y, x + 1, y + 1, rect);
    }

    protected Rect getBufferDrawRect(@NonNull final Rect rect) {
        return getBufferDrawRect(rect.left, rect.top, rect.right, rect.bottom);
    }

    protected Rect getBufferDrawRect(final int left, final int top,
                                     final int right, final int bottom) {
        final Rect r = new Rect();
        getBufferDrawRect(left, top, right, bottom, r);
        return r;
    }

    protected void getBufferDrawRect(final int left, final int top,
                                     final int right, final int bottom,
                                     @NonNull final Rect rect) {
        rect.left = (int) ((left - scrollPosition.x) * mFontWidth);
        rect.top = (int) ((top - scrollPosition.y) * mFontHeight);
        rect.right = (int) ((right - scrollPosition.x) * mFontWidth);
        rect.bottom = (int) ((bottom - scrollPosition.y) * mFontHeight);
    }

    protected Rect getBufferTextRect(final int left, final int top,
                                     final int right, final int bottom) {
        final Rect r = new Rect();
        getBufferTextRect(left, top, right, bottom, r);
        return r;
    }

    protected void getBufferTextRect(final int left, final int top,
                                     final int right, final int bottom,
                                     @NonNull final Rect rect) {
        rect.left = (int) Math.floor(left / mFontWidth + scrollPosition.x);
        rect.top = (int) Math.floor(top / mFontHeight + scrollPosition.y);
        rect.right = (int) Math.ceil(right / mFontWidth + scrollPosition.x);
        rect.bottom = (int) Math.ceil(bottom / mFontHeight + scrollPosition.y);
    }

    protected Rect getBufferDrawRectInc(@NonNull final Point first, @NonNull final Point last) {
        return getBufferDrawRectInc(first.x, first.y, last.x, last.y);
    }

    protected Rect getBufferDrawRectInc(final int x1, final int y1, final int x2, final int y2) {
        final Rect r = new Rect();
        getBufferDrawRect(Math.min(x1, x2), Math.min(y1, y2),
                Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, r);
        return r;
    }

    protected float getBufferDrawPosXF(final float x) {
        return (x - scrollPosition.x) * mFontWidth;
    }

    protected float getBufferDrawPosYF(final float y) {
        return (y - scrollPosition.y) * mFontHeight;
    }

    protected float getBufferTextPosXF(final float x) {
        return x / mFontWidth + scrollPosition.x;
    }

    protected float getBufferTextPosYF(final float y) {
        return y / mFontHeight + scrollPosition.y;
    }

    protected int getBufferTextPosX(final float x) {
        return (int) Math.floor(getBufferTextPosXF(x));
    }

    protected int getBufferTextPosY(final float y) {
        return (int) Math.floor(getBufferTextPosYF(y));
    }

    protected void getBufferTextPos(final float x, final float y, @NonNull final Point r) {
        r.x = getBufferTextPosX(x);
        r.y = getBufferTextPosY(y);
    }

    public int getCols() {
        return (int) (getWidth() / mFontWidth);
    }

    public int getRows() {
        return (int) (getHeight() / mFontHeight);
    }

    public void applyCharAttrs() {
        final int fgColor;
        final int bgColor;
        if (charAttrs.inverse) {
            fgColor = charAttrs.bgColor;
            bgColor = charAttrs.fgColor;
        } else {
            fgColor = charAttrs.fgColor;
            bgColor = charAttrs.bgColor;
        }
        fgPaint.setTypeface(typefaces[(charAttrs.bold ? 1 : 0) | (charAttrs.italic ? 2 : 0)]);
        fgPaint.setColor(charAttrs.bold ? fgColor : ((fgColor >> 1) & 0x007F7F7F | 0xFF000000));
        fgPaint.setUnderlineText(charAttrs.underline);
//        fgPaint.setShadowLayer(1, 0, 0, fgColor);
        bgPaint.setColor(bgColor);
    }

    public void setConsoleInput(@NonNull final ConsoleInput consoleInput) {
        this.consoleInput = consoleInput;
        this.consoleInput.addOnInvalidateSink(this);
        resizeBuffer();
    }

    public void unsetConsoleInput() {
        consoleInput.removeOnInvalidateSink(this);
        consoleInput = null;
    }

    @Nullable
    public String clipboardCopy() {
        if (consoleInput == null) return null;
        final ConsoleScreenSelection s = selection.getDirect();
        final StringBuilder sb = new StringBuilder();
        CharSequence v;
        if (s.first.y == s.last.y) {
            v = consoleInput.currScrBuf.getChars(s.first.x, s.first.y, s.last.x - s.first.x + 1);
            if (v != null) sb.append(v.toString().trim());
        } else if (selection.isRectangular) {
            for (int y = s.first.y; y <= s.last.y - 1; y++) {
                v = consoleInput.currScrBuf.getChars(s.first.x, y, s.last.x - s.first.x + 1);
                if (v != null) sb.append(v.toString().replaceAll(" *$", ""));
                sb.append('\n');
            }
            v = consoleInput.currScrBuf.getChars(0, s.last.y, s.last.x + 1);
            if (v != null) sb.append(v.toString().replaceAll(" *$", ""));
        } else {
            v = consoleInput.currScrBuf.getChars(s.first.x, s.first.y, getCols() - s.first.x);
            if (v != null) sb.append(v.toString().replaceAll(" *$", ""));
            sb.append('\n');
            for (int y = s.first.y + 1; y <= s.last.y - 1; y++) {
                v = consoleInput.currScrBuf.getChars(0, y, getCols());
                if (v != null) sb.append(v.toString().replaceAll(" *$", ""));
                sb.append('\n');
            }
            v = consoleInput.currScrBuf.getChars(0, s.last.y, s.last.x + 1);
            if (v != null) sb.append(v.toString().replaceAll(" *$", ""));
        }
        final String r = sb.toString();
        if (r.isEmpty()) return null;
        return r;
    }

    @Nullable
    public Bitmap makeThumbnail(int w, int h) {
        if (getWidth() <= 0 || getHeight() <= 0)
            return null;
        final float s = Math.min((float) w / getWidth(), (float) h / getHeight());
        w = (int) (getWidth() * s);
        h = (int) (getHeight() * s);
        final Bitmap r = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        final Canvas c = new Canvas(r);
        c.scale(s, s);
        drawContent(c);
        return r;
    }

    @Override
    public void onInvalidateSink(final Rect rect) {
        if (rect == null) ViewCompat.postInvalidateOnAnimation(this);
        else invalidate(rect);
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resizeBuffer();
    }

    protected int mButtons = 0;
    protected final Point mXY = new Point();

    protected int translateButtons(int buttons) {
        if ((buttons & MotionEvent.BUTTON_BACK) != 0)
            buttons |= MotionEvent.BUTTON_SECONDARY;
        if ((buttons & MotionEvent.BUTTON_STYLUS_PRIMARY) != 0)
            buttons |= MotionEvent.BUTTON_PRIMARY;
        if ((buttons & MotionEvent.BUTTON_STYLUS_SECONDARY) != 0)
            buttons |= MotionEvent.BUTTON_SECONDARY;
        return buttons;
    }

    public boolean isMouseSupported() {
        return consoleInput != null && consoleInput.consoleOutput != null &&
                consoleInput.consoleOutput.isMouseSupported();
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (mouseMode) { // No gestures here
            if (consoleInput != null && consoleInput.consoleOutput != null) {
                final int x = getBufferTextPosX(MathUtils.clamp((int) event.getX(), 0, getWidth() - 1));
                final int y = getBufferTextPosY(MathUtils.clamp((int) event.getY(), 0, getHeight() - 1));
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        final int buttons;
                        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) {
                            buttons = MotionEvent.BUTTON_PRIMARY;
                        } else {
                            final int bs = translateButtons(event.getButtonState());
                            buttons = bs & ~mButtons;
                            if (buttons == 0) break; // strange
                            mButtons = bs;
                        }
                        consoleInput.consoleOutput.feed(ConsoleOutput.MouseEventType.PRESS, buttons, x, y);
                        mXY.x = x;
                        mXY.y = y;
                        break;
                    }
                    case MotionEvent.ACTION_MOVE: {
                        if (mXY.x == x && mXY.y == y) break;
                        final int buttons = event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER
                                ? MotionEvent.BUTTON_PRIMARY
                                : translateButtons(event.getButtonState());

                        consoleInput.consoleOutput.feed(ConsoleOutput.MouseEventType.MOVE, buttons, x, y);
                        mXY.x = x;
                        mXY.y = y;
                        break;
                    }
                    case MotionEvent.ACTION_UP: {
                        final int buttons;
                        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) {
                            buttons = MotionEvent.BUTTON_PRIMARY;
                        } else {
                            final int bs = translateButtons(event.getButtonState());
                            buttons = mButtons & ~bs;
                            if (buttons == 0) break; // strange
                            mButtons = bs;
                        }
                        consoleInput.consoleOutput.feed(ConsoleOutput.MouseEventType.RELEASE, buttons, x, y);
                        break;
                    }
                }
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(final MotionEvent event) {
        if (mouseMode) {
            if (consoleInput != null && consoleInput.consoleOutput != null) {
                final int x = getBufferTextPosX(MathUtils.clamp((int) event.getX(), 0, getWidth() - 1));
                final int y = getBufferTextPosY(MathUtils.clamp((int) event.getY(), 0, getHeight() - 1));
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_HOVER_MOVE: {
                        if (mXY.x == x && mXY.y == y) break;
                        final int buttons = translateButtons(event.getButtonState());
                        consoleInput.consoleOutput.feed(ConsoleOutput.MouseEventType.MOVE, buttons, x, y);
                        mXY.x = x;
                        mXY.y = y;
                        break;
                    }
                    case MotionEvent.ACTION_SCROLL: {
                        final float vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                        if (vScroll != 0)
                            consoleInput.consoleOutput.feed(ConsoleOutput.MouseEventType.VSCROLL, (int) vScroll, x, y);
                        break;
                    }
                }
            }
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private float prevVScrollPos = 0;

    @Override
    public boolean onScroll(final MotionEvent e1, final MotionEvent e2,
                            final float distanceX, final float distanceY) {
        if (selectionMode) {
            selectionMarker.x = MathUtils.clamp((int) (selectionMarker.x - distanceX), 0, getWidth() - 1);
            selectionMarker.y = MathUtils.clamp((int) (selectionMarker.y - distanceY), 0, getHeight() - 1);
            getBufferTextPos(selectionMarkerFirst.x, selectionMarkerFirst.y, selection.first);
            getBufferTextPos(selectionMarkerLast.x, selectionMarkerLast.y, selection.last);
            ViewCompat.postInvalidateOnAnimation(this);
            return true;
        }
        if (isMouseSupported() && consoleInput.isAltBuf()) {
            prevVScrollPos += distanceY / scrollScale.y;
            final int lines = (int) prevVScrollPos;
            prevVScrollPos -= lines;
            consoleInput.consoleOutput.vScroll(lines);
        }
        return super.onScroll(e1, e2, distanceX, distanceY);
    }

    @Override
    public boolean onSingleTapUp(final MotionEvent e) {
        if (selectionMode) {
            setSelectionMarker(!getSelectionMarker());
            ViewCompat.postInvalidateOnAnimation(this);
            return true;
        }
        if (isMouseSupported()) {
            final int x = getBufferTextPosX(MathUtils.clamp((int) e.getX(), 0, getWidth() - 1));
            final int y = getBufferTextPosY(MathUtils.clamp((int) e.getY(), 0, getHeight() - 1));
            consoleInput.consoleOutput.feed(ConsoleOutput.MouseEventType.PRESS, ConsoleOutput.MOUSE_LEFT, x, y);
            consoleInput.consoleOutput.feed(ConsoleOutput.MouseEventType.RELEASE, ConsoleOutput.MOUSE_LEFT, x, y);
        }
        return super.onSingleTapUp(e);
    }

    @Override
    public boolean isOpaque() {
        return true;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        drawContent(canvas);
        if (consoleInput != null) {
            if (mBlinkState && consoleInput.cursorVisibility) canvas.drawRect(getBufferDrawRect(
                    consoleInput.currScrBuf.getAbsPosX(),
                    consoleInput.currScrBuf.getAbsPosY()),
                    cursorPaint);
        }
        super.onDraw(canvas);
    }

    protected final boolean isAllSpaces(final CharSequence s) {
        for (int i = 0; i < s.length(); ++i) if (s.charAt(i) != ' ') return false;
        return true;
    }

    protected void drawContent(final Canvas canvas) {
        if (consoleInput != null) {
//            canvas.drawColor(charAttrs.bgColor);
            final float vDivBuf = getBufferDrawPosYF(0) - 1;
            final float vDivBottom = getBufferDrawPosYF(consoleInput.currScrBuf.getHeight()) - 1;
            final float hDiv = getBufferDrawPosXF(consoleInput.currScrBuf.getWidth()) - 1;
            final Rect rect = getBufferTextRect(0, 0, getWidth(), getHeight());
            for (int j = rect.top; j < rect.bottom; j++) {
                final float strTop = getBufferDrawPosYF(j);
                final float strBottom = getBufferDrawPosYF(j + 1)
                        + 1; // fix for old phones rendering glitch
                int i = rect.left;
                while (i < rect.right) {
                    final float strFragLeft = getBufferDrawPosXF(i);
                    consoleInput.currScrBuf.getAttrs(i, j, charAttrs);
                    applyCharAttrs();
                    final CharSequence s =
                            consoleInput.currScrBuf.getCharsSameAttr(i, j, rect.right);
                    if (s == null) {
                        canvas.drawRect(strFragLeft, strTop, getWidth(), strBottom, bgPaint);
                        if (charAttrs.blinking) drawDrawable(canvas, attrMarkupBlinking,
                                (int) strFragLeft, (int) strTop, getWidth(), (int) strBottom);
                        break;
                    }
                    final float strFragRight = getBufferDrawPosXF(i + s.length());
                    canvas.drawRect(strFragLeft, strTop, strFragRight, strBottom, bgPaint);
                    if (charAttrs.blinking) drawDrawable(canvas, attrMarkupBlinking,
                            (int) strFragLeft, (int) strTop, (int) strFragRight, (int) strBottom);
                    if (!isAllSpaces(s))
                        canvas.drawText(s, 0, s.length(),
                                strFragLeft, strTop - fgPaint.ascent(), fgPaint);
                    i += s.length();
                }
            }
            if (paddingMarkup != null) {
                if (vDivBottom < getHeight())
                    drawDrawable(canvas, paddingMarkup, 0, (int) vDivBottom,
                            getWidth(), getHeight());
                if (hDiv < getWidth())
                    drawDrawable(canvas, paddingMarkup, (int) hDiv, 0,
                            getWidth(), Math.min(getHeight(), (int) vDivBottom));
            }
            canvas.drawLine(0, vDivBottom, getWidth(), vDivBottom, paddingMarkupPaint);
            canvas.drawLine(0, vDivBuf, getWidth(), vDivBuf, paddingMarkupPaint);
            canvas.drawLine(hDiv, 0, hDiv, getHeight(), paddingMarkupPaint);
            if (selectionMode && selection != null) {
                final int selH = Math.abs(selection.last.y - selection.first.y) + 1;
                if (selH == 1 || selection.isRectangular) {
                    canvas.drawRect(getBufferDrawRectInc(selection.first, selection.last), selectionPaint);
                } else if (selH >= 2) {
                    final ConsoleScreenSelection s = selection.getDirect();
                    canvas.drawRect(getBufferDrawRectInc(
                            s.first.x,
                            s.first.y,
                            getCols() - 1,
                            s.first.y
                    ), selectionPaint);
                    if (selH > 2) {
                        canvas.drawRect(getBufferDrawRectInc(
                                0,
                                s.first.y + 1,
                                getCols() - 1,
                                s.last.y - 1
                        ), selectionPaint);
                    }
                    canvas.drawRect(getBufferDrawRectInc(
                            0,
                            s.last.y,
                            s.last.x,
                            s.last.y
                    ), selectionPaint);
                }
                if (selectionMarkerPtr != null) {
                    final float smSize = mFontHeight * 3;
                    final float smPosX = selectionMarker.x - smSize / 2;
                    final float smPosY = selectionMarker.y - smSize / 2;
                    canvas.save();
                    canvas.translate(smPosX, smPosY);
                    canvas.clipRect(0, 0, smSize, smSize);
                    selectionMarkerPtr.setBounds(0, 0, (int) smSize, (int) smSize);
                    selectionMarkerPtr.draw(canvas);
                    canvas.restore();
                }
            }
        }
    }

    protected void drawDrawable(final Canvas canvas, final Drawable drawable,
                                final int left, final int top, final int right, final int bottom) {
        if (drawable == null) return;
        int xOff = 0;
        int yOff = 0;
        int xSize = 0;
        int ySize = 0;
        if (drawable instanceof BitmapDrawable) {
            final BitmapDrawable d = (BitmapDrawable) drawable;
            if (d.getTileModeX() != Shader.TileMode.CLAMP) {
                xSize = d.getIntrinsicWidth() * 2;
                xOff = (int) (scrollPosition.x * mFontWidth) % xSize;
            }
            if (d.getTileModeY() != Shader.TileMode.CLAMP) {
                ySize = d.getIntrinsicHeight() * 2;
                yOff = (int) (scrollPosition.y * mFontHeight) % ySize;
            }
        }
        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        canvas.translate(-xOff, -yOff);
        drawable.setBounds(left - xSize, top - ySize,
                right + xSize, bottom + ySize);
        drawable.draw(canvas);
        canvas.restore();
    }
}
