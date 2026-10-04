# kotlinx.serialization ships its own consumer rules; keep our @Serializable classes' companions/serializers.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class nl.bluecard.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class nl.bluecard.**$$serializer { *; }
-keepclassmembers class nl.bluecard.** {
    *** Companion;
}
-keepclasseswithmembers class nl.bluecard.** {
    kotlinx.serialization.KSerializer serializer(...);
}
