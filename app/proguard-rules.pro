# Route navigasi type-safe memakai kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class id.perusahaan.asetga.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class id.perusahaan.asetga.ui.navigation.** { *; }
# Enum domain disimpan sebagai nama di Room
-keepclassmembers enum id.perusahaan.asetga.domain.model.** { *; }
