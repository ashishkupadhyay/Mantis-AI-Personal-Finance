package io.github.ashishkupadhyay.mantis.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Pending
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.LocalMantisColors

/** UI-level categorisation source (mapped from the domain `CategorySource` in `core:ui`). */
enum class SourceBadgeKind(val label: String) {
    USER("Set by you"),
    MODEL_HIGH("Categorized by Mantis"),
    SUGGESTED("Suggested — tap to confirm"),
    AI("Suggested by your AI provider"),
    IMPORT("From the statement"),
    UNCATEGORIZED("Uncategorized"),
    CHECKING("Checking with AI…"),
}

/** Trailing badge on transaction rows: ✓ user, ◐ model, ~ suggested, ✦ AI, ⚡ import, ? uncategorized (doc 05 §4.2). */
@Composable
fun SourceBadge(kind: SourceBadgeKind, modifier: Modifier = Modifier, confidencePercent: Int? = null) {
    val colors = LocalMantisColors.current
    val description = buildString {
        append(kind.label)
        if (confidencePercent != null) append(", $confidencePercent percent confidence")
    }
    val tint = when (kind) {
        SourceBadgeKind.USER, SourceBadgeKind.MODEL_HIGH -> colors.confidenceHigh
        SourceBadgeKind.SUGGESTED, SourceBadgeKind.AI, SourceBadgeKind.CHECKING -> colors.confidenceMedium
        SourceBadgeKind.IMPORT -> MaterialTheme.colorScheme.secondary
        SourceBadgeKind.UNCATEGORIZED -> colors.confidenceLow
    }
    Box(
        modifier = modifier
            .size(20.dp)
            .semantics { contentDescription = description; stateDescription = kind.label },
        contentAlignment = Alignment.Center,
    ) {
        if (kind == SourceBadgeKind.CHECKING) {
            MantisLoadingIndicator(Modifier.size(16.dp), contentDescription = kind.label)
        } else {
            Icon(
                imageVector = when (kind) {
                    SourceBadgeKind.USER -> Icons.Default.Check
                    SourceBadgeKind.MODEL_HIGH -> Icons.Outlined.Contrast
                    SourceBadgeKind.SUGGESTED -> Icons.Outlined.Pending
                    SourceBadgeKind.AI -> Icons.Default.AutoAwesome
                    SourceBadgeKind.IMPORT -> Icons.Default.Bolt
                    SourceBadgeKind.UNCATEGORIZED -> Icons.Default.QuestionMark
                    SourceBadgeKind.CHECKING -> Icons.Outlined.Pending
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

enum class PaceStatus(val label: String) { ON_TRACK("On track"), WATCH("Slightly over"), OVER("Over budget") }

/** Small filled chip communicating budget pace (doc 05 §2.2). */
@Composable
fun PaceChip(status: PaceStatus, modifier: Modifier = Modifier, text: String = status.label) {
    val colors = LocalMantisColors.current
    val (bg, fg) = when (status) {
        PaceStatus.ON_TRACK -> colors.paceOnTrack to colors.onPaceOnTrack
        PaceStatus.WATCH -> colors.paceWatch to colors.onPaceWatch
        PaceStatus.OVER -> colors.paceOver to colors.onPaceOver
    }
    Box(
        modifier = modifier
            .background(bg, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { stateDescription = status.label },
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
    }
}
