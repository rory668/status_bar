package com.studio.statusbar;

import android.view.MotionEvent;

// ACHTUNG!
public interface Gefingerpoken {
    boolean onInterceptTouchEvent(MotionEvent ev);
    boolean onTouchEvent(MotionEvent ev);
}
