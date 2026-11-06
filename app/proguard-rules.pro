# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.keyforge.chat.**$$serializer { *; }
-keepclassmembers class com.keyforge.chat.** {
    *** Companion;
}
-keepclasseswithmembers class com.keyforge.chat.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Keep provider wire-format models (reflection-free serialization wants the fields).
-keep @kotlinx.serialization.Serializable class com.keyforge.chat.** { *; }
