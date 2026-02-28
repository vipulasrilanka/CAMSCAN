# Add project specific ProGuard rules here.

# Rules for CameraX
-keep public class androidx.camera.core.CameraX { *; }
-keep class androidx.camera.core.impl.utils.executor.SequentialExecutor { *; }
-keep class androidx.camera.camera2.internal.compat.quirk.* { *; }
-keep class androidx.camera.core.impl.compat.quirk.* { *; }
-keep public class androidx.camera.extensions.** { *; }
-keep public enum androidx.camera.extensions.ExtensionMode { *; }
-dontwarn androidx.camera.extensions.**
-dontwarn androidx.camera.core.impl.utils.executor.SequentialExecutor

# Rules for ML Kit Barcode Scanning
-keep class com.google.mlkit.vision.barcode.internal.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-dontwarn com.google.android.gms.internal.mlkit_vision_barcode.**

# Rules for Jetpack Compose
-keepclassmembers class * { @androidx.compose.runtime.Composable <methods>; }
-keepclassmembers class * { @androidx.compose.runtime.Composable <fields>; }

# Rules for Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
