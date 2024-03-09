package com.studio.statusbar;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;

/**
 * A Button which doesn't have overlapping drawing commands
 */
public class AlphaOptimizedButton extends Button {
    public AlphaOptimizedButton(Context context) {
        super(context);
    }

    public AlphaOptimizedButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AlphaOptimizedButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AlphaOptimizedButton(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
