package com.studio.statusbar.stack;

import android.view.View;

import com.studio.statusbar.ActivatableNotificationView;

import java.util.ArrayList;

/**
 * A global state to track all input states for the algorithm.
 */
public class AmbientState {
    private ArrayList<View> mDraggedViews = new ArrayList<View>();
    private int mScrollY;
    private boolean mDimmed;
    private ActivatableNotificationView mActivatedChild;
    private float mOverScrollTopAmount;
    private float mOverScrollBottomAmount;
    private int mSpeedBumpIndex = -1;
    private boolean mDark;
    private boolean mHideSensitive;
    private float mStackTranslation;
    private int mLayoutHeight;
    private int mTopPadding;
    private boolean mShadeExpanded;
    private float mMaxHeadsUpTranslation;
    private boolean mDismissAllInProgress;

    public int getScrollY() {
        return mScrollY;
    }

    public void setScrollY(int scrollY) {
        this.mScrollY = scrollY;
    }

    public void onBeginDrag(View view) {
        mDraggedViews.add(view);
    }

    public void onDragFinished(View view) {
        mDraggedViews.remove(view);
    }

    public ArrayList<View> getDraggedViews() {
        return mDraggedViews;
    }

    /**
     * @param dimmed Whether we are in a dimmed state (on the lockscreen), where the backgrounds are
     *               translucent and everything is scaled back a bit.
     */
    public void setDimmed(boolean dimmed) {
        mDimmed = dimmed;
    }

    /** In dark mode, we draw as little as possible, assuming a black background */
    public void setDark(boolean dark) {
        mDark = dark;
    }

    public void setHideSensitive(boolean hideSensitive) {
        mHideSensitive = hideSensitive;
    }

    /**
     * In dimmed mode, a child can be activated, which happens on the first tap of the double-tap
     * interaction. This child is then scaled normally and its background is fully opaque.
     */
    public void setActivatedChild(ActivatableNotificationView activatedChild) {
        mActivatedChild = activatedChild;
    }

    public boolean isDimmed() {
        return mDimmed;
    }

    public boolean isDark() {
        return mDark;
    }

    public boolean isHideSensitive() {
        return mHideSensitive;
    }

    public ActivatableNotificationView getActivatedChild() {
        return mActivatedChild;
    }

    public void setOverScrollAmount(float amount, boolean onTop) {
        if (onTop) {
            mOverScrollTopAmount = amount;
        } else {
            mOverScrollBottomAmount = amount;
        }
    }

    public float getOverScrollAmount(boolean top) {
        return top ? mOverScrollTopAmount : mOverScrollBottomAmount;
    }

    public int getSpeedBumpIndex() {
        return mSpeedBumpIndex;
    }

    public void setSpeedBumpIndex(int speedBumpIndex) {
        mSpeedBumpIndex = speedBumpIndex;
    }

    public float getStackTranslation() {
        return mStackTranslation;
    }

    public void setStackTranslation(float stackTranslation) {
        mStackTranslation = stackTranslation;
    }

    public int getLayoutHeight() {
        return mLayoutHeight;
    }

    public void setLayoutHeight(int layoutHeight) {
        mLayoutHeight = layoutHeight;
    }

    public float getTopPadding() {
        return mTopPadding;
    }

    public void setTopPadding(int topPadding) {
        mTopPadding = topPadding;
    }

    public int getInnerHeight() {
        return mLayoutHeight - mTopPadding;
    }

    public boolean isShadeExpanded() {
        return mShadeExpanded;
    }

    public void setShadeExpanded(boolean shadeExpanded) {
        mShadeExpanded = shadeExpanded;
    }

    public void setDismissAllInProgress(boolean dismissAllInProgress) {
        mDismissAllInProgress = dismissAllInProgress;
    }

    public boolean isDismissAllInProgress() {
        return mDismissAllInProgress;
    }
}
