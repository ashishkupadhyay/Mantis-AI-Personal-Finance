package io.github.ashishkupadhyay.mantis.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.LocalReducedMotion

/**
 * A large expressive illustration (doc 05 §4.11 onboarding): an icon inside a shape that slowly morphs between a
 * category group's Material shape and a circle. Static under reduced motion (NFR-29).
 */
@Composable
fun ShapeHero(
    groupKey: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    contentDescription: String? = null,
) {
    val reducedMotion = LocalReducedMotion.current
    val morph = remember(groupKey) { Morph(CategoryShapes.forGroup(groupKey), CategoryShapes.circle) }
    val progress = if (reducedMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "shapeHero")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(MORPH_DURATION_MS), RepeatMode.Reverse),
            label = "morph",
        )
        animated
    }
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primaryContainer, MorphShape(morph, progress)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(ICON_FRACTION),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private const val MORPH_DURATION_MS = 4000
private const val ICON_FRACTION = 0.42f
