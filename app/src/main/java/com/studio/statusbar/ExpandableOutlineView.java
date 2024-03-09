package com.studio.statusbar;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * Like {@link ExpandableView}, but setting an outline for the height and clipping.
 */
public abstract class ExpandableOutlineView extends ExpandableView {

    private final Rect mOutlineRect = new Rect();
    private boolean mCustomOutline;
    private float mOutlineAlpha = -1f;

    ViewOutlineProvider mProvider = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            int translation = (int) getTranslation();
            if (!mCustomOutline) {
                outline.setRect(translation,
                        mClipTopAmount,
                        getWidth() + translation,
                        Math.max(getActualHeight(), mClipTopAmount));
            } else {
                outline.setRect(mOutlineRect);
            }
            outline.setAlpha(mOutlineAlpha);
        }
    };

    public ExpandableOutlineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOutlineProvider(mProvider);
    }

    @Override
    public void setActualHeight(int actualHeight, boolean notifyListeners) {
        super.setActualHeight(actualHeight, notifyListeners);
        invalidateOutline();
    }

    @Override
    public void setClipTopAmount(int clipTopAmount) {
        super.setClipTopAmount(clipTopAmount);
        invalidateOutline();
    }

    protected void setOutlineAlpha(float alpha) {
        if (alpha != mOutlineAlpha) {
            mOutlineAlpha = alpha;
            invalidateOutline();
        }
    }

    @Override
    public float getOutlineAlpha() {
        return mOutlineAlpha;
    }

    protected void setOutlineRect(RectF rect) {
        if (rect != null) {
            setOutlineRect(rect.left, rect.top, rect.right, rect.bottom);
        } else {
            mCustomOutline = false;
            setClipToOutline(false);
            invalidateOutline();
        }
    }

    @Override
    public int getOutlineTranslation() {
        return mCustomOutline ? mOutlineRect.left : (int) getTranslation();
    }

    public void updateOutline() {
        if (mCustomOutline) {
            return;
        }
        boolean hasOutline = true;
        if (isChildInGroup()) {
            hasOutline = isGroupExpanded() && !isGroupExpansionChanging();
        } else if (isSummaryWithChildren()) {
            hasOutline = !isGroupExpanded() || isGroupExpansionChanging();
        }
        setOutlineProvider(hasOutline ? mProvider : null);
    }

    public boolean isOutlineShowing() {
        ViewOutlineProvider op = getOutlineProvider();
        return op != null;
    }

    protected void setOutlineRect(float left, float top, float right, float bottom) {
        mCustomOutline = true;
        setClipToOutline(true);

        mOutlineRect.set((int) left, (int) top, (int) right, (int) bottom);

        // Outlines need to be at least 1 dp
        mOutlineRect.bottom = (int) Math.max(top, mOutlineRect.bottom);
        mOutlineRect.right = (int) Math.max(left, mOutlineRect.right);

        invalidateOutline();
    }
}
