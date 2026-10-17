# Keep gson models
-keep class com.sukot.vpn.model.** { *; }
-keep class com.sukot.vpn.data.** { *; }

# Gson generic signatures
-keepattributes Signature
-keepattributes *Annotation*

# Go mobile bridge (libv2ray) uses reflection through go.Seq
-keep class go.** { *; }
-keep class libv2ray.** { *; }

# zxing
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
