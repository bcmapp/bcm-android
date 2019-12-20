-dontwarn org.webrtc.NetworkMonitorAutoDetect
-dontwarn android.net.Network
-keep class org.webrtc.** { *; }

-keepclassmembers public class android.os.Parcelable {*;}
-keepclassmembers class * implements android.os.Parcelable {
    <methods>;
    <fields>;
}
