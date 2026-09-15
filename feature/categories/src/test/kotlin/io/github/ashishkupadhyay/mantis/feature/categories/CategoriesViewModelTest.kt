package io.github.ashishkupadhyay.mantis.feature.categories

import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeCategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.newId
import io.github.ashishkupadhyay.mantis.core.domain.fakes.testMeta
import io.github.ashishkupadhyay.mantis.core.domain.message.UserMessages
import io.github.ashishkupadhyay.mantis.core.model.Category
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.CategoryKind
import io.github.ashishkupadhyay.mantis.core.model.DefaultTaxonomy
import io.kotest.matchers.collections.shouldContainExactly
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
import app.cash.turbine.test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val categories = FakeCategoryRepository.withDefaults()
    private val messages = UserMessages()
    private val food = categories.categories.value.values.first { it.key == "food" }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun FR_CAT_1_groupsAreSplitByKindWithLeavesInOrderAndSystemBucketsHidden() = runTest(dispatcher) {
        val state = CategoriesViewModel(categories, messages).state.filter { it.loaded }.first()

        val expected = { kind: CategoryKind -> DefaultTaxonomy.groups.filter { it.kind == kind }.map { it.key } }
        state.expense.map { it.group.key } shouldContainExactly expected(CategoryKind.EXPENSE)
        state.income.map { it.group.key } shouldContainExactly expected(CategoryKind.INCOME)
        val foodLeaves = state.expense.first { it.group.id == food.id }.leaves
        foodLeaves.map { it.key } shouldContainExactly DefaultTaxonomy.groups.first { it.key == "food" }.leaves.map { it.key }
        (state.expense + state.income).flatMap { it.leaves + it.group }.none { it.key == DefaultTaxonomy.KEY_TRANSFER } shouldBe true
    }

    @Test
    fun FR_CAT_1_moveReordersSiblingsAndHideKeepsThemListed() = runTest(dispatcher) {
        val vm = CategoriesViewModel(categories, messages)
        val before = vm.state.filter { it.loaded }.first().expense.first { it.group.id == food.id }.leaves
        val second = before[1]

        vm.onEvent(CategoriesEvent.Move(second.id, up = true))
        advanceUntilIdle()
        val after = vm.state.filter { it.loaded }.first().expense.first { it.group.id == food.id }.leaves
        after[0].id shouldBe second.id
        after[1].id shouldBe before[0].id
        after.map { it.sortOrder } shouldContainExactly after.indices.toList()

        vm.onEvent(CategoriesEvent.SetHidden(second.id, true))
        advanceUntilIdle()
        vm.state.filter { it.loaded }.first().expense.first { it.group.id == food.id }.leaves.first().isHidden shouldBe true
    }

    @Test
    fun FR_CAT_1_systemRowsCannotBeDeletedButUserRowsCan() = runTest(dispatcher) {
        val vm = CategoriesViewModel(categories, messages)
        val custom = Category(CategoryId(newId()), "Pets", CategoryKind.EXPENSE, parentId = food.id, meta = testMeta())
        categories.save(custom)

        messages.messages.test {
            vm.onEvent(CategoriesEvent.Delete(food.id))
            awaitItem().text shouldBe "System categories cannot be deleted"
            vm.onEvent(CategoriesEvent.Delete(custom.id))
            awaitItem().text shouldBe "Category deleted"
        }
        categories.getCategory(custom.id) shouldBe null
    }

    @Test
    fun FR_CAT_1_editorCreatesLeavesUnderAGroupAndEditsKeepIdentity() = runTest(dispatcher) {
        val ids = UuidV7()
        val creating = CategoryEditorViewModel(null, food.id.value, categories, ids)
        val loaded = creating.state.filter { it.loaded }.first()
        loaded.parentId shouldBe food.id
        loaded.kind shouldBe CategoryKind.EXPENSE
        loaded.groupsOfKind.all { it.kind == CategoryKind.EXPENSE } shouldBe true

        creating.onEvent(CategoryEditorEvent.Save)
        creating.state.value.nameError shouldBe true
        creating.onEvent(CategoryEditorEvent.NameChanged("  Street food "))
        creating.onEvent(CategoryEditorEvent.IconChanged("local_cafe"))
        creating.onEvent(CategoryEditorEvent.ColorChanged(3))
        creating.effects.test {
            creating.onEvent(CategoryEditorEvent.Save)
            val saved = awaitItem().shouldBeInstanceOf<CategoryEditorEffect.Saved>()
            val category = categories.getCategory(saved.id).shouldNotBeNull()
            category.name shouldBe "Street food"
            category.parentId shouldBe food.id
            category.icon shouldBe "local_cafe"
            category.paletteIndex shouldBe 3
            category.isSystem shouldBe false
            category.sortOrder shouldBe categories.categories().filter { it.parentId == food.id }.maxOf { it.sortOrder }
        }

        val editing = CategoryEditorViewModel(food.id.value, null, categories, ids)
        val existing = editing.state.filter { it.loaded }.first()
        existing.isNew shouldBe false
        existing.isSystem shouldBe true
        existing.isGroup shouldBe true
        editing.onEvent(CategoryEditorEvent.NameChanged("Food & drink"))
        editing.effects.test {
            editing.onEvent(CategoryEditorEvent.Save)
            awaitItem().shouldBeInstanceOf<CategoryEditorEffect.Saved>().id shouldBe food.id
        }
        categories.getCategory(food.id)?.let {
            it.name shouldBe "Food & drink"
            it.key shouldBe "food"
            it.isSystem shouldBe true
        }
    }
}
