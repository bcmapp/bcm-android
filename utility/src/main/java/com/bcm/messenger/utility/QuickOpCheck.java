package com.bcm.messenger.utility;

import android.os.SystemClock;

import com.bcm.messenger.utility.logger.ALog;

import java.util.HashMap;

/**
 * When two isQuick () calls occur within quickTime, isQuick returns true; otherwise returns false.
 * The first time isQuick () is called, it will return false;
 * Usually used to avoid excessively frequent operations.
 *
 * // IsQuick is called when two isQuick calls occur within 1s
 * QuickOpCheck checker = new QuickOpCheck(1000);
 * //first call
 * if(checker.isQuick()){//return false
 *
 * }
 *
 * //twice call immediately
 * if(checker.isQuick()){return true
 *
 * }
 */
public class QuickOpCheck {
    private long mQuickTime;
    private boolean mAutoReset;
    private static final String MANUAL_CHECKER = "manual";
    private HashMap<String,Long> checkerList;

    public static QuickOpCheck getDefault(){
        return QuickOperationCheckerHolder.instance;
    }

    private static class QuickOperationCheckerHolder{
        private final static QuickOpCheck instance = new QuickOpCheck(600);
    }

    public QuickOpCheck(long quickTime){
        this(quickTime,true);
    }

    private QuickOpCheck(long quickTime, boolean autoReset){
        this.mQuickTime = quickTime;
        mAutoReset = autoReset;
        checkerList = new HashMap<>();

        if ( !autoReset ){
            checkerList.put(MANUAL_CHECKER, 0L);
        }
    }

    public void reset(){
        long cur = SystemClock.uptimeMillis();
        for (String key : checkerList.keySet()){
            checkerList.put(key, cur);
        }
    }

    public boolean isQuick(){
        String position = MANUAL_CHECKER;
        if ( mAutoReset ){
            position = ClassHelper.getCallerMethodPosition();
        }
        if ( !checkerList.containsKey(position) ){
            checkerList.put(position,0L);
        }

        boolean quick = leftTime(position) > 0;
        if ( !quick && mAutoReset){
            reset(position);
        }
        return quick;
    }

    private void reset( String methodPosition ){
        long cur = SystemClock.uptimeMillis();
        checkerList.put(methodPosition, cur);
    }

    private long leftTime(String methodPosition){
        ALog.i("QuickOpCheck", methodPosition);
        Long startTime = checkerList.get(methodPosition);
        if (null == startTime) {
            startTime = 0L;
        }
        return mQuickTime - (SystemClock.uptimeMillis() - startTime);
    }
}
