# Add project specific ProGuard rules here.

# Keep TFLite classes
-keep class org.tensorflow.** { *; }
-keepclassmembers class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# Keep MediaPipe classes
-keep class com.google.mediapipe.** { *; }
-keepclassmembers class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Keep Room entities and DAOs
-keep class com.jasper.app.data.db.** { *; }
-keepclassmembers class com.jasper.app.data.db.** { *; }

# Keep data models
-keep class com.jasper.app.data.repository.model.** { *; }

# TFLite GPU delegate — suppress warnings if not used
-dontwarn org.tensorflow.lite.gpu.**

# Suppress warnings for optional native libraries
-dontwarn sun.misc.**
