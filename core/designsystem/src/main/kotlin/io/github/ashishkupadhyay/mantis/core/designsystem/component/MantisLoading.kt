package io.github.ashishkupadhyay.mantis.core.designsystem.component

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.LocalReducedMotion

/** Indeterminate shape-morphing indicator (Expressive `LoadingIndicator`); plain spinner under reduced motion. */
@Composable
fun MantisLoadingIndicator(modifier: Modifier = Modifier, contentDescription: String = "Loading") {
    val described = modifier.semantics { this.contentDescription = contentDescription }
    if (LocalReducedMotion.current) CircularProgressIndicator(described) else LoadingIndicator(described)
}

/** Loading indicator on a container — for cards and in-place refreshes (doc 05 §9). */
@Composable
fun MantisContainedLoading(modifier: Modifier = Modifier, contentDescription: String = "Loading") {
    val described = modifier.semantics { this.contentDescription = contentDescription }
    if (LocalReducedMotion.current) CircularProgressIndicator(described) else ContainedLoadingIndicator(described)
}

/** Determinate wavy bar (import progress, download) — linear under reduced motion. */
@Composable
fun MantisWavyProgress(progress: () -> Float, modifier: Modifier = Modifier) {
    if (LocalReducedMotion.current) LinearProgressIndicator(progress = progress, modifier = modifier)
    else LinearWavyProgressIndicator(progress = progress, modifier = modifier)
}

/** Indeterminate wavy bar (sync in progress). */
@Composable
fun MantisWavyProgressIndeterminate(modifier: Modifier = Modifier) {
    if (LocalReducedMotion.current) LinearProgressIndicator(modifier = modifier) else LinearWavyProgressIndicator(modifier = modifier)
}

/** Circular determinate wavy indicator used behind the pace ring when a budget is at risk (doc 05 §4.1). */
@Composable
fun MantisCircularWavy(progress: () -> Float, modifier: Modifier = Modifier) {
    if (LocalReducedMotion.current) CircularProgressIndicator(progress = progress, modifier = modifier)
    else CircularWavyProgressIndicator(progress = progress, modifier = modifier)
}
