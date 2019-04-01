package green_green_avk.anotherterm.ui;

import android.content.Context;
import android.graphics.PointF;
import android.support.v4.math.MathUtils;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Scroller;

public abstract class ScrollableView extends GestureView {

    public PointF scrollPosition = new PointF(0, 0);
    protected PointF scrollScale = new PointF(16, 16);
    protected Scroller mScroller;

    public boolean scrollDisabled = false;

    public ScrollableView(Context context) {
        super(context);
        init();
    }

    public ScrollableView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ScrollableView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public ScrollableView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        mScroller = new Scroller(getContext());
    }

    public void setScrollScale(float x, float y) {
        mScroller.forceFinished(true);
        scrollScale.x = x;
        scrollScale.y = y;
    }

    private float toFloatX(int v) {
        return (float) v / scrollScale.x;
    }

    private float toFloatY(int v) {
        return (float) v / scrollScale.y;
    }

    private int toIntX(float v) {
        return (int) (v * scrollScale.x);
    }

    private int toIntY(float v) {
        return (int) (v * scrollScale.y);
    }

    protected float getLeftScrollLimit() {
        return 0;
    }

    protected float getTopScrollLimit() {
        return 0;
    }

    protected float getRightScrollLimit() {
        return 0;
    }

    protected float getBottomScrollLimit() {
        return 0;
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        boolean needsInvalidate = false;
        if (mScroller.computeScrollOffset()) {
            scrollPosition.x = toFloatX(mScroller.getCurrX());
            scrollPosition.y = toFloatY(mScroller.getCurrY());
            needsInvalidate = true;
        }
        if (needsInvalidate) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    @Override
    public boolean onDown(MotionEvent e) {
        mScroller.forceFinished(true);
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (scrollDisabled) return true;
        int x1 = toIntX(scrollPosition.x);
        int y1 = toIntY(scrollPosition.y);
        int x2 = (int) (distanceX) + x1;
        int y2 = (int) (distanceY) + y1;
        x2 = MathUtils.clamp(x2, toIntX(getLeftScrollLimit()), toIntX(getRightScrollLimit()));
        y2 = MathUtils.clamp(y2, toIntY(getTopScrollLimit()), toIntY(getBottomScrollLimit()));
//        mScroller.startScroll(x1, y1, x2 - x1, y2 - y1);
        scrollPosition.x = toFloatX(x2);
        scrollPosition.y = toFloatY(y2);
        ViewCompat.postInvalidateOnAnimation(this);
        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        mScroller.forceFinished(true);
        if (scrollDisabled) return true;
        mScroller.fling(toIntX(scrollPosition.x), toIntY(scrollPosition.y),
                -(int) velocityX, -(int) velocityY,
                toIntX(getLeftScrollLimit()),
                toIntX(getRightScrollLimit()),
                toIntY(getTopScrollLimit()),
                toIntY(getBottomScrollLimit())
        );
        ViewCompat.postInvalidateOnAnimation(this);
        return true;
    }
}
