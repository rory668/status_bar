package com.studio.statusbar;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * A frame layout which does not have overlapping renderings commands and therefore does not need a
 * layer when alpha is changed.
 */
public class AlphaOptimizedFrameLayout extends FrameLayout
{
    public AlphaOptimizedFrameLayout(Context context) {
        super(context);
    }

    public AlphaOptimizedFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AlphaOptimizedFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AlphaOptimizedFrameLayout(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
