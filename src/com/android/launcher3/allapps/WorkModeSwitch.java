/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.allapps;

import static com.android.launcher3.workprofile.PersonalWorkSlidingTabStrip.getTabWidth;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.KeyboardInsetAnimationCallback;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.StringCache;
import com.android.launcher3.views.ActivityContext;
/**
 * Work profile toggle switch shown at the bottom of AllApps work tab
 */
public class WorkModeSwitch extends LinearLayout implements Insettable,
        KeyboardInsetAnimationCallback.KeyboardInsetListener {

    private static final int FLAG_FADE_ONGOING = 1 << 1;
    private static final int FLAG_TRANSLATION_ONGOING = 1 << 2;
    private static final int FLAG_PROFILE_TOGGLE_ONGOING = 1 << 3;
    private static final int SCROLL_THRESHOLD_DP = 10;

    private final Rect mInsets = new Rect();
    private final Rect mImeInsets = new Rect();
    private int mFlags;
    private final ActivityContext mActivityContext;

    // Threshold when user scrolls up/down to determine when should button extend/collapse
    private final int mScrollThreshold;
    private ImageView mIcon;
    private TextView mTextView;
    private final StatsLogManager mStatsLogManager;
    private boolean mDoPause = true;


    public WorkModeSwitch(@NonNull Context context) {
        this(context, null, 0);
    }

    public WorkModeSwitch(@NonNull Context context, @NonNull AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WorkModeSwitch(@NonNull Context context, @NonNull AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mScrollThreshold = Utilities.dpToPx(SCROLL_THRESHOLD_DP);
        mActivityContext = ActivityContext.lookupContext(getContext());
        mStatsLogManager = mActivityContext.getStatsLogManager();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mIcon = findViewById(R.id.work_icon);
        mTextView = findViewById(R.id.pause_text);
        setSelected(true);
        if (Utilities.ATLEAST_R) {
            KeyboardInsetAnimationCallback keyboardInsetAnimationCallback =
                    new KeyboardInsetAnimationCallback(this);
            setWindowInsetsAnimationCallback(keyboardInsetAnimationCallback);
        }

        setInsets(mActivityContext.getDeviceProfile().getInsets());
        updatePauseMode();
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        updateTranslationY();
        MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
        if (lp != null) {
            int bottomMargin = getResources().getDimensionPixelSize(R.dimen.work_fab_margin_bottom);
            DeviceProfile dp = ActivityContext.lookupContext(getContext()).getDeviceProfile();
            if (mActivityContext.getAppsView().isSearchBarFloating()) {
                bottomMargin += dp.hotseatQsbHeight;
            }

            if (!dp.isGestureMode && dp.isTaskbarPresent) {
                bottomMargin += dp.taskbarHeight;
            }

            lp.bottomMargin = bottomMargin;
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        View parent = (View) getParent();
        boolean isRtl = Utilities.isRtl(getResources());
        Rect allAppsPadding = mActivityContext.getDeviceProfile().allAppsPadding;
        int size = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight()
                - (allAppsPadding.left + allAppsPadding.right);
        int tabWidth = getTabWidth(getContext(), size);
        int shift = (size - tabWidth) / 2 + (isRtl ? allAppsPadding.left : allAppsPadding.right);
        setTranslationX(isRtl ? shift : -shift);
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled() && getVisibility() == VISIBLE && mFlags == 0;
    }

    public void animateVisibility(boolean visible) {
        clearAnimation();
        if (visible) {
            setFlag(FLAG_FADE_ONGOING);
            setVisibility(VISIBLE);
            extend();
            animate().alpha(1).withEndAction(() -> removeFlag(FLAG_FADE_ONGOING)).start();
        } else if (getVisibility() != GONE) {
            setFlag(FLAG_FADE_ONGOING);
            animate().alpha(0).withEndAction(() -> {
                removeFlag(FLAG_FADE_ONGOING);
                setVisibility(GONE);
            }).start();
        }
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        WindowInsetsCompat windowInsetsCompat =
                WindowInsetsCompat.toWindowInsetsCompat(insets, this);
        if (windowInsetsCompat.isVisible(WindowInsetsCompat.Type.ime())) {
            setInsets(mImeInsets, windowInsetsCompat.getInsets(WindowInsetsCompat.Type.ime()));
        } else {
            mImeInsets.setEmpty();
        }
        updateTranslationY();
        return super.onApplyWindowInsets(insets);
    }

    void updateTranslationY() {
        setTranslationY(-mImeInsets.bottom);
    }

    @Override
    public void setTranslationY(float translationY) {
        // Always translate at least enough for nav bar insets.
        super.setTranslationY(Math.min(translationY, -mInsets.bottom));
    }

    private void setInsets(Rect rect, Insets insets) {
        rect.set(insets.left, insets.top, insets.right, insets.bottom);
    }

    public Rect getImeInsets() {
        return mImeInsets;
    }

    @Override
    public void onTranslationStart() {
        setFlag(FLAG_TRANSLATION_ONGOING);
    }

    @Override
    public void onTranslationEnd() {
        removeFlag(FLAG_TRANSLATION_ONGOING);
    }

    private void setFlag(int flag) {
        mFlags |= flag;
    }

    private void removeFlag(int flag) {
        mFlags &= ~flag;
    }

    public void extend() {
        mTextView.setVisibility(VISIBLE);
    }

    public void shrink(){
        mTextView.setVisibility(GONE);
    }

    public int getScrollThreshold() {
        return mScrollThreshold;
    }

    public void updateStringFromCache() {
        StringCache cache = mActivityContext.getStringCache();
        if (cache != null) {
            mTextView.setText(mDoPause ? cache.workProfilePauseButton :
                    cache.workProfileEnableButton);
        }
    }

    private void updatePauseMode() {
        mIcon.setImageResource(mDoPause ? R.drawable.ic_corp_off : R.drawable.ic_corp);
        updateStringFromCache();
    }

    public void setPauseMode(final boolean doPause) {
        mDoPause = doPause;
        updatePauseMode();
    }
}
