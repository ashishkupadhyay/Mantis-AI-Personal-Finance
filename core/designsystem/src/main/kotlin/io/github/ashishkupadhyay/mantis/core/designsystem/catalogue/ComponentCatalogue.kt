package io.github.ashishkupadhyay.mantis.core.designsystem.catalogue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ashishkupadhyay.mantis.core.designsystem.charts.BarGroup
import io.github.ashishkupadhyay.mantis.core.designsystem.charts.BarSegment
import io.github.ashishkupadhyay.mantis.core.designsystem.charts.ChartDataTable
import io.github.ashishkupadhyay.mantis.core.designsystem.charts.ChartSlice
import io.github.ashishkupadhyay.mantis.core.designsystem.charts.CumulativeCurve
import io.github.ashishkupadhyay.mantis.core.designsystem.charts.DonutChart
import io.github.ashishkupadhyay.mantis.core.designsystem.charts.PaceRing
import io.github.ashishkupadhyay.mantis.core.designsystem.charts.RankedBars
import io.github.ashishkupadhyay.mantis.core.designsystem.charts.Sparkline
import io.github.ashishkupadhyay.mantis.core.designsystem.charts.StackedBars
import io.github.ashishkupadhyay.mantis.core.designsystem.charts.toTableRows
import io.github.ashishkupadhyay.mantis.core.designsystem.component.CategoryAvatar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.EmptyState
import io.github.ashishkupadhyay.mantis.core.designsystem.component.ErrorState
import io.github.ashishkupadhyay.mantis.core.designsystem.component.FabMenuItem
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisContainedLoading
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisFabMenu
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisFloatingToolbar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisLargeTopAppBar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisLoadingIndicator
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisMediumTopAppBar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisSplitButton
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisToggleGroup
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisTopAppBar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisWavyProgress
import io.github.ashishkupadhyay.mantis.core.designsystem.component.PaceChip
import io.github.ashishkupadhyay.mantis.core.designsystem.component.PaceStatus
import io.github.ashishkupadhyay.mantis.core.designsystem.component.SourceBadge
import io.github.ashishkupadhyay.mantis.core.designsystem.component.SourceBadgeKind
import io.github.ashishkupadhyay.mantis.core.designsystem.component.SplitMenuItem
import io.github.ashishkupadhyay.mantis.core.designsystem.component.ToolbarAction
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.LocalMantisColors
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.MantisColors
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Every design-system piece on one scrolling screen (doc 05 §11). Shown from the debug build's Settings and used
 * as the subject of the design-system screenshot tests, so a Compose alpha bump that changes a component is caught.
 * Each section is also its own composable so it can be previewed on its own (see `CataloguePreviews.kt`).
 */
@Composable
fun ComponentCatalogue(modifier: Modifier = Modifier, animate: Boolean = true) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        CatalogueColourRoles()
        CatalogueTypography()
        CatalogueAppBars()
        CatalogueButtons()
        CatalogueBadges()
        CatalogueLoading()
        CatalogueToolbars()
        CatalogueCharts()
        CatalogueStates()
        if (!animate) Text("static", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun CatalogueColourRoles() {
    val colors = LocalMantisColors.current
    Section("Colour roles") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Swatch(MaterialTheme.colorScheme.primary, "primary")
            Swatch(MaterialTheme.colorScheme.secondaryContainer, "secondary container")
            Swatch(MaterialTheme.colorScheme.tertiaryContainer, "tertiary container")
            Swatch(colors.paceWatch, "pace watch")
            Swatch(colors.paceOver, "pace over")
            Swatch(colors.income, "income")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            colors.categoryPalette.forEach { Swatch(it, "category") }
        }
    }
}

@Composable
internal fun CatalogueTypography() {
    Section("Typography") {
        Text("Display ₹1,23,456", style = MaterialTheme.typography.displaySmallEmphasized)
        Text("Headline medium", style = MaterialTheme.typography.headlineMedium)
        Text("Title large emphasized", style = MaterialTheme.typography.titleLargeEmphasized)
        Text("Body large — Manrope for display, Inter for body.", style = MaterialTheme.typography.bodyLarge)
        Text("Label medium", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun CatalogueAppBars() {
    Section("App bars") {
        MantisTopAppBar(
            title = "Compact",
            navigationIcon = { IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            actions = { IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, contentDescription = "More") } },
        )
        MantisMediumTopAppBar(
            title = { Text("₹1,240.00") },
            subtitle = { Text("Blinkit · 12 Sep") },
            navigationIcon = { IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
        )
        MantisLargeTopAppBar(title = "Home", subtitle = "September 2026")
    }
}

@Composable
internal fun CatalogueButtons() {
    Section("Buttons & toggles") {
        var selected by remember { mutableIntStateOf(1) }
        MantisToggleGroup(persistentListOf("Week", "Month", "Quarter", "Year"), selected, { selected = it }, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {}, shapes = ButtonDefaults.shapes()) { Text("Morphing") }
            MantisSplitButton(
                label = "Save",
                onClick = {},
                menuItems = persistentListOf(SplitMenuItem("Save & add another") {}, SplitMenuItem("Save as template") {}),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PaceChip(PaceStatus.ON_TRACK)
            PaceChip(PaceStatus.WATCH)
            PaceChip(PaceStatus.OVER)
        }
    }
}

@Composable
internal fun CatalogueBadges() {
    Section("Badges & avatars") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SourceBadgeKind.entries.forEach { SourceBadge(it, confidencePercent = 91) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("food", "transport", "shopping", "bills", "health", "entertainment", "travel", "personal", "finance", "income")
                .forEach { group ->
                    var selected by remember { mutableStateOf(false) }
                    CategoryAvatar(
                        groupKey = group,
                        colorKey = group,
                        fallbackLetter = group.take(1),
                        selected = selected,
                        modifier = Modifier.clickable { selected = !selected },
                        contentDescription = group,
                    )
                }
        }
    }
}

@Composable
internal fun CatalogueLoading() {
    Section("Loading & progress") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            MantisLoadingIndicator()
            MantisContainedLoading()
        }
        MantisWavyProgress(progress = { 0.35f }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun CatalogueToolbars() {
    Section("Floating toolbar & FAB menu") {
        MantisFloatingToolbar(
            expanded = true,
            actions = persistentListOf(
                ToolbarAction("Tag", Icons.Default.Label, {}),
                ToolbarAction("Recategorize", Icons.Default.Edit, {}),
                ToolbarAction("Delete", Icons.Default.Delete, {}),
            ),
        )
        var fabExpanded by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.BottomEnd) {
            MantisFabMenu(
                expanded = fabExpanded,
                onExpandedChange = { fabExpanded = it },
                items = persistentListOf(
                    FabMenuItem("Expense", Icons.Default.Remove) {},
                    FabMenuItem("Income", Icons.Default.Add) {},
                    FabMenuItem("Scan bill", Icons.Default.DocumentScanner) {},
                    FabMenuItem("Import CSV", Icons.Default.UploadFile) {},
                ),
            )
        }
    }
}

@Composable
internal fun CatalogueCharts() {
    val colors = LocalMantisColors.current
    val slices = remember(colors) { sampleSlices(colors) }
    val groups = remember(colors) { sampleGroups(colors) }
    Section("Charts") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            DonutChart(slices, Modifier.width(140.dp), selectedKey = "food", center = {
                Text("₹16.5K", style = MaterialTheme.typography.titleMediumEmphasized)
            })
            RankedBars(slices, Modifier.weight(1f), selectedKey = "food")
        }
        ChartDataTable(slices.toTableRows(), Modifier.fillMaxWidth(), selectedKey = "food")
        StackedBars(groups, Modifier.fillMaxWidth(), selectedKey = "Sep")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PaceRing(
                spentFraction = 0.64f, projectedFraction = 0.97f, periodFraction = 0.4f, status = PaceStatus.ON_TRACK,
                modifier = Modifier.width(120.dp),
            ) { Text("64%", style = MaterialTheme.typography.titleMediumEmphasized) }
            PaceRing(
                spentFraction = 1.15f, projectedFraction = 1.3f, periodFraction = 0.8f, status = PaceStatus.OVER,
                modifier = Modifier.width(120.dp), lowConfidence = true,
            ) { Text("115%", style = MaterialTheme.typography.titleMediumEmphasized) }
            Sparkline(persistentListOf(3f, 5f, 4f, 7f, 6f, 9f, 8f), Modifier.weight(1f).height(48.dp))
        }
        CumulativeCurve(
            current = (0..11).map { it / 30f * 1.4f }.toImmutableList(),
            historical = (0..29).map { it / 30f }.toImmutableList(),
            periodDays = 30,
            todayIndex = 11,
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
    }
}

@Composable
internal fun CatalogueStates() {
    Section("States") {
        Card {
            EmptyState(
                icon = Icons.Default.Inbox,
                title = "No transactions yet",
                body = "Import a statement or add your first expense.",
                primaryAction = "Import a statement" to {},
                secondaryAction = "Add manually" to {},
            )
        }
        Card { ErrorState("Couldn't load budgets", "Something went wrong on our side.", onRetry = {}) }
    }
}

// Sample data: literal amounts are the point, so MagicNumber is suppressed for these two helpers only.
@Suppress("MagicNumber")
private fun sampleSlices(colors: MantisColors): ImmutableList<ChartSlice> =
    listOf("Food" to 6180f, "Transport" to 2910f, "Shopping" to 2200f, "Bills" to 4300f, "Health" to 900f)
        .map { (label, value) -> ChartSlice(label.lowercase(), label, value, colors.forKey(label.lowercase()), "₹${value.toInt()}") }
        .toImmutableList()

@Suppress("MagicNumber")
private fun sampleGroups(colors: MantisColors): ImmutableList<BarGroup> =
    listOf("Jun" to 12f, "Jul" to 15f, "Aug" to 9f, "Sep" to 11f).map { (month, value) ->
        BarGroup(
            key = month,
            label = month,
            ghostTotal = value * 0.9f,
            segments = persistentListOf(
                BarSegment("food", "Food", value * 0.4f, colors.forKey("food")),
                BarSegment("transport", "Transport", value * 0.35f, colors.forKey("transport")),
                BarSegment("bills", "Bills", value * 0.25f, colors.forKey("bills")),
            ),
        )
    }.toImmutableList()

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun Swatch(color: Color, label: String) {
    Box(
        Modifier
            .size(28.dp)
            .background(color, MaterialTheme.shapes.small)
            .semantics { contentDescription = label },
    )
}
