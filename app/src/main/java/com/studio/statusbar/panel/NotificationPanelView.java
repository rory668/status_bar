package com.studio.statusbar.panel;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.StatusBarManager;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.MathUtils;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.studio.statusbar.AutoReinflateContainer;
import com.studio.statusbar.AutoReinflateContainer.InflateListener;
import com.studio.statusbar.animation.Interpolators;
import com.studio.statusbar.R;
import com.studio.statusbar.ExpandableView;
import com.studio.statusbar.animation.FlingAnimationUtils;
import com.studio.statusbar.animation.GestureRecorder;
import com.studio.statusbar.StatusBarState;
import com.studio.statusbar.stack.NotificationStackScrollLayout;
import com.studio.statusbar.stack.StackStateAnimator;

import java.util.List;

public class NotificationPanelView extends PanelView implements
        ExpandableView.OnHeightChangedListener,
        View.OnClickListener, NotificationStackScrollLayout.OnOverscrollTopChangedListener,
        NotificationStackScrollLayout.OnEmptySpaceClickListener {

    private static final boolean DEBUG = false;

    // Cap and total height of Roboto font. Needs to be adjusted when font for the big clock is
    // changed.
    private static final int CAP_HEIGHT = 1456;
    private static final int FONT_HEIGHT = 2163;

    private static final float HEADER_RUBBERBAND_FACTOR = 2.05f;
    private static final float LOCK_ICON_ACTIVE_SCALE = 1.2f;

    private static final String COUNTER_PANEL_OPEN = "panel_open";
    private static final String COUNTER_PANEL_OPEN_QS = "panel_open_qs";
    private static final String COUNTER_PANEL_OPEN_PEEK = "panel_open_peek";

    private static final Rect mDummyDirtyRect = new Rect(0, 0, 1, 1);

    private AutoReinflateContainer mQsAutoReinflateContainer;
    private TextView mClockView;
    private View mReserveNotificationSpace;
    private View mQsNavbarScrim;
    protected NotificationsQuickSettingsContainer mNotificationContainerParent;
    protected NotificationStackScrollLayout mNotificationStackScroller;
    private boolean mAnimateNextTopPaddingChange;

    private int mTrackingPointer;
    private VelocityTracker mVelocityTracker;
    private boolean mQsTracking;

    private boolean mConflictingQsExpansionGesture;

    /**
     * Whether we are currently handling a motion gesture in #onInterceptTouchEvent, but haven't
     * intercepted yet.
     */
    private boolean mIntercepting;
    private boolean mPanelExpanded;
    private boolean mQsExpanded;
    private boolean mQsExpandedWhenExpandingStarted;
    private boolean mQsFullyExpanded;
    private boolean mKeyguardShowing;
    private boolean mDozing;
    private boolean mDozingOnDown;
    private int mStatusBarState;
    private float mInitialHeightOnTouch;
    private float mInitialTouchX;
    private float mInitialTouchY;
    private float mLastTouchX;
    private float mLastTouchY;
    protected float mQsExpansionHeight;
    protected int mQsMinExpansionHeight;
    protected int mQsMaxExpansionHeight;
    private int mQsPeekHeight;
    private boolean mStackScrollerOverscrolling;
    private boolean mQsExpansionFromOverscroll;
    private float mLastOverscroll;
    protected boolean mQsExpansionEnabled = true;
    private ValueAnimator mQsExpansionAnimator;
    private FlingAnimationUtils mFlingAnimationUtils;
    private int mStatusBarMinHeight;
    private boolean mUnlockIconActive;
    private int mNotificationsHeaderCollideDistance;
    private int mUnlockMoveDistance;
    private float mEmptyDragAmount;

    private int mTopPaddingAdjustment;
    private boolean mKeyguardShowing;
    private boolean mIsExpanding;

    private boolean mBlockTouches;
    private int mNotificationScrimWaitDistance;
    // Used for two finger gesture as well as accessibility shortcut to QS.
    private boolean mQsExpandImmediate;
    private boolean mTwoFingerQsExpandPossible;

    /**
     * If we are in a panel collapsing motion, we reset scrollY of our scroll view but still
     * need to take this into account in our panel height calculation.
     */
    private boolean mQsAnimatorExpand;
    private boolean mIsLaunchTransitionFinished;
    private boolean mIsLaunchTransitionRunning;
    private Runnable mLaunchAnimationEndRunnable;
    private boolean mOnlyAffordanceInThisMotion;
    private boolean mKeyguardStatusViewAnimating;
    private ValueAnimator mQsSizeChangeAnimator;

    private boolean mShadeEmpty;

    private boolean mQsScrimEnabled = true;
    private boolean mLastAnnouncementWasQuickSettings;
    private boolean mQsTouchAboveFalsingThreshold;

    private float mKeyguardStatusBarAnimateAlpha = 1f;
    private int mOldLayoutDirection;
    private int mNavigationBarBottomHeight;
    private boolean mExpandingFromHeadsUp;
    private boolean mCollapsedOnDown;
    private int mPositionMinSideMargin;
    private int mLastOrientation = -1;
    private boolean mClosingWithAlphaFadeOut;

    public NotificationPanelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(!DEBUG);
    }

    public void setStatusBar(PhoneStatusBar bar) {
        mStatusBar = bar;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mNotificationContainerParent = (NotificationsQuickSettingsContainer)
                findViewById(R.id.notification_container_parent);
        mNotificationStackScroller = (NotificationStackScrollLayout)
                findViewById(R.id.notification_stack_scroller);
        mNotificationStackScroller.setOnHeightChangedListener(this);
        mNotificationStackScroller.setOverscrollTopChangedListener(this);
        mNotificationStackScroller.setOnEmptySpaceClickListener(this);
        mQsAutoReinflateContainer =
                (AutoReinflateContainer) findViewById(R.id.qs_auto_reinflate_container);
    }

    @Override
    protected void loadDimens() {
        super.loadDimens();
        mFlingAnimationUtils = new FlingAnimationUtils(getContext(), 0.4f);
        mStatusBarMinHeight = getResources().getDimensionPixelSize(
                R.dimen.custom_status_bar_height);
        mQsPeekHeight = getResources().getDimensionPixelSize(R.dimen.qs_peek_height);
        mNotificationsHeaderCollideDistance =
                getResources().getDimensionPixelSize(R.dimen.header_notifications_collide_distance);
        mNotificationScrimWaitDistance =
                getResources().getDimensionPixelSize(R.dimen.notification_scrim_wait_distance);
        mPositionMinSideMargin = getResources().getDimensionPixelSize(
                R.dimen.notification_panel_min_side_margin);
    }

    public void updateResources() {
        int panelWidth = getResources().getDimensionPixelSize(R.dimen.notification_panel_width);
        int panelGravity = getResources().getInteger(R.integer.notification_panel_layout_gravity);
        FrameLayout.LayoutParams lp =
                (FrameLayout.LayoutParams) mQsAutoReinflateContainer.getLayoutParams();
        if (lp.width != panelWidth) {
            lp.width = panelWidth;
            lp.gravity = panelGravity;
            mQsAutoReinflateContainer.setLayoutParams(lp);
        }

        lp = (FrameLayout.LayoutParams) mNotificationStackScroller.getLayoutParams();
        if (lp.width != panelWidth) {
            lp.width = panelWidth;
            lp.gravity = panelGravity;
            mNotificationStackScroller.setLayoutParams(lp);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        // Calculate quick setting heights.
        int oldMaxHeight = mQsMaxExpansionHeight;
        mQsMinExpansionHeight = mKeyguardShowing ? 0 : mQsContainer.getQsMinExpansionHeight();
        mQsMaxExpansionHeight = mQsContainer.getDesiredHeight();
        if (mQsExpanded && mQsFullyExpanded) {
            mQsExpansionHeight = mQsMaxExpansionHeight;
            requestScrollerTopPaddingUpdate(false /* animate */);
            requestPanelHeightUpdate();

            // Size has changed, start an animation.
            if (mQsMaxExpansionHeight != oldMaxHeight) {
                startQsSizeChangeAnimation(oldMaxHeight, mQsMaxExpansionHeight);
            }
        } else if (!mQsExpanded) {
            setQsExpansion(mQsMinExpansionHeight + mLastOverscroll);
        }
        updateStackHeight(getExpandedHeight());
        updateHeader();
    }

    private void startQsSizeChangeAnimation(int oldHeight, final int newHeight) {
        if (mQsSizeChangeAnimator != null) {
            oldHeight = (int) mQsSizeChangeAnimator.getAnimatedValue();
            mQsSizeChangeAnimator.cancel();
        }
        mQsSizeChangeAnimator = ValueAnimator.ofInt(oldHeight, newHeight);
        mQsSizeChangeAnimator.setDuration(300);
        mQsSizeChangeAnimator.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        mQsSizeChangeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                requestScrollerTopPaddingUpdate(false /* animate */);
                requestPanelHeightUpdate();
                int height = (int) mQsSizeChangeAnimator.getAnimatedValue();
                //mQsContainer.setHeightOverride(height);
            }
        });
        mQsSizeChangeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mQsSizeChangeAnimator = null;
            }
        });
        mQsSizeChangeAnimator.start();
    }

    public void animateToFullShade(long delay) {
        mAnimateNextTopPaddingChange = true;
        mNotificationStackScroller.goToFullShade(delay);
        requestLayout();
    }

    public void setQsExpansionEnabled(boolean qsExpansionEnabled) {
        mQsExpansionEnabled = qsExpansionEnabled;
        //mQsContainer.setHeaderClickable(qsExpansionEnabled);
    }

    @Override
    public void resetViews() {
        mIsLaunchTransitionFinished = false;
        mBlockTouches = false;
        closeQs();
        mStatusBar.dismissPopups();
        mNotificationStackScroller.setOverScrollAmount(0f, true /* onTop */, false /* animate */,
                true /* cancelAnimators */);
        mNotificationStackScroller.resetScrollPosition();
    }

    public void closeQs() {
        cancelQsAnimation();
        setQsExpansion(mQsMinExpansionHeight);
    }

    public void animateCloseQs() {
        if (mQsExpansionAnimator != null) {
            if (!mQsAnimatorExpand) {
                return;
            }
            float height = mQsExpansionHeight;
            mQsExpansionAnimator.cancel();
            setQsExpansion(height);
        }
        flingSettings(0 /* vel */, false);
    }

    public void openQs() {
        cancelQsAnimation();
        if (mQsExpansionEnabled) {
            setQsExpansion(mQsMaxExpansionHeight);
        }
    }

    public void expandWithQs() {
        if (mQsExpansionEnabled) {
            mQsExpandImmediate = true;
        }
        expand(true /* animate */);
    }

    @Override
    public void fling(float vel, boolean expand) {
        GestureRecorder gr = ((PhoneStatusBarView) mBar).mBar.getGestureRecorder();
        if (gr != null) {
            gr.tag("fling " + ((vel > 0) ? "open" : "closed"), "notifications,v=" + vel);
        }
        super.fling(vel, expand);
    }

    @Override
    protected void flingToHeight(float vel, boolean expand, float target,
            float collapseSpeedUpFactor) {
        setClosingWithAlphaFadeout(!expand && getFadeoutAlpha() == 1.0f);
        super.flingToHeight(vel, expand, target, collapseSpeedUpFactor, expandBecauseOfFalsing);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEventInternal(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.getText().add(getKeyguardOrLockScreenString());
            mLastAnnouncementWasQuickSettings = false;
            return true;
        }
        return super.dispatchPopulateAccessibilityEventInternal(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (mBlockTouches) {
            return false;
        }
        initDownStates(event);
        if (!isFullyCollapsed() && onQsIntercept(event)) {
            return true;
        }
        return super.onInterceptTouchEvent(event);
    }

    private boolean onQsIntercept(MotionEvent event) {
        int pointerIndex = event.findPointerIndex(mTrackingPointer);
        if (pointerIndex < 0) {
            pointerIndex = 0;
            mTrackingPointer = event.getPointerId(pointerIndex);
        }
        final float x = event.getX(pointerIndex);
        final float y = event.getY(pointerIndex);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mIntercepting = true;
                mInitialTouchY = y;
                mInitialTouchX = x;
                initVelocityTracker();
                trackMovement(event);
                if (shouldQuickSettingsIntercept(mInitialTouchX, mInitialTouchY, 0)) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (mQsExpansionAnimator != null) {
                    onQsExpansionStarted();
                    mInitialHeightOnTouch = mQsExpansionHeight;
                    mQsTracking = true;
                    mIntercepting = false;
                    mNotificationStackScroller.removeLongPressCallback();
                }
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

            case MotionEvent.ACTION_MOVE:
                final float h = y - mInitialTouchY;
                trackMovement(event);
                if (mQsTracking) {

                    // Already tracking because onOverscrolled was called. We need to update here
                    // so we don't stop for a frame until the next touch event gets handled in
                    // onTouchEvent.
                    setQsExpansion(h + mInitialHeightOnTouch);
                    trackMovement(event);
                    mIntercepting = false;
                    return true;
                }
                if (Math.abs(h) > mTouchSlop && Math.abs(h) > Math.abs(x - mInitialTouchX)
                        && shouldQuickSettingsIntercept(mInitialTouchX, mInitialTouchY, h)) {
                    mQsTracking = true;
                    onQsExpansionStarted();
                    notifyExpandingFinished();
                    mInitialHeightOnTouch = mQsExpansionHeight;
                    mInitialTouchY = y;
                    mInitialTouchX = x;
                    mIntercepting = false;
                    mNotificationStackScroller.removeLongPressCallback();
                    return true;
                }
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                trackMovement(event);
                if (mQsTracking) {
                    flingQsWithCurrentVelocity(y,
                            event.getActionMasked() == MotionEvent.ACTION_CANCEL);
                    mQsTracking = false;
                }
                mIntercepting = false;
                break;
        }
        return false;
    }

    @Override
    protected boolean isInContentBounds(float x, float y) {
        float stackScrollerX = mNotificationStackScroller.getX();
        return !mNotificationStackScroller.isBelowLastNotification(x - stackScrollerX, y)
                && stackScrollerX < x && x < stackScrollerX + mNotificationStackScroller.getWidth();
    }

    private void initDownStates(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mCollapsedOnDown = isFullyCollapsed();
        }
    }

    private void flingQsWithCurrentVelocity(float y, boolean isCancelMotionEvent) {
        float vel = getCurrentVelocity();
        final boolean expandsQs = flingExpandsQs(vel);
        if (expandsQs) {
        }
        flingSettings(vel, expandsQs && !isCancelMotionEvent);
    }

    private boolean flingExpandsQs(float vel) {
        if (Math.abs(vel) < mFlingAnimationUtils.getMinVelocityPxPerSecond()) {
            return getQsExpansionFraction() > 0.5f;
        } else {
            return vel > 0;
        }
    }

    private float getQsExpansionFraction() {
        return Math.min(1f, (mQsExpansionHeight - mQsMinExpansionHeight)
                / (getTempQsMaxExpansion() - mQsMinExpansionHeight));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mBlockTouches) {
            return false;
        }
        initDownStates(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && isFullyCollapsed()) {
            updateVerticalPanelPosition(event.getX());
        }
        super.onTouchEvent(event);
        return true;
    }

    private boolean handleQsTouch(MotionEvent event) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN && getExpandedFraction() == 1f
                && mStatusBar.getBarState() != StatusBarState.KEYGUARD && !mQsExpanded
                && mQsExpansionEnabled) {

            // Down in the empty area while fully expanded - go to QS.
            mQsTracking = true;
            mConflictingQsExpansionGesture = true;
            onQsExpansionStarted();
            mInitialHeightOnTouch = mQsExpansionHeight;
            mInitialTouchY = event.getX();
            mInitialTouchX = event.getY();
        }
        if (!isFullyCollapsed()) {
            handleQsDown(event);
        }
        if (!mQsExpandImmediate && mQsTracking) {
            onQsTouch(event);
            if (!mConflictingQsExpansionGesture) {
                return true;
            }
        }
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            mConflictingQsExpansionGesture = false;
        }
        if (action == MotionEvent.ACTION_DOWN && isFullyCollapsed()
                && mQsExpansionEnabled) {
            mTwoFingerQsExpandPossible = true;
        }
        if (mTwoFingerQsExpandPossible && isOpenQsEvent(event)
                && event.getY(event.getActionIndex()) < mStatusBarMinHeight) {
            MetricsLogger.count(mContext, COUNTER_PANEL_OPEN_QS, 1);
            mQsExpandImmediate = true;
            requestPanelHeightUpdate();

            // Normally, we start listening when the panel is expanded, but here we need to start
            // earlier so the state is already up to date when dragging down.
            setListening(true);
        }
        return false;
    }

    private boolean isInQsArea(float x, float y) {
        return (x >= mQsAutoReinflateContainer.getX()
                && x <= mQsAutoReinflateContainer.getX() + mQsAutoReinflateContainer.getWidth())
                && (y <= mNotificationStackScroller.getBottomMostNotificationBottom());
    }

    private boolean isOpenQsEvent(MotionEvent event) {
        final int pointerCount = event.getPointerCount();
        final int action = event.getActionMasked();

        final boolean twoFingerDrag = action == MotionEvent.ACTION_POINTER_DOWN
                && pointerCount == 2;

        final boolean stylusButtonClickDrag = action == MotionEvent.ACTION_DOWN
                && (event.isButtonPressed(MotionEvent.BUTTON_STYLUS_PRIMARY)
                        || event.isButtonPressed(MotionEvent.BUTTON_STYLUS_SECONDARY));

        final boolean mouseButtonClickDrag = action == MotionEvent.ACTION_DOWN
                && (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)
                        || event.isButtonPressed(MotionEvent.BUTTON_TERTIARY));

        return twoFingerDrag || stylusButtonClickDrag || mouseButtonClickDrag;
    }

    private void handleQsDown(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                && shouldQuickSettingsIntercept(event.getX(), event.getY(), -1)) {
            mFalsingManager.onQsDown();
            mQsTracking = true;
            onQsExpansionStarted();
            mInitialHeightOnTouch = mQsExpansionHeight;
            mInitialTouchY = event.getX();
            mInitialTouchX = event.getY();

            // If we interrupt an expansion gesture here, make sure to update the state correctly.
            notifyExpandingFinished();
        }
    }

    @Override
    protected boolean flingExpands(float vel, float vectorVel, float x, float y) {
        boolean expands = super.flingExpands(vel, vectorVel, x, y);
        // If we are already running a QS expansion, make sure that we keep the panel open.
        if (mQsExpansionAnimator != null) {
            expands = true;
        }
        return expands;
    }

    @Override
    protected boolean hasConflictingGestures() {
        return mStatusBar.getBarState() != StatusBarState.SHADE;
    }

    @Override
    protected boolean shouldGestureIgnoreXTouchSlop(float x, float y) {
        return false;
    }

    private void onQsTouch(MotionEvent event) {
        int pointerIndex = event.findPointerIndex(mTrackingPointer);
        if (pointerIndex < 0) {
            pointerIndex = 0;
            mTrackingPointer = event.getPointerId(pointerIndex);
        }
        final float y = event.getY(pointerIndex);
        final float x = event.getX(pointerIndex);
        final float h = y - mInitialTouchY;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mQsTracking = true;
                mInitialTouchY = y;
                mInitialTouchX = x;
                onQsExpansionStarted();
                mInitialHeightOnTouch = mQsExpansionHeight;
                initVelocityTracker();
                trackMovement(event);
                break;

            case MotionEvent.ACTION_POINTER_UP:
                final int upPointer = event.getPointerId(event.getActionIndex());
                if (mTrackingPointer == upPointer) {
                    // gesture is ongoing, find a new pointer to track
                    final int newIndex = event.getPointerId(0) != upPointer ? 0 : 1;
                    final float newY = event.getY(newIndex);
                    final float newX = event.getX(newIndex);
                    mTrackingPointer = event.getPointerId(newIndex);
                    mInitialHeightOnTouch = mQsExpansionHeight;
                    mInitialTouchY = newY;
                    mInitialTouchX = newX;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                setQsExpansion(h + mInitialHeightOnTouch);
                if (h >= getFalsingThreshold()) {
                    mQsTouchAboveFalsingThreshold = true;
                }
                trackMovement(event);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mQsTracking = false;
                mTrackingPointer = -1;
                trackMovement(event);
                float fraction = getQsExpansionFraction();
                if (fraction != 0f || y >= mInitialTouchY) {
                    flingQsWithCurrentVelocity(y,
                            event.getActionMasked() == MotionEvent.ACTION_CANCEL);
                }
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;
        }
    }

    @Override
    public void onOverscrollTopChanged(float amount, boolean isRubberbanded) {
        cancelQsAnimation();
        if (!mQsExpansionEnabled) {
            amount = 0f;
        }
        float rounded = amount >= 1f ? amount : 0f;
        setOverScrolling(rounded != 0f && isRubberbanded);
        mQsExpansionFromOverscroll = rounded != 0f;
        mLastOverscroll = rounded;
        updateQsState();
        setQsExpansion(mQsMinExpansionHeight + rounded);
    }

    @Override
    public void flingTopOverscroll(float velocity, boolean open) {
        mLastOverscroll = 0f;
        mQsExpansionFromOverscroll = false;
        setQsExpansion(mQsExpansionHeight);
        flingSettings(!mQsExpansionEnabled && open ? 0f : velocity, open && mQsExpansionEnabled,
                new Runnable() {
                    @Override
                    public void run() {
                        mStackScrollerOverscrolling = false;
                        setOverScrolling(false);
                        updateQsState();
                    }
                }, false /* isClick */);
    }

    private void setOverScrolling(boolean overscrolling) {
        mStackScrollerOverscrolling = overscrolling;
        //mQsContainer.setOverscrolling(overscrolling);
    }

    private void onQsExpansionStarted() {
        onQsExpansionStarted(0);
    }

    private void onQsExpansionStarted(int overscrollAmount) {
        cancelQsAnimation();
        cancelHeightAnimator();

        // Reset scroll position and apply that position to the expanded height.
        float height = mQsExpansionHeight - overscrollAmount;
        setQsExpansion(height);
        requestPanelHeightUpdate();
    }

    private void setQsExpanded(boolean expanded) {
        boolean changed = mQsExpanded != expanded;
        if (changed) {
            mQsExpanded = expanded;
            updateQsState();
            requestPanelHeightUpdate();
            mStatusBar.setQsExpanded(expanded);
            mNotificationContainerParent.setQsExpanded(expanded);
        }
    }

    public void setBarState(int statusBarState, boolean keyguardFadingAway,
            boolean goingToFullShade) {
        int oldState = mStatusBarState;
        boolean keyguardShowing = statusBarState == StatusBarState.KEYGUARD;

        mStatusBarState = statusBarState;
        mKeyguardShowing = keyguardShowing;
        //mQsContainer.setKeyguardShowing(mKeyguardShowing);

        if (goingToFullShade || (oldState == StatusBarState.KEYGUARD
                && statusBarState == StatusBarState.SHADE_LOCKED)) {
            long delay = mStatusBarState == StatusBarState.SHADE_LOCKED
                    ? 0 : mStatusBar.calculateGoingToFullShadeDelay();
            //mQsContainer.animateHeaderSlidingIn(delay);
        } else if (oldState == StatusBarState.SHADE_LOCKED
                && statusBarState == StatusBarState.KEYGUARD) {
            //mQsContainer.animateHeaderSlidingOut();
        }
        resetVerticalPanelPosition();
        updateQsState();
    }

    private final ValueAnimator.AnimatorUpdateListener mStatusBarAnimateAlphaListener =
            new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
        }
    };

    private void updateQsState() {
        //mQsContainer.setExpanded(mQsExpanded);
        mNotificationStackScroller.setScrollingEnabled(
                mStatusBarState != StatusBarState.KEYGUARD && (!mQsExpanded
                        || mQsExpansionFromOverscroll));
        updateEmptyShadeView();
    }

    private void setQsExpansion(float height) {
        height = Math.min(Math.max(height, mQsMinExpansionHeight), mQsMaxExpansionHeight);
        mQsFullyExpanded = height == mQsMaxExpansionHeight && mQsMaxExpansionHeight != 0;
        if (height > mQsMinExpansionHeight && !mQsExpanded && !mStackScrollerOverscrolling) {
            setQsExpanded(true);
        } else if (height <= mQsMinExpansionHeight && mQsExpanded) {
            setQsExpanded(false);
            if (mLastAnnouncementWasQuickSettings && !mTracking && !isCollapsing()) {
                announceForAccessibility(getKeyguardOrLockScreenString());
                mLastAnnouncementWasQuickSettings = false;
            }
        }
        mQsExpansionHeight = height;
        updateQsExpansion();
        requestScrollerTopPaddingUpdate(false /* animate */);
        if (mStatusBarState == StatusBarState.SHADE && mQsExpanded
                && !mStackScrollerOverscrolling && mQsScrimEnabled) {
        }

        // Upon initialisation when we are not layouted yet we don't want to announce that we are
        // fully expanded, hence the != 0.0f check.
        if (height != 0.0f && mQsFullyExpanded && !mLastAnnouncementWasQuickSettings) {
            announceForAccessibility(getContext().getString(
                    R.string.accessibility_desc_quick_settings));
            mLastAnnouncementWasQuickSettings = true;
        }
    }

    protected void updateQsExpansion() {
        //mQsContainer.setQsExpansion(getQsExpansionFraction(), getHeaderTranslation());
    }

    private float calculateQsTopPadding() {
        if (mKeyguardShowing
                && (mQsExpandImmediate || mIsExpanding && mQsExpandedWhenExpandingStarted)) {

            // Either QS pushes the notifications down when fully expanded, or QS is fully above the
            // notifications (mostly on tablets). maxNotifications denotes the normal top padding
            // on Keyguard, maxQs denotes the top padding from the quick settings panel. We need to
            // take the maximum and linearly interpolate with the panel expansion for a nice motion.
            int maxQs = getTempQsMaxExpansion();
            int max = mStatusBarState == StatusBarState.KEYGUARD
                    ? Math.max(maxQs)
                    : maxQs;
            return (int) interpolate(getExpandedFraction(),
                    mQsMinExpansionHeight, max);
        } else if (mQsSizeChangeAnimator != null) {
            return (int) mQsSizeChangeAnimator.getAnimatedValue();
        } else if (mKeyguardShowing) {

            // We can only do the smoother transition on Keyguard when we also are not collapsing
            // from a scrolled quick settings.
            return interpolate(getQsExpansionFraction(),
                    mNotificationStackScroller.getIntrinsicPadding(),
                    mQsMaxExpansionHeight);
        } else {
            return mQsExpansionHeight;
        }
    }

    protected void requestScrollerTopPaddingUpdate(boolean animate) {
        mNotificationStackScroller.updateTopPadding(calculateQsTopPadding(),
                mAnimateNextTopPaddingChange || animate,
                mKeyguardShowing
                        && (mQsExpandImmediate || mIsExpanding && mQsExpandedWhenExpandingStarted));
        mAnimateNextTopPaddingChange = false;
    }

    private void trackMovement(MotionEvent event) {
        if (mVelocityTracker != null) mVelocityTracker.addMovement(event);
        mLastTouchX = event.getX();
        mLastTouchY = event.getY();
    }

    private void initVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
        }
        mVelocityTracker = VelocityTracker.obtain();
    }

    private float getCurrentVelocity() {
        if (mVelocityTracker == null) {
            return 0;
        }
        mVelocityTracker.computeCurrentVelocity(1000);
        return mVelocityTracker.getYVelocity();
    }

    private void cancelQsAnimation() {
        if (mQsExpansionAnimator != null) {
            mQsExpansionAnimator.cancel();
        }
    }

    private void flingSettings(float vel, boolean expand) {
        flingSettings(vel, expand, null, false /* isClick */);
    }

    private void flingSettings(float vel, boolean expand, final Runnable onFinishRunnable,
            boolean isClick) {
        float target = expand ? mQsMaxExpansionHeight : mQsMinExpansionHeight;
        if (target == mQsExpansionHeight) {
            if (onFinishRunnable != null) {
                onFinishRunnable.run();
            }
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(mQsExpansionHeight, target);
        if (isClick) {
            animator.setInterpolator(Interpolators.TOUCH_RESPONSE);
            animator.setDuration(368);
        }
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setQsExpansion((Float) animation.getAnimatedValue());
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mQsExpansionAnimator = null;
                if (onFinishRunnable != null) {
                    onFinishRunnable.run();
                }
            }
        });
        animator.start();
        mQsExpansionAnimator = animator;
        mQsAnimatorExpand = expand;
    }

    /**
     * @return Whether we should intercept a gesture to open Quick Settings.
     */
    private boolean shouldQuickSettingsIntercept(float x, float y, float yDiff) {
        if (!mQsExpansionEnabled || mCollapsedOnDown) {
            return false;
        }
        View header = mQsContainer.getHeader();
        boolean onHeader = x >= mQsAutoReinflateContainer.getX()
                && x <= mQsAutoReinflateContainer.getX() + mQsAutoReinflateContainer.getWidth()
                && y >= header.getTop() && y <= header.getBottom();
        if (mQsExpanded) {
            return onHeader || (yDiff < 0 && isInQsArea(x, y));
        } else {
            return onHeader;
        }
    }

    @Override
    protected boolean isScrolledToBottom() {
        if (!isInSettings()) {
            return mStatusBar.getBarState() == StatusBarState.KEYGUARD
                    || mNotificationStackScroller.isScrolledToBottom();
        } else {
            return true;
        }
    }

    @Override
    protected int getMaxPanelHeight() {
        int min = mStatusBarMinHeight;
        if (mStatusBar.getBarState() != StatusBarState.KEYGUARD
                && mNotificationStackScroller.getNotGoneChildCount() == 0) {
            int minHeight = (int) ((mQsMinExpansionHeight + getOverExpansionAmount())
                    * HEADER_RUBBERBAND_FACTOR);
            min = Math.max(min, minHeight);
        }
        int maxHeight;
        if (mQsExpandImmediate || mQsExpanded || mIsExpanding && mQsExpandedWhenExpandingStarted) {
            maxHeight = calculatePanelHeightQsExpanded();
        } else {
            maxHeight = calculatePanelHeightShade();
        }
        maxHeight = Math.max(maxHeight, min);
        return maxHeight;
    }

    private boolean isInSettings() {
        return mQsExpanded;
    }

    @Override
    protected void onHeightUpdated(float expandedHeight) {
        if (!mQsExpanded || mQsExpandImmediate || mIsExpanding && mQsExpandedWhenExpandingStarted) {
        }
        if (mQsExpandImmediate || mQsExpanded && !mQsTracking && mQsExpansionAnimator == null
                && !mQsExpansionFromOverscroll) {
            float t;
            if (mKeyguardShowing) {

                // On Keyguard, interpolate the QS expansion linearly to the panel expansion
                t = expandedHeight / getMaxPanelHeight();
            } else {

                // In Shade, interpolate linearly such that QS is closed whenever panel height is
                // minimum QS expansion + minStackHeight
                float panelHeightQsCollapsed = mNotificationStackScroller.getIntrinsicPadding()
                        + mNotificationStackScroller.getLayoutMinHeight();
                float panelHeightQsExpanded = calculatePanelHeightQsExpanded();
                t = (expandedHeight - panelHeightQsCollapsed)
                        / (panelHeightQsExpanded - panelHeightQsCollapsed);
            }
            setQsExpansion(mQsMinExpansionHeight
                    + t * (getTempQsMaxExpansion() - mQsMinExpansionHeight));
        }
        updateStackHeight(expandedHeight);
        updateHeader();
        updateNotificationTranslucency();
        updatePanelExpanded();
        mNotificationStackScroller.setShadeExpanded(!isFullyCollapsed());
    }

    private void updatePanelExpanded() {
        boolean isExpanded = !isFullyCollapsed();
        if (mPanelExpanded != isExpanded) {
            mStatusBar.setPanelExpanded(isExpanded);
            mPanelExpanded = isExpanded;
        }
    }

    /**
     * @return a temporary override of {@link #mQsMaxExpansionHeight}, which is needed when
     *         collapsing QS / the panel when QS was scrolled
     */
    private int getTempQsMaxExpansion() {
        return mQsMaxExpansionHeight;
    }

    private int calculatePanelHeightShade() {
        int emptyBottomMargin = mNotificationStackScroller.getEmptyBottomMargin();
        int maxHeight = mNotificationStackScroller.getHeight() - emptyBottomMargin
                - mTopPaddingAdjustment;
        maxHeight += mNotificationStackScroller.getTopPaddingOverflow();
        return maxHeight;
    }

    private int calculatePanelHeightQsExpanded() {
        float notificationHeight = mNotificationStackScroller.getHeight()
                - mNotificationStackScroller.getEmptyBottomMargin()
                - mNotificationStackScroller.getTopPadding();

        // When only empty shade view is visible in QS collapsed state, simulate that we would have
        // it in expanded QS state as well so we don't run into troubles when fading the view in/out
        // and expanding/collapsing the whole panel from/to quick settings.
        if (mNotificationStackScroller.getNotGoneChildCount() == 0
                && mShadeEmpty) {
            notificationHeight = mNotificationStackScroller.getEmptyShadeViewHeight()
                    + mNotificationStackScroller.getBottomStackPeekSize()
                    + mNotificationStackScroller.getBottomStackSlowDownHeight();
        }
        int maxQsHeight = mQsMaxExpansionHeight;

        // If an animation is changing the size of the QS panel, take the animated value.
        if (mQsSizeChangeAnimator != null) {
            maxQsHeight = (int) mQsSizeChangeAnimator.getAnimatedValue();
        }
        float totalHeight = Math.max(
                maxQsHeight, 0)
                + notificationHeight;
        if (totalHeight > mNotificationStackScroller.getHeight()) {
            float fullyCollapsedHeight = maxQsHeight
                    + mNotificationStackScroller.getLayoutMinHeight();
            totalHeight = Math.max(fullyCollapsedHeight, mNotificationStackScroller.getHeight());
        }
        return (int) totalHeight;
    }

    private void updateNotificationTranslucency() {
        float alpha = 1f;
        if (mClosingWithAlphaFadeOut) {
            alpha = getFadeoutAlpha();
        }
        mNotificationStackScroller.setAlpha(alpha);
    }

    private float getFadeoutAlpha() {
        float alpha = (getNotificationsTopY() + mNotificationStackScroller.getFirstItemMinHeight())
                / (mQsMinExpansionHeight + mNotificationStackScroller.getBottomStackPeekSize()
                - mNotificationStackScroller.getBottomStackSlowDownHeight());
        alpha = Math.max(0, Math.min(alpha, 1));
        alpha = (float) Math.pow(alpha, 0.75);
        return alpha;
    }

    @Override
    protected float getOverExpansionAmount() {
        return mNotificationStackScroller.getCurrentOverScrollAmount(true /* top */);
    }

    @Override
    protected float getOverExpansionPixels() {
        return mNotificationStackScroller.getCurrentOverScrolledPixels(true /* top */);
    }

    /**
     * Hides the header when notifications are colliding with it.
     */
    private void updateHeader() {
        updateQsExpansion();
    }



    protected float getHeaderTranslation() {
        if (mStatusBar.getBarState() == StatusBarState.KEYGUARD) {
            return 0;
        }
        if (mNotificationStackScroller.getNotGoneChildCount() == 0) {
            return Math.min(0, mExpandedHeight / HEADER_RUBBERBAND_FACTOR - mQsMinExpansionHeight);
        }
        float stackTranslation = mNotificationStackScroller.getStackTranslation();
        float translation = stackTranslation / HEADER_RUBBERBAND_FACTOR;
        if (mHeadsUpManager.hasPinnedHeadsUp() || mIsExpansionFromHeadsUp) {
            translation = mNotificationStackScroller.getTopPadding() + stackTranslation
                    - mQsMinExpansionHeight;
        }
        return Math.min(0, translation);
    }

    private float getNotificationsTopY() {
        if (mNotificationStackScroller.getNotGoneChildCount() == 0) {
            return getExpandedHeight();
        }
        return mNotificationStackScroller.getNotificationsTopY();
    }

    @Override
    protected void onExpandingStarted() {
        super.onExpandingStarted();
        mNotificationStackScroller.onExpansionStarted();
        mIsExpanding = true;
        mQsExpandedWhenExpandingStarted = mQsFullyExpanded;
        if (mQsExpanded) {
            onQsExpansionStarted();
        }
    }

    @Override
    protected void onExpandingFinished() {
        super.onExpandingFinished();
        mNotificationStackScroller.onExpansionStopped();
        mIsExpanding = false;
        if (isFullyCollapsed()) {
            // Workaround b/22639032: Make sure we invalidate something because else RenderThread
            // thinks we are actually drawing a frame put in reality we don't, so RT doesn't go
            // ahead with rendering and we jank.
            postOnAnimation(new Runnable() {
                @Override
                public void run() {
                    getParent().invalidateChild(NotificationPanelView.this, mDummyDirtyRect);
                }
            });
        } else {
            setListening(true);
        }
        mQsExpandImmediate = false;
        mTwoFingerQsExpandPossible = false;
        mIsExpansionFromHeadsUp = false;
        mNotificationStackScroller.setTrackingHeadsUp(false);
        setPanelScrimMinFraction(0.0f);
    }

    private void setListening(boolean listening) {
        mQsContainer.setListening(listening);
    }

    @Override
    public void expand(boolean animate) {
        super.expand(animate);
        setListening(true);
    }

    @Override
    protected void setOverExpansion(float overExpansion, boolean isPixels) {
        if (mConflictingQsExpansionGesture || mQsExpandImmediate) {
            return;
        }
        if (mStatusBar.getBarState() != StatusBarState.KEYGUARD) {
            mNotificationStackScroller.setOnHeightChangedListener(null);
            if (isPixels) {
                mNotificationStackScroller.setOverScrolledPixels(
                        overExpansion, true /* onTop */, false /* animate */);
            } else {
                mNotificationStackScroller.setOverScrollAmount(
                        overExpansion, true /* onTop */, false /* animate */);
            }
            mNotificationStackScroller.setOnHeightChangedListener(this);
        }
    }

    @Override
    protected void onTrackingStarted() {
        super.onTrackingStarted();
        if (mQsFullyExpanded) {
            mQsExpandImmediate = true;
        }
        mNotificationStackScroller.onPanelTrackingStarted();
    }

    @Override
    protected void onTrackingStopped(boolean expand) {
        super.onTrackingStopped(expand);
        if (expand) {
            mNotificationStackScroller.setOverScrolledPixels(
                    0.0f, true /* onTop */, true /* animate */);
        }
        mNotificationStackScroller.onPanelTrackingStopped();
    }

    @Override
    public void onHeightChanged(ExpandableView view, boolean needsAnimation) {

        // Block update if we are in quick settings and just the top padding changed
        // (i.e. view == null).
        if (view == null && mQsExpanded) {
            return;
        }
        requestPanelHeightUpdate();
    }

    @Override
    public void onReset(ExpandableView view) {
    }

    public void onQsHeightChanged() {
        mQsMaxExpansionHeight = mQsContainer.getDesiredHeight();
        if (mQsExpanded && mQsFullyExpanded) {
            mQsExpansionHeight = mQsMaxExpansionHeight;
            requestScrollerTopPaddingUpdate(false /* animate */);
            requestPanelHeightUpdate();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation != mLastOrientation) {
            resetVerticalPanelPosition();
        }
        mLastOrientation = newConfig.orientation;
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        return insets;
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        if (layoutDirection != mOldLayoutDirection) {
            mOldLayoutDirection = layoutDirection;
        }
    }

    @Override
    public void onClick(View v) {
    }

    @Override
    public float getMaxTranslationDistance() {
        return (float) Math.hypot(getWidth(), getHeight());
    }

    @Override
    protected float getPeekHeight() {
        if (mNotificationStackScroller.getNotGoneChildCount() > 0) {
            return mNotificationStackScroller.getPeekHeight();
        } else {
            return mQsMinExpansionHeight * HEADER_RUBBERBAND_FACTOR;
        }
    }

    @Override
    protected float getCannedFlingDurationFactor() {
        if (mQsExpanded) {
            return 0.7f;
        } else {
            return 0.6f;
        }
    }

    @Override
    protected boolean fullyExpandedClearAllVisible() {
        return mNotificationStackScroller.isDismissViewNotGone()
                && mNotificationStackScroller.isScrolledToBottom() && !mQsExpandImmediate;
    }

    @Override
    protected boolean isClearAllVisible() {
        return mNotificationStackScroller.isDismissViewVisible();
    }

    @Override
    protected int getClearAllHeight() {
        return mNotificationStackScroller.getDismissViewHeight();
    }

    @Override
    protected boolean isTrackingBlocked() {
        return mConflictingQsExpansionGesture && mQsExpanded;
    }

    public boolean isQsExpanded() {
        return mQsExpanded;
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return true;
    }

    public boolean isLaunchTransitionFinished() {
        return mIsLaunchTransitionFinished;
    }

    public boolean isLaunchTransitionRunning() {
        return mIsLaunchTransitionRunning;
    }

    public void setLaunchTransitionEndRunnable(Runnable r) {
        mLaunchAnimationEndRunnable = r;
    }

    public void setEmptyDragAmount(float amount) {
        float factor = 0.8f;
        if (mNotificationStackScroller.getNotGoneChildCount() > 0) {
            factor = 0.4f;
        } else if (!mStatusBar.hasActiveNotifications()) {
            factor = 0.4f;
        }
        mEmptyDragAmount = amount * factor;
    }

    private static float interpolate(float t, float start, float end) {
        return (1 - t) * start + t * end;
    }

    public void setShadeEmpty(boolean shadeEmpty) {
        mShadeEmpty = shadeEmpty;
        updateEmptyShadeView();
    }

    private void updateEmptyShadeView() {
        // Hide "No notifications" in QS.
        mNotificationStackScroller.updateEmptyShadeView(mShadeEmpty && !mQsExpanded);
    }

    public void setQsScrimEnabled(boolean qsScrimEnabled) {
        boolean changed = mQsScrimEnabled != qsScrimEnabled;
        mQsScrimEnabled = qsScrimEnabled;
        if (changed) {
            updateQsState();
        }
    }

    private final Runnable mUpdateHeader = new Runnable() {
        @Override
        public void run() {
            //mQsContainer.getHeader().updateEverything();
        }
    };

    @Override
    public void onEmptySpaceClicked(float x, float y) {
        onEmptySpaceClick(x);
    }

    protected boolean onMiddleClicked() {
        switch (mStatusBar.getBarState()) {
            case StatusBarState.KEYGUARD:
                return true;
            case StatusBarState.SHADE_LOCKED:
                if (!mQsExpanded) {
                }
                return true;
            case StatusBarState.SHADE:

                // This gets called in the middle of the touch handling, where the state is still
                // that we are tracking the panel. Collapse the panel after this is done.
                post(mPostCollapseRunnable);
                return false;
            default:
                return true;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onClosingFinished() {
        super.onClosingFinished();
        resetVerticalPanelPosition();
        setClosingWithAlphaFadeout(false);
    }

    private void setClosingWithAlphaFadeout(boolean closing) {
        mClosingWithAlphaFadeOut = closing;
        mNotificationStackScroller.forceNoOverlappingRendering(closing);
    }

    /**
     * Updates the vertical position of the panel so it is positioned closer to the touch
     * responsible for opening the panel.
     *
     * @param x the x-coordinate the touch event
     */
    protected void updateVerticalPanelPosition(float x) {
        if (mNotificationStackScroller.getWidth() * 1.75f > getWidth()) {
            resetVerticalPanelPosition();
            return;
        }
        float leftMost = mPositionMinSideMargin + mNotificationStackScroller.getWidth() / 2;
        float rightMost = getWidth() - mPositionMinSideMargin
                - mNotificationStackScroller.getWidth() / 2;
        if (Math.abs(x - getWidth() / 2) < mNotificationStackScroller.getWidth() / 4) {
            x = getWidth() / 2;
        }
        x = Math.min(rightMost, Math.max(leftMost, x));
        setVerticalPanelTranslation(x -
                (mNotificationStackScroller.getLeft() + mNotificationStackScroller.getWidth() / 2));
     }

    private void resetVerticalPanelPosition() {
        setVerticalPanelTranslation(0f);
    }

    private void resetVerticalPanelPosition() {
        setVerticalPanelTranslation(0f);
    }

    protected void setVerticalPanelTranslation(float translation) {
        mNotificationStackScroller.setTranslationX(translation);
        mQsAutoReinflateContainer.setTranslationX(translation);
    }

    protected void updateStackHeight(float stackHeight) {
        mNotificationStackScroller.setStackHeight(stackHeight);
    }

    public void setPanelScrimMinFraction(float minFraction) {
        mBar.panelScrimMinFractionChanged(minFraction);
    }

    public void clearNotificationEffects() {
        mStatusBar.clearNotificationEffects();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return true;
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        mNotificationStackScroller.setParentFadingOut(alpha != 1.0f);
    }
}
