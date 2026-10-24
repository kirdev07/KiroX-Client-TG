# TDLib: the native library calls into these Java classes and constructors by name over JNI,
# so none of them may be renamed or removed.
-keep class org.drinkless.tdlib.** { *; }
-keepclassmembers class org.drinkless.tdlib.** { *; }

# Strip verbose logging calls in release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
