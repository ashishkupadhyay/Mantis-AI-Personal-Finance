package io.github.ashishkupadhyay.mantis.feature.categories.rules

import app.cash.turbine.test
import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeCategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeTransactionRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.newId
import io.github.ashishkupadhyay.mantis.core.domain.fakes.testMeta
import io.github.ashishkupadhyay.mantis.core.domain.message.UserMessages
import io.github.ashishkupadhyay.mantis.core.domain.usecase.ApplyRuleUseCase
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.CategoryRule
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.RuleField
import io.github.ashishkupadhyay.mantis.core.model.RuleId
import io.github.ashishkupadhyay.mantis.core.model.RuleMatchType
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.core.ui.navigation.RuleEditor
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class RulesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val categories = FakeCategoryRepository.withDefaults()
    private val delivery = categories.categories.value.values.first { it.key == "food.delivery" }
    private val groceries = categories.categories.value.values.first { it.key == "food.groceries" }
    private val messages = UserMessages()
    private val transactions = FakeTransactionRepository(
        listOf(row("t1", "SWIGGY ORDER"), row("t2", "swiggy instamart"), row("t3", "ZOMATO")),
    )

    private fun row(id: String, description: String) = Transaction(
        id = TransactionId(id), accountId = AccountId("bank"), type = TransactionType.EXPENSE,
        amount = Money.ofMajor(-10, Currency.INR), postedAt = Instant.EPOCH, postedLocalDate = LocalDate.of(2026, 9, 1),
        descriptionRaw = description, fingerprint = newId(), meta = testMeta(),
    )

    private fun rule(id: String, pattern: String, priority: Int) =
        CategoryRule(RuleId(id), RuleMatchType.CONTAINS, pattern, RuleField.DESCRIPTION, delivery.id, priority, meta = testMeta())

    private fun listVm() = RulesViewModel(categories, ApplyRuleUseCase(transactions), messages)

    private fun editorVm(key: RuleEditor) =
        RuleEditorViewModel(key, categories, transactions, ApplyRuleUseCase(transactions), messages, UuidV7())

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun FR_CAT_6_rulesListInPriorityOrderAndMoveRenumbersThem() = runTest(dispatcher) {
        categories.saveRule(rule("low", "zomato", 1))
        categories.saveRule(rule("high", "swiggy", 5))
        val vm = listVm()

        vm.state.filter { it.loaded }.first().rules.map { it.rule.id.value } shouldContainExactly listOf("high", "low")

        vm.onEvent(RulesEvent.Move(RuleId("low"), up = true))
        advanceUntilIdle()
        val moved = vm.state.filter { it.loaded && it.rules.first().rule.id.value == "low" }.first().rules
        moved.map { it.rule.priority } shouldContainExactly listOf(2, 1)
        moved.first().category?.id shouldBe delivery.id
    }

    @Test
    fun FR_CAT_6_applyToExistingReportsTheCountAndDeleteRemovesTheRule() = runTest(dispatcher) {
        categories.saveRule(rule("r", "swiggy", 1))
        val vm = listVm()
        messages.messages.test {
            vm.onEvent(RulesEvent.ApplyToExisting(RuleId("r")))
            awaitItem().text shouldBe "Recategorized 2 transactions"
            transactions.getTransaction(TransactionId("t1"))?.categorySource shouldBe CategorySource.USER_RULE
            transactions.getTransaction(TransactionId("t3"))?.categoryId shouldBe null

            vm.onEvent(RulesEvent.Delete(RuleId("r")))
            awaitItem().text shouldBe "Rule deleted"
        }
        categories.rules().size shouldBe 0
    }

    @Test
    fun NFR_20b_editorValidatesLiveAndPreviewsMatches() = runTest(dispatcher) {
        val vm = editorVm(RuleEditor())
        vm.state.filter { it.loaded }.first()

        vm.onEvent(RuleEditorEvent.MatchTypeChanged(RuleMatchType.REGEX))
        vm.onEvent(RuleEditorEvent.PatternChanged("(swig"))
        vm.state.value.patternError shouldBe "Not a valid expression: missing closing )"
        vm.onEvent(RuleEditorEvent.PatternChanged("^swig"))
        vm.state.value.patternError shouldBe null
        advanceUntilIdle()
        vm.state.value.preview shouldContainExactlyInAnyOrder listOf("SWIGGY ORDER", "swiggy instamart")

        vm.onEvent(RuleEditorEvent.Save)
        vm.state.value.categoryError shouldBe true // no category yet
        categories.rules().size shouldBe 0
    }

    @Test
    fun FR_CAT_6_prefilledRuleFromACorrectionSavesAndAppliesToExisting() = runTest(dispatcher) {
        val vm = editorVm(RuleEditor(pattern = "swiggy", categoryId = delivery.id.value, fromTransactionId = "t1"))
        val loaded = vm.state.filter { it.loaded }.first()
        loaded.pattern shouldBe "swiggy"
        loaded.categoryId shouldBe delivery.id
        loaded.applyToExisting shouldBe true

        messages.messages.test {
            vm.effects.test {
                vm.onEvent(RuleEditorEvent.Save)
                val saved = awaitItem().shouldBeInstanceOf<RuleEditorEffect.Saved>()
                val rule = categories.rules().single()
                rule.id shouldBe saved.id
                rule.createdFromTransactionId shouldBe TransactionId("t1")
                rule.priority shouldBe 1
            }
            awaitItem().text shouldBe "Rule saved · 2 transactions recategorized"
        }
        transactions.getTransaction(TransactionId("t2"))?.categoryId shouldBe delivery.id
    }

    @Test
    fun editingARuleKeepsItsPriorityAndDoesNotReapplyByDefault() = runTest(dispatcher) {
        categories.saveRule(rule("r", "swiggy", 7))
        val vm = editorVm(RuleEditor(id = "r"))
        val loaded = vm.state.filter { it.loaded }.first()
        loaded.isNew shouldBe false
        loaded.applyToExisting shouldBe false

        vm.onEvent(RuleEditorEvent.CategoryChanged(groceries.id))
        vm.effects.test {
            vm.onEvent(RuleEditorEvent.Save)
            awaitItem().shouldBeInstanceOf<RuleEditorEffect.Saved>().id shouldBe RuleId("r")
        }
        categories.rules().single().let {
            it.priority shouldBe 7
            it.categoryId shouldBe groceries.id
        }
        transactions.getTransaction(TransactionId("t1")).shouldNotBeNull().categoryId shouldBe null
    }
}
