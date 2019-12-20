# https://code.google.com/p/android/issues/detail?id=78377
#-keepnames class !android.support.v7.internal.view.menu.**, ** { *; }

-keep public class android.support.v7.widget.** { *; }
-keep public class android.support.v7.internal.widget.** { *; }

-keep public class * extends androidx.core.view.ActionProvider {
    public <init>(android.content.Context);
}

-keepattributes *Annotation*
-keep public class * extends androidx.coordinatorlayout.widget.CoordinatorLayout.Behavior { *; }
-keep public class * extends android.support.design.widget.ViewOffsetBehavior { *; }