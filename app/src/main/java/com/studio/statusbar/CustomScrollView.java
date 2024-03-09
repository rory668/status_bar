package com.studio.statusbar;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.Scroller;

public class CustomScrollView extends View {
    private Scroller mScroller;
    private VelocityTracker mVelocityTracker;
    private int mLastY;

    public CustomScrollView(Context context) {
        super(context);
        mScroller = new Scroller(context);
        mVelocityTracker = VelocityTracker.obtain();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mVelocityTracker.addMovement(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
                }
                mLastY = (int) event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                int y = (int) event.getY();
                int dy = y - mLastY;
                mScroller.startScroll(0, mScroller.getFinalY(), 0, -dy);
                invalidate();
                mLastY = y;
                break;
            case MotionEvent.ACTION_UP:
                mVelocityTracker.computeCurrentVelocity(1000);
                int initialVelocity = (int) mVelocityTracker.getYVelocity();
                if (Math.abs(initialVelocity) > 1000) {
                    mScroller.fling(0, mScroller.getFinalY(), 0, -initialVelocity, 0, 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
                    invalidate();
                }
                break;
        }
        return true;
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollTo(0, mScroller.getCurrY());
            postInvalidate();
        }
    }
}
