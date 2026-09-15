package io.github.ashishkupadhyay.mantis.feature.categories

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.DarkMode
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.then
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ashishkupadhyay.mantis.core.designsystem.preview.MantisPreviewTheme
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeCategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.testMeta
import io.github.ashishkupadhyay.mantis.core.model.CategoryKind
import io.github.ashishkupadhyay.mantis.core.model.CategoryRule
import io.github.ashishkupadhyay.mantis.core.model.RuleField
import io.github.ashishkupadhyay.mantis.core.model.RuleId
import io.github.ashishkupadhyay.mantis.core.model.RuleMatchType
import io.github.ashishkupadhyay.mantis.core.testing.screenshot.MantisScreenshots
import io.github.ashishkupadhyay.mantis.feature.categories.rules.RuleEditorSheet
import io.github.ashishkupadhyay.mantis.feature.categories.rules.RuleEditorUiState
import io.github.ashishkupadhyay.mantis.feature.categories.rules.RuleUi
import io.github.ashishkupadhyay.mantis.feature.categories.rules.RulesScreen
import io.github.ashishkupadhyay.mantis.feature.categories.rules.RulesUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Categories, rules and both editors in light / dark / 200 % font (doc 06 definition of done). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi")
class CategoriesScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val categories = runBlocking { FakeCategoryRepository.withDefaults().categories() }
    private val delivery = categories.first { it.key == "food.delivery" }

    private fun capture(name: String, dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.DarkMode(dark) then DeviceConfigurationOverride.FontScale(fontScale)) {
                MantisPreviewTheme(content = content)
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/categories_$name.png", roborazziOptions = MantisScreenshots.options)
    }

    private fun listState(): CategoriesUiState {
        val byParent = categories.filter { !it.isGroup }.groupBy { it.parentId }
        fun groups(kind: CategoryKind) = categories.filter { it.isGroup && it.kind == kind && it.isManaged }
            .map { group -> CategoryGroupUi(group, byParent[group.id].orEmpty().toImmutableList()) }
            .toImmutableList()
        return CategoriesUiState(expense = groups(CategoryKind.EXPENSE), income = groups(CategoryKind.INCOME), loaded = true)
    }

    @Composable
    private fun List() = CategoriesScreen(listState(), onEvent = {}, onEdit = {}, onAdd = {}, onOpenRules = {}, onBack = {})

    @Test
    fun list_light() = capture("list_light") { List() }

    @Test
    fun list_dark() = capture("list_dark", dark = true) { List() }

    @Test
    fun list_font200() = capture("list_font200", fontScale = 2f) { List() }

    @Test
    fun editor_light() = capture("editor_light") {
        CategoryEditorSheet(
            state = CategoryEditorUiState(
                name = "Street food",
                parentId = delivery.parentId,
                icon = "local_cafe",
                groups = categories.filter { it.isGroup && it.isManaged }.toImmutableList(),
                loaded = true,
            ),
            onEvent = {},
            onClose = {},
        )
    }

    private fun rule(id: String, type: RuleMatchType, pattern: String, priority: Int) =
        CategoryRule(RuleId(id), type, pattern, RuleField.DESCRIPTION, delivery.id, priority, meta = testMeta())

    private fun rules(): RulesUiState = RulesUiState(
        rules = persistentListOf(
            RuleUi(rule("1", RuleMatchType.CONTAINS, "swiggy", 2), delivery),
            RuleUi(rule("2", RuleMatchType.REGEX, "^upi/(zom|swig)", 1), delivery),
        ),
        loaded = true,
    )

    @Test
    fun rules_light() = capture("rules_light") { RulesScreen(rules(), onEvent = {}, onEdit = {}, onAdd = {}, onBack = {}) }

    @Test
    fun rules_dark() = capture("rules_dark", dark = true) { RulesScreen(rules(), onEvent = {}, onEdit = {}, onAdd = {}, onBack = {}) }

    @Test
    fun rules_empty_font200() = capture("rules_empty_font200", fontScale = 2f) {
        RulesScreen(RulesUiState(loaded = true), onEvent = {}, onEdit = {}, onAdd = {}, onBack = {})
    }

    @Test
    fun rule_editor_light() = capture("rule_editor_light") {
        RuleEditorSheet(
            state = RuleEditorUiState(
                matchType = RuleMatchType.REGEX,
                pattern = "(swiggy",
                patternError = "Not a valid expression: missing closing )",
                categoryId = delivery.id,
                categories = categories.toImmutableList(),
                preview = persistentListOf("SWIGGY ORDER", "swiggy instamart"),
                loaded = true,
            ),
            onEvent = {},
            onClose = {},
        )
    }
}
