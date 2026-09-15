package io.github.ashishkupadhyay.mantis.deeplink

import io.github.ashishkupadhyay.mantis.core.ui.navigation.AiSettings
import io.github.ashishkupadhyay.mantis.core.ui.navigation.BudgetDetail
import io.github.ashishkupadhyay.mantis.core.ui.navigation.ImportWizard
import io.github.ashishkupadhyay.mantis.core.ui.navigation.SavedView
import io.github.ashishkupadhyay.mantis.core.ui.navigation.ScanReceipt
import io.github.ashishkupadhyay.mantis.core.ui.navigation.TopLevelDestination
import io.github.ashishkupadhyay.mantis.core.ui.navigation.TransactionDetail
import io.github.ashishkupadhyay.mantis.core.ui.navigation.TransactionsFiltered
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

/** The allow-list is the security boundary for links (NFR-20f): every accepted shape and the rejections are pinned here. */
class DeepLinkParserTest {

    private val parser = DeepLinkParser()
    private val id = "0192b1c4-7f3a-7c2e-9d1a-3c4f5e6a7b8c"

    @Test
    fun acceptsEveryAllowListedShape() {
        parser.parse("mantis://txn/$id") shouldBe DeepLink(TopLevelDestination.TRANSACTIONS, listOf(TransactionDetail(id)))
        parser.parse("mantis://txn/$id/review") shouldBe
            DeepLink(TopLevelDestination.TRANSACTIONS, listOf(TransactionDetail(id, openReview = true)))
        parser.parse("mantis://transactions") shouldBe DeepLink(TopLevelDestination.TRANSACTIONS)
        parser.parse("mantis://transactions?filter=category%3Dfood") shouldBe
            DeepLink(TopLevelDestination.TRANSACTIONS, listOf(TransactionsFiltered("category=food")))
        parser.parse("mantis://budget/$id") shouldBe DeepLink(TopLevelDestination.BUDGETS, listOf(BudgetDetail(id)))
        parser.parse("mantis://view/$id") shouldBe DeepLink(TopLevelDestination.REPORTS, listOf(SavedView(id)))
        parser.parse("mantis://import") shouldBe DeepLink(TopLevelDestination.HOME, listOf(ImportWizard()))
        parser.parse("mantis://scan") shouldBe DeepLink(TopLevelDestination.HOME, listOf(ScanReceipt()))
        parser.parse("mantis://settings/ai") shouldBe DeepLink(TopLevelDestination.SETTINGS, listOf(AiSettings))
    }

    @Test
    fun rejectsEverythingOutsideTheAllowList() {
        listOf(
            null,
            "",
            "https://example.com/txn/$id",
            "mantis://txn",
            "mantis://txn/$id/edit",
            "mantis://txn/${"a".repeat(65)}",
            "mantis://txn/../$id",
            "mantis://txn/$id?x=1/../../etc",
            "mantis://budget/$id/extra",
            "mantis://settings/keys",
            "mantis://wipe",
            "mantis://transactions/all",
            "mantis://transactions?filter=" + "x".repeat(201),
            "mantis://transactions?filter=%00",
            "mantis://txn/" + "b".repeat(600),
            "not a uri at all",
        ).forEach { link -> parser.parse(link).shouldBeNull() }
    }
}
