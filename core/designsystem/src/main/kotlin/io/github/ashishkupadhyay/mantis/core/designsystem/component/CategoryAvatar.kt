package io.github.ashishkupadhyay.mantis.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.LocalMantisColors
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.LocalReducedMotion

/** Stable Expressive shape per category group (doc 05 §2.4); same group → same silhouette everywhere. */
object CategoryShapes {
    private val byGroup: Map<String, RoundedPolygon> = mapOf(
        "food" to MaterialShapes.Cookie9Sided,
        "transport" to MaterialShapes.Arrow,
        "shopping" to MaterialShapes.Clover8Leaf,
        "bills" to MaterialShapes.Square,
        "health" to MaterialShapes.Heart,
        "entertainment" to MaterialShapes.Sunny,
        "travel" to MaterialShapes.Pill,
        "personal" to MaterialShapes.Flower,
        "finance" to MaterialShapes.Diamond,
        "income" to MaterialShapes.Burst,
        "system" to MaterialShapes.Circle,
    )

    fun forGroup(groupKey: String): RoundedPolygon = byGroup[groupKey] ?: MaterialShapes.Circle

    val circle: RoundedPolygon get() = MaterialShapes.Circle
}

/** A Compose [Shape] that morphs between two Material shapes; `progress` 0 = start, 1 = end. */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress).asComposePath()
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

/**
 * Category avatar: group silhouette in the category colour, morphing to a circle-with-check when selected
 * (multi-select in lists). Icon or the first letter of the name as content. The colour is the palette slot hashed
 * from [colorKey] unless the user picked one ([paletteIndex], FR-CAT-1).
 */
@Composable
fun CategoryAvatar(
    groupKey: String,
    colorKey: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    fallbackLetter: String = "?",
    selected: Boolean = false,
    size: Dp = 40.dp,
    contentDescription: String? = null,
    paletteIndex: Int? = null,
) {
    val colors = LocalMantisColors.current
    val reduced = LocalReducedMotion.current
    val target = if (selected) 1f else 0f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduced) androidx.compose.animation.core.snap() else MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "avatarMorph",
    )
    val polygon = remember(groupKey) { CategoryShapes.forGroup(groupKey) }
    val morph = remember(polygon) { Morph(polygon, MaterialShapes.Circle) }
    val shape = remember(morph, progress) { MorphShape(morph, progress) }
    val chosen = paletteIndex?.let { colors.categoryPalette[it.mod(colors.categoryPalette.size)] }
    val tint = if (selected) MaterialTheme.colorScheme.primary else chosen ?: colors.forKey(colorKey)
    val onTint = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(tint)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(Icons.Default.Check, contentDescription = null, tint = onTint)
            icon != null -> Icon(icon, contentDescription = null, tint = onTint)
            else -> Text(fallbackLetter.take(1).uppercase(), color = onTint, style = MaterialTheme.typography.labelLarge)
        }
    }
}
