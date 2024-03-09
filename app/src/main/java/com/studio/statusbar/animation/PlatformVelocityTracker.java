package com.studio.statusbar.animation;

import androidx.core.util.Pools;
import android.view.MotionEvent;
import android.view.VelocityTracker;

/**
 * An implementation of {@link VelocityTrackerInterface} using the platform-standard
 * {@link VelocityTracker}.
 */
public class PlatformVelocityTracker implements VelocityTrackerInterface {

    private static final Pools.SynchronizedPool<PlatformVelocityTracker> sPool =
            new Pools.SynchronizedPool<>(2);

    private VelocityTracker mTracker;

    public static PlatformVelocityTracker obtain() {
        PlatformVelocityTracker tracker = sPool.acquire();
        if (tracker == null) {
            tracker = new PlatformVelocityTracker();
        }
        tracker.setTracker(VelocityTracker.obtain());
        return tracker;
    }

    public void setTracker(VelocityTracker tracker) {
        mTracker = tracker;
    }

    @Override
    public void addMovement(MotionEvent event) {
        mTracker.addMovement(event);
    }

    @Override
    public void computeCurrentVelocity(int units) {
        mTracker.computeCurrentVelocity(units);
    }

    @Override
    public float getXVelocity() {
        return mTracker.getXVelocity();
    }

    @Override
    public float getYVelocity() {
        return mTracker.getYVelocity();
    }

    @Override
    public void recycle() {
        mTracker.recycle();
        sPool.release(this);
    }
}
