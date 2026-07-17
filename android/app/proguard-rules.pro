# LiteRT-LM loads native code via JNI; keep its whole surface un-obfuscated.
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

# kotlinx.serialization — keep generated serializers for our records.
-keepclassmembers class gr.orestislef.accesseye.** {
    *** Companion;
}
-keepclasseswithmembers class gr.orestislef.accesseye.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class gr.orestislef.accesseye.**$$serializer { *; }

# OkHttp platform warnings
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
