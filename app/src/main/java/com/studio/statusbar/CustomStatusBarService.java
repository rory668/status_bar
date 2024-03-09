package com.studio.statusbar;

import android.accessibilityservice.AccessibilityService;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

public class CustomStatusBarService extends AccessibilityService {
    private WindowManager windowManager;
    private View customStatusBarView;

    private boolean isSwipeDownEnabled;

    private boolean isPermissionGranted;

    private Animation fadeInAnimation;
    private Animation fadeOutAnimation;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        fadeOutAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_out);
        if (!Settings.canDrawOverlays(this)) {
            // Permission not granted, set the flag accordingly
            isPermissionGranted = false;
        } else {
            // Permission granted, proceed with creating the custom status bar
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createCustomStatusBar();
            }
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        removeCustomStatusBar();
        return super.onUnbind(intent);
    }

    @Override
    public boolean onGesture(int gestureId) {
        if (isSwipeDownEnabled && gestureId == GESTURE_SWIPE_DOWN) {
            //showCustomQuickSettingsPanel();
            return true;
        }
        return super.onGesture(gestureId);
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            //showCustomQuickSettingsPanel();
            return true;
        }
        return super.onKeyEvent(event);
    }

    private boolean isBottomLayoutVisible = false;

    @SuppressLint("InflateParams")
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createCustomStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.custom_status_bar_initial_width),
                getResources().getDimensionPixelSize(R.dimen.custom_status_bar_height),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        layoutParams.gravity = Gravity.TOP | Gravity.END;

        LayoutInflater inflater = LayoutInflater.from(this);
        customStatusBarView = inflater.inflate(R.layout.custom_status_bar_layout, null);
        isPermissionGranted = true;
        View customQSPanelView = inflater.inflate(R.layout.custom_qs_panel_layout, null);
        
        customStatusBarView.setOnTouchListener(new View.OnTouchListener() {
            private float startY;
            private float startTranslationY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Capture the initial touch down event
                        return true;
                    case MotionEvent.ACTION_UP:
                        // Handle swipe down gesture
                        if (event.getY() > v.getHeight() / 2) {
                            showCustomQuickSettingsPanel();
                            return true;
                        }
                }
                return false;
            }
        });

        windowManager.addView(customStatusBarView, layoutParams);
    }

    private void removeCustomStatusBar() {
        if (windowManager != null && customStatusBarView != null) {
            windowManager.removeView(customStatusBarView);
        }
    }

    private void showCustomQuickSettingsPanel() {
        if (isPermissionGranted && Settings.canDrawOverlays(this)) {
            LayoutInflater inflater = LayoutInflater.from(this);
            @SuppressLint("InflateParams") View customQSPanelView = inflater.inflate(R.layout.custom_qs_panel_layout, null);
            
            // Display the custom quick settings panel
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSPARENT);
            layoutParams.gravity = Gravity.TOP;

            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            windowManager.addView(customQSPanelView, layoutParams);

            final Context context = this;

            // Find the root view in the custom panel layout
	        View rootView = customQSPanelView.findViewById(R.id.cardView_gear);
            rootView.setTranslationY(-rootView.getHeight());

            // Animate the translation to slide the panel into view
            ValueAnimator animator = ValueAnimator.ofFloat(rootView.getHeight(), 0);
            animator.setDuration(300);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float translationY = (float) animation.getAnimatedValue();
                    rootView.setTranslationY(translationY);
                }
            });
            animator.start();
	        // Handle the swipe-down gesture to close the panel
            rootView.setOnTouchListener(new View.OnTouchListener() {
                private float startY;
                private float startTranslationY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            startY = event.getRawY();
                            startTranslationY = v.getTranslationY();
			                // Show the custom quick settings panel with fade-in animation
                            customQSPanelView.startAnimation(fadeInAnimation);
                            break;
                        case MotionEvent.ACTION_MOVE:
                            float deltaY = event.getRawY() - startY;
                            float translationY = startTranslationY + deltaY;
                            if (translationY <= 0) {
                                // Translate the panel with the swipe gesture
                                v.setTranslationY(translationY);
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                            float deltaReleaseY = event.getRawY() - startY;
                            float releaseTranslationY = startTranslationY + deltaReleaseY;
                            if (-releaseTranslationY >= v.getHeight() / 2) {
                                // Close the panel if released more than halfway
                                ValueAnimator closeAnimator = ValueAnimator.ofFloat(releaseTranslationY, -v.getHeight());
                                closeAnimator.setDuration(300);
                                closeAnimator.setInterpolator(new DecelerateInterpolator());
                                closeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                    @Override
                                    public void onAnimationUpdate(ValueAnimator animation) {
                                        float translationY = (float) animation.getAnimatedValue();
                                        v.setTranslationY(translationY);
                                    }
                                });
                                closeAnimator.addListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
					                customQSPanelView.startAnimation(fadeOutAnimation);
                                        windowManager.removeView(customQSPanelView);
                                    }
                                });
                                closeAnimator.start();
                            } else {
                                // Restore the panel position if released less than halfway
                                animator.reverse();
                            }
                            break;
                    }
                    return true;
                }
            });

        } else {
            // Permission not granted, show a message to the user
            Toast.makeText(this, "Permission required to show custom quick settings panel", Toast.LENGTH_SHORT).show();
        }
    }

    public void setSwipeDownEnabled(boolean swipeDownEnabled) {
        isSwipeDownEnabled = swipeDownEnabled;
    }
}

