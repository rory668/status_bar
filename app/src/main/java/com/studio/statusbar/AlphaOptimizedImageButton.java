package com.studio.statusbar;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageButton;

/**
 * A frame layout which does not have overlapping renderings commands and therefore does not need a
 * layer when alpha is changed.
 */
public class AlphaOptimizedImageButton extends ImageButton {

    public AlphaOptimizedImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
