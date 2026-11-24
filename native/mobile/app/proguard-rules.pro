# kotlinx.serialization: keep generated serializers of the :core models.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.kpakam.core.** {
    *** Companion;
}
-keepclasseswithmembers class app.kpakam.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
