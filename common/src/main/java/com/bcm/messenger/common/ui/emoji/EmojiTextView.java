package com.bcm.messenger.common.ui.emoji;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.widget.TextViewCompat;

import com.bcm.messenger.common.R;
import com.bcm.messenger.common.preferences.TextSecurePreferences;
import com.bcm.messenger.common.ui.emoji.EmojiProvider.EmojiDrawable;
import com.bcm.messenger.common.ui.emoji.parsing.EmojiParser;
import com.bcm.messenger.utility.logger.ALog;

import java.util.Objects;


public class EmojiTextView extends AppCompatTextView {

    private String TAG = "EmojiTextView";
    private static final char ELLIPSIS = '…';

    private boolean scaleEmojis = false;

    private float        originalFontSize = 0;
    private boolean      useSystemEmoji;
    private boolean      sizeChangeInProgress;

    private CharSequence sourceText = null;//原设置的文本
    private BufferType sourceBufferType = null;

    public EmojiTextView(Context context) {
        this(context, null);
    }

    public EmojiTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EmojiTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.EmojiTextView, 0, 0);
        scaleEmojis = a.getBoolean(R.styleable.EmojiTextView_scaleEmojis, false);
        a.recycle();

        a = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.textSize});
        originalFontSize = a.getDimensionPixelSize(0, 0);
        a.recycle();

    }

    @Override public void setText(@Nullable CharSequence text, BufferType type) {

        //ALog.d(TAG, "setText: " + text);
        EmojiProvider             provider   = EmojiProvider.getInstance(getContext());
        EmojiParser.CandidateList candidates = provider.getCandidates(text);

        if (scaleEmojis && candidates != null && candidates.allEmojis) {
            int   emojis = candidates.size();
            float scale  = 1.0f;

            if (emojis <= 8) scale += 0.25f;
            if (emojis <= 6) scale += 0.25f;
            if (emojis <= 4) scale += 0.25f;
            if (emojis <= 2) scale += 0.25f;

            super.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalFontSize * scale);
        } else if (scaleEmojis) {
            super.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalFontSize);
        }

        if (!sizeChangeInProgress && TextUtils.equals(sourceText, text) && Objects.equals(sourceBufferType, type) && useSystemEmoji == useSystemEmoji()) { //对于要设置的文本和原文本一致，则不处理，解决重复绘制的问题
            return;
        }
        sourceText = text;
        sourceBufferType = type;
        useSystemEmoji = useSystemEmoji();

        CharSequence currentText = "";
        if (useSystemEmoji || candidates == null || candidates.size() == 0) {
            currentText = text;
//            super.setText(currentText, BufferType.NORMAL);
            setTextCatchException(currentText, BufferType.NORMAL);

            if (getEllipsize() == TextUtils.TruncateAt.END && getMaxEms() > 0) {
                ellipsizeAnyTextForMaxLength();
            }

        } else {
            currentText = provider.emojify(candidates, text, this);
//            super.setText(currentText, BufferType.SPANNABLE);
            setTextCatchException(currentText, BufferType.SPANNABLE);

            // Android fails to ellipsize spannable strings. (https://issuetracker.google.com/issues/36991688)
            // We ellipsize them ourselves by manually truncating the appropriate section.
            if (getEllipsize() == TextUtils.TruncateAt.END) {
                if (getMaxEms() > 0) {
                    ellipsizeAnyTextForMaxLength();
                }else {
                    ellipsizeEmojiTextForMaxLines();
                }
            }
        }

        ALog.d(TAG, "setText finish: " + text);
    }

    /**
     * setText捕获异常，如果是webview类异常，证明设备可能没有安装webview，所以要取消LinkMask，再设置text一遍
     * @param text
     * @param type
     */
    private void setTextCatchException(@Nullable CharSequence text, BufferType type) {
        try {
            super.setText(text, type);
        }catch (Exception ex) {
            String exceptionMessage = ex.getMessage();
            if (exceptionMessage != null && exceptionMessage.toLowerCase().contains("webview")) {
                setAutoLinkMask(0);
                super.setText(text, type);
            }
        }
    }


    private boolean useSystemEmoji() {
        return TextSecurePreferences.isSystemEmojiPreferred(getContext());
    }

    private void ellipsizeEmojiTextForMaxLines() {
        post(() -> {
            if (getLayout() == null) {
                ellipsizeEmojiTextForMaxLines();
                return;
            }

            int maxLines = TextViewCompat.getMaxLines(EmojiTextView.this);
            ALog.d("EmojiTextView", "maxLines: " + maxLines + ", lineCount: " + getLineCount());
            if (maxLines <= 0) {
                return;
            }

            int lineCount = getLineCount();
            if (lineCount > maxLines) {
                int overflowStart = getLayout().getLineStart(maxLines - 1);
                CharSequence overflow = getText().subSequence(overflowStart, getText().length());
                CharSequence ellipsized = TextUtils.ellipsize(overflow, getPaint(), getWidth(), TextUtils.TruncateAt.END);

                SpannableStringBuilder newContent = new SpannableStringBuilder();
                newContent.append(getText().subSequence(0, overflowStart))
                        .append(ellipsized.subSequence(0, ellipsized.length()));

                EmojiParser.CandidateList newCandidates = EmojiProvider.getInstance(getContext()).getCandidates(newContent);
                CharSequence              emojified     = EmojiProvider.getInstance(getContext()).emojify(newCandidates, newContent, this);

//                super.setText(emojified, BufferType.SPANNABLE);
                setTextCatchException(emojified, BufferType.SPANNABLE);
            }
        });
    }

    private void ellipsizeAnyTextForMaxLength() {
        int maxLength = getMaxEms();
        if (maxLength > 0 && getText().length() > maxLength + 1) {
            SpannableStringBuilder newContent = new SpannableStringBuilder();
            newContent.append(getText().subSequence(0, maxLength)).append(ELLIPSIS);

            EmojiParser.CandidateList newCandidates = EmojiProvider.getInstance(getContext()).getCandidates(newContent);

            if (useSystemEmoji || newCandidates == null || newCandidates.size() == 0) {
//                super.setText(newContent, BufferType.NORMAL);
                setTextCatchException(newContent, BufferType.NORMAL);
            } else {
                CharSequence emojified = EmojiProvider.getInstance(getContext()).emojify(newCandidates, newContent, this);
//                super.setText(emojified, BufferType.SPANNABLE);
                setTextCatchException(emojified, BufferType.SPANNABLE);
            }
        }
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable drawable) {
        if (drawable instanceof EmojiDrawable) invalidate();
        else                                   super.invalidateDrawable(drawable);
    }

    @Override
    public void setTextSize(float size) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
    }

    @Override
    public void setTextSize(int unit, float size) {
        this.originalFontSize = TypedValue.applyDimension(unit, size, getResources().getDisplayMetrics());
        super.setTextSize(unit, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (!sizeChangeInProgress) {
            sizeChangeInProgress = true;
            setText(sourceText, sourceBufferType);
            sizeChangeInProgress = false;
        }
    }

}
