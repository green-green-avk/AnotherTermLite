package green_green_avk.anotherterm.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import green_green_avk.anotherterm.utils.WeakHandler;

// TODO: http://android-designing.blogspot.com/2017/05/get-height-of-soft-keyboard-of-device.html

public class BottomImePlaceholderView extends View {

    public BottomImePlaceholderView(Context context) {
        super(context);
        init();
    }

    public BottomImePlaceholderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BottomImePlaceholderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public BottomImePlaceholderView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    protected void init() {
    }

    protected final WeakHandler handler = new WeakHandler();

    protected final Runnable rLayout = new Runnable() {
        @Override
        public void run() {
            requestLayout();
        }
    };

    private int oldH = -1;

    private final Rect r = new Rect();

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        handler.removeCallbacks(rLayout);
        int h;
        final int hMode = MeasureSpec.getMode(heightMeasureSpec);
        if (hMode == MeasureSpec.EXACTLY) {
            h = MeasureSpec.getSize(heightMeasureSpec);
        } else {
            final View v = (View) getParent();
            v.getWindowVisibleDisplayFrame(r);
            h = v.getBottom() - r.bottom;
            if (h < 0) h = 0;
            if (hMode == MeasureSpec.AT_MOST) {
                final int hMax = MeasureSpec.getSize(heightMeasureSpec);
                if (h > hMax) h = hMax;
            }
        }
        if (h != oldH) { // Lost re-rendering workaround when IME is shown after hidden navigation bar
            handler.postDelayed(rLayout, 500);
            oldH = h;
        }
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), h);
    }
}
