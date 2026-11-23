# My News (আমার খবর) ProGuard / R8 Rules
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Kotlinx Serialization
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep,includedescriptorclasses class bd.nib.songbadpatro.**$$serializer { *; }
-keepclassmembers class bd.nib.songbadpatro.domain.model.** {
    *** Companion;
}
-keepclassmembers class bd.nib.songbadpatro.data.registry.** {
    *** Companion;
}
-keepclasseswithmembers class bd.nib.songbadpatro.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Jsoup
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
