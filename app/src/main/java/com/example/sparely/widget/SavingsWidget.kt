package com.example.sparely.widget

import com.example.sparely.domain.model.displayName

import com.example.sparely.domain.model.nextOccurrenceAfter
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.Alignment
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sparely.MainActivity
import com.sparely.app.R
import com.example.sparely.data.local.SparelyDatabase
import com.example.sparely.data.local.ExpenseEntity
import com.example.sparely.data.preferences.UserPreferencesRepository
import com.example.sparely.data.local.toDomain
import com.example.sparely.domain.logic.BudgetEngine
import com.example.sparely.domain.model.AllocationBreakdown
import com.example.sparely.domain.model.BudgetOverrunPrompt
import com.example.sparely.domain.model.BudgetPromptReason
import com.example.sparely.domain.model.EducationStatus
import com.example.sparely.domain.model.EmploymentStatus
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.RecurringExpense
import com.example.sparely.domain.model.SavingsPercentages
import com.example.sparely.domain.model.SparelySettings
import com.example.sparely.domain.model.SuggestionConfidence
import com.example.sparely.domain.model.UpcomingRecurringExpense
import androidx.compose.ui.tooling.preview.Preview
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import java.time.format.DateTimeFormatter

import java.time.temporal.ChronoUnit
class SavingsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = SavingsWidgetDataRepository(context)
        val snapshot = repository.loadSnapshot()
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppAction = actionStartActivity(openAppIntent)

        provideContent {
            SavingsWidgetContent(snapshot = snapshot, openApp = openAppAction)
        }
    }
}

private fun EducationStatus.displayLabel(): String = when (this) {
    EducationStatus.HIGH_SCHOOL -> "High school"
    EducationStatus.UNIVERSITY -> "University/college"
    EducationStatus.GRADUATED -> "Graduated"
    EducationStatus.OTHER -> "Other"
}

private fun EmploymentStatus.displayLabel(): String = when (this) {
    EmploymentStatus.STUDENT -> "Student"
    EmploymentStatus.PART_TIME -> "Part-time"
    EmploymentStatus.FULL_TIME, EmploymentStatus.EMPLOYED -> "Full-time"
    EmploymentStatus.SELF_EMPLOYED -> "Self-employed"
    EmploymentStatus.UNEMPLOYED -> "Unemployed"
    EmploymentStatus.RETIRED -> "Retired"
}

private fun ExpenseCategory.displayLabel(): String {
    val locale = Locale.getDefault()
    return name.lowercase(locale).replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString()
    }
}

private fun BudgetPromptReason.describe(): String = when (this) {
    BudgetPromptReason.POTENTIAL_ONE_OFF -> "Looks like a one-off spike. Confirm if it won't repeat."
    BudgetPromptReason.TRENDING_HIGH -> "Spending trend is running hot versus your usual pattern."
    BudgetPromptReason.UNPLANNED_CATEGORY -> "No budget set yet—consider adding one so Sparely can help."
}

private fun SuggestionConfidence.displayLabel(): String = when (this) {
    SuggestionConfidence.HIGH -> "high confidence"
    SuggestionConfidence.MEDIUM -> "medium confidence"
    SuggestionConfidence.LOW -> "early insight"
}

private fun formatCountdown(ctx: android.content.Context, daysUntil: Int): String = when {
    daysUntil < 0 -> ctx.getString(R.string.widget_countdown_overdue, -daysUntil)
    daysUntil == 0 -> ctx.getString(R.string.widget_countdown_today)
    daysUntil == 1 -> ctx.getString(R.string.widget_countdown_tomorrow)
    else -> ctx.getString(R.string.widget_countdown_days, daysUntil)
}

private fun computeBirthdayMessage(settings: SparelySettings, today: LocalDate = LocalDate.now()): String? {
    val birthday = settings.birthday ?: return null
    val next = nextBirthdayDate(birthday, today)
    val days = ChronoUnit.DAYS.between(today, next).toInt()
    val nameSuffix = settings.displayName?.let { " $it" } ?: ""
    return when {
        days == 0 -> "Happy birthday$nameSuffix!"
        days in 1..30 -> "Birthday in $days day${if (days == 1) "" else "s"} (${next.format(widgetDateFormatter)})"
        else -> null
    }
}

private fun nextBirthdayDate(birthday: LocalDate, today: LocalDate): LocalDate {
    val thisYear = birthdayInYear(birthday, today.year)
    return if (!thisYear.isBefore(today)) thisYear else birthdayInYear(birthday, today.year + 1)
}

private fun birthdayInYear(birthday: LocalDate, year: Int): LocalDate {
    val month = birthday.monthValue
    val length = YearMonth.of(year, month).lengthOfMonth()
    val day = birthday.dayOfMonth.coerceAtMost(length)
    return LocalDate.of(year, month, day)
}

class SavingsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SavingsWidget()
}

private data class SavingsWidgetSnapshot(
    val totalSaved: Double,
    val monthSaved: Double,
    val monthSpent: Double,
    val recentExpense: ExpenseSummary?,
    val settings: SparelySettings,
    val budgetPrompt: BudgetPromptSummary?,
    val nextRecurring: NextRecurringSummary?
)

private data class ExpenseSummary(
    val description: String,
    val amount: Double
)

private data class BudgetPromptSummary(
    val category: ExpenseCategory,
    val month: YearMonth,
    val spent: Double,
    val limit: Double,
    val overspend: Double,
    val reason: BudgetPromptReason,
    val suggestionLimit: Double?,
    val suggestionConfidence: SuggestionConfidence?
)

private data class NextRecurringSummary(
    val description: String,
    val amount: Double,
    val dueDate: LocalDate,
    val daysUntil: Int,
    val autoLog: Boolean,
    val reminderDays: Int
)

private val widgetDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

private class SavingsWidgetDataRepository(private val context: Context) {
    private val database = SparelyDatabase.getInstance(context)
    private val transferDao = database.transferDao()
    private val expenseDao = database.expenseDao()
    private val budgetDao = database.budgetDao()
    private val recurringDao = database.recurringExpenseDao()
    private val accountsDao = database.savingsAccountDao()
    private val preferences = UserPreferencesRepository(context)

    suspend fun loadSnapshot(): SavingsWidgetSnapshot {
        return runCatching {
            withContext(Dispatchers.IO) {
                val now = LocalDate.now()
                val month = YearMonth.from(now)
                val monthStart = month.atDay(1)
                val monthEnd = month.atEndOfMonth()

                val totalSaved = accountsDao.getTotalBalance()
                val monthSaved = transferDao.getTotalSavedBetween(monthStart, monthEnd)
                val monthSpent = expenseDao.getTotalSpentBetween(monthStart, monthEnd)
                val recentExpense = expenseDao.getMostRecentExpense()?.toSummary()
                val settings = preferences.getSettingsSnapshot()
                val monthlyExpenses = expenseDao.getExpensesBetween(monthStart, monthEnd)
                val domainExpenses = monthlyExpenses.map { it.toDomain() }
                val currentBudgets = budgetDao.getBudgetsForMonth(month.year, month.monthValue)
                    .map { it.toDomain() }
                val budgetSummary = currentBudgets.takeIf { it.isNotEmpty() }?.let {
                    BudgetEngine.generateBudgetSummary(it, domainExpenses, month)
                }
                val budgetSuggestions = if (budgetSummary != null) {
                    BudgetEngine.suggestBudgetAdjustments(
                        currentBudgets = currentBudgets, 
                        expenses = domainExpenses, 
                        settings = settings, 
                        context = context
                    )
                } else {
                    emptyList()
                }
                val budgetPrompt = if (budgetSummary != null) {
                    BudgetEngine.detectBudgetPrompts(
                        budgetSummary,
                        domainExpenses,
                        budgetSuggestions,
                        settings
                    ).maxByOrNull { prompt -> prompt.overspendAmount }
                        ?.toWidgetSummary()
                } else {
                    null
                }
                val upcomingRecurring = recurringDao.getAll()
                    .map { it.toDomain() }
                    .let { computeUpcomingRecurring(it, now) }
                    .firstOrNull()
                    ?.toWidgetSummary()

                SavingsWidgetSnapshot(
                    totalSaved = totalSaved,
                    monthSaved = monthSaved,
                    monthSpent = monthSpent,
                    recentExpense = recentExpense,
                    settings = settings,
                    budgetPrompt = budgetPrompt,
                    nextRecurring = upcomingRecurring
                )
            }
        }.getOrElse {
            SavingsWidgetSnapshot(
                totalSaved = 0.0,
                monthSaved = 0.0,
                monthSpent = 0.0,
                recentExpense = null,
                settings = SparelySettings(),
                budgetPrompt = null,
                nextRecurring = null
            )
        }
    }

    private fun ExpenseEntity.toSummary(): ExpenseSummary = ExpenseSummary(
        description = description,
        amount = amount
    )

    private fun ExpenseEntity.toDomain(): Expense {
        val appliedPercentages = SavingsPercentages(
            emergency = appliedPercentEmergency,
            invest = appliedPercentInvest,
            `fun` = appliedPercentFun,
            safeInvestmentSplit = appliedSafeSplit
        )
        return Expense(
            id = id,
            description = description,
            amount = amount,
            category = category,
            date = date,
            includesTax = includesTax,
            allocation = AllocationBreakdown(
                emergencyAmount = emergencyAmount,
                investmentAmount = investmentAmount,
                funAmount = funAmount,
                safeInvestmentAmount = safeInvestmentAmount,
                highRiskInvestmentAmount = highRiskInvestmentAmount
            ),
            appliedPercentages = appliedPercentages,
            autoRecommended = autoRecommended,
            riskLevelUsed = riskLevelUsed
        )
    }
}

private fun BudgetOverrunPrompt.toWidgetSummary(): BudgetPromptSummary = BudgetPromptSummary(
    category = category,
    month = month,
    spent = status.spent,
    limit = status.limit,
    overspend = overspendAmount,
    reason = reason,
    suggestionLimit = suggestion?.suggestedLimit,
    suggestionConfidence = suggestion?.confidence
)

private fun computeUpcomingRecurring(
    recurring: List<RecurringExpense>,
    today: LocalDate = LocalDate.now(),
    windowDays: Int = 30
): List<UpcomingRecurringExpense> {
    return recurring
        .filter { it.isActive }
        .mapNotNull { expense ->
            val baseDate = expense.lastProcessedDate ?: expense.startDate.minusDays(1)
            var nextDue = expense.nextOccurrenceAfter(baseDate)
            
            // Advance until we reach a future date
            while (nextDue.isBefore(today) || nextDue.isEqual(baseDate)) {
                nextDue = expense.nextOccurrenceAfter(nextDue)
            }
            
            expense.endDate?.let { end ->
                if (nextDue.isAfter(end)) return@mapNotNull null
            }
            val daysUntil = ChronoUnit.DAYS.between(today, nextDue).toInt()
            if (daysUntil < 0 || daysUntil > windowDays) return@mapNotNull null
            UpcomingRecurringExpense(expense, nextDue, daysUntil)
        }
        .sortedBy { it.dueDate }
}


private fun UpcomingRecurringExpense.toWidgetSummary(): NextRecurringSummary = NextRecurringSummary(
    description = recurringExpense.description,
    amount = recurringExpense.amount,
    dueDate = dueDate,
    daysUntil = daysUntilDue,
    autoLog = recurringExpense.executeAutomatically,
    reminderDays = recurringExpense.reminderDaysBefore
)
@Composable
private fun SavingsWidgetContent(snapshot: SavingsWidgetSnapshot, openApp: Action) {
    val ctx = androidx.glance.LocalContext.current
    val currencyFormatter = rememberCurrencyFormatter()

    // Sparely brand palette (same as the splash and lock screens), in light and dark mode alike.
    fun brand(color: Long) = androidx.glance.color.ColorProvider(
        day = androidx.compose.ui.graphics.Color(color),
        night = androidx.compose.ui.graphics.Color(color)
    )
    val surfaceColor = brand(0xFF0F3D34)            // spruce
    val onSurfaceColor = brand(0xFFEAF5F0)          // ink
    val onSurfaceVariantColor = brand(0xFF9DBDB2)   // muted
    val primaryColor = brand(0xFF8FE3C0)            // mint
    val primaryContainerColor = brand(0xFF0A2C26)   // deep spruce
    val secondaryColor = brand(0xFFF4CB6A)          // gold
    val secondaryContainerColor = brand(0xFF2E3F2A) // spruce warmed toward gold
    val surfaceContainerColor = brand(0xFF14483E)   // raised spruce
    val dividerColor = brand(0xFF1F5A4D)
    val errorContainerColor = brand(0xFF3D2A26)
    val errorColor = brand(0xFFFFB4A6)

    val settings = snapshot.settings
    val monthBadge = remember {
        val month = YearMonth.now()
        month.month.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
    }

    // Calculate savings metrics
    val totalFlow = snapshot.monthSpent + snapshot.monthSaved
    val savingsRate = if (totalFlow > 0) {
        ((snapshot.monthSaved / totalFlow) * 100).toInt()
    } else 0
    
    val monthDelta = snapshot.monthSaved - snapshot.monthSpent
    val netLabel = if (monthDelta >= 0) {
        "+${currencyFormatter(monthDelta)}"
    } else {
        currencyFormatter(monthDelta)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(surfaceColor)
            .cornerRadius(28.dp)
            .padding(20.dp)
            .clickable(openApp)
    ) {
        // Header Section - Google style with avatar placeholder
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_sparely_logo),
                contentDescription = null,
                modifier = GlanceModifier.size(32.dp)
            )
            Spacer(modifier = GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                // Widgets can't load the Poppins font, so the wordmark is drawn from its outlines.
                Image(
                    provider = ImageProvider(R.drawable.ic_sparely_wordmark),
                    contentDescription = "Sparely",
                    modifier = GlanceModifier.width(74.dp).height(19.dp)
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                settings.displayName?.takeIf { it.isNotBlank() }?.let { name ->
                    Text(
                        text = "$name - $monthBadge",
                        style = TextStyle(
                            color = onSurfaceVariantColor,
                            fontSize = 13.sp
                        )
                    )
                } ?: run {
                    Text(
                        text = monthBadge,
                        style = TextStyle(
                            color = onSurfaceVariantColor,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
        
        Spacer(modifier = GlanceModifier.height(20.dp))
        
        // Hero Metrics - Total Savings with Monthly Performance
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(primaryContainerColor)
                .cornerRadius(16.dp)
                .padding(16.dp)
        ) {
            Text(
                text = ctx.getString(R.string.widget_total_savings),
                style = TextStyle(
                    color = primaryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = currencyFormatter(snapshot.totalSaved),
                style = TextStyle(
                    color = onSurfaceColor,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            
            Spacer(modifier = GlanceModifier.height(12.dp))
            
            // Divider line
            Spacer(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(dividerColor)
            )
            
            Spacer(modifier = GlanceModifier.height(12.dp))
            
            // Monthly Stats Grid - Google style
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = ctx.getString(R.string.widget_this_month),
                        style = TextStyle(
                            color = onSurfaceVariantColor,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = netLabel,
                        style = TextStyle(
                            color = if (monthDelta >= 0) primaryColor else errorColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                
                if (savingsRate > 0) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = GlanceModifier.defaultWeight()
                    ) {
                        Text(
                            text = ctx.getString(R.string.dashboard_savings_rate_label),
                            style = TextStyle(
                                color = onSurfaceVariantColor,
                                fontSize = 11.sp
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Text(
                            text = "$savingsRate%",
                            style = TextStyle(
                                color = primaryColor,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = GlanceModifier.height(16.dp))
        
        // Monthly Activity - Clean dual card layout
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            // Saved this month
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .background(surfaceContainerColor)
                    .cornerRadius(12.dp)
                    .padding(14.dp)
            ) {
                Text(
                    text = ctx.getString(R.string.widget_saved),
                    style = TextStyle(
                        color = onSurfaceVariantColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    text = currencyFormatter(snapshot.monthSaved),
                    style = TextStyle(
                        color = primaryColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            
            Spacer(modifier = GlanceModifier.width(12.dp))
            
            // Spent this month
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .background(surfaceContainerColor)
                    .cornerRadius(12.dp)
                    .padding(14.dp)
            ) {
                Text(
                    text = ctx.getString(R.string.widget_spent),
                    style = TextStyle(
                        color = onSurfaceVariantColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    text = currencyFormatter(snapshot.monthSpent),
                    style = TextStyle(
                        color = onSurfaceColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        
        // Smart Insights - Contextual cards
        snapshot.budgetPrompt?.let { prompt ->
            Spacer(modifier = GlanceModifier.height(12.dp))
            BudgetAlertCard(
                prompt = prompt,
                currencyFormatter = currencyFormatter,
                errorColor = errorColor,
                onSurfaceVariantColor = onSurfaceVariantColor,
                errorContainerColor = errorContainerColor
            )
        }
        
        snapshot.nextRecurring?.let { recurring ->
            Spacer(modifier = GlanceModifier.height(12.dp))
            UpcomingBillsCard(
                summary = recurring,
                currencyFormatter = currencyFormatter,
                onSurfaceColor = onSurfaceColor,
                onSurfaceVariantColor = onSurfaceVariantColor,
                surfaceContainerColor = surfaceContainerColor
            )
        }
        
        // Emergency Fund Quick View
        if (settings.currentEmergencyFund > 0 || settings.hasDebts) {
            Spacer(modifier = GlanceModifier.height(12.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                if (settings.currentEmergencyFund > 0) {
                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .background(surfaceContainerColor)
                            .cornerRadius(12.dp)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = ctx.getString(R.string.widget_emergency),
                            style = TextStyle(
                                color = onSurfaceVariantColor,
                                fontSize = 10.sp
                            )
                        )
                        Text(
                            text = currencyFormatter(settings.currentEmergencyFund),
                            style = TextStyle(
                                color = primaryColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(8.dp))
                }
                
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .background(if (settings.hasDebts) errorContainerColor else surfaceContainerColor)
                        .cornerRadius(12.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = ctx.getString(R.string.widget_debts),
                        style = TextStyle(
                            color = onSurfaceVariantColor,
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = if (settings.hasDebts) ctx.getString(R.string.widget_debts_active) else ctx.getString(R.string.widget_debts_clear),
                        style = TextStyle(
                            color = if (settings.hasDebts) errorColor else primaryColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
        
        // Goal Footer
        settings.primaryGoal?.takeIf { it.isNotBlank() }?.let { goal ->
            Spacer(modifier = GlanceModifier.height(12.dp))
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(secondaryContainerColor)
                    .cornerRadius(12.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "$goal",  // Removed emoji
                    style = TextStyle(
                        color = secondaryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
private fun rememberCurrencyFormatter(): (Double) -> String {
    val formatter = remember {
        NumberFormat.getCurrencyInstance().apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            isGroupingUsed = false
        }
    }
    return { value -> formatter.format(value) }
}

@Composable
private fun BudgetPromptWidgetCard(
    prompt: BudgetPromptSummary,
    currencyFormatter: (Double) -> String,
    onSurfaceColor: ColorProvider,
    onSurfaceVariantColor: ColorProvider,
    accentColor: ColorProvider,
    spentColor: ColorProvider,
    spentContainerColor: ColorProvider
) {
    val monthLabel = remember(prompt.month) {
        val label = prompt.month.month.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()).uppercase(Locale.getDefault())
        "$label budget"
    }
    val categoryLabel = prompt.category.displayLabel()
    val reasonLabel = prompt.reason.describe()
    val suggestionText = prompt.suggestionLimit?.let { limit ->
        val base = "Suggested limit ${currencyFormatter(limit)}"
        prompt.suggestionConfidence?.displayLabel()?.let { confidence ->
            "$base ($confidence)"
        } ?: base
    }

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(spentContainerColor)
            .cornerRadius(18.dp)
            .padding(14.dp)
    ) {
        Text(
            text = "Budget focus",
            style = TextStyle(
                color = spentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "$categoryLabel exceeded by ${currencyFormatter(prompt.overspend)}",
            style = TextStyle(
                color = onSurfaceColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = "$monthLabel · Spent ${currencyFormatter(prompt.spent)} of ${currencyFormatter(prompt.limit)}",
            style = TextStyle(
                color = onSurfaceVariantColor,
                fontSize = 11.sp
            )
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = reasonLabel,
            style = TextStyle(
                color = onSurfaceVariantColor,
                fontSize = 11.sp
            )
        )
        suggestionText?.let { suggestion ->
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = suggestion,
                style = TextStyle(
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun NextRecurringWidgetCard(
    summary: NextRecurringSummary,
    currencyFormatter: (Double) -> String,
    onSurfaceColor: ColorProvider,
    onSurfaceVariantColor: ColorProvider,
    accentColor: ColorProvider,    neutralContainerColor: ColorProvider
) {
    val ctx = androidx.glance.LocalContext.current

    val dueDateLabel = summary.dueDate.format(widgetDateFormatter)
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(neutralContainerColor)
            .cornerRadius(18.dp)
            .padding(14.dp)
    ) {
        Text(
            text = "Next recurring",
            style = TextStyle(
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = summary.description,
            style = TextStyle(
                color = onSurfaceColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = ctx.getString(R.string.widget_due, dueDateLabel, formatCountdown(ctx, summary.daysUntil)),
            style = TextStyle(
                color = onSurfaceVariantColor,
                fontSize = 11.sp
            )
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = currencyFormatter(summary.amount),
            style = TextStyle(
                color = onSurfaceColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = if (summary.autoLog) "Auto-log on" else "Manual confirm",
            style = TextStyle(
                color = onSurfaceVariantColor,
                fontSize = 11.sp
            )
        )
        Text(
            text = "Reminder ${summary.reminderDays}d before",
            style = TextStyle(
                color = onSurfaceVariantColor,
                fontSize = 11.sp
            )
        )
    }
}


@Composable
private fun BudgetAlertCard(
    prompt: BudgetPromptSummary,
    currencyFormatter: (Double) -> String,
    errorColor: ColorProvider,
    onSurfaceVariantColor: ColorProvider,
    errorContainerColor: ColorProvider
) {
    val ctx = androidx.glance.LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(errorContainerColor)
            .cornerRadius(16.dp)
            .padding(16.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_warning),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp),
                colorFilter = androidx.glance.ColorFilter.tint(errorColor)
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = ctx.getString(R.string.widget_budget_alert),
                style = TextStyle(
                    color = errorColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            text = "${prompt.category.displayName()}: ${currencyFormatter(prompt.spent)} / ${currencyFormatter(prompt.limit)}",
            style = TextStyle(
                color = errorColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = if (prompt.overspend > 0) ctx.getString(R.string.widget_over_by, currencyFormatter(prompt.overspend)) else ctx.getString(R.string.widget_approaching_limit),
            style = TextStyle(
                color = onSurfaceVariantColor,
                fontSize = 12.sp
            )
        )
    }
}

@Composable
private fun UpcomingBillsCard(
    summary: NextRecurringSummary,
    currencyFormatter: (Double) -> String,
    onSurfaceColor: ColorProvider,
    onSurfaceVariantColor: ColorProvider,
    surfaceContainerColor: ColorProvider
) {
    val ctx = androidx.glance.LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(surfaceContainerColor)
            .cornerRadius(16.dp)
            .padding(16.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_calendar),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp),
                colorFilter = androidx.glance.ColorFilter.tint(onSurfaceColor)
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = ctx.getString(R.string.widget_upcoming_bills),
                style = TextStyle(
                    color = onSurfaceColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            text = summary.description,
            style = TextStyle(
                color = onSurfaceColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        val dueDateLabel = summary.dueDate.format(widgetDateFormatter)
        Text(
            text = ctx.getString(R.string.widget_due_with_amount, dueDateLabel, formatCountdown(ctx, summary.daysUntil), currencyFormatter(summary.amount)),
            style = TextStyle(
                color = onSurfaceVariantColor,
                fontSize = 12.sp
            )
        )
    }
}

