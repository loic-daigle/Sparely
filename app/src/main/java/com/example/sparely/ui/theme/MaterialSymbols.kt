package com.example.sparely.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sparely.app.R

/**
 * Helper object to migrate from old Material Icons to Material Symbols.
 * Maps icon names to drawable resources.
 * 
 * All icons are XML vector drawables (48px base size) that scale perfectly to any size.
 * Download from: https://fonts.google.com/icons
 * 1. Search for icon
 * 2. Click icon → Download → Android (XML)
 * 3. Place in res/drawable/
 * 4. Rename to follow pattern: iconname_48px.xml
 * 5. Add constant here
 * 
 * REQUIRED ICONS TO DOWNLOAD:
 * - arrow_back_48px.xml (Back navigation)
 * - bar_chart_48px.xml (History)
 * - savings_48px.xml (Goals/Save)
 * - account_balance_48px.xml (Budgets)
 * - emoji_events_48px.xml (Challenges)
 * - schedule_48px.xml (Recurring)
 * - favorite_48px.xml (Health)
 * - more_horiz_48px.xml (Settings)
 */
object MaterialSymbols {
    // Navigation & UI icons
    val HOME = R.drawable.home_48px
    val ARROW_BACK = R.drawable.arrow_back_48px
    val BAR_CHART = R.drawable.bar_chart_48px
    val SAVINGS = R.drawable.savings_48px
    val ACCOUNT_BALANCE = R.drawable.account_balance_48px
    val SCHEDULE = R.drawable.schedule_48px
    val FAVORITE = R.drawable.favorite_48px
    val SETTINGS = R.drawable.settings_48px
    val TRENDING_UP = R.drawable.trending_up_48px
    val TRENDING_DOWN = R.drawable.trending_down_48px

    // Core UI icons
    val ADD = R.drawable.add_48px
    val EDIT = R.drawable.edit_48px
    val DELETE = R.drawable.delete_48px
    val REMOVE = R.drawable.remove_48px
    val CHECK = R.drawable.check_48px
    val CHECK_CIRCLE = R.drawable.check_circle_48px
    val ADD_CIRCLE = R.drawable.add_circle_48px
    val CLOSE = R.drawable.close_48px
    val CLEAR = CLOSE
    val HISTORY = R.drawable.history_48px
    val LOCAL_FIRE_DEPARTMENT = R.drawable.local_fire_department_48px
    val SECURITY = R.drawable.security_48px
    val ARROW_DOWNWARD = R.drawable.arrow_downward_48px
    val ARROW_UPWARD = R.drawable.arrow_upward_48px
    val ARROW_FORWARD = R.drawable.arrow_forward_48px
    val ARROW_DROP_DOWN = R.drawable.arrow_drop_down_48px

    val ARROW_DROP_UP = R.drawable.arrow_drop_up_48px
    val SHOPPING_CART = R.drawable.shopping_cart_48px
    val RECEIPT = R.drawable.receipt_48px
    val PERSON = R.drawable.person_48px
    val CALENDAR_MONTH = R.drawable.calendar_month_48px
    val TODAY = R.drawable.today_48px
    val LIGHTBULB = R.drawable.lightbulb_48px
    val WARNING = R.drawable.warning_48px
    val INFO = R.drawable.info_48px
    val NOTIFICATIONS = R.drawable.notifications_48px
    val PLAY_ARROW = R.drawable.play_arrow_48px
    val TROPHY = R.drawable.trophy_48px
    val CELEBRATION = R.drawable.celebration_48px
    val BLOCK = R.drawable.block_48px
    val WORK = R.drawable.work_48px
    val CAKE = R.drawable.cake_48px
    val LOCK = R.drawable.lock_48px
    val ACCOUNT_BALANCE_WALLET = R.drawable.account_balance_wallet_48px
    val FLAG = R.drawable.flag_48px
    val ATTACH_MONEY = R.drawable.attach_money_48px
    val ROCKET_LAUNCH = R.drawable.rocket_launch_48px
    val PUBLIC = R.drawable.public_48px
    val REFRESH = R.drawable.refresh_48px
    val LIST = R.drawable.list_48px
    val SYNC = R.drawable.sync_48px

    val DIRECTIONS_CAR = R.drawable.directions_car_48px
    val FLIGHT = R.drawable.flight_48px
    val SCHOOL = R.drawable.school_48px
    val SHOPPING_BAG = R.drawable.shopping_bag_48px
    val PETS = R.drawable.pets_48px
    val RESTAURANT = R.drawable.restaurant_48px
    val COMPUTER = R.drawable.computer_48px

    val HEALTH_AND_SAFETY = R.drawable.health_and_safety_48px

    val PIE_CHART = R.drawable.pie_chart_48px
    val CREDIT_CARD = R.drawable.credit_card_48px
    val PAYMENTS = R.drawable.payments_48px

    val SWAP_HORIZ = R.drawable.swap_horiz_48px
    val DOWNLOAD = R.drawable.download_48px
    val MORE_VERT = LIST
    val STORE = SHOPPING_BAG
    var SEARCH = R.drawable.search_48px
    var AUTORENEW = R.drawable.autorenew_48px
    val UPLOAD_FILE = R.drawable.upload_file_48px
    val CSV = R.drawable.list_48px

    /**
     * Map of stable icon names to their resource IDs.
     * These names are stored in the database for persistence.
     */
    private val iconMap = mapOf(
        "account_balance_wallet" to ACCOUNT_BALANCE_WALLET,
        "savings" to SAVINGS,
        "directions_car" to DIRECTIONS_CAR,
        "home" to HOME,
        "flight" to FLIGHT,
        "school" to SCHOOL,
        "shopping_bag" to SHOPPING_BAG,
        "pets" to PETS,
        "restaurant" to RESTAURANT,
        "computer" to COMPUTER,
        "local_fire_department" to LOCAL_FIRE_DEPARTMENT,
        "trending_up" to TRENDING_UP,
        "attach_money" to ATTACH_MONEY,
        "rocket_launch" to ROCKET_LAUNCH,
        "account_balance" to ACCOUNT_BALANCE,
        "flag" to FLAG
    )

    private val reverseIconMap = iconMap.entries.associate { it.value to it.key }

    fun getIconByName(name: String?): Int? = iconMap[name?.lowercase()]
    
    fun getNameByIcon(@DrawableRes icon: Int): String? = reverseIconMap[icon]
}

/**
 * Composable Icon component that uses Material Symbols drawables.
 * Drop-in replacement for Material Icons.
 * 
 * All icons should be 48px base size and will be scaled to the desired size.
 * 
 * @param icon Drawable resource ID from MaterialSymbols object
 * @param contentDescription Description for accessibility
 * @param modifier Modifier for the icon
 * @param size Size of the icon in dp (default 24dp)
 * @param tint Color to tint the icon
 */
@Composable
fun MaterialSymbolIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current
) {
    Icon(
        painter = painterResource(id = icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint
    )
}
