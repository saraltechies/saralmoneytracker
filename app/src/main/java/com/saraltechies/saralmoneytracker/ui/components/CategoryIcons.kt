package com.saraltechies.saralmoneytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saraltechies.saralmoneytracker.model.AccountType

/** Icons people can pick for a category. Keys are stored in the database and backups, so never rename one. */
object CategoryIcons {
    val all: Map<String, ImageVector> = linkedMapOf(
        "restaurant" to Icons.Filled.Restaurant,
        "fastfood" to Icons.Filled.Fastfood,
        "cafe" to Icons.Filled.LocalCafe,
        "groceries" to Icons.Filled.LocalGroceryStore,
        "shopping" to Icons.Filled.ShoppingBag,
        "clothes" to Icons.Filled.Checkroom,
        "bus" to Icons.Filled.DirectionsBus,
        "train" to Icons.Filled.Train,
        "taxi" to Icons.Filled.LocalTaxi,
        "car" to Icons.Filled.DirectionsCar,
        "bike" to Icons.Filled.TwoWheeler,
        "fuel" to Icons.Filled.LocalGasStation,
        "bills" to Icons.Filled.Receipt,
        "electricity" to Icons.Filled.ElectricBolt,
        "water" to Icons.Filled.WaterDrop,
        "phone" to Icons.Filled.Smartphone,
        "internet" to Icons.Filled.Wifi,
        "home" to Icons.Filled.Home,
        "house" to Icons.Filled.House,
        "loan" to Icons.Filled.AccountBalance,
        "card" to Icons.Filled.CreditCard,
        "health" to Icons.Filled.MedicalServices,
        "hospital" to Icons.Filled.LocalHospital,
        "fitness" to Icons.Filled.FitnessCenter,
        "education" to Icons.Filled.School,
        "child" to Icons.Filled.ChildCare,
        "movie" to Icons.Filled.Movie,
        "games" to Icons.Filled.SportsEsports,
        "travel" to Icons.Filled.Flight,
        "care" to Icons.Filled.Spa,
        "family" to Icons.Filled.FamilyRestroom,
        "pets" to Icons.Filled.Pets,
        "gift" to Icons.Filled.CardGiftcard,
        "donation" to Icons.Filled.VolunteerActivism,
        "party" to Icons.Filled.Celebration,
        "repairs" to Icons.Filled.Build,
        "invest" to Icons.AutoMirrored.Filled.TrendingUp,
        "savings" to Icons.Filled.Savings,
        "salary" to Icons.Filled.Payments,
        "work" to Icons.Filled.Work,
        "store" to Icons.Filled.Store,
        "laptop" to Icons.Filled.Laptop,
        "star" to Icons.Filled.Star,
        "refund" to Icons.Filled.Replay,
        "redeem" to Icons.Filled.Redeem,
        "wallet" to Icons.Filled.AccountBalanceWallet,
        "interest" to Icons.Filled.Percent,
        "lend" to Icons.Filled.Handshake,
        "other" to Icons.Filled.MoreHoriz,
    )

    fun of(key: String?): ImageVector = all[key] ?: Icons.Filled.Category

    val transfer: ImageVector = Icons.Filled.SwapHoriz

    fun account(type: AccountType): ImageVector = when (type) {
        AccountType.CASH -> Icons.Filled.Payments
        AccountType.BANK -> Icons.Filled.AccountBalance
        AccountType.CARD -> Icons.Filled.CreditCard
        AccountType.WALLET -> Icons.Filled.AccountBalanceWallet
        AccountType.OTHER -> Icons.Filled.Savings
        AccountType.LOAN -> Icons.Filled.Handshake
    }
}

/** A round coloured badge with an icon, for categories and accounts. */
@Composable
fun IconBadge(icon: ImageVector, color: Color, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).background(color.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}
