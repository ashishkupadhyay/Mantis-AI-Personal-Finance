package io.github.ashishkupadhyay.mantis.core.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icon names stored on categories and accounts (Material Symbols names, doc 01 Appendix A) resolved to vectors.
 * Unknown names fall back to a letter avatar, so a future taxonomy addition never breaks an old build.
 */
object MantisIcons {
    private val byName: Map<String, ImageVector> = mapOf(
        "restaurant" to Icons.Default.Restaurant,
        "delivery_dining" to Icons.Default.DeliveryDining,
        "local_grocery_store" to Icons.Default.LocalGroceryStore,
        "local_cafe" to Icons.Default.LocalCafe,
        "local_bar" to Icons.Default.LocalBar,
        "directions_car" to Icons.Default.DirectionsCar,
        "local_taxi" to Icons.Default.LocalTaxi,
        "local_gas_station" to Icons.Default.LocalGasStation,
        "directions_bus" to Icons.Default.DirectionsBus,
        "local_parking" to Icons.Default.LocalParking,
        "car_repair" to Icons.Default.CarRepair,
        "shopping_bag" to Icons.Default.ShoppingBag,
        "shopping_cart" to Icons.Default.ShoppingCart,
        "checkroom" to Icons.Default.Checkroom,
        "devices" to Icons.Default.Devices,
        "chair" to Icons.Default.Chair,
        "card_giftcard" to Icons.Default.CardGiftcard,
        "receipt_long" to Icons.Default.ReceiptLong,
        "bolt" to Icons.Default.Bolt,
        "water_drop" to Icons.Default.WaterDrop,
        "wifi" to Icons.Default.Wifi,
        "live_tv" to Icons.Default.LiveTv,
        "home" to Icons.Default.Home,
        "apartment" to Icons.Default.Apartment,
        "favorite" to Icons.Default.Favorite,
        "medication" to Icons.Default.Medication,
        "local_hospital" to Icons.Default.LocalHospital,
        "fitness_center" to Icons.Default.FitnessCenter,
        "health_and_safety" to Icons.Default.HealthAndSafety,
        "theaters" to Icons.Default.Theaters,
        "movie" to Icons.Default.Movie,
        "subscriptions" to Icons.Default.Subscriptions,
        "sports_esports" to Icons.Default.SportsEsports,
        "palette" to Icons.Default.Palette,
        "flight" to Icons.Default.Flight,
        "hotel" to Icons.Default.Hotel,
        "train" to Icons.Default.Train,
        "luggage" to Icons.Default.Luggage,
        "family_restroom" to Icons.Default.FamilyRestroom,
        "school" to Icons.Default.School,
        "child_care" to Icons.Default.ChildCare,
        "spa" to Icons.Default.Spa,
        "pets" to Icons.Default.Pets,
        "volunteer_activism" to Icons.Default.VolunteerActivism,
        "account_balance" to Icons.Default.AccountBalance,
        "payments" to Icons.Default.Payments,
        "credit_card" to Icons.Default.CreditCard,
        "trending_up" to Icons.Default.TrendingUp,
        "account_balance_wallet" to Icons.Default.AccountBalanceWallet,
        "request_quote" to Icons.Default.RequestQuote,
        "shield" to Icons.Default.Shield,
        "savings" to Icons.Default.Savings,
        "work" to Icons.Default.Work,
        "laptop" to Icons.Default.Laptop,
        "percent" to Icons.Default.Percent,
        "undo" to Icons.Default.Undo,
        "attach_money" to Icons.Default.AttachMoney,
        "settings" to Icons.Default.Settings,
        "swap_horiz" to Icons.Default.SwapHoriz,
        "help_outline" to Icons.Default.HelpOutline,
        "block" to Icons.Default.Block,
        "category" to Icons.Default.Category,
    )

    fun forName(name: String?): ImageVector? = name?.let(byName::get)

    /** Names offered in pickers (categories, accounts), in a stable order. */
    val names: List<String> get() = byName.keys.toList()
}
