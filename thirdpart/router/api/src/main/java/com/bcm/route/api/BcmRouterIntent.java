package com.bcm.route.api;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.util.Size;
import android.util.SizeF;
import android.util.SparseArray;
import android.widget.Toast;
import com.bcm.route.annotation.RouteModel;
import com.bcm.route.annotation.RouteType;

import java.io.Serializable;
import java.util.ArrayList;

public final class BcmRouterIntent {
    private Class target;
    private RouteType type;
    private Bundle bundle = new Bundle();
    private String path;
    private int flags = 0;
    private Uri uri;

    private Bundle optionsCompat;
    private int enterAnimation;
    private int exitAnimation;

    BcmRouterIntent(String path) {
        this.path = path;
        RouteModel model = RouteMap.getModel(path);
        if (model == null) {
            if (BcmInnerRouter.isDebuggable()) {
//                throw new RuntimeException("Path is not exist!!");
                return;
            } else {
//                Toast.makeText(BcmInnerRouter.getContext(), path + " not found!!", Toast.LENGTH_LONG).show();
                return;
            }
        }
        target = model.getRouteClass();
        type = model.getType();
    }

    public BcmRouterIntent putBoolean(String key, boolean value) {
        bundle.putBoolean(key, value);
        return this;
    }

    public BcmRouterIntent putInt(String key, int value) {
        bundle.putInt(key, value);
        return this;
    }

    public BcmRouterIntent putLong(String key, long value) {
        bundle.putLong(key, value);
        return this;
    }

    public BcmRouterIntent putString(String key, String value) {
        bundle.putString(key, value);
        return this;
    }

    public BcmRouterIntent putByte(String key, byte value) {
        bundle.putByte(key, value);
        return this;
    }

    public BcmRouterIntent putChar(String key, char value) {
        bundle.putChar(key, value);
        return this;
    }

    public BcmRouterIntent putFloat(String key, float value) {
        bundle.putFloat(key, value);
        return this;
    }

    public BcmRouterIntent putDouble(String key, double value) {
        bundle.putDouble(key, value);
        return this;
    }

    public BcmRouterIntent putShort(String key, short value) {
        bundle.putShort(key, value);
        return this;
    }

    public BcmRouterIntent putCharSequence(String key, CharSequence value) {
        bundle.putCharSequence(key, value);
        return this;
    }

    public BcmRouterIntent putParcelable(String key, Parcelable value) {
        bundle.putParcelable(key, value);
        return this;
    }

    public BcmRouterIntent putSize(String key, Size value) {
        bundle.putSize(key, value);
        return this;
    }

    public BcmRouterIntent putSizeF(String key, SizeF value) {
        bundle.putSizeF(key, value);
        return this;
    }

    public BcmRouterIntent putParcelableArray(String key, Parcelable[] value) {
        bundle.putParcelableArray(key, value);
        return this;
    }

    public BcmRouterIntent putParcelableArrayList(String key, ArrayList<? extends Parcelable> value) {
        bundle.putParcelableArrayList(key, value);
        return this;
    }

    public BcmRouterIntent putSparseParcelableArray(String key, SparseArray<? extends Parcelable> value) {
        bundle.putSparseParcelableArray(key, value);
        return this;
    }

    public BcmRouterIntent putIntegerArrayList(String key, ArrayList<Integer> value) {
        bundle.putIntegerArrayList(key, value);
        return this;
    }

    public BcmRouterIntent putStringArrayList(String key, ArrayList<String> value) {
        bundle.putStringArrayList(key, value);
        return this;
    }

    public BcmRouterIntent putCharSequenceArrayList(String key, ArrayList<CharSequence> value) {
        bundle.putCharSequenceArrayList(key, value);
        return this;
    }

    public BcmRouterIntent putSerializable(String key, Serializable value) {
        bundle.putSerializable(key, value);
        return this;
    }

    public BcmRouterIntent putByteArray(String key, byte[] value) {
        bundle.putByteArray(key, value);
        return this;
    }

    public BcmRouterIntent putShortArray(String key, short[] value) {
        bundle.putShortArray(key, value);
        return this;
    }

    public BcmRouterIntent putCharArray(String key, char[] value) {
        bundle.putCharArray(key, value);
        return this;
    }

    public BcmRouterIntent putFloatArray(String key, float[] value) {
        bundle.putFloatArray(key, value);
        return this;
    }

    public BcmRouterIntent putCharSequenceArray(String key, CharSequence[] value) {
        bundle.putCharSequenceArray(key, value);
        return this;
    }

    public BcmRouterIntent putBundle(String key, Bundle value) {
        bundle.putBundle(key, value);
        return this;
    }

    public BcmRouterIntent putBinder(String key, IBinder value) {
        bundle.putBinder(key, value);
        return this;
    }

    public BcmRouterIntent putStringArray(String key, String[] value) {
        bundle.putStringArray(key, value);
        return this;
    }

    public BcmRouterIntent setAnimation(int enterAnimation, int exitAnimation) {
        this.enterAnimation = enterAnimation;
        this.exitAnimation = exitAnimation;
        return this;
    }

    public BcmRouterIntent setActivityOptionsCompat(Bundle activityOptionsCompat) {
        this.optionsCompat = activityOptionsCompat;
        return this;
    }

    public BcmRouterIntent setFlags(int flags) {
        this.flags = flags;
        return this;
    }

    public BcmRouterIntent setUri(Uri uri) {
        this.uri = uri;
        return this;
    }

    Uri getUri() {
        return uri;
    }

    public Class getTarget() {
        return target;
    }

    RouteType getType() {
        return type;
    }

    Bundle getBundle() {
        return bundle;
    }

    String getPath() {
        return path;
    }

    Bundle getOptionsCompat() {
        return optionsCompat;
    }

    int getEnterAnimation() {
        return enterAnimation;
    }

    int getExitAnimation() {
        return exitAnimation;
    }

    int getFlags() {
        return flags;
    }

    public Object navigation() {
        return navigationWithCast();
    }

    public Object navigation(Context context) {
        return navigationWithCast(context);
    }

    public Object navigation(Activity context, int requestCode) {
        return navigationWithCast(context, requestCode);
    }

    public <T> T navigationWithCast() {
        return BcmRouter.getInstance().navigation(null, this, 0);
    }

    public <T> T navigationWithCast(Context context) {
        return BcmRouter.getInstance().navigation(context, this, 0);
    }

    public <T> T navigationWithCast(Activity context, int requestCode) {
        return BcmRouter.getInstance().navigation(context, this, requestCode);
    }
}
