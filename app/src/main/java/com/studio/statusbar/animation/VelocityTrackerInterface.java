package com.studio.statusbar.animation;

import android.view.MotionEvent;

/**
 * An interface for a velocity tracker to delegate. To be implemented by different velocity tracking
 * algorithms.
 */
public interface VelocityTrackerInterface {
    public void addMovement(MotionEvent event);
    public void computeCurrentVelocity(int units);
    public float getXVelocity();
    public float getYVelocity();
    public void recycle();
}
