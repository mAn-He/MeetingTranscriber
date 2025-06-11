# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ONNX Runtime proguard rules
-keep class ai.onnxruntime.** { *; }

# JNI related rules
-keep class com.meetingtranscriber.ml.TokenizerUtils { *; }

# Model classes
-keep class com.meetingtranscriber.ml.** { *; }

# Don't warn about missing dependencies
-dontwarn com.microsoft.onnxruntime.**
-dontwarn ai.onnxruntime.**