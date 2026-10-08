package io.appwin.analytics.replay

import android.graphics.Rect
import android.view.View
import androidx.compose.runtime.collection.MutableVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.painter.BrushPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.appwin.analytics.AppwinMaskKey
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Compose draws a whole tree into one view, so masking walks its layout tree,
 * with the same rules as views. Compose is compileOnly: nothing here runs
 * unless its classes are present.
 *
 * Semantics alone miss images: `Image(painter, contentDescription = null)`
 * has no semantics node at all. So images are found by the painter in their
 * modifiers, and semantics only carry text, inputs and the mask tags.
 * Vector, color and brush painters are icons and backgrounds, left visible
 * like an SF Symbol on iOS; any other painter (bitmap, Coil, unknown) is an
 * image.
 */
internal object ComposeMasking {
  @Volatile
  private var available = true

  /** Children of a `LayoutNode`, an internal class: reached by reflection. */
  private var childrenMethod: Method? = null

  /** Per modifier element class, its `Painter` field, or [NO_PAINTER]. */
  private val painterFields = HashMap<Class<*>, Any>()
  private val NO_PAINTER = Any()

  fun isComposeRoot(view: View): Boolean {
    if (!available) return false
    return try {
      view is ViewRootForTest
    } catch (_: NoClassDefFoundError) {
      available = false
      false
    }
  }

  fun collect(
    view: View,
    viewRect: Rect,
    inheritedUnmask: Boolean,
    config: ReplayConfig,
    out: MutableList<Rect>,
  ) {
    val found = ArrayList<Rect>()
    val walked = runCatching {
      val root = (view as ViewRootForTest).semanticsOwner.unmergedRootSemanticsNode
      val semantics = HashMap<Int, SemanticsConfiguration>()
      index(root, semantics)
      walk(root.layoutInfo, viewRect, inheritedUnmask, semantics, config, found)
    }.isSuccess
    if (walked) {
      out.addAll(found)
    } else {
      // An unreadable tree fails closed whatever the settings: text fields are
      // always masked and cannot be located without the tree.
      out.add(Rect(viewRect))
    }
  }

  private fun index(node: SemanticsNode, out: MutableMap<Int, SemanticsConfiguration>) {
    out[node.id] = node.config
    for (child in node.children) index(child, out)
  }

  private fun walk(
    node: LayoutInfo,
    clip: Rect,
    inheritedUnmask: Boolean,
    semanticsById: Map<Int, SemanticsConfiguration>,
    config: ReplayConfig,
    out: MutableList<Rect>,
  ) {
    if (!node.isAttached || !node.isPlaced || node.isDeactivated) return
    val semantics = semanticsById[node.semanticsId]
    val tag = semantics?.getOrNull(AppwinMaskKey)
    val unmasked = tag == false || (tag == null && inheritedUnmask)
    val rect = node.coordinates.windowRect()
    val visible = Rect(rect).intersect(clip)
    val input = semantics != null && (
      SemanticsProperties.EditableText in semantics ||
        SemanticsProperties.Password in semantics ||
        SemanticsActions.SetText in semantics
      )
    if (input || tag == true) {
      if (visible) out.add(rect)
      return
    }
    if (visible && !unmasked) {
      val text = semantics != null && SemanticsProperties.Text in semantics
      if (text && config.maskAllText) out.add(rect)
      if (config.maskAllImages) {
        if (semantics?.getOrNull(SemanticsProperties.Role) == Role.Image) {
          out.add(rect)
        } else {
          imageBounds(node)?.let(out::add)
        }
      }
    }
    for (child in children(node)) walk(child, clip, unmasked, semanticsById, config, out)
  }

  private fun imageBounds(node: LayoutInfo): Rect? {
    for (info in node.getModifierInfo()) {
      val painter = painterOf(info.modifier) ?: continue
      if (painter is VectorPainter || painter is ColorPainter || painter is BrushPainter) continue
      val coordinates = if (info.coordinates.isAttached) info.coordinates else node.coordinates
      return coordinates.windowRect()
    }
    return null
  }

  private fun painterOf(element: Any): Painter? {
    val type = element.javaClass
    // Matched by field type, not name: R8 renames the private fields of
    // `PainterElement` and of Coil's element in a minified app.
    val field = painterFields.getOrPut(type) {
      generateSequence<Class<*>>(type) { it.superclass }
        .flatMap { it.declaredFields.asSequence() }
        .firstOrNull { Painter::class.java.isAssignableFrom(it.type) }
        ?.apply { isAccessible = true }
        ?: NO_PAINTER
    }
    return (field as? Field)?.get(element) as? Painter
  }

  private fun children(node: LayoutInfo): List<LayoutInfo> {
    val method = childrenMethod
      ?: node.javaClass.getMethod("getZSortedChildren").also { childrenMethod = it }
    @Suppress("UNCHECKED_CAST")
    return (method.invoke(node) as MutableVector<LayoutInfo>).asMutableList()
  }

  /**
   * Not `boundsInWindow()`: it clips to the parents, and Compose does not clip
   * drawing by default, so a child drawn past its parent would leak.
   */
  private fun LayoutCoordinates.windowRect(): Rect {
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val corners = listOf(Offset.Zero, Offset(w, 0f), Offset(0f, h), Offset(w, h)).map(::localToWindow)
    return Rect(
      floor(corners.minOf { it.x }).toInt(),
      floor(corners.minOf { it.y }).toInt(),
      ceil(corners.maxOf { it.x }).toInt(),
      ceil(corners.maxOf { it.y }).toInt(),
    )
  }
}
