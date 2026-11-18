package io.appwin.community.ui

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import io.appwin.community.domain.CommunityFontFamily
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves a studio font setting to a Compose [FontFamily].
 *
 * A custom font is looked up, in order, where each kind of host app keeps it:
 *  1. `res/font/` of a native app, under Android's resource naming
 *     (`Caprasimo-Regular` → `caprasimo_regular`);
 *  2. `assets/fonts/` or `assets/`, where React Native links its fonts;
 *  3. the fonts of a Flutter app's `pubspec.yaml`, listed in
 *     `flutter_assets/FontManifest.json`, matched by family or file name.
 * Nothing found means the system font, and one warning in Logcat.
 */
internal object CommunityFonts {
  private const val TAG = "Appwin"

  /** Typeface lookups read the APK: once per name for the process. */
  private val cache = ConcurrentHashMap<String, FontFamily>()
  private val missing = ConcurrentHashMap.newKeySet<String>()

  fun resolve(context: Context, family: CommunityFontFamily, name: String?): FontFamily =
    when (family) {
      CommunityFontFamily.SERIF -> FontFamily.Serif
      CommunityFontFamily.MONOSPACE -> FontFamily.Monospace
      CommunityFontFamily.CUSTOM -> custom(context, name?.trim().orEmpty())
      // `inter` and `rounded` have no system equivalent on Android: the SDK does
      // not bundle a font every integrating app would pay for. Roboto is close
      // enough to Inter for the feed to read as the mock.
      else -> FontFamily.Default
    }

  private fun custom(context: Context, name: String): FontFamily {
    if (name.isEmpty() || name in missing) return FontFamily.Default
    cache[name]?.let { return it }

    val found = fromResources(context, name) ?: fromAssets(context, name) ?: fromFlutter(context, name)
    if (found == null) {
      missing.add(name)
      Log.w(
        TAG,
        "community font \"$name\" not found, using the system font. Put it in res/font/ " +
          "(e.g. ${resourceName(name)}.ttf) or assets/fonts/$name.ttf. " +
          "See docs: Community > Using your own font.",
      )
      return FontFamily.Default
    }
    cache[name] = found
    return found
  }

  private fun fromResources(context: Context, name: String): FontFamily? {
    @Suppress("DiscouragedApi") // The name comes from the studio, not from R.
    val id = context.resources.getIdentifier(resourceName(name), "font", context.packageName)
    return if (id != 0) FontFamily(Font(id)) else null
  }

  private fun fromAssets(context: Context, name: String): FontFamily? {
    val candidates = listOf("fonts/$name.ttf", "fonts/$name.otf", "$name.ttf", "$name.otf")
    return candidates.firstNotNullOfOrNull { typeface(context, it) }
  }

  private fun fromFlutter(context: Context, name: String): FontFamily? {
    val manifest = runCatching {
      context.assets.open("flutter_assets/FontManifest.json").bufferedReader().use { it.readText() }
    }.getOrNull() ?: return null

    val families = runCatching { JSONArray(manifest) }.getOrNull() ?: return null
    for (i in 0 until families.length()) {
      val entry = families.optJSONObject(i) ?: continue
      val fonts = entry.optJSONArray("fonts") ?: continue
      val assets = (0 until fonts.length()).mapNotNull { fonts.optJSONObject(it)?.optString("asset") }
      // The studio asks for a PostScript name, usually the file name; the
      // Flutter family name is accepted too, it is what a Flutter dev knows.
      val match = if (entry.optString("family") == name) {
        assets.firstOrNull()
      } else {
        assets.firstOrNull { it.substringAfterLast('/').substringBeforeLast('.') == name }
      }
      if (match != null) return typeface(context, "flutter_assets/$match")
    }
    return null
  }

  private fun typeface(context: Context, path: String): FontFamily? =
    runCatching { FontFamily(Typeface.createFromAsset(context.assets, path)) }.getOrNull()

  private fun resourceName(name: String): String =
    name.lowercase().replace(Regex("[^a-z0-9_]"), "_")
}
