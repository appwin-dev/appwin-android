# Session replay reads Compose's semantics tree when Compose is in the app.
# Compose is compileOnly in this module: an app without it must not fail R8 on
# the classes it never loads.
-dontwarn androidx.compose.**

# Session replay walks Compose's layout tree through this method of an
# internal class, by reflection: R8 must not rename it.
-keepclassmembers class androidx.compose.ui.node.LayoutNode {
  public androidx.compose.runtime.collection.MutableVector getZSortedChildren();
}

# Session replay recognizes these views by class name (no dependency on Flutter
# or React Native). R8 renames them in a minified app: Flutter's surface was
# then never copied and every Android Flutter replay came out black.
-keepnames class io.flutter.embedding.android.FlutterSurfaceView
-keepnames class com.facebook.react.views.text.ReactTextView
-keepnames class com.facebook.react.views.text.PreparedLayoutTextView
-keepnames class com.facebook.react.views.textinput.ReactEditText
