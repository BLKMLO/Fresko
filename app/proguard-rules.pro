# kotlinx.serialization (DTO Asura)
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.fresko.sources.asura.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.fresko.sources.asura.**$$serializer { *; }
# Worker instancié par nom de classe
-keep class com.fresko.feature.checker.CheckWorker { *; }
