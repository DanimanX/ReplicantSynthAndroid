# JNI entry points are resolved by name from Kotlin.
-keep class com.danimanx.replicantsynth.audio.NativeEngine { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
