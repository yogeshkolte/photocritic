# kotlinx.serialization: keep generated serializers (used from :core).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class ** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.yk.photocritic.**$$serializer { *; }
-keepclassmembers class com.yk.photocritic.** { *** Companion; }
