-keep class org.pbt.rh.ar.** { *; }
-keep class ai.onnxruntime.** { *; }
-keep class com.microsoft.onnxruntime.** { *; }
-keep class com.github.olga_yakovleva.rhvoice.** { *; }

-dontwarn com.android.org.conscrypt.SSLParametersImpl
-dontwarn org.apache.harmony.xnet.provider.jsse.SSLParametersImpl
-dontwarn ai.onnxruntime.**
-dontwarn com.microsoft.onnxruntime.**
