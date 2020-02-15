# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile


-keep public class android.support.design.widget.BottomNavigationView { *; }
-keep public class android.support.design.internal.BottomNavigationMenuView { *; }
-keep public class android.support.design.internal.BottomNavigationPresenter { *; }
-keep public class android.support.design.internal.BottomNavigationItemView { *; }

-keepclassmembers class * {
   public <init> (org.json.JSONObject);
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-dontoptimize
-keepattributes SourceFile,LineNumberTable

-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-dontwarn
-ignorewarnings

-verbose
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

-dontwarn androidx.**

-dontwarn com.google.firebase.analytics.connector.AnalyticsConnector

-keep class androidx.** { *; }
-keep class * extends androidx.**
#-keep class android.support.v7.** { *; }
#-keep class * extends android.support.v7.**
-keep interface androidx.** { *; }
-keep class * implements java.io.Serializable {
    *;
}

-keep class com.bcm.messenger.netswitchy.SSRSystem {
   <methods>;
}
-keep class com.bcm.netswitchy.HookSystem {
    <methods>;
}

-keep class com.bcm.messenger.common.p {
    <methods>;
}

-keepclassmembers class ** {
    public void onEvent*(**);
}

-dontwarn afu.org.checkerframework.**
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}


#webview
-keepclassmembers class * extends android.webkit.webViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}
-keepclassmembers class * extends android.webkit.webViewClient {
    public void *(android.webkit.webView, java.lang.String);
}


# Gson
-keepattributes Signature
-keepattributes *Annotation*

-keep class com.google.**{*;}

-keepclassmembers public interface com.bcm.messenger.utility.proguard.NotGuard{ public *; }
-keepclassmembers class * implements com.bcm.messenger.utility.proguard.NotGuard {
    <methods>;
    <fields>;
}

-keep class com.bcm.messenger.utility.IActivityCounter{public *;}
-keep class com.bcm.messenger.common.IApplicationlImpl{public *;}


-keepclassmembers public interface org.whispersystems.signalservice.api.profiles.ISignalProfile{public *;}
-keepclassmembers public interface com.bcm.messenger.netswitchy.aidl.IShadowsocksService{public *;}
-keepclassmembers public interface com.bcm.messenger.netswitchy.aidl.IShadowsocksServiceCallback{public *;}


-keep class org.whispersystems.signalservice.internal.push.**{*;}
-keep class org.whispersystems.signalservice.api.**{*;}


-keep interface org.whispersystems.jobqueue.dependencies.ContextDependent
-keep class * implements org.whispersystems.jobqueue.dependencies.ContextDependent

-keep public class * extends android.app.Activity
-keep public class * extends android.view.View
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# Constructors
-keep class * {
    <init>(***);
}

-keepclasseswithmembernames class * {
    native <methods>;
}


-keep class org.whispersystems.signalservice.internal.websocket.**{*;}

# OkHttp3
-dontwarn com.squareup.okhttp3.**
-keep class com.squareup.okhttp3.** { *;}
-dontwarn okio.**

# EventBus
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }


-keep class edu.umd.cs.findbugs.annotations.**{*;}
-keep class org.fusesource.leveldbjni.**{*;}

-dontwarn java.**
-dontwarn javax.**
-dontwarn groovy.**
-dontwarn android.**
-dontwarn org.iq80.leveldb.**
-dontwarn org.slf4j.impl.**
-dontwarn org.conscrypt.**
-dontwarn org.spongycastle.pqc.**
-dontwarn dalvik.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn build.**
-dontwarn org.fusesource.leveldbjni.**
-dontwarn com.google.**
-dontwarn libcore.**
-dontwarn com.yy.httpproxy.thirdparty.**
-dontwarn okio.**


-dontnote java.**
-dontnote javax.**
-dontnote groovy.**
-dontnote android.**
-dontnote com.google.**
-dontnote libcore.**

-keep public class android.net.compatibility.**{*;}
-keep public class android.net.http.**{*;}
-keep public class com.android.internal.http.multipart.**{*;}
-keep public class org.apache.commons.**{*;}
-keep public class org.apache.http.**{*;}
-dontwarn android.net.compatibility.**
-dontwarn android.net.http.**
-dontwarn com.android.internal.http.multipart.**
-dontwarn org.apache.commons.**
-dontwarn org.apache.http.**

-keep public class android.net.SSLCertificateSocketFactory
-keep public class android.util.FloatMath
-dontwarn android.net.SSLCertificateSocketFactory
-dontwarn android.util.FloatMath

-dontnote android.net.http.**
-dontnote org.apache.commons.codec.**
-dontnote org.apache.http.**
-dontnote android.net.compatibility.**
-dontnote com.android.internal.http.**
-dontnote org.apache.commons.logging.**

##FCM
-dontwarn com.google.firebase.analytics.connector.AnalyticsConnector

##crash
-keep class com.sdk.crashreport.**{*;}
-keep interface com.sdk.crashreport.**{*;}

#wallet
-keep class org.web3j.** {*;}
-keep class org.bitcoinj.** {*;}
-keep class org.bitcoin.** {*;}
-keep class com.werb.lib.** {*;}

-keep class org.whispersystems.signalservice.api.push.**{*;}

#Curve25519
-keep class org.whispersystems.curve25519.** {*;}

-keep class com.bcm.route.**{*;}


#adhoc
-keep class com.bcm.imcore.im.util.Session {
    *;
}
-keep class com.bcm.imcore.im.MessageStore$* {
    *;
}
-keep class com.bcm.imcore.im.util.MessageIdGenerator {
    *;
}
-keep class com.bcm.imcore.p2p.udp.Kcp {
   <methods>;
}
-keep interface com.bcm.imcore.p2p.udp.Kcp$OutputHandler {
   <methods>;
}
-keep class com.bcm.imcore.im.util.adapter.**{*;}