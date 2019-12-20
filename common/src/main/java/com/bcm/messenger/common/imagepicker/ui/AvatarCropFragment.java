/*
 *
 *  * Copyright (C) 2015 Eason.Lai (easonline7@gmail.com)
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.bcm.messenger.common.imagepicker.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import com.bcm.messenger.common.imagepicker.BcmPickPhotoCropHelper;
import com.bcm.messenger.common.imagepicker.widget.AvatarRectView;
import com.bcm.messenger.common.imagepicker.widget.SuperImageView;
import com.bcm.messenger.common.utils.AppUtil;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import java.io.File;
import com.bcm.messenger.utility.logger.ALog;
import com.bumptech.glide.request.target.Target;

import com.bcm.messenger.common.R;

/**
 * <b>Image crop Fragment for avatar</b><br/>
 * Created by Eason.Lai on 2015/11/1 10:42 <br/>
 * contact：easonline7@gmail.com <br/>
 */
public class AvatarCropFragment extends Fragment {

    private static String TAG = "AvatarCropFragment";

    private Activity mContext;

    private SuperImageView superImageView;
    private AvatarRectView mRectView;

    private int screenWidth;
    private final int margin = 0;//the left and right margins of the center circular shape

    private FrameLayout rootView;

    private String picPath;//the local image path in sdcard

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getActivity();

    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View contentView = inflater.inflate(R.layout.common_fragment_avatar_pick_crop, container, false);

        DisplayMetrics dm = new DisplayMetrics();
        mContext.getWindowManager().getDefaultDisplay().getMetrics(dm);
        screenWidth = dm.widthPixels;

        initView(contentView);

        //get the image path from Arguments
        picPath = getArguments().getString(BcmPickPhotoCropHelper.KEY_PIC_PATH);

        if (TextUtils.isEmpty(picPath)) {
            throw new RuntimeException("AndroidImagePicker:you have to give me an image path from sdcard");
        } else {
            onPresentImage(superImageView, picPath, screenWidth);
        }

        return contentView;

    }

    /**
     * init all views
     *
     * @param contentView
     */
    void initView(View contentView) {
        superImageView = contentView.findViewById(R.id.iv_pic);
        rootView = contentView.findViewById(R.id.container);

        RelativeLayout.LayoutParams rlLayoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        //rlLayoutParams.addRule(RelativeLayout.ABOVE, R.id.photo_preview_dock);
        mRectView = new AvatarRectView(mContext, screenWidth - margin * 2);
        rootView.addView(mRectView, 1, rlLayoutParams);

    }


    /**
     * public method to get the crop bitmap
     *
     * @return
     */
    public Bitmap getCropBitmap(int expectSize) {
        if (expectSize <= 0) {
            return null;
        }
        Drawable drawable = superImageView.getDrawable();
        if (drawable == null) {
            ALog.e(TAG, "Get drawable null.");
            return null;
        }

        Bitmap srcBitmap = ((BitmapDrawable) drawable).getBitmap();
//        double rotation = superImageView.getImageRotation();
//        int level = (int) Math.floor((rotation + Math.PI / 4) / (Math.PI / 2));
        int level = superImageView.getImageRotationLevel();
        if (level != 0) {
            srcBitmap = rotate(srcBitmap, 90 * level);
        }
        Rect centerRect = mRectView.getCropRect();
        RectF matrixRect = superImageView.getMatrixRect();

        return makeCropBitmap(getContext(), srcBitmap, centerRect, matrixRect, expectSize);
    }

    private void onPresentImage(ImageView imageView, String imageUri, int size) {
        RequestOptions options = new RequestOptions();
        options.fitCenter();
        options.override(Target.SIZE_ORIGINAL);
        Glide.with(imageView.getContext())
                .load(new File(imageUri))
                .apply(options)
                .into(imageView);

    }

    private Bitmap makeCropBitmap(Context context, Bitmap bitmap, Rect rectBox, RectF imageMatrixRect, int expectSize) {
        Bitmap bmp = bitmap;
        float f = imageMatrixRect.width() / bmp.getWidth();
        int left = (int) ((rectBox.left - imageMatrixRect.left) / f);
        int top = (int) ((rectBox.top - imageMatrixRect.top) / f);
        int width = (int) (rectBox.width() / f);
        int height = (int) (rectBox.height() / f);

        if (left < 0) {
            left = 0;
        }
        if (top < 0) {
            top = 0;
        }

        if (left + width > bmp.getWidth()) {
            width = bmp.getWidth() - left;
        }
        if (top + height > bmp.getHeight()) {
            height = bmp.getHeight() - top;
        }

        int k = AppUtil.INSTANCE.dp2Px(context.getResources(), expectSize);

        try {
            bmp = Bitmap.createBitmap(bmp, left, top, width, height);

            if (k != width && k != height) {//don't do this if equals
                bmp = Bitmap.createScaledBitmap(bmp, k, k, true);//scale the bitmap
            }

        } catch (OutOfMemoryError localOutOfMemoryError1) {
            ALog.w(TAG, "OOM when create bitmap");
        }
        return bmp;
    }

    public static Bitmap rotate(Bitmap b, int degrees) {
        if (degrees != 0 && b != null) {
            Matrix m = new Matrix();
            m.setRotate(degrees, (float) b.getWidth() / 2, (float) b.getHeight() / 2);
            try {
                Bitmap b2 = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (b != b2) {
//                    b.recycle();
                    b = b2;
                }
            } catch (OutOfMemoryError ex) {
                // We have no memory to rotate. Return the original bitmap.
            }
        }
        return b;
    }
}
