package io.github.ashishkupadhyay.mantis.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Expressive multi-browse carousel (doc 05 §3): the leading item is large, following items peek. Used for
 * account cards and budget cards. Items are keyed by index; keep [itemCount] stable across recompositions. Item
 * content runs in [CarouselItemScope] so it can `maskClip` its container and fade text with [revealFraction].
 */
@Composable
fun MantisCarousel(
    itemCount: Int,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    preferredItemWidth: Dp = 280.dp,
    itemSpacing: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable CarouselItemScope.(index: Int) -> Unit,
) {
    val state = rememberCarouselState { itemCount }
    HorizontalMultiBrowseCarousel(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        preferredItemWidth = preferredItemWidth,
        itemSpacing = itemSpacing,
        contentPadding = contentPadding,
    ) { index -> content(index) }
}

/**
 * 0 while the item is peeking (anything up to half-way between its smallest and largest size), rising to 1 once it is
 * fully shown. Read it inside `graphicsLayer {}` so scrolling animates the fade without recomposing the item.
 */
fun CarouselItemScope.revealFraction(): Float = with(carouselItemDrawInfo) {
    if (maxSize <= minSize) return 1f
    val shown = (size - minSize) / (maxSize - minSize)
    ((shown - REVEAL_START) / (1f - REVEAL_START)).coerceIn(0f, 1f)
}

private const val REVEAL_START = 0.5f
