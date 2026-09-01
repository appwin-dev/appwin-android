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
