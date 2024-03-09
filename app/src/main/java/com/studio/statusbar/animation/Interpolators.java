package com.studio.statusbar.animation;

import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

/**
 * Utility class to receive interpolators from
 */
public class Interpolators {
    public static final Interpolator FAST_OUT_SLOW_IN = new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    public static final Interpolator FAST_OUT_LINEAR_IN = new PathInterpolator(0.4f, 0f, 1f, 1f);
    public static final Interpolator LINEAR_OUT_SLOW_IN = new PathInterpolator(0f, 0f, 0.2f, 1f);
    public static final Interpolator ALPHA_IN = new PathInterpolator(0.4f, 0f, 1f, 1f);
    public static final Interpolator ALPHA_OUT = new PathInterpolator(0f, 0f, 0.8f, 1f);
    public static final Interpolator LINEAR = new LinearInterpolator();
    public static final Interpolator ACCELERATE = new AccelerateInterpolator();
    public static final Interpolator ACCELERATE_DECELERATE = new AccelerateDecelerateInterpolator();
    public static final Interpolator DECELERATE_QUINT = new DecelerateInterpolator(2.5f);
    public static final Interpolator CUSTOM_40_40 = new PathInterpolator(0.4f, 0f, 0.6f, 1f);
        public static final Interpolator ICON_OVERSHOT = new PathInterpolator(0.4f, 0f, 0.2f, 1.4f);
    public static final Interpolator PANEL_CLOSE_ACCELERATED
            = new PathInterpolator(0.3f, 0, 0.5f, 1);
    public static final Interpolator BOUNCE = new BounceInterpolator();

    /**
     * Interpolator to be used when animating a move based on a click. Pair with enough duration.
     */
    public static final Interpolator TOUCH_RESPONSE =
            new PathInterpolator(0.3f, 0f, 0.1f, 1f);
}
