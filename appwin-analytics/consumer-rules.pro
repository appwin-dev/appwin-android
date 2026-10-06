# Session replay reads Compose's semantics tree when Compose is in the app.
# Compose is compileOnly in this module: an app without it must not fail R8 on
# the classes it never loads.
-dontwarn androidx.compose.**

# Session replay walks Compose's layout tree through this method of an
# internal class, by reflection: R8 must not rename it.
-keepclassmembers class androidx.compose.ui.node.LayoutNode {
  public androidx.compose.runtime.collection.MutableVector getZSortedChildren();
}
