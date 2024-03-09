package com.studio.statusbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import com.studio.statusbar.R;
import com.studio.statusbar.ScrimView;
import com.studio.statusbar.stack.StackStateAnimator;

/**
 * Controls both the scrim behind the notifications and in front of the notifications (when a
 * security method gets shown).
 */
public class ScrimController implements ViewTreeObserver.OnPreDrawListener {
    public static final long ANIMATION_DURATION = 220;
    public static final Interpolator KEYGUARD_FADE_OUT_INTERPOLATOR
            = new PathInterpolator(0f, 0, 0.7f, 1f);
    private static final float SCRIM_BEHIND_ALPHA = 0.62f;
    private static final float SCRIM_BEHIND_ALPHA_KEYGUARD = 0.45f;
    private static final float SCRIM_BEHIND_ALPHA_UNLOCKING = 0.2f;
    private static final float SCRIM_IN_FRONT_ALPHA = 0.75f;
    private static final int TAG_KEY_ANIM = R.id.scrim;
    private static final int TAG_KEY_ANIM_TARGET = R.id.scrim_target;
    private static final int TAG_START_ALPHA = R.id.scrim_alpha_start;
    private static final int TAG_END_ALPHA = R.id.scrim_alpha_end;

    protected final ScrimView mScrimBehind;
    private final ScrimView mScrimInFront;

    private float mScrimBehindAlpha = SCRIM_BEHIND_ALPHA;
    private float mScrimBehindAlphaKeyguard = SCRIM_BEHIND_ALPHA_KEYGUARD;
    private float mScrimBehindAlphaUnlocking = SCRIM_BEHIND_ALPHA_UNLOCKING;

    private float mFraction;

    private boolean mDarkenWhileDragging;
    private boolean mWakeAndUnlocking;
    private boolean mAnimateChange;
    private boolean mUpdatePending;
    private boolean mExpanding;
    private long mDurationOverride = -1;
    private long mAnimationDelay;
    private Runnable mOnAnimationFinished;
    private final Interpolator mInterpolator = new DecelerateInterpolator();
    private float mCurrentInFrontAlpha;
    private float mCurrentBehindAlpha;
    private boolean mForceHideScrims;
    private boolean mSkipFirstFrame;

    public ScrimController(ScrimView scrimBehind, ScrimView scrimInFront, View headsUpScrim) {
        mScrimBehind = scrimBehind;
        mScrimInFront = scrimInFront;
        final Context context = scrimBehind.getContext();
    }

    public void setShowScrimBehind(boolean show) {
        if (show) {
            mScrimBehindAlpha = SCRIM_BEHIND_ALPHA;
        } else {
            mScrimBehindAlpha = 0;
        }
        scheduleUpdate();
    }

    public void onTrackingStarted() {
        mExpanding = true;
    }

    public void onExpandingFinished() {
        mExpanding = false;
    }

    public void setPanelExpansion(float fraction) {
        if (mFraction != fraction) {
            mFraction = fraction;
            scheduleUpdate();
        }
    }

    public void animateGoingToFullShade(long delay, long duration) {
        mDurationOverride = duration;
        mAnimationDelay = delay;
        mAnimateChange = true;
        scheduleUpdate();
    }

    public void animateNextChange() {
        mAnimateChange = true;
    }

    private void scheduleUpdate() {
        if (mUpdatePending) return;

        // Make sure that a frame gets scheduled.
        mScrimBehind.invalidate();
        mScrimBehind.getViewTreeObserver().addOnPreDrawListener(this);
        mUpdatePending = true;
    }

    protected void updateScrims() {
        if (mForceHideScrims) {
            setScrimInFrontColor(0f);
            setScrimBehindColor(0f);
        }
        mAnimateChange = false;
    }

    private void updateScrimNormal() {
        float frac = mFraction;
        // let's start this 20% of the way down the screen
        frac = frac * 1.2f - 0.2f;
        if (frac <= 0) {
            setScrimBehindColor(0);
        } else {
            // woo, special effects
            final float k = (float)(1f-0.5f*(1f-Math.cos(3.14159f * Math.pow(1f-frac, 2f))));
            setScrimBehindColor(k * mScrimBehindAlpha);
        }
    }

    private void setScrimBehindColor(float alpha) {
        setScrimColor(mScrimBehind, alpha);
    }

    private void setScrimInFrontColor(float alpha) {
        setScrimColor(mScrimInFront, alpha);
        if (alpha == 0f) {
            mScrimInFront.setClickable(false);
        }
    }

    private void setScrimColor(View scrim, float alpha) {
        updateScrim(mAnimateChange, scrim, alpha, getCurrentScrimAlpha(scrim));
    }

    private float getCurrentScrimAlpha(View scrim) {
        return scrim == mScrimBehind ? mCurrentBehindAlpha
                : scrim == mScrimInFront ? mCurrentInFrontAlpha
                : mCurrentHeadsUpAlpha;
    }

    private void setCurrentScrimAlpha(View scrim, float alpha) {
        if (scrim == mScrimBehind) {
            mCurrentBehindAlpha = alpha;
        } else if (scrim == mScrimInFront) {
            mCurrentInFrontAlpha = alpha;
        } else {
            alpha = Math.max(0.0f, Math.min(1.0f, alpha));
            mCurrentHeadsUpAlpha = alpha;
        }
    }

    private void updateScrimColor(View scrim) {
        float alpha1 = getCurrentScrimAlpha(scrim);
        if (scrim instanceof ScrimView) {
            float alpha2 = getDozeAlpha(scrim);
            float alpha = 1 - (1 - alpha1) * (1 - alpha2);
            alpha = Math.max(0, Math.min(1.0f, alpha));
            ((ScrimView) scrim).setScrimColor(Color.argb((int) (alpha * 255), 0, 0, 0));
        } else {
            scrim.setAlpha(alpha1);
        }
    }

    private void startScrimAnimation(final View scrim, float target) {
        float current = getCurrentScrimAlpha(scrim);
        ValueAnimator anim = ValueAnimator.ofFloat(current, target);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float alpha = (float) animation.getAnimatedValue();
                setCurrentScrimAlpha(scrim, alpha);
                updateScrimColor(scrim);
            }
        });
        anim.setInterpolator(getInterpolator());
        anim.setStartDelay(mAnimationDelay);
        anim.setDuration(mDurationOverride != -1 ? mDurationOverride : ANIMATION_DURATION);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mOnAnimationFinished != null) {
                    mOnAnimationFinished.run();
                    mOnAnimationFinished = null;
                }
                scrim.setTag(TAG_KEY_ANIM, null);
                scrim.setTag(TAG_KEY_ANIM_TARGET, null);
            }
        });
        anim.start();
        if (mSkipFirstFrame) {
            anim.setCurrentPlayTime(16);
        }
        scrim.setTag(TAG_KEY_ANIM, anim);
        scrim.setTag(TAG_KEY_ANIM_TARGET, target);
    }

    private Interpolator getInterpolator() {
        return mAnimateKeyguardFadingOut ? KEYGUARD_FADE_OUT_INTERPOLATOR : mInterpolator;
    }

    @Override
    public boolean onPreDraw() {
        mScrimBehind.getViewTreeObserver().removeOnPreDrawListener(this);
        mUpdatePending = false;
        updateScrims();
        mDurationOverride = -1;
        mAnimationDelay = 0;
        mSkipFirstFrame = false;

        return true;
    }

    private boolean isAnimating(View scrim) {
        return scrim.getTag(TAG_KEY_ANIM) != null;
    }

    public void setDrawBehindAsSrc(boolean asSrc) {
        mScrimBehind.setDrawAsSrc(asSrc);
    }

    private void updateScrim(boolean animate, View scrim, float alpha, float currentAlpha) {
        ValueAnimator previousAnimator = StackStateAnimator.getChildTag(scrim,
                TAG_KEY_ANIM);
        float animEndValue = -1;
        if (previousAnimator != null) {
            if (animate || alpha == currentAlpha) {
                previousAnimator.cancel();
            } else {
                animEndValue = StackStateAnimator.getChildTag(scrim, TAG_END_ALPHA);
            }
        }
        if (alpha != currentAlpha && alpha != animEndValue) {
            if (animate) {
                startScrimAnimation(scrim, alpha);
                scrim.setTag(TAG_START_ALPHA, currentAlpha);
                scrim.setTag(TAG_END_ALPHA, alpha);
            } else {
                if (previousAnimator != null) {
                    float previousStartValue = StackStateAnimator.getChildTag(scrim,
                            TAG_START_ALPHA);
                    float previousEndValue = StackStateAnimator.getChildTag(scrim,
                            TAG_END_ALPHA);
                    // we need to increase all animation keyframes of the previous animator by the
                    // relative change to the end value
                    PropertyValuesHolder[] values = previousAnimator.getValues();
                    float relativeDiff = alpha - previousEndValue;
                    float newStartValue = previousStartValue + relativeDiff;
                    newStartValue = Math.max(0, Math.min(1.0f, newStartValue));
                    values[0].setFloatValues(newStartValue, alpha);
                    scrim.setTag(TAG_START_ALPHA, newStartValue);
                    scrim.setTag(TAG_END_ALPHA, alpha);
                    previousAnimator.setCurrentPlayTime(previousAnimator.getCurrentPlayTime());
                } else {
                    // update the alpha directly
                    setCurrentScrimAlpha(scrim, alpha);
                    updateScrimColor(scrim);
                }
            }
        }
    }

    public void forceHideScrims(boolean hide) {
        mForceHideScrims = hide;
        mAnimateChange = false;
        scheduleUpdate();
    }

    public void setExcludedBackgroundArea(Rect area) {
        mScrimBehind.setExcludedArea(area);
    }

    public int getScrimBehindColor() {
        return mScrimBehind.getScrimColorWithAlpha();
    }

    public void setScrimBehindChangeRunnable(Runnable changeRunnable) {
        mScrimBehind.setChangeRunnable(changeRunnable);
    }

    public void onDensityOrFontScaleChanged() {
    }
}
