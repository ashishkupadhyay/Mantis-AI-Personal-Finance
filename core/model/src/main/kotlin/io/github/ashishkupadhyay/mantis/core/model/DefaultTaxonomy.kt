package io.github.ashishkupadhyay.mantis.core.model

/**
 * The bundled two-level taxonomy (doc 01 Appendix A). Keys are stable identifiers shared by the classifier
 * labels, sync payloads and AppFunctions; names are the default English display strings (localised in resources).
 * Seeding assigns ids; this object never changes an existing key — add, don't rename.
 */
object DefaultTaxonomy {

    data class GroupSpec(val key: String, val name: String, val kind: CategoryKind, val icon: String, val leaves: List<LeafSpec>)
    data class LeafSpec(val key: String, val name: String, val icon: String)

    private fun leaf(key: String, name: String, icon: String) = LeafSpec(key, name, icon)

    val groups: List<GroupSpec> = listOf(
        GroupSpec(
            "food", "Food & Dining", CategoryKind.EXPENSE, "restaurant",
            listOf(
                leaf("food.restaurants", "Restaurants", "restaurant"),
                leaf("food.delivery", "Food Delivery", "delivery_dining"),
                leaf("food.groceries", "Groceries", "local_grocery_store"),
                leaf("food.cafe", "Cafés & Snacks", "local_cafe"),
                leaf("food.alcohol", "Alcohol & Bars", "local_bar"),
            ),
        ),
        GroupSpec(
            "transport", "Transport", CategoryKind.EXPENSE, "directions_car",
            listOf(
                leaf("transport.cab", "Cab & Auto", "local_taxi"),
                leaf("transport.fuel", "Fuel", "local_gas_station"),
                leaf("transport.public", "Public Transport", "directions_bus"),
                leaf("transport.parking", "Parking & Tolls", "local_parking"),
                leaf("transport.maintenance", "Vehicle Maintenance", "car_repair"),
            ),
        ),
        GroupSpec(
            "shopping", "Shopping", CategoryKind.EXPENSE, "shopping_bag",
            listOf(
                leaf("shopping.online", "Online Shopping", "shopping_cart"),
                leaf("shopping.clothing", "Clothing", "checkroom"),
                leaf("shopping.electronics", "Electronics", "devices"),
                leaf("shopping.home", "Home & Furniture", "chair"),
                leaf("shopping.gifts", "Gifts", "card_giftcard"),
            ),
        ),
        GroupSpec(
            "bills", "Bills & Utilities", CategoryKind.EXPENSE, "receipt_long",
            listOf(
                leaf("bills.electricity", "Electricity", "bolt"),
                leaf("bills.water_gas", "Water & Gas", "water_drop"),
                leaf("bills.mobile_internet", "Mobile & Internet", "wifi"),
                leaf("bills.streaming", "DTH & Streaming", "live_tv"),
                leaf("bills.rent", "Rent", "home"),
                leaf("bills.society", "Maintenance/Society", "apartment"),
            ),
        ),
        GroupSpec(
            "health", "Health & Wellness", CategoryKind.EXPENSE, "favorite",
            listOf(
                leaf("health.pharmacy", "Pharmacy", "medication"),
                leaf("health.doctor", "Doctor & Hospital", "local_hospital"),
                leaf("health.fitness", "Fitness", "fitness_center"),
                leaf("health.insurance", "Insurance", "health_and_safety"),
            ),
        ),
        GroupSpec(
            "entertainment", "Entertainment", CategoryKind.EXPENSE, "theaters",
            listOf(
                leaf("entertainment.events", "Movies & Events", "movie"),
                leaf("entertainment.subscriptions", "Subscriptions", "subscriptions"),
                leaf("entertainment.games", "Games", "sports_esports"),
                leaf("entertainment.hobbies", "Hobbies", "palette"),
            ),
        ),
        GroupSpec(
            "travel", "Travel", CategoryKind.EXPENSE, "flight",
            listOf(
                leaf("travel.flights", "Flights", "flight"),
                leaf("travel.hotels", "Hotels", "hotel"),
                leaf("travel.train_bus", "Train & Bus", "train"),
                leaf("travel.trip", "Trip Expenses", "luggage"),
            ),
        ),
        GroupSpec(
            "personal", "Personal & Family", CategoryKind.EXPENSE, "family_restroom",
            listOf(
                leaf("personal.education", "Education", "school"),
                leaf("personal.childcare", "Childcare", "child_care"),
                leaf("personal.care", "Personal Care", "spa"),
                leaf("personal.pets", "Pets", "pets"),
                leaf("personal.charity", "Charity & Gifts", "volunteer_activism"),
            ),
        ),
        GroupSpec(
            "finance", "Finance", CategoryKind.EXPENSE, "account_balance",
            listOf(
                leaf("finance.emi", "Loan EMI", "payments"),
                leaf("finance.card_payment", "Credit Card Payment", "credit_card"),
                leaf("finance.investments", "Investments", "trending_up"),
                leaf("finance.bank_charges", "Bank Charges", "account_balance_wallet"),
                leaf("finance.taxes", "Taxes", "request_quote"),
                leaf("finance.insurance_premium", "Insurance Premium", "shield"),
            ),
        ),
        GroupSpec(
            "income", "Income", CategoryKind.INCOME, "savings",
            listOf(
                leaf("income.salary", "Salary", "work"),
                leaf("income.freelance", "Freelance", "laptop"),
                leaf("income.interest", "Interest & Dividends", "percent"),
                leaf("income.refunds", "Refunds & Cashback", "undo"),
                leaf("income.other", "Other Income", "attach_money"),
            ),
        ),
        GroupSpec(
            "system", "System", CategoryKind.SYSTEM, "settings",
            listOf(
                leaf("system.transfer", "Transfer", "swap_horiz"),
                leaf("system.uncategorized", "Uncategorized", "help_outline"),
                leaf("system.excluded", "Excluded", "block"),
            ),
        ),
    )

    val leaves: List<LeafSpec> = groups.flatMap { it.leaves }
    val leafKeys: Set<String> = leaves.map { it.key }.toSet()

    /** Classifier label space: every leaf except `system.excluded` (doc 04 §3.1). */
    val classifierLabels: List<String> = leaves.map { it.key }.filter { it != KEY_EXCLUDED }

    const val KEY_TRANSFER = "system.transfer"
    const val KEY_UNCATEGORIZED = "system.uncategorized"
    const val KEY_EXCLUDED = "system.excluded"

    fun groupKeyOf(leafKey: String): String = leafKey.substringBefore('.')
}
