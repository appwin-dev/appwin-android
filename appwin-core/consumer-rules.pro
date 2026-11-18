# Les DTO sont sérialisés par kotlinx.serialization, qui génère un
# `Companion.serializer()` par classe. R8 ne voit pas ces usages et les
# supprimerait, ce qui casse la sérialisation en release.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class io.appwin.core.** {
    *** Companion;
}
-keepclasseswithmembers class io.appwin.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Play Billing est en compileOnly : absent d'une app qui ne l'embarque pas.
# StoreCountry teste sa présence à l'exécution ; R8 ne doit pas échouer sur
# ces classes manquantes.
-dontwarn com.android.billingclient.**
