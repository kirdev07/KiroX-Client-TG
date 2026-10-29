# TDLib: the native library calls into these Java classes and constructors by name over JNI,
# so none of them may be renamed or removed.
-keep class org.drinkless.tdlib.** { *; }
-keepclassmembers class org.drinkless.tdlib.** { *; }

# Strip verbose logging calls in release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# Call engine: libkiroxvoip calls these classes, methods and fields by name over JNI.
-keep class org.webrtc.** { *; }
-keep class com.kirox.app.voip.NativeCall { *; }
# Video chats: kirox_group_jni.cpp calls onJoinPayload/onNetwork/onLevels/onStopped by name.
-keep class com.kirox.app.voip.NativeGroupCall { *; }
-keep class com.kirox.app.voip.VideoCapturerDevice { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn org.webrtc.**
