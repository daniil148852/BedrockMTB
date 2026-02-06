# app/proguard-rules.pro

# Keep model classes
-keep class com.bedrockconverter.model.** { *; }

# Keep JSON serialization
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.json.* <fields>;
}

# Keep OpenGL classes
-keep class android.opengl.** { *; }
-keep class javax.microedition.khronos.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }

# Keep Kotlin metadata
-keepattributes RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}ber information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile