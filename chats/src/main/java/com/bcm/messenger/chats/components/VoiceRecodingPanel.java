package com.bcm.messenger.chats.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.format.DateUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import com.bcm.messenger.chats.R;
import com.bcm.messenger.common.ui.KeyboardAwareLinearLayout;
import com.bcm.messenger.common.utils.AppUtilKotlinKt;
import com.bcm.messenger.utility.ViewUtils;
import com.bcm.messenger.utility.concurrent.ListenableFuture;
import com.bcm.messenger.utility.concurrent.SettableFuture;

import java.util.concurrent.TimeUnit;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

/**
 * @author ling
 */
public class VoiceRecodingPanel implements KeyboardAwareLinearLayout.OnKeyboardShownListener, View.OnTouchListener {

    private static final String TAG = VoiceRecodingPanel.class.getSimpleName();

    private static final int FADE_TIME = 150;
    private static final int ANIMATION_DURATION = 200;

    private View slideBgView;
    private ImageView recordDotView;
    private SlideToCancel slideToCancel;
    private TextView recordTimeView;
    private @Nullable
    Listener listener;

    private boolean actionInProgress;
    private View recordButton;
    private View recordButtonFab;

    private float startPositionX;
    private float lastPositionX;
    private ObjectAnimator alphaIn;
    private ObjectAnimator alphaOut;

    public void onFinishInflate(View view) {

        this.slideBgView = view.findViewById(R.id.slide_bg_view);
        this.recordTimeView = view.findViewById(R.id.record_time);
        this.slideToCancel = new SlideToCancel(view.findViewById(R.id.slide_to_cancel));
        this.recordDotView = view.findViewById(R.id.record_dot_view);
        recordButton = view.findViewById(R.id.panel_audio_toggle);
        recordButton.setOnTouchListener(this);

        recordButtonFab = view.findViewById(R.id.quick_audio_fab);
    }

    public void setListener(final @NonNull Listener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        lastPositionX = event.getX();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                this.actionInProgress = true;
                if (null != listener) {
                    listener.onStartClicked();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (listener != null) {
                    listener.onCancelClicked();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (this.actionInProgress) {
                    this.recordButton.setVisibility(VISIBLE);
                    if (listener != null) {
                        listener.onFinishClicked();
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (this.actionInProgress) {
                    moveTo(event.getX());
                    onRecordMoved(event.getX(), event.getRawX());
                }
                break;
            default:
                break;
        }

        return false;
    }


    private void playRedDot() {
        alphaIn = ObjectAnimator.ofFloat(recordDotView, "alpha", 1.0f, 0.3f).setDuration(500);
        alphaOut = ObjectAnimator.ofFloat(recordDotView, "alpha", 0.3f, 1.0f).setDuration(500);
        alphaIn.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (alphaOut != null) {
                    alphaOut.start();
                }
            }
        });
        alphaOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (alphaIn != null) {
                    alphaIn.start();
                }
            }
        });
        alphaIn.start();
    }

    private void stopRedDot() {
        if (alphaIn != null) {
            alphaIn.cancel();
        }
        if (alphaOut != null) {
            alphaOut.cancel();
        }
        alphaIn = null;
        alphaOut = null;
    }

    private void onRecordMoved(float x, float absoluteX) {
        slideToCancel.moveTo(x);
        float position = absoluteX / slideBgView.getWidth();

        if (position <= 0.7) {
            if (null != listener) {
                listener.onCancelClicked();
            }
            recordDotView.setImageResource(R.drawable.common_close_icon);
            recordDotView.getDrawable().setTint(AppUtilKotlinKt.getAttrColor(recordDotView.getContext(), R.attr.common_setting_item_warn_color));
        }
    }

    public void onPause() {
        if (actionInProgress && null != listener) {
            listener.onCancelClicked();
        }
    }


    @Override
    public void onKeyboardShown() {
    }

    private void display(float x) {
        this.startPositionX = x;
        this.lastPositionX = x;

        recordButtonFab.setVisibility(VISIBLE);
        recordButtonFab.setX(getWidthAdjustment() + getOffset(x));

        AnimationSet animation = new AnimationSet(true);

        ScaleAnimation scaleAnimation = new ScaleAnimation(0.5f, 1f, 0.5f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        animation.addAnimation(scaleAnimation);
        animation.setFillBefore(true);
        animation.setFillAfter(true);
        animation.setDuration(ANIMATION_DURATION);
        animation.setInterpolator(new OvershootInterpolator());

        recordButtonFab.startAnimation(animation);
    }

    private void moveTo(float x) {
        this.lastPositionX = x;

        float offset = getOffset(x);
        int widthAdjustment = getWidthAdjustment();

        recordButtonFab.setX(widthAdjustment + offset);
    }

    private void hide(float x) {
        this.lastPositionX = x;

        float offset = getOffset(x);
        int widthAdjustment = getWidthAdjustment();

        AnimationSet animation = new AnimationSet(false);
        Animation scaleAnimation = new ScaleAnimation(1, 0.5f, 1, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);

        Animation translateAnimation = new TranslateAnimation(Animation.ABSOLUTE, offset + widthAdjustment,
                Animation.ABSOLUTE, widthAdjustment,
                Animation.RELATIVE_TO_SELF, -.25f,
                Animation.RELATIVE_TO_SELF, -.25f);

        scaleAnimation.setInterpolator(new AnticipateOvershootInterpolator(1.5f));
        translateAnimation.setInterpolator(new DecelerateInterpolator());
        animation.addAnimation(scaleAnimation);
        animation.addAnimation(translateAnimation);
        animation.setDuration(ANIMATION_DURATION);
        animation.setFillBefore(true);
        animation.setFillAfter(false);
        animation.setInterpolator(new AnticipateOvershootInterpolator(1.5f));

        recordButtonFab.setVisibility(View.GONE);
        recordButtonFab.clearAnimation();
        recordButtonFab.startAnimation(animation);
    }

    private float getOffset(float x) {
        return ViewCompat.getLayoutDirection(recordButtonFab) == ViewCompat.LAYOUT_DIRECTION_LTR ?
                -Math.max(0, this.startPositionX - x) : Math.max(0, x - this.startPositionX);
    }

    private int getWidthAdjustment() {
        int width = recordButtonFab.getWidth() / 4;
        return ViewCompat.getLayoutDirection(recordButtonFab) == ViewCompat.LAYOUT_DIRECTION_LTR ? -width : width;
    }

    public void showPlayTime(long playTime) {
        recordTimeView.setVisibility(VISIBLE);
        this.recordTimeView.setText(DateUtils.formatElapsedTime(TimeUnit.MILLISECONDS.toSeconds(playTime)));
        ViewUtils.INSTANCE.fadeIn(this.recordTimeView, FADE_TIME);
    }

    private void hidePlayTime() {
        ViewUtils.INSTANCE.fadeOut(this.recordTimeView, FADE_TIME, View.VISIBLE);
    }

    public void hideRecordState() {
        this.actionInProgress = false;

        hide(lastPositionX);
        this.recordButton.setVisibility(VISIBLE);

        slideToCancel.hide(lastPositionX);
        hidePlayTime();
        slideBgView.setVisibility(View.GONE);
        stopRedDot();
        recordDotView.setVisibility(View.GONE);
    }

    public void showRecordState() {
        showPlayTime(0);
        recordDotView.setImageResource(R.drawable.chats_record_dot);
        slideToCancel.display(startPositionX);
        slideBgView.setVisibility(VISIBLE);
        ViewUtils.INSTANCE.fadeIn(recordDotView, FADE_TIME);
        recordDotView.setVisibility(VISIBLE);
        playRedDot();

        this.actionInProgress = true;
        this.recordButton.setVisibility(INVISIBLE);
        display(lastPositionX);
    }

    private static class SlideToCancel {

        private final TextView slideToCancelView;

        private float startPositionX;

        public SlideToCancel(TextView slideToCancelView) {
            this.slideToCancelView = slideToCancelView;

            Context context = slideToCancelView.getContext();
            int clr = context.getResources().getColor(R.color.common_foreground_color);
            for (Drawable drawable : slideToCancelView.getCompoundDrawables()) {
                if (drawable != null) {
                    drawable.setColorFilter(new PorterDuffColorFilter(clr, PorterDuff.Mode.SRC_IN));
                }
            }
        }

        public void display(float startPositionX) {
            this.startPositionX = startPositionX;
            ViewUtils.INSTANCE.fadeIn(this.slideToCancelView, FADE_TIME);
        }

        public ListenableFuture<Void> hide(float x) {
            final SettableFuture<Void> future = new SettableFuture<>();
            float offset = getOffset(x);

            AnimationSet animation = new AnimationSet(true);
            animation.addAnimation(new TranslateAnimation(Animation.ABSOLUTE, offset,
                    Animation.ABSOLUTE, 0,
                    Animation.RELATIVE_TO_SELF, 0,
                    Animation.RELATIVE_TO_SELF, 0));
            animation.addAnimation(new AlphaAnimation(1, 0));

            animation.setDuration(ANIMATION_DURATION);
            animation.setFillBefore(true);
            animation.setFillAfter(false);
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    future.set(null);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });

            slideToCancelView.setVisibility(View.GONE);
            slideToCancelView.startAnimation(animation);

            return future;
        }

        public void moveTo(float x) {
            float offset = getOffset(x);
            Animation animation = new TranslateAnimation(Animation.ABSOLUTE, offset,
                    Animation.ABSOLUTE, offset,
                    Animation.RELATIVE_TO_SELF, 0,
                    Animation.RELATIVE_TO_SELF, 0);

            animation.setDuration(0);
            animation.setFillAfter(true);
            animation.setFillBefore(true);

            slideToCancelView.startAnimation(animation);
        }

        private float getOffset(float x) {
            return ViewCompat.getLayoutDirection(slideToCancelView) == ViewCompat.LAYOUT_DIRECTION_LTR ?
                    -Math.max(0, this.startPositionX - x) : Math.max(0, x - this.startPositionX);
        }

    }

    public interface Listener {
        void onStartClicked();

        void onFinishClicked();

        void onCancelClicked();
    }
}
