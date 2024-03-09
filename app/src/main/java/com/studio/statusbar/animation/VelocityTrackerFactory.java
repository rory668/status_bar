package com.studio.statusbar.animation;

import android.content.Context;

import com.studio.statusbar.R;

public class VelocityTrackerFactory {

    public static final String PLATFORM_IMPL = "platform";
    public static final String NOISY_IMPL = "noisy";

    public static VelocityTrackerInterface obtain(Context ctx) {
        String tracker = ctx.getResources().getString(R.string.velocity_tracker_impl);
        switch (tracker) {
            case NOISY_IMPL:
                return NoisyVelocityTracker.obtain();
            case PLATFORM_IMPL:
                return PlatformVelocityTracker.obtain();
            default:
                throw new IllegalStateException("Invalid tracker: " + tracker);
        }
    }
}
