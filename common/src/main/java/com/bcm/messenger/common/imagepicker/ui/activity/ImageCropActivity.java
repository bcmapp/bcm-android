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

package com.bcm.messenger.common.imagepicker.ui.activity;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.bcm.messenger.common.R;
import com.bcm.messenger.common.core.AmeLanguageUtilsKt;
import com.bcm.messenger.common.imagepicker.BcmPickPhotoCropHelper;
import com.bcm.messenger.common.imagepicker.ui.AvatarCropFragment;
import com.bcm.messenger.common.utils.AppUtilKotlinKt;


/**
 * 
 */
public class ImageCropActivity extends FragmentActivity implements View.OnClickListener {

    private View mFillLayout;
    private TextView btnReChoose;
    private TextView btnOk;
    private AvatarCropFragment mFragment;
    private String imagePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_activity_pick_crop);

        mFillLayout = findViewById(R.id.photo_preview_fill);
        btnOk = findViewById(R.id.btn_pic_ok);
        btnReChoose = findViewById(R.id.btn_pic_rechoose);
        btnOk.setOnClickListener(this);
        btnReChoose.setOnClickListener(this);

        imagePath = getIntent().getStringExtra(BcmPickPhotoCropHelper.KEY_PIC_PATH);
        mFragment = new AvatarCropFragment();
        Bundle data = new Bundle();
        data.putString(BcmPickPhotoCropHelper.KEY_PIC_PATH, imagePath);
        mFragment.setArguments(data);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, mFragment)
                .commit();

        int statusBarHeight = AppUtilKotlinKt.getStatusBarHeight(this);
        ViewGroup.LayoutParams lp = mFillLayout.getLayoutParams();
        if (lp != null) {
            lp.height += statusBarHeight;
            mFillLayout.setLayoutParams(lp);
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AmeLanguageUtilsKt.setLocale(newBase));
    }

    @Override
    public void onClick(View v) {

        if (v.getId() == R.id.btn_pic_ok) {
            Bitmap bmp = mFragment.getCropBitmap(BcmPickPhotoCropHelper.INSTANCE.getCropSize());
            BcmPickPhotoCropHelper.INSTANCE.notifyImageCropComplete(bmp, 0);
            finish();
        } else if (v.getId() == R.id.btn_pic_rechoose) {
            finish();
        }

    }

    private float lastX = 0f;
    private float lastY = 0f;
    private long lastTime = 0;


    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if(event.getAction() == MotionEvent.ACTION_DOWN) {
            lastX = event.getX();
            lastY = event.getY();
            lastTime = System.currentTimeMillis();
        }else if(event.getAction() == MotionEvent.ACTION_UP) {
            try {
                long now = System.currentTimeMillis();
                int exceptY = 0;
                int[] l = new int[2];
                View v = mFragment.getView();
                if(v != null) {
                    v.getLocationOnScreen(l);
                    exceptY = l[1];
                }else {
                    btnOk.getLocationOnScreen(l);
                    exceptY = l[1] + btnOk.getHeight() + 30;
                }

                if (event.getY() > exceptY && Math.abs(event.getX() - lastX) <= 15 && Math.abs(event.getY() - lastY) <= 15 && now - lastTime <= 600) {
                    return true;
                }

            }catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return super.dispatchTouchEvent(event);
    }

}
