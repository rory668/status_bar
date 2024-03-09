package com.studio.statusbar.notification;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.ViewGroup;

import com.studio.statusbar.stack.NotificationStackScrollLayout;
import com.studio.statusbar.AlphaOptimizedButton;

public class DismissViewButton extends AlphaOptimizedButton {

    public DismissViewButton(Context context) {
        this(context, null);
    }

    public DismissViewButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DismissViewButton(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public DismissViewButton(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    /**
     * This method returns the drawing rect for the view which is different from the regular
     * drawing rect, since we layout all children in the {@link NotificationStackScrollLayout} at
     * position 0 and usually the translation is neglected. The standard implementation doesn't
     * account for translation.
     *
     * @param outRect The (scrolled) drawing bounds of the view.
     */
    @Override
    public void getDrawingRect(Rect outRect) {
        super.getDrawingRect(outRect);
        float translationX = ((ViewGroup) mParent).getTranslationX();
        float translationY = ((ViewGroup) mParent).getTranslationY();
        outRect.left += translationX;
        outRect.right += translationX;
        outRect.top += translationY;
        outRect.bottom += translationY;
    }
}
