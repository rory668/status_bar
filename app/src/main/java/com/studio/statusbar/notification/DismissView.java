package com.studio.statusbar.notification;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.View;

import com.studio.statusbar.R;

public class DismissView extends StackScrollerDecorView {
    private DismissViewButton mDismissButton;

    public DismissView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected View findContentView() {
        return findViewById(R.id.dismiss_text);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mDismissButton = (DismissViewButton) findContentView();
    }

    public void setOnButtonClickListener(OnClickListener listener) {
        mContent.setOnClickListener(listener);
    }

    public boolean isOnEmptySpace(float touchX, float touchY) {
        return touchX < mContent.getX()
                || touchX > mContent.getX() + mContent.getWidth()
                || touchY < mContent.getY()
                || touchY > mContent.getY() + mContent.getHeight();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDismissButton.setText(R.string.clear_all_notifications_text);
    }

    public boolean isButtonVisible() {
        return mDismissButton.getAlpha() != 0.0f;
    }
}
