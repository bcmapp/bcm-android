package com.bcm.messenger.utility;

import com.bcm.messenger.utility.proguard.NotGuard;

/**
 * solution from http://stackoverflow.com/questions/17473148/dynamically-get-the-current-line-number/26410435#26410435
 */
public class ClassHelper implements NotGuard {

    public static String getCallerMethodPosition(){
        boolean signFunction = false;
        boolean callerFunction = false;
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        for(StackTraceElement element : elements) {
            if(signFunction && callerFunction) {
                StringBuffer buffer = new StringBuffer();
                return buffer.append(element.getClassName())
                        .append(":")
                        .append(element.getMethodName())
                        .append(":")
                        .append(element.getLineNumber()).toString();

            } else if(signFunction) {
                callerFunction = true;
            }
            if(element.getClassName().endsWith("ClassHelper") && element.getMethodName().equals("getCallerMethodPosition")) {
                signFunction = true;
            }
        }
        return "unknown";
    }

    public static String getCallStack() {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        StringBuilder buffer = new StringBuilder();
        for(StackTraceElement element : elements) {
            buffer.append(element.getClassName())
                        .append(":")
                        .append(element.getMethodName())
                        .append(":")
                        .append(element.getLineNumber()).append("\n");
        }

        return buffer.toString();
    }
}
