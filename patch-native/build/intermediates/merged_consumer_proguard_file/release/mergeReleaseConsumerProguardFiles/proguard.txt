# Consumer rules for patch-native library

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep NativePatchEngine class
-keep class com.orange.patchnative.NativePatchEngine { *; }
-keep class com.orange.patchnative.NativeProgressCallback { *; }
