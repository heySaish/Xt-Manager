# Keep all JNI native methods across the application
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep JNI model classes used by native C/Rust JNI engines
-keep class com.xtmanager.core.filesystem.LocalFileSystem** { *; }
-keep class com.xtmanager.core.model.** { *; }
-keep class com.xtmanager.runtime.** { *; }
-keep class com.termux.** { *; }
-keep class com.jcraft.jsch.** { *; }

# Keep Compose metadata
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
