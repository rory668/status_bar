package com.studio.statusbar.animation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import com.studio.statusbar.R;
import com.studio.statusbar.animation.FlingAnimationUtils;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.function.BiConsumer;

public abstract class PanelScrollView extends FrameLayout {
    private FlingAnimationUtils mFlingAnimationUtils;
    private FlingAnimationUtils mFlingAnimationUtilsClosing;
    private FlingAnimationUtils mFlingAnimationUtilsDismissing;
    /**
     * Whether or not the PanelView can be expanded or collapsed with a drag.
     */
    private boolean mNotificationsDragEnabled;
    protected boolean mExpanding;
    private boolean mClosing;
    private ValueAnimator mHeightAnimator;
    private ObjectAnimator mPeekAnimator;
    private float mPeekHeight;
    private boolean mJustPeeked;
    protected boolean mTracking;
    private float mExpandedFraction = 0;
    protected float mExpandedHeight = 0;
    private boolean mPanelUpdateWhenAnimatorEnds;
    public static final int STATE_CLOSED = 0;
    public static final int STATE_OPENING = 1;
    public static final int STATE_OPEN = 2;
    private boolean mExpanded;
    private int mState = STATE_CLOSED;
    private boolean mInstantExpanding;
    private BiConsumer<Float, Boolean> mExpansionListener;
    /**
     * Speed-up factor to be used when {@link #mFlingCollapseRunnable} runs the next time.
     */
    private float mNextCollapseSpeedUpFactor = 1.0f;
    private boolean mOverExpandedBeforeFling;
    private boolean mExpandLatencyTracking;
    private boolean mPeekTouching;
    private int mFixedDuration = NO_FIXED_DURATION;
    private static final int NO_FIXED_DURATION = -1;
    private boolean mTouchSlopExceeded;
    private int mTrackingPointer;
    protected int mTouchSlop;
    private float mHintDistance;
    private VelocityTrackerInterface mVelocityTracker;
    private boolean mTouchDisabled;

    public PanelScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mFlingAnimationUtils = new FlingAnimationUtils(context, 0.6f /* maxLengthSeconds */,
                0.6f /* speedUpFactor */);
        mFlingAnimationUtilsClosing = new FlingAnimationUtils(context, 0.5f /* maxLengthSeconds */,
                0.6f /* speedUpFactor */);
        mFlingAnimationUtilsDismissing = new FlingAnimationUtils(context,
                0.5f /* maxLengthSeconds */, 0.2f /* speedUpFactor */, 0.6f /* x2 */,
                0.84f /* y2 */);
        mNotificationsDragEnabled =
                getResources().getBoolean(R.bool.config_enableNotificationShadeDrag);
    }

    protected void onExpandingFinished() {
    }

    protected void onExpandingStarted() {
    }

    private void notifyExpandingStarted() {
        if (!mExpanding) {
            mExpanding = true;
            onExpandingStarted();
        }
    }

    protected final void notifyExpandingFinished() {
        endClosing();
        if (mExpanding) {
            mExpanding = false;
            onExpandingFinished();
        }
    }

    protected void onClosingFinished() {
    }

    private void runPeekAnimation(long duration, float peekHeight, boolean collapseWhenFinished) {
        mPeekHeight = peekHeight;
        if (mHeightAnimator != null) {
            return;
        }
        if (mPeekAnimator != null) {
            mPeekAnimator.cancel();
        }
        mPeekAnimator = ObjectAnimator.ofFloat(this, "expandedHeight", mPeekHeight)
                .setDuration(duration);
        mPeekAnimator.setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
        mPeekAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mPeekAnimator = null;
                if (!mCancelled && collapseWhenFinished) {
                    postOnAnimation(mPostCollapseRunnable);
                }

            }
        });
        notifyExpandingStarted();
        mPeekAnimator.start();
        mJustPeeked = true;
    }

    protected final Runnable mPostCollapseRunnable = new Runnable() {
        @Override
        public void run() {
            collapse(false /* delayed */, 1.0f /* speedUpFactor */);
        }
    };

    public void collapse(boolean delayed, float speedUpFactor) {
        if (canPanelBeCollapsed()) {
            cancelHeightAnimator();
            notifyExpandingStarted();

            // Set after notifyExpandingStarted, as notifyExpandingStarted resets the closing state.
            mClosing = true;
            if (delayed) {
                mNextCollapseSpeedUpFactor = speedUpFactor;
                postDelayed(mFlingCollapseRunnable, 120);
            } else {
                fling(0, false /* expand */, speedUpFactor, false /* expandBecauseOfFalsing */);
            }
        }
    }

    public boolean canPanelBeCollapsed() {
        return !isFullyCollapsed() && !mTracking && !mClosing;
    }

    public boolean isFullyCollapsed() {
        return mExpandedFraction <= 0.0f;
    }

    protected void cancelHeightAnimator() {
        if (mHeightAnimator != null) {
            if (mHeightAnimator.isRunning()) {
                mPanelUpdateWhenAnimatorEnds = false;
            }
            mHeightAnimator.cancel();
        }
        endClosing();
    }

    private void endClosing() {
        if (mClosing) {
            mClosing = false;
            onClosingFinished();
        }
    }

    private final Runnable mFlingCollapseRunnable = new Runnable() {
        @Override
        public void run() {
            fling(0, false /* expand */, mNextCollapseSpeedUpFactor,
                    false /* expandBecauseOfFalsing */);
        }
    };

    protected void fling(float vel, boolean expand) {
        fling(vel, expand, 1.0f /* collapseSpeedUpFactor */, false);
    }

    protected void fling(float vel, boolean expand, boolean expandBecauseOfFalsing) {
        fling(vel, expand, 1.0f /* collapseSpeedUpFactor */, expandBecauseOfFalsing);
    }

    protected abstract int getMaxPanelHeight();

    protected void fling(float vel, boolean expand, float collapseSpeedUpFactor,
                         boolean expandBecauseOfFalsing) {
        cancelPeek();
        float target = expand ? getMaxPanelHeight() : 0;
        if (!expand) {
            mClosing = true;
        }
        flingToHeight(vel, expand, target, collapseSpeedUpFactor, expandBecauseOfFalsing);
    }

    public void cancelPeek() {
        boolean cancelled = false;
        if (mPeekAnimator != null) {
            cancelled = true;
            mPeekAnimator.cancel();
        }

        if (cancelled) {
            // When peeking, we already tell mBar that we expanded ourselves. Make sure that we also
            // notify mBar that we might have closed ourselves.
            notifyBarPanelExpansionChanged();
        }
    }

    protected void notifyBarPanelExpansionChanged() {
        panelExpansionChanged(mExpandedFraction, mExpandedFraction > 0f
                || mPeekAnimator != null || mInstantExpanding
                || mTracking || mHeightAnimator != null);
        if (mExpansionListener != null) {
            mExpansionListener.accept(mExpandedFraction, mTracking);
        }
    }

    public void panelExpansionChanged(float frac, boolean expanded) {
        boolean fullyClosed = true;
        boolean fullyOpened = false;
        mExpanded = expanded;
        updateVisibility();
        // adjust any other panels that may be partially visible
        if (expanded) {
            if (mState == STATE_CLOSED) {
                go(STATE_OPENING);
                onPanelPeeked();
            }
            fullyClosed = false;
            final float thisFrac = getExpandedFraction();
            fullyOpened = thisFrac >= 1f;
        }
        if (fullyOpened && !mTracking) {
            go(STATE_OPEN);
            onPanelFullyOpened();
        } else if (fullyClosed && !mTracking && mState != STATE_CLOSED) {
            go(STATE_CLOSED);
            onPanelCollapsed();
        }
    }

    public float getExpandedFraction() {
        return mExpandedFraction;
    }

    public void onPanelPeeked() {
    }

    public void onPanelCollapsed() {
    }

    public void onPanelFullyOpened() {
    }

    public void go(int state) {
        mState = state;
    }

    private void updateVisibility() {
        setVisibility(mExpanded ? VISIBLE : INVISIBLE);
    }

    protected abstract float getPeekHeight();
    /**
     * @return whether "Clear all" button will be visible when the panel is fully expanded
     */
    protected abstract boolean fullyExpandedClearAllVisible();

    protected abstract boolean isClearAllVisible();

    /**
     * @return the height of the clear all button, in pixels
     */
    protected abstract int getClearAllHeight();
    protected abstract float getOverExpansionAmount();
    protected abstract float getOverExpansionPixels();
    protected abstract boolean shouldUseDismissingAnimation();
    protected void flingToHeight(float vel, boolean expand, float target,
                                 float collapseSpeedUpFactor, boolean expandBecauseOfFalsing) {
        // Hack to make the expand transition look nice when clear all button is visible - we make
        // the animation only to the last notification, and then jump to the maximum panel height so
        // clear all just fades in and the decelerating motion is towards the last notification.
        final boolean clearAllExpandHack = expand && fullyExpandedClearAllVisible()
                && mExpandedHeight < getMaxPanelHeight() - getClearAllHeight()
                && !isClearAllVisible();
        if (clearAllExpandHack) {
            target = getMaxPanelHeight() - getClearAllHeight();
        }
        if (target == mExpandedHeight || getOverExpansionAmount() > 0f && expand) {
            notifyExpandingFinished();
            return;
        }
        mOverExpandedBeforeFling = getOverExpansionAmount() > 0f;
        ValueAnimator animator = createHeightAnimator(target);
        if (expand) {
            if (expandBecauseOfFalsing && vel < 0) {
                vel = 0;
            }
            mFlingAnimationUtils.apply(animator, mExpandedHeight, target, vel, getHeight());
            if (vel == 0) {
                animator.setDuration(350);
            }
        } else {
            if (shouldUseDismissingAnimation()) {
                if (vel == 0) {
                    animator.setInterpolator(Interpolators.PANEL_CLOSE_ACCELERATED);
                    long duration = (long) (200 + mExpandedHeight / getHeight() * 100);
                    animator.setDuration(duration);
                } else {
                    mFlingAnimationUtilsDismissing.apply(animator, mExpandedHeight, target, vel,
                            getHeight());
                }
            } else {
                mFlingAnimationUtilsClosing
                        .apply(animator, mExpandedHeight, target, vel, getHeight());
            }

            // Make it shorter if we run a canned animation
            if (vel == 0) {
                animator.setDuration((long) (animator.getDuration() / collapseSpeedUpFactor));
            }
            if (mFixedDuration != NO_FIXED_DURATION) {
                animator.setDuration(mFixedDuration);
            }
        }
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (clearAllExpandHack && !mCancelled) {
                    setExpandedHeightInternal(getMaxPanelHeight());
                }
                setAnimator(null);
                if (!mCancelled) {
                    notifyExpandingFinished();
                }
                notifyBarPanelExpansionChanged();
            }
        });
        setAnimator(animator);
        animator.start();
    }

    private ValueAnimator createHeightAnimator(float targetHeight) {
        ValueAnimator animator = ValueAnimator.ofFloat(mExpandedHeight, targetHeight);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setExpandedHeightInternal((Float) animation.getAnimatedValue());
            }
        });
        return animator;
    }

    protected abstract void onHeightUpdated(float expandedHeight);
    protected abstract void setOverExpansion(float overExpansion, boolean isPixels);

    public void setExpandedHeightInternal(float h) {
        if (mExpandLatencyTracking && h != 0f) {
            mExpandLatencyTracking = false;
        }
        float fhWithoutOverExpansion = getMaxPanelHeight() - getOverExpansionAmount();
        if (mHeightAnimator == null) {
            float overExpansionPixels = Math.max(0, h - fhWithoutOverExpansion);
            if (getOverExpansionPixels() != overExpansionPixels && mTracking) {
                setOverExpansion(overExpansionPixels, true /* isPixels */);
            }
            mExpandedHeight = Math.min(h, fhWithoutOverExpansion) + getOverExpansionAmount();
        } else {
            mExpandedHeight = h;
            if (mOverExpandedBeforeFling) {
                setOverExpansion(Math.max(0, h - fhWithoutOverExpansion), false /* isPixels */);
            }
        }

        // If we are closing the panel and we are almost there due to a slow decelerating
        // interpolator, abort the animation.
        if (mExpandedHeight < 1f && mExpandedHeight != 0f && mClosing) {
            mExpandedHeight = 0f;
            if (mHeightAnimator != null) {
                mHeightAnimator.end();
            }
        }
        mExpandedFraction = Math.min(1f,
                fhWithoutOverExpansion == 0 ? 0 : mExpandedHeight / fhWithoutOverExpansion);
        onHeightUpdated(mExpandedHeight);
        notifyBarPanelExpansionChanged();
    }

    private void setAnimator(ValueAnimator animator) {
        mHeightAnimator = animator;
        if (animator == null && mPanelUpdateWhenAnimatorEnds) {
            mPanelUpdateWhenAnimatorEnds = false;
            requestPanelHeightUpdate();
        }
    }

    protected abstract boolean isTrackingBlocked();

    protected void requestPanelHeightUpdate() {
        float currentMaxPanelHeight = getMaxPanelHeight();

        if (isFullyCollapsed()) {
            return;
        }

        if (currentMaxPanelHeight == mExpandedHeight) {
            return;
        }

        if (mPeekAnimator != null || mPeekTouching) {
            return;
        }

        if (mTracking && !isTrackingBlocked()) {
            return;
        }

        if (mHeightAnimator != null) {
            mPanelUpdateWhenAnimatorEnds = true;
            return;
        }

        setExpandedHeight(currentMaxPanelHeight);
    }

    public void setExpandedHeight(float height) {
        setExpandedHeightInternal(height + getOverExpansionPixels());
    }

    protected void loadDimens() {
        final Resources res = getContext().getResources();
        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mHintDistance = res.getDimension(R.dimen.hint_move_distance);
    }

    private void trackMovement(MotionEvent event) {
        // Add movement to velocity tracker using raw screen X and Y coordinates instead
        // of window coordinates because the window frame may be moving at the same time.
        float deltaX = event.getRawX() - event.getX();
        float deltaY = event.getRawY() - event.getY();
        event.offsetLocation(deltaX, deltaY);
        if (mVelocityTracker != null) mVelocityTracker.addMovement(event);
        event.offsetLocation(-deltaX, -deltaY);
    }

    public void setTouchDisabled(boolean disabled) {
        mTouchDisabled = disabled;
        if (mTouchDisabled) {
            cancelHeightAnimator();
            if (mTracking) {
                onTrackingStopped(true /* expanded */);
            }
            notifyExpandingFinished();
        }
    }

    protected void onTrackingStopped(boolean expand) {
        mTracking = false;
        onTrackingStopped(expand);
        notifyBarPanelExpansionChanged();
    }

    private boolean mGestureWaitForTouchSlop;
    private boolean mMotionAborted;
    private boolean mIgnoreXTouchSlop;
    protected abstract boolean hasConflictingGestures();
    protected abstract boolean shouldGestureIgnoreXTouchSlop(float x, float y);
    private float mMinExpandHeight;
    protected boolean mHintAnimationRunning;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mInstantExpanding
                || (mTouchDisabled && event.getActionMasked() != MotionEvent.ACTION_CANCEL)
                || (mMotionAborted && event.getActionMasked() != MotionEvent.ACTION_DOWN)) {
            return false;
        }

        // If dragging should not expand the notifications shade, then return false.
        if (!mNotificationsDragEnabled) {
            if (mTracking) {
                // Turn off tracking if it's on or the shade can get stuck in the down position.
                onTrackingStopped(true /* expand */);
            }
            return false;
        }

        // On expanding, single mouse click expands the panel instead of dragging.
        if (isFullyCollapsed() && event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                expand(true);
            }
            return true;
        }

        /*
         * We capture touch events here and update the expand height here in case according to
         * the users fingers. This also handles multi-touch.
         *
         * If the user just clicks shortly, we show a quick peek of the shade.
         *
         * Flinging is also enabled in order to open or close the shade.
         */

        int pointerIndex = event.findPointerIndex(mTrackingPointer);
        if (pointerIndex < 0) {
            pointerIndex = 0;
            mTrackingPointer = event.getPointerId(pointerIndex);
        }
        final float x = event.getX(pointerIndex);
        final float y = event.getY(pointerIndex);

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mGestureWaitForTouchSlop = isFullyCollapsed() || hasConflictingGestures();
            mIgnoreXTouchSlop = isFullyCollapsed() || shouldGestureIgnoreXTouchSlop(x, y);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startExpandMotion(x, y, false /* startTracking */, mExpandedHeight);
                mJustPeeked = false;
                mMinExpandHeight = 0.0f;
                mPanelClosedOnDown = isFullyCollapsed();
                mHasLayoutedSinceDown = false;
                mUpdateFlingOnLayout = false;
                mMotionAborted = false;
                mPeekTouching = mPanelClosedOnDown;
                mDownTime = SystemClock.uptimeMillis();
                if (mVelocityTracker == null) {
                    initVelocityTracker();
                }
                trackMovement(event);
                if (!mGestureWaitForTouchSlop || (mHeightAnimator != null && !mHintAnimationRunning)
                        || mPeekAnimator != null) {
                    mTouchSlopExceeded = (mHeightAnimator != null && !mHintAnimationRunning)
                            || mPeekAnimator != null;
                    cancelHeightAnimator();
                    cancelPeek();
                    onTrackingStarted();
                }
                if (isFullyCollapsed()) {
                    startOpening(event);
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                final int upPointer = event.getPointerId(event.getActionIndex());
                if (mTrackingPointer == upPointer) {
                    // gesture is ongoing, find a new pointer to track
                    final int newIndex = event.getPointerId(0) != upPointer ? 0 : 1;
                    final float newY = event.getY(newIndex);
                    final float newX = event.getX(newIndex);
                    mTrackingPointer = event.getPointerId(newIndex);
                    startExpandMotion(newX, newY, true /* startTracking */, mExpandedHeight);
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                mMotionAborted = true;
                endMotionEvent(event, x, y, true /* forceCancel */);
                break;
            case MotionEvent.ACTION_MOVE:
                trackMovement(event);
                float h = y - mInitialTouchY;

                // If the panel was collapsed when touching, we only need to check for the
                // y-component of the gesture, as we have no conflicting horizontal gesture.
                if (Math.abs(h) > mTouchSlop
                        && (Math.abs(h) > Math.abs(x - mInitialTouchX)
                        || mIgnoreXTouchSlop)) {
                    mTouchSlopExceeded = true;
                    if (mGestureWaitForTouchSlop && !mTracking) {
                        if (!mJustPeeked && mInitialOffsetOnTouch != 0f) {
                            startExpandMotion(x, y, false /* startTracking */, mExpandedHeight);
                            h = 0;
                        }
                        cancelHeightAnimator();
                        onTrackingStarted();
                    }
                }
                float newHeight = Math.max(0, h + mInitialOffsetOnTouch);
                if (newHeight > mPeekHeight) {
                    if (mPeekAnimator != null) {
                        mPeekAnimator.cancel();
                    }
                    mJustPeeked = false;
                } else if (mPeekAnimator == null && mJustPeeked) {
                    // The initial peek has finished, but we haven't dragged as far yet, lets
                    // speed it up by starting at the peek height.
                    mInitialOffsetOnTouch = mExpandedHeight;
                    mInitialTouchY = y;
                    mMinExpandHeight = mExpandedHeight;
                    mJustPeeked = false;
                }
                newHeight = Math.max(newHeight, mMinExpandHeight);
                if (!mJustPeeked && (!mGestureWaitForTouchSlop || mTracking) &&
                        !isTrackingBlocked()) {
                    setExpandedHeightInternal(newHeight);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                trackMovement(event);
                endMotionEvent(event, x, y, false /* forceCancel */);
                break;
        }
        return !mGestureWaitForTouchSlop || mTracking;
    }

    private void initVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
        }
        mVelocityTracker = VelocityTrackerFactory.obtain(getContext());
    }

    private float mInitialOffsetOnTouch;

    protected void startExpandMotion(float newX, float newY, boolean startTracking,
                                     float expandedHeight) {
        mInitialOffsetOnTouch = expandedHeight;
        mInitialTouchY = newY;
        mInitialTouchX = newX;
        if (startTracking) {
            mTouchSlopExceeded = true;
            setExpandedHeight(mInitialOffsetOnTouch);
            onTrackingStarted();
        }
    }

    protected void onTrackingStarted() {
        endClosing();
        mTracking = true;
        notifyExpandingStarted();
        notifyBarPanelExpansionChanged();
    }

    private static final int INITIAL_OPENING_PEEK_DURATION = 200;
    private static final int PEEK_ANIMATION_DURATION = 360;
    protected abstract float getOpeningHeight();

    private void startOpening(MotionEvent event) {
        runPeekAnimation(INITIAL_OPENING_PEEK_DURATION, getOpeningHeight(),
                false /* collapseWhenFinished */);
        notifyBarPanelExpansionChanged();
        float width = getDisplayWidth();
        float height = getDisplayHeight();
    }

    private final DisplayMetrics mDisplayMetrics = new DisplayMetrics();

    float getDisplayWidth() {
        return mDisplayMetrics.widthPixels;
    }

    float getDisplayHeight() {
        return mDisplayMetrics.heightPixels;
    }

    float getDisplayDensity() {
        return mDisplayMetrics.density;
    }

    public boolean isCollapsing() {
        return mClosing;
    }

    private boolean mAnimateAfterExpanding;
    private boolean mUpdateFlingOnLayout;

    public void expand(final boolean animate) {
        if (!isFullyCollapsed() && !isCollapsing()) {
            return;
        }

        mInstantExpanding = true;
        mAnimateAfterExpanding = animate;
        mUpdateFlingOnLayout = false;
        abortAnimations();
        cancelPeek();
        if (mTracking) {
            onTrackingStopped(true /* expands */); // The panel is expanded after this call.
        }
        if (mExpanding) {
            notifyExpandingFinished();
        }
        notifyBarPanelExpansionChanged();

        // Wait for window manager to pickup the change, so we know the maximum height of the panel
        // then.
        getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (!mInstantExpanding) {
                            getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            return;
                        }
                        if (getHeight()
                                != getStatusBarHeight()) {
                            getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            if (mAnimateAfterExpanding) {
                                notifyExpandingStarted();
                                fling(0, true /* expand */);
                            } else {
                                setExpandedFraction(1f);
                            }
                            mInstantExpanding = false;
                        }
                    }
                });

        // Make sure a layout really happens.
        requestLayout();
    }

    public int getStatusBarHeight() {
        Resources resources = getResources();
        return resources.getDimensionPixelSize(R.dimen.custom_status_bar_height);
    }

    public void instantCollapse() {
        abortAnimations();
        setExpandedFraction(0f);
        if (mExpanding) {
            notifyExpandingFinished();
        }
        if (mInstantExpanding) {
            mInstantExpanding = false;
            notifyBarPanelExpansionChanged();
        }
    }

    private void abortAnimations() {
        cancelPeek();
        cancelHeightAnimator();
        removeCallbacks(mPostCollapseRunnable);
        removeCallbacks(mFlingCollapseRunnable);
    }

    public void setExpandedFraction(float frac) {
        setExpandedHeight(getMaxPanelHeight() * frac);
    }

    private float mInitialTouchY;
    private float mInitialTouchX;
    private boolean mPanelClosedOnDown;
    private boolean mHasLayoutedSinceDown;
    private float mUpdateFlingVelocity;
    private long mDownTime;

    private void endMotionEvent(MotionEvent event, float x, float y, boolean forceCancel) {
        mTrackingPointer = -1;
        if ((mTracking && mTouchSlopExceeded)
                || Math.abs(x - mInitialTouchX) > mTouchSlop
                || Math.abs(y - mInitialTouchY) > mTouchSlop
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL
                || forceCancel) {
            float vel = 0f;
            float vectorVel = 0f;
            if (mVelocityTracker != null) {
                mVelocityTracker.computeCurrentVelocity(1000);
                vel = mVelocityTracker.getYVelocity();
                vectorVel = (float) Math.hypot(
                        mVelocityTracker.getXVelocity(), mVelocityTracker.getYVelocity());
            }
            boolean expand = flingExpands(vel, vectorVel, x, y)
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL
                    || forceCancel;
            // Log collapse gesture if on lock screen.
            if (!expand) {
                float displayDensity = getDisplayDensity();
                int heightDp = (int) Math.abs((y - mInitialTouchY) / displayDensity);
                int velocityDp = (int) Math.abs(vel / displayDensity);
            }
            fling(vel, expand);
            onTrackingStopped(expand);
            mUpdateFlingOnLayout = expand && mPanelClosedOnDown && !mHasLayoutedSinceDown;
            if (mUpdateFlingOnLayout) {
                mUpdateFlingVelocity = vel;
            }
        } else if (mPanelClosedOnDown && !mTracking) {
            long timePassed = SystemClock.uptimeMillis() - mDownTime;
            if (timePassed < ViewConfiguration.getLongPressTimeout()) {
                // Lets show the user that he can actually expand the panel
                runPeekAnimation(PEEK_ANIMATION_DURATION, getPeekHeight(), true /* collapseWhenFinished */);
            } else {
                // We need to collapse the panel since we peeked to the small height.
                postOnAnimation(mPostCollapseRunnable);
            }
        }

        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
        mPeekTouching = false;
    }

    protected float getCurrentExpandVelocity() {
        if (mVelocityTracker == null) {
            return 0;
        }
        mVelocityTracker.computeCurrentVelocity(1000);
        return mVelocityTracker.getYVelocity();
    }

    protected boolean flingExpands(float vel, float vectorVel, float x, float y) {
        if (Math.abs(vectorVel) < mFlingAnimationUtils.getMinVelocityPxPerSecond()) {
            return getExpandedFraction() > 0.5f;
        } else {
            return vel > 0;
        }
    }

    /**
     * @return whether the swiping direction is upwards and above a 45 degree angle compared to the
     * horizontal direction
     */
    private boolean isDirectionUpwards(float x, float y) {
        float xDiff = x - mInitialTouchX;
        float yDiff = y - mInitialTouchY;
        if (yDiff >= 0) {
            return false;
        }
        return Math.abs(yDiff) >= Math.abs(xDiff);
    }

    protected void startExpandingFromPeek() {
        handlePeekToExpandTransistion();
    }

    void handlePeekToExpandTransistion() {
        onPanelRevealed();
    }

    private void onPanelRevealed() {
    }

    private boolean mAnimatingOnDown;
    private boolean mTouchStartedInEmptyArea;

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (mInstantExpanding || !mNotificationsDragEnabled || mTouchDisabled
                || (mMotionAborted && event.getActionMasked() != MotionEvent.ACTION_DOWN)) {
            return false;
        }

        /*
         * If the user drags anywhere inside the panel we intercept it if the movement is
         * upwards. This allows closing the shade from anywhere inside the panel.
         *
         * We only do this if the current content is scrolled to the bottom,
         * i.e isScrolledToBottom() is true and therefore there is no conflicting scrolling gesture
         * possible.
         */
        int pointerIndex = event.findPointerIndex(mTrackingPointer);
        if (pointerIndex < 0) {
            pointerIndex = 0;
            mTrackingPointer = event.getPointerId(pointerIndex);
        }
        final float x = event.getX(pointerIndex);
        final float y = event.getY(pointerIndex);
        boolean scrolledToBottom = isScrolledToBottom();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mAnimatingOnDown = mHeightAnimator != null;
                mMinExpandHeight = 0.0f;
                mDownTime = SystemClock.uptimeMillis();
                if (mAnimatingOnDown && mClosing && !mHintAnimationRunning
                        || mPeekAnimator != null) {
                    cancelHeightAnimator();
                    cancelPeek();
                    mTouchSlopExceeded = true;
                    return true;
                }
                mInitialTouchY = y;
                mInitialTouchX = x;
                mTouchStartedInEmptyArea = !isInContentBounds(x, y);
                mTouchSlopExceeded = false;
                mJustPeeked = false;
                mMotionAborted = false;
                mPanelClosedOnDown = isFullyCollapsed();
                mHasLayoutedSinceDown = false;
                mUpdateFlingOnLayout = false;
                initVelocityTracker();
                trackMovement(event);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                final int upPointer = event.getPointerId(event.getActionIndex());
                if (mTrackingPointer == upPointer) {
                    // gesture is ongoing, find a new pointer to track
                    final int newIndex = event.getPointerId(0) != upPointer ? 0 : 1;
                    mTrackingPointer = event.getPointerId(newIndex);
                    mInitialTouchX = event.getX(newIndex);
                    mInitialTouchY = event.getY(newIndex);
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                break;
            case MotionEvent.ACTION_MOVE:
                final float h = y - mInitialTouchY;
                trackMovement(event);
                if (scrolledToBottom || mTouchStartedInEmptyArea || mAnimatingOnDown) {
                    float hAbs = Math.abs(h);
                    if ((h < -mTouchSlop || (mAnimatingOnDown && hAbs > mTouchSlop))
                            && hAbs > Math.abs(x - mInitialTouchX)) {
                        cancelHeightAnimator();
                        startExpandMotion(x, y, true /* startTracking */, mExpandedHeight);
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;
        }
        return false;
    }

    /**
     * @return Whether a pair of coordinates are inside the visible view content bounds.
     */
    protected abstract boolean isInContentBounds(float x, float y);

    protected boolean isScrolledToBottom() {
        return true;
    }

    protected float getContentHeight() {
        return mExpandedHeight;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        loadDimens();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        loadDimens();
    }

    private String mViewName;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mViewName = getResources().getResourceName(getId());
    }

    public String getName() {
        return mViewName;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        requestPanelHeightUpdate();
        mHasLayoutedSinceDown = true;
        if (mUpdateFlingOnLayout) {
            abortAnimations();
            fling(mUpdateFlingVelocity, true /* expands */);
            mUpdateFlingOnLayout = false;
        }
    }

    public float getExpandedHeight() {
        return mExpandedHeight;
    }

    public boolean isFullyExpanded() {
        return mExpandedHeight >= getMaxPanelHeight();
    }

    public boolean isTracking() {
        return mTracking;
    }

    protected abstract boolean onMiddleClicked();

    /**
     * Gets called when the user performs a click anywhere in the empty area of the panel.
     *
     * @return whether the panel will be expanded after the action performed by this method
     */
    protected boolean onEmptySpaceClick(float x) {
        if (mHintAnimationRunning) {
            return true;
        }
        return onMiddleClicked();
    }

    public void setExpansionListener(BiConsumer<Float, Boolean> consumer) {
        mExpansionListener = consumer;
    }

    public abstract void resetViews();

    public void collapseWithDuration(int animationDuration) {
        mFixedDuration = animationDuration;
        collapse(false /* delayed */, 1.0f /* speedUpFactor */);
        mFixedDuration = NO_FIXED_DURATION;
    }
}
