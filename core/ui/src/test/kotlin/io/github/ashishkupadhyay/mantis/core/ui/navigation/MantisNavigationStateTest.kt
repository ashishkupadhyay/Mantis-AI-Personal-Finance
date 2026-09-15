package io.github.ashishkupadhyay.mantis.core.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The multi-stack rules (doc 02 §4.2): per-tab stacks, "exit through home", deep-link stack replacement, reselect. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MantisNavigationStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var state: MantisNavigationState
    private var entries: List<NavEntry<NavKey>> = emptyList()

    private fun compose() {
        composeRule.setContent {
            state = rememberMantisNavigationState()
            entries = state.rememberEntries { key -> NavEntry(key) { Text(key.toString()) } }
        }
    }

    // NavEntry.key is private; the decorators expose contentKey = Pair(key.toString(), key class), so compare strings.
    private fun keys(): List<String> = composeRule.runOnIdle {
        entries.map { entry -> ((entry.contentKey as? Pair<*, *>)?.first ?: entry.contentKey).toString() }
    }

    private fun keysOf(vararg keys: MantisKey): List<String> = keys.map { it.toString() }

    @Test
    fun startsOnHomeWithOnlyTheHomeEntry() {
        compose()

        composeRule.runOnIdle { state.selected shouldBe TopLevelDestination.HOME }
        keys() shouldContainExactly keysOf(Home)
    }

    @Test
    fun pushesOntoTheSelectedTabAndKeepsHomeUnderneath() {
        compose()

        composeRule.runOnIdle {
            state.navigate(Transactions)
            state.navigate(TransactionDetail("t1"))
        }

        keys() shouldContainExactly keysOf(Home, Transactions, TransactionDetail("t1"))
    }

    @Test
    fun switchingTabsRetainsEachTabsStack() {
        compose()

        composeRule.runOnIdle {
            state.navigate(Transactions)
            state.navigate(TransactionDetail("t1"))
            state.navigate(Budgets)
        }
        keys() shouldContainExactly keysOf(Home, Budgets)

        composeRule.runOnIdle { state.navigate(Transactions) }
        keys() shouldContainExactly keysOf(Home, Transactions, TransactionDetail("t1"))
    }

    @Test
    fun backPopsThenExitsThroughHome() {
        compose()

        composeRule.runOnIdle {
            state.navigate(Transactions)
            state.navigate(TransactionDetail("t1"))
            state.goBack()
        }
        keys() shouldContainExactly keysOf(Home, Transactions)

        composeRule.runOnIdle { state.goBack() }
        keys() shouldContainExactly keysOf(Home)

        // At Home's root back is a no-op for the state; NavDisplay leaves the system to finish the activity.
        composeRule.runOnIdle { state.goBack() }
        keys() shouldContainExactly keysOf(Home)
    }

    @Test
    fun openStackReplacesTheTargetTabsStack() {
        compose()

        composeRule.runOnIdle {
            state.navigate(Budgets)
            state.navigate(BudgetDetail("old"))
            state.openStack(TopLevelDestination.BUDGETS, listOf(BudgetDetail("fromLink")))
        }

        keys() shouldContainExactly keysOf(Home, Budgets, BudgetDetail("fromLink"))
    }

    @Test
    fun reselectEmitsTheTabKey() {
        compose()
        val received = mutableListOf<MantisKey>()

        val collector = CoroutineScope(Dispatchers.Unconfined)
        composeRule.runOnIdle {
            collector.launch { state.reselectEvents.collect { received += it } }
            state.reselect(TopLevelDestination.HOME)
        }
        collector.cancel()

        received shouldContainExactly listOf(Home)
    }
}
