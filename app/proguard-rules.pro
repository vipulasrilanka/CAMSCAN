# Add project specific ProGuard rules here.

# Rules for ML Kit Barcode Scanning
-keep class com.google.mlkit.vision.barcode.internal.** { *; }
-dontwarn com.google.android.gms.internal.mlkit_vision_barcode.**

# Rules for Jetpack Compose
-keepclassmembers class * { @androidx.compose.runtime.Composable <methods>; }
-keepclassmembers class * { @androidx.compose.runtime.Composable <fields>; }

# Rules for Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
