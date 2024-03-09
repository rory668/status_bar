package com.studio.statusbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.StyleableRes;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.res.ResourcesCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

public class SwipeLayout extends ConstraintLayout {

    public void onSwipeListener(CustomStatusBarService customStatusBarService) {
    }

    public static final class Swipe {

        public static final int NONE = 0x0000;
        public static final int START = Gravity.START;
        public static final int END = Gravity.END;
        public static final int TOP = Gravity.TOP;
        public static final int BOTTOM = Gravity.BOTTOM;
        public static final int ALL = START | END | TOP | BOTTOM;

        public static final boolean canSwipeStart(final int swipe) {
            return (swipe & Swipe.START) == Swipe.START;
        }

        public static final boolean canSwipeEnd(final int swipe) {
            return (swipe & Swipe.END) == Swipe.END;
        }

        public static final boolean canSwipeTop(final int swipe) {
            return (swipe & Swipe.TOP) == Swipe.TOP;
        }

        public static final boolean canSwipeBottom(final int swipe) {
            return (swipe & Swipe.BOTTOM) == Swipe.BOTTOM;
        }

        public static final boolean canSwipeHorizontal(final int swipe) {
            return canSwipeStart(swipe) || canSwipeEnd(swipe);
        }

        public static final boolean canSwipeVertical(final int swipe) {
            return canSwipeTop(swipe) || canSwipeBottom(swipe);
        }

    }

    private static final int DEFAULT_SWIPE = Swipe.NONE;
    private static final float DEFAULT_SNAP_RATIO = 0.5F;
    private static final int DEFAULT_ANIMATION_DURATION = 200;

    private final GestureDetector mGestureDetector;

    private int mSwipe;
    private @IdRes int mSwipeViewId;
    private float mSnapRatio;
    private int mMinFlingDistance;
    private int mMinFlingVelocity;
    private int mAnimationDuration;
    private final SwipeConstraints mSwipeConstraints = new SwipeConstraints();

    private int mActiveTouchPointerId;
    private Point mTouchStartPoint;
    private Point mTouchPoint;
    private boolean mTouchSwiping;

    private View mSwipeView;

    private final Rect mSwipeViewHitRect = new Rect();
    private final Rect mConstraintViewHitRect = new Rect();

    private Animator mSwipeViewTranslationXAnimator;
    private Animator mSwipeViewTranslationYAnimator;

    public OnSwipeListener mSwipeListener;

    public SwipeLayout(final Context context) {
        this(context, null, 0);
    }

    public SwipeLayout(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeLayout(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        this.mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public final boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX, final float velocityY) {
                return SwipeLayout.this.onFling(e1, e2, velocityX, velocityY);
            }
        });

        initialize(context, attrs);
    }

    private final void initialize(final Context context, final AttributeSet attrs) {
        final Resources resources = context.getResources();

        mSwipe = DEFAULT_SWIPE;
        mSwipeViewId = ResourcesCompat.ID_NULL;
        mSnapRatio = DEFAULT_SNAP_RATIO;

        final ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
        mMinFlingDistance = viewConfiguration.getScaledTouchSlop();
        mMinFlingVelocity = viewConfiguration.getScaledMinimumFlingVelocity();

        mAnimationDuration = DEFAULT_ANIMATION_DURATION;

        final TypedArray typedArray = attrs != null ? context.getTheme().obtainStyledAttributes(attrs, R.styleable.SwipeLayout, 0, 0) : null;

        if (typedArray != null) {
            try {
                mSwipe = typedArray.getInt(R.styleable.SwipeLayout_swipe, mSwipe);
                mSwipeViewId = obtainIdResource(resources, typedArray, R.styleable.SwipeLayout_swipeView);
                mSnapRatio = Math.max(0F, Math.min(1F, typedArray.getFloat(R.styleable.SwipeLayout_swipeSnapRatio, mSnapRatio)));
                mMinFlingDistance = Math.max(0, typedArray.getDimensionPixelSize(R.styleable.SwipeLayout_swipeMinFlingDistance, mMinFlingDistance));
                mMinFlingVelocity = Math.max(0, typedArray.getInteger(R.styleable.SwipeLayout_swipeMinFlingVelocity, mMinFlingVelocity));
                mAnimationDuration = Math.max(0, typedArray.getInteger(R.styleable.SwipeLayout_swipeAnimationDuration, mAnimationDuration));

                mSwipeConstraints.initialize(context, typedArray);
            } finally {
                typedArray.recycle();
            }
        }

        mActiveTouchPointerId = MotionEvent.INVALID_POINTER_ID;
        mTouchStartPoint = null;
        mTouchPoint = null;

        mSwipeView = null;
        mSwipeViewHitRect.setEmpty();

        if (mSwipeViewId != ResourcesCompat.ID_NULL) {
            View childView;
            final int childCount = getChildCount();
            for (int childIndex = 0; childIndex < childCount; ++childIndex) {
                childView = getChildAt(childIndex);

                if (childView.getId() == mSwipeViewId) {
                    mSwipeView = childView;
                    break;
                }
            }

            if (mSwipeView != null) {
                mSwipeView.getHitRect(mSwipeViewHitRect);
            }
        }

        mSwipeConstraints.update(this);

        mSwipeViewTranslationXAnimator = null;
        mSwipeViewTranslationYAnimator = null;
    }

    public final void setOnSwipeListener(final OnSwipeListener listener) {
        this.mSwipeListener = listener;
    }

    @Override
    public void onViewAdded(final View view) {
        super.onViewAdded(view);

        if (mSwipeView == null && mSwipeViewId == view.getId()) {
            mSwipeView = view;
            mSwipeView.getHitRect(mSwipeViewHitRect);
        }

        mSwipeConstraints.update(this);
    }

    @Override
    public void onViewRemoved(final View view) {
        super.onViewRemoved(view);

        if (view == mSwipeView) {
            mSwipeView = null;
            mSwipeViewHitRect.setEmpty();
        }

        mSwipeConstraints.update(this);
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent event) {
        final int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                onTouchStart(event);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                onTouchMove(event);
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                onTouchStop(event);
                break;
            }
        }

        return mTouchSwiping;
    }

    @Override
    public final boolean onTouchEvent(final MotionEvent event) {
        if (!mTouchSwiping) {
            super.onTouchEvent(event);
        }

        final int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                onTouchStart(event);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                onTouchMove(event);
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                onTouchStop(event);
                break;
            }
        }

        return mActiveTouchPointerId != MotionEvent.INVALID_POINTER_ID;
    }

    private final void onTouchStart(final MotionEvent event) {
        if (mSwipe != Swipe.NONE && mSwipeViewId != ResourcesCompat.ID_NULL) {
            if (mActiveTouchPointerId == MotionEvent.INVALID_POINTER_ID) {
                mSwipeView.getHitRect(mSwipeViewHitRect);

                final int pointerIndex = event.getActionIndex();
                final int pointerX = (int) event.getX(pointerIndex);
                final int pointerY = (int) event.getY(pointerIndex);

                if (mSwipeViewHitRect.contains(pointerX, pointerY)) {
                    mGestureDetector.onTouchEvent(event);

                    mActiveTouchPointerId = event.getPointerId(pointerIndex);
                    mTouchStartPoint = new Point(pointerX, pointerY);
                    mTouchPoint = new Point(mTouchStartPoint);
                }
            }
        }
    }

    private final void onTouchMove(final MotionEvent event) {
        if (mActiveTouchPointerId != MotionEvent.INVALID_POINTER_ID) {
            mGestureDetector.onTouchEvent(event);

            final int activePointerIndex = event.findPointerIndex(mActiveTouchPointerId);

            final int oldPointerX = mTouchPoint.x;
            final int oldPointerY = mTouchPoint.y;

            mTouchPoint.set((int) event.getX(activePointerIndex), (int) event.getY(activePointerIndex));

            if (!mTouchSwiping) {
                final boolean canSwipeHorizontal = Swipe.canSwipeHorizontal(mSwipe);
                final boolean canSwipeVertical = Swipe.canSwipeVertical(mSwipe);

                mTouchSwiping = Double.compare(
                        mMinFlingDistance,
                        canSwipeHorizontal && canSwipeVertical ?
                                Math.sqrt(Math.pow(mTouchPoint.x - mTouchStartPoint.x, 2) +
                                        Math.pow(mTouchPoint.y - mTouchStartPoint.y, 2)) :
                                canSwipeHorizontal ?
                                        Math.abs(mTouchPoint.x - mTouchStartPoint.x) :
                                        canSwipeVertical ? Math.abs(mTouchPoint.y - mTouchStartPoint.y) : 0)
                        <= 0;
            }

            if (mTouchSwiping) {
                final ViewParent parent = getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }

                swipeBy(mSwipe, mTouchPoint.x - oldPointerX, mTouchPoint.y - oldPointerY, false);
            }
        }
    }

    private final void onTouchStop(final MotionEvent event) {
        if (mActiveTouchPointerId == event.getPointerId(event.getActionIndex())) {
            final boolean isLtr = getLayoutDirection() == LAYOUT_DIRECTION_LTR;

            if (isSwipedStart()) {
                if (Float.compare(mSnapRatio, Math.abs(mSwipeView.getTranslationX() / Math.abs(getStartSwipeConstraint() - (isLtr ? mSwipeView.getLeft() : mSwipeView.getRight())))) < 0) {
                    swipeOpen(Swipe.START, true);
                } else {
                    swipeClose(Swipe.START, true);
                }
            } else if (isSwipedEnd()) {
                if (Float.compare(mSnapRatio, Math.abs(mSwipeView.getTranslationX() / Math.abs(getEndSwipeConstraint() - (isLtr ? mSwipeView.getRight() : mSwipeView.getLeft())))) < 0) {
                    swipeOpen(Swipe.END, true);
                } else {
                    swipeClose(Swipe.END, true);
                }
            }

            if (isSwipedTop()) {
                if (Float.compare(mSnapRatio, Math.abs(mSwipeView.getTranslationY() / Math.abs(getTopSwipeConstraint() - mSwipeView.getTop()))) < 0) {
                    swipeOpen(Swipe.TOP, true);
                } else {
                    swipeClose(Swipe.TOP, true);
                }
            } else if (isSwipedBottom()) {
                if (Float.compare(mSnapRatio, Math.abs(mSwipeView.getTranslationY() / Math.abs(getBottomSwipeConstraint() - mSwipeView.getBottom()))) < 0) {
                    swipeOpen(Swipe.BOTTOM, true);
                } else {
                    swipeClose(Swipe.BOTTOM, true);
                }
            }

            mGestureDetector.onTouchEvent(event);

            mActiveTouchPointerId = MotionEvent.INVALID_POINTER_ID;
            mTouchStartPoint = null;
            mTouchPoint = null;

            mTouchSwiping = false;
        }
    }

    private final boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX, final float velocityY) {
        final float dX = e2.getX() - e1.getX();
        final float dY = e2.getY() - e1.getY();

        if (Math.abs(dX) >= mMinFlingDistance && Math.abs(velocityX) >= mMinFlingVelocity) {
            final boolean isLtr = getLayoutDirection() == LAYOUT_DIRECTION_LTR;

            if (isSwipedStart()) {
                if (isLtr) {
                    if (dX > 0) {
                        swipeOpen(Swipe.START, true);
                    } else {
                        swipeClose(Swipe.START, true);
                    }
                } else {
                    if (dX > 0) {
                        swipeClose(Swipe.START, true);
                    } else {
                        swipeOpen(Swipe.START, true);
                    }
                }
            } else if (isSwipedEnd()) {
                if (isLtr) {
                    if (dX > 0) {
                        swipeClose(Swipe.END, true);
                    } else {
                        swipeOpen(Swipe.END, true);
                    }
                } else {
                    if (dX > 0) {
                        swipeOpen(Swipe.END, true);
                    } else {
                        swipeClose(Swipe.END, true);
                    }
                }
            }
        }

        if (Math.abs(dY) >= mMinFlingDistance && Math.abs(velocityY) >= mMinFlingVelocity) {
            if (isSwipedTop()) {
                if (dY > 0) {
                    swipeOpen(Swipe.TOP, true);
                } else {
                    swipeClose(Swipe.TOP, true);
                }
            } else if (isSwipedBottom()) {
                if (dY > 0) {
                    swipeClose(Swipe.BOTTOM, true);
                } else {
                    swipeOpen(Swipe.BOTTOM, true);
                }
            }
        }

        return true;
    }

    public final boolean isSwipedStart() {
        final boolean isLtr = getLayoutDirection() == LAYOUT_DIRECTION_LTR;
        return (mSwipeView != null &&
                ((isLtr && mSwipeView.getTranslationX() > 0) || (!isLtr && mSwipeView.getTranslationX() < 0)));
    }

    public final boolean isSwipedEnd() {
        final boolean isLtr = getLayoutDirection() == LAYOUT_DIRECTION_LTR;
        return (mSwipeView != null &&
                ((isLtr && mSwipeView.getTranslationX() < 0) || (!isLtr && mSwipeView.getTranslationX() > 0)));
    }

    public final boolean isSwipedTop() {
        return (mSwipeView != null && mSwipeView.getTranslationY() > 0);
    }

    public final boolean isSwipedBottom() {
        return (mSwipeView != null && mSwipeView.getTranslationY() < 0);
    }

    public final boolean isSwiped() {
        return mSwipeView != null &&
                (mSwipeView.getTranslationX() != 0F || mSwipeView.getTranslationY() != 0);
    }

    public final int getSwiped() {
        return mSwipeView == null ? Swipe.NONE :
                (isSwipedStart() ? Swipe.START : 0) |
                        (isSwipedEnd() ? Swipe.END : 0) |
                        (isSwipedTop() ? Swipe.TOP : 0) |
                        (isSwipedBottom() ? Swipe.BOTTOM : 0);
    }

    public final void swipeOpen(final int swipe, final boolean animated) {
        int closeSwipe = 0;
        int filteredSwipe = 0;
        float swipeDx = 0F;
        float swipeDy = 0F;

        if (Swipe.canSwipeStart(swipe)) {
            closeSwipe |= isSwipedEnd() ? Swipe.END : 0;
            filteredSwipe |= Swipe.START;
            swipeDx = getLayoutDirection() == LAYOUT_DIRECTION_LTR ?
                    getWidth() - mSwipeView.getX() + 1 :
                    -(mSwipeView.getX() + mSwipeView.getWidth() + 1);
        } else if (Swipe.canSwipeEnd(swipe)) {
            closeSwipe |= isSwipedStart() ? Swipe.START : 0;
            filteredSwipe |= Swipe.END;
            swipeDx = getLayoutDirection() == LAYOUT_DIRECTION_LTR ?
                    -(mSwipeView.getX() + mSwipeView.getWidth() + 1):
                    getWidth() - mSwipeView.getX() + 1;
        }

        if (Swipe.canSwipeTop(swipe)) {
            closeSwipe |= isSwipedBottom() ? Swipe.BOTTOM : 0;
            filteredSwipe |= Swipe.TOP;
            swipeDy = getHeight() - mSwipeView.getY() + 1;
        } else if (Swipe.canSwipeBottom(swipe)) {
            closeSwipe |= isSwipedTop() ? Swipe.TOP : 0;
            filteredSwipe |= Swipe.BOTTOM;
            swipeDy = -(mSwipeView.getY() + mSwipeView.getHeight() + 1);
        }

        swipeClose(closeSwipe, animated);
        swipeBy(filteredSwipe, swipeDx, swipeDy, animated);
    }

    public final void swipeClose(final boolean animated) {
        swipeClose(Swipe.ALL, animated);
    }

    public final void swipeClose(final int swipe, final boolean animated) {
        final int filteredSwipe = swipe & getSwiped();
        if (filteredSwipe != Swipe.NONE) {
            swipeBy(filteredSwipe, -mSwipeView.getTranslationX(), -mSwipeView.getTranslationY(), animated);
        }
    }

    private final void swipeBy(final int swipe, final float dX, final float dY, final boolean animated) {
        mSwipeView.getHitRect(mSwipeViewHitRect);

        final int _swipe = swipe & mSwipe;

        final boolean canSwipeStart = Swipe.canSwipeStart(_swipe);
        final boolean canSwipeEnd = Swipe.canSwipeEnd(_swipe);
        final boolean canSwipeTop = Swipe.canSwipeTop(_swipe);
        final boolean canSwipeBottom = Swipe.canSwipeBottom(_swipe);

        float swipeDx = canSwipeStart || canSwipeEnd ? dX : 0F;
        float swipeDy = canSwipeTop || canSwipeBottom ? dY : 0F;

        final boolean isLtr = getLayoutDirection() == LAYOUT_DIRECTION_LTR;
        final float translationX = mSwipeView.getTranslationX();
        final float translationY = mSwipeView.getTranslationY();

        if (swipeDx != 0F) {
            final float swipeTranslationX = translationX + dX;
            float constrainedSwipeDx = 0F;

            if (canSwipeStart && (((isLtr && swipeTranslationX >= 0) || (!isLtr && swipeTranslationX <= 0)))) {
                constrainedSwipeDx = constrainSwipeStart(swipeDx);
            } else if (canSwipeEnd && (((isLtr && swipeTranslationX <= 0) || (!isLtr && swipeTranslationX >= 0)))) {
                constrainedSwipeDx = constrainSwipeEnd(swipeDx);
            }

            swipeDx = constrainedSwipeDx;
        }

        if (swipeDy != 0F) {
            final float swipeTranslationY = translationY + dY;
            float constrainedSwipeDy = 0F;

            if (canSwipeTop && swipeTranslationY >= 0) {
                constrainedSwipeDy = constrainSwipeTop(swipeDy);
            } else if (canSwipeBottom && swipeTranslationY <= 0) {
                constrainedSwipeDy = constrainSwipeBottom(swipeDy);
            }

            swipeDy = constrainedSwipeDy;
        }

        final boolean swipeHorizontal = swipeDx != 0F;

        if (swipeHorizontal) {
            if (mSwipeViewTranslationXAnimator != null) {
                mSwipeViewTranslationXAnimator.cancel();
                mSwipeViewTranslationXAnimator = null;
            }

            if (animated) {
                mSwipeViewTranslationXAnimator = ObjectAnimator.ofFloat(mSwipeView, "translationX", translationX, translationX + swipeDx);
                mSwipeViewTranslationXAnimator.setInterpolator(new FastOutSlowInInterpolator());

                mSwipeViewTranslationXAnimator.setDuration((long) (mAnimationDuration * Math.abs(swipeDx / (getLayoutDirection() == LAYOUT_DIRECTION_LTR ?
                        (canSwipeStart ? getStartSwipeConstraint() : getWidth() - getEndSwipeConstraint()) :
                        (canSwipeStart ? getWidth() - getStartSwipeConstraint() : getEndSwipeConstraint())))));

                mSwipeViewTranslationXAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public final void onAnimationEnd(final Animator animation) {
                        if (mSwipeViewTranslationXAnimator == animation) {
                            mSwipeViewTranslationXAnimator = null;
                        }

                        onSwipeChanged(mSwipeView.getTranslationX(), mSwipeView.getTranslationY());
                    }
                });

                mSwipeViewTranslationXAnimator.start();
            } else {
                mSwipeView.setTranslationX(translationX + swipeDx);
            }
        }

        final boolean swipeVertical = swipeDy != 0F;

        if (swipeVertical) {
            if (mSwipeViewTranslationYAnimator != null) {
                mSwipeViewTranslationYAnimator.cancel();
                mSwipeViewTranslationYAnimator = null;
            }

            if (animated) {
                mSwipeViewTranslationYAnimator = ObjectAnimator.ofFloat(mSwipeView, "translationY", translationY, translationY + swipeDy);
                mSwipeViewTranslationYAnimator.setInterpolator(new FastOutSlowInInterpolator());
                mSwipeViewTranslationYAnimator.setDuration((long) (mAnimationDuration * Math.abs(swipeDy / (Math.abs(canSwipeTop ? getTopSwipeConstraint() : getHeight() - getBottomSwipeConstraint())))));

                mSwipeViewTranslationYAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public final void onAnimationEnd(final Animator animation) {
                        if (mSwipeViewTranslationYAnimator == animation) {
                            mSwipeViewTranslationYAnimator = null;
                        }

                        onSwipeChanged(mSwipeView.getTranslationX(), mSwipeView.getTranslationY());
                    }
                });

                mSwipeViewTranslationYAnimator.start();
            } else {
                mSwipeView.setTranslationY(translationY + swipeDy);
            }
        }

        mSwipeView.getHitRect(mSwipeViewHitRect);

        if (!animated && (swipeHorizontal || swipeVertical)) {
            onSwipeChanged(mSwipeView.getTranslationX(), mSwipeView.getTranslationY());
        }
    }

    private final void onSwipeChanged(final float swipeX, final float swipeY) {
        if (mSwipeListener != null) {
            mSwipeListener.onSwipeChanged(this, swipeX, swipeY);
        }
    }

    private final float constrainSwipeStart(final float dX) {
        float constrainedDx = 0F;

        final boolean isLtr = getLayoutDirection() == LAYOUT_DIRECTION_LTR;
        if ((isLtr && mSwipeView.getTranslationX() + dX >= 0) || (!isLtr && mSwipeView.getTranslationX() + dX <= 0)) {
            final int constraint = getStartSwipeConstraint();
            if (constraint != Integer.MIN_VALUE && constraint != Integer.MAX_VALUE) {
                constrainedDx = isLtr ?
                        Math.min(dX, Math.max(0F, constraint - (mSwipeViewHitRect.left))) :
                        Math.max(dX, Math.min(0F, constraint - (mSwipeViewHitRect.right)));
            }
        }

        return constrainedDx;
    }

    private final float constrainSwipeEnd(final float dX) {
        float constrainedDx = 0F;

        final boolean isLtr = getLayoutDirection() == LAYOUT_DIRECTION_LTR;
        if ((isLtr && mSwipeView.getTranslationX() + dX <= 0) || (!isLtr && mSwipeView.getTranslationX() + dX >= 0)) {
            final int constraint = getEndSwipeConstraint();
            if (constraint != Integer.MIN_VALUE && constraint != Integer.MAX_VALUE) {
                constrainedDx = isLtr ?
                        Math.max(dX, Math.min(0F, constraint - (mSwipeViewHitRect.right))) :
                        Math.min(dX, Math.max(0F, constraint - (mSwipeViewHitRect.left)));
            }
        }

        return constrainedDx;
    }

    private final float constrainSwipeTop(final float dY) {
        float constrainedDy = 0F;

        if (mSwipeView.getTranslationY() + dY >= 0) {
            final int constraint = getTopSwipeConstraint();
            if (constraint != Integer.MAX_VALUE) {
                constrainedDy = Math.min(dY, Math.max(0F, constraint - (mSwipeViewHitRect.top)));
            }
        }

        return constrainedDy;
    }

    private final float constrainSwipeBottom(final float dY) {
        float constrainedDy = 0F;

        if (mSwipeView.getTranslationY() + dY <= 0) {
            final int constraint = getBottomSwipeConstraint();
            if (constraint != Integer.MIN_VALUE) {
                constrainedDy = Math.max(dY, Math.min(0F, constraint - (mSwipeViewHitRect.bottom)));
            }
        }

        return constrainedDy;
    }

    private final int getStartSwipeConstraint() {
        final boolean isLtr = getLayoutDirection() == LAYOUT_DIRECTION_LTR;

        int constraint = isLtr ? getWidth() : 0;

        if (mSwipeConstraints.startViewConstraint != null && mSwipeConstraints.startViewConstraint.anchorView != null) {
            mSwipeConstraints.startViewConstraint.anchorView.getHitRect(mConstraintViewHitRect);
            switch (mSwipeConstraints.startViewConstraint.anchorViewGravity) {
                case Gravity.START: {
                    constraint = isLtr ?
                            Math.min(constraint, mConstraintViewHitRect.left) :
                            Math.max(constraint, mConstraintViewHitRect.right);
                    break;
                }
                case Gravity.END: {
                    constraint = isLtr ?
                            Math.min(constraint, mConstraintViewHitRect.right) :
                            Math.max(constraint, mConstraintViewHitRect.left);
                    break;
                }
            }
        }

        if (mSwipeConstraints.startMaxLayoutOffset >= 0) {
            constraint = isLtr ?
                    Math.min(constraint, mSwipeConstraints.startMaxLayoutOffset) :
                    Math.max(constraint, getWidth() - mSwipeConstraints.startMaxLayoutOffset);
        }

        return constraint;
    }

    private final int getEndSwipeConstraint() {
        final boolean isLtr = getLayoutDirection() == LAYOUT_DIRECTION_LTR;

        int constraint = isLtr ? 0 : getWidth();

        if (mSwipeConstraints.endViewConstraint != null && mSwipeConstraints.endViewConstraint.anchorView != null) {
            mSwipeConstraints.endViewConstraint.anchorView.getHitRect(mConstraintViewHitRect);
            switch (mSwipeConstraints.endViewConstraint.anchorViewGravity) {
                case Gravity.START: {
                    constraint = isLtr ?
                            Math.max(constraint, mConstraintViewHitRect.left) :
                            Math.min(constraint, mConstraintViewHitRect.right);
                    break;
                }
                case Gravity.END: {
                    constraint = isLtr ?
                            Math.max(constraint, mConstraintViewHitRect.right) :
                            Math.min(constraint, mConstraintViewHitRect.left);
                    break;
                }
            }
        }

        if (mSwipeConstraints.endMaxLayoutOffset >= 0) {
            constraint = isLtr ?
                    Math.max(constraint, getWidth() - mSwipeConstraints.endMaxLayoutOffset) :
                    Math.min(constraint, mSwipeConstraints.endMaxLayoutOffset);
        }

        return constraint;
    }

    private final int getTopSwipeConstraint() {
        int constraint = getHeight();

        if (mSwipeConstraints.topViewConstraint != null && mSwipeConstraints.topViewConstraint.anchorView != null) {
            mSwipeConstraints.topViewConstraint.anchorView.getHitRect(mConstraintViewHitRect);
            switch (mSwipeConstraints.topViewConstraint.anchorViewGravity) {
                case Gravity.TOP: constraint = Math.min(constraint, mConstraintViewHitRect.top); break;
                case Gravity.BOTTOM: constraint = Math.min(constraint, mConstraintViewHitRect.bottom); break;
            }
        }

        if (mSwipeConstraints.topMaxLayoutOffset >= 0) {
            constraint = Math.min(constraint, mSwipeConstraints.topMaxLayoutOffset);
        }

        return constraint;
    }

    private final int getBottomSwipeConstraint() {
        int constraint = 0;

        if (mSwipeConstraints.bottomViewConstraint != null && mSwipeConstraints.bottomViewConstraint.anchorView != null) {
            mSwipeConstraints.bottomViewConstraint.anchorView.getHitRect(mConstraintViewHitRect);
            switch (mSwipeConstraints.bottomViewConstraint.anchorViewGravity) {
                case Gravity.TOP: constraint = Math.max(constraint, mConstraintViewHitRect.top); break;
                case Gravity.BOTTOM: constraint = Math.max(constraint, mConstraintViewHitRect.bottom); break;
            }
        }

        if (mSwipeConstraints.bottomMaxLayoutOffset >= 0) {
            constraint = Math.max(constraint, getHeight() - mSwipeConstraints.bottomMaxLayoutOffset);
        }

        return constraint;
    }

    private static final class SwipeConstraints {

        ViewConstraint startViewConstraint;
        ViewConstraint endViewConstraint;
        ViewConstraint topViewConstraint;
        ViewConstraint bottomViewConstraint;

        int startMaxLayoutOffset;
        int endMaxLayoutOffset;
        int topMaxLayoutOffset;
        int bottomMaxLayoutOffset;

        SwipeConstraints() {
            this.startViewConstraint = null;
            this.endViewConstraint = null;
            this.topViewConstraint = null;
            this.bottomViewConstraint = null;

            this.startMaxLayoutOffset = Integer.MIN_VALUE;
            this.endMaxLayoutOffset = Integer.MIN_VALUE;
            this.topMaxLayoutOffset = Integer.MIN_VALUE;
            this.bottomMaxLayoutOffset = Integer.MIN_VALUE;
        }

        private void initialize(final Context context, final TypedArray typedArray) {
            final Resources resources = context.getResources();

            int constraintToId;

            constraintToId = obtainIdResource(resources, typedArray, R.styleable.SwipeLayout_swipe_constraintStart_toStartOf);
            this.startViewConstraint = constraintToId != ResourcesCompat.ID_NULL ?
                    new ViewConstraint(constraintToId, Gravity.START) : this.startViewConstraint;

            constraintToId = obtainIdResource(resources, typedArray, R.styleable.SwipeLayout_swipe_constraintStart_toEndOf);
            this.startViewConstraint = constraintToId != ResourcesCompat.ID_NULL ?
                    new ViewConstraint(constraintToId, Gravity.END) : this.startViewConstraint;

            constraintToId = obtainIdResource(resources, typedArray, R.styleable.SwipeLayout_swipe_constraintEnd_toStartOf);
            this.endViewConstraint = constraintToId != ResourcesCompat.ID_NULL ?
                    new ViewConstraint(constraintToId, Gravity.START) : this.endViewConstraint;

            constraintToId = obtainIdResource(resources, typedArray, R.styleable.SwipeLayout_swipe_constraintEnd_toEndOf);
            this.endViewConstraint = constraintToId != ResourcesCompat.ID_NULL ?
                    new ViewConstraint(constraintToId, Gravity.END) : this.endViewConstraint;

            constraintToId = obtainIdResource(resources, typedArray, R.styleable.SwipeLayout_swipe_constraintTop_toTopOf);
            this.topViewConstraint = constraintToId != ResourcesCompat.ID_NULL ?
                    new ViewConstraint(constraintToId, Gravity.TOP) : this.topViewConstraint;

            constraintToId = obtainIdResource(resources, typedArray, R.styleable.SwipeLayout_swipe_constraintTop_toBottomOf);
            this.topViewConstraint = constraintToId != ResourcesCompat.ID_NULL ?
                    new ViewConstraint(constraintToId, Gravity.BOTTOM) : this.topViewConstraint;

            constraintToId = obtainIdResource(resources, typedArray, R.styleable.SwipeLayout_swipe_constraintBottom_toTopOf);
            this.bottomViewConstraint = constraintToId != ResourcesCompat.ID_NULL ?
                    new ViewConstraint(constraintToId, Gravity.TOP) : this.bottomViewConstraint;

            constraintToId = obtainIdResource(resources, typedArray, R.styleable.SwipeLayout_swipe_constraintBottom_toBottomOf);
            this.bottomViewConstraint = constraintToId != ResourcesCompat.ID_NULL ?
                    new ViewConstraint(constraintToId, Gravity.BOTTOM) : this.bottomViewConstraint;

            this.startMaxLayoutOffset = typedArray.getDimensionPixelSize(R.styleable.SwipeLayout_swipe_constraintStart_maxLayoutOffset, startMaxLayoutOffset);
            this.endMaxLayoutOffset = typedArray.getDimensionPixelSize(R.styleable.SwipeLayout_swipe_constraintEnd_maxLayoutOffset, endMaxLayoutOffset);
            this.topMaxLayoutOffset = typedArray.getDimensionPixelSize(R.styleable.SwipeLayout_swipe_constraintTop_maxLayoutOffset, topMaxLayoutOffset);
            this.bottomMaxLayoutOffset = typedArray.getDimensionPixelSize(R.styleable.SwipeLayout_swipe_constraintBottom_maxLayoutOffset, bottomMaxLayoutOffset);
        }

        void update(final SwipeLayout layout) {
            final ViewConstraint[] viewAnchors = new ViewConstraint[] {startViewConstraint, endViewConstraint, topViewConstraint, bottomViewConstraint};
            for (ViewConstraint viewAnchor : viewAnchors) {
                if (viewAnchor != null) {
                    viewAnchor.update(layout);
                }
            }
        }

        private static final class ViewConstraint {

            @IdRes
            int anchorViewId;
            int anchorViewGravity;

            protected View anchorView;

            ViewConstraint() {
                this.anchorViewId = ResourcesCompat.ID_NULL;
                this.anchorViewGravity = Gravity.NO_GRAVITY;
            }

            ViewConstraint(@IdRes final int anchorViewId, final int anchorViewGravity) {
                this.anchorViewId = anchorViewId;
                this.anchorViewGravity = anchorViewGravity;
            }

            void update(final SwipeLayout layout) {
                anchorView = null;

                View childView;

                final int layoutChildCount = layout.getChildCount();
                for (int childIndex = 0 ; childIndex < layoutChildCount ; ++childIndex) {
                    childView = layout.getChildAt(childIndex);
                    if (childView.getId() == anchorViewId) {
                        anchorView = childView;
                        break;
                    }
                }
            }

        }

    }

    public interface OnSwipeListener {

        void onSwipeChanged(SwipeLayout swipeLayout, float swipeX, float swipeY);

    }

    @IdRes
    private static final int obtainIdResource(@NonNull final Resources resources, @NonNull final TypedArray typedArray, @StyleableRes final int index) {
        final int resourceId = typedArray.getResourceId(index, ResourcesCompat.ID_NULL);
        if (resourceId != ResourcesCompat.ID_NULL) {
            if (! "id".equals(resources.getResourceTypeName(resourceId))) {
                return ResourcesCompat.ID_NULL;
            }
        }

        return resourceId;
    }

}
