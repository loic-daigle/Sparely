package com.example.sparely.ui

import androidx.compose.ui.unit.sp

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.input.nestedscroll.nestedScroll

import androidx.compose.runtime.key

import androidx.compose.material3.rememberTopAppBarState

import androidx.compose.material3.TopAppBarScrollBehavior

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.material3.ScaffoldDefaults
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.rememberDatePickerState
import com.example.sparely.ui.utils.DateUtils
import com.example.sparely.ui.utils.filterAmountExpressionInput
import com.example.sparely.ui.utils.toSafeDouble
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.material3.Icon
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.sparely.domain.model.VaultArchivePrompt
import com.example.sparely.ui.components.SparelySheetHeader
import com.example.sparely.ui.components.SparelyBottomSheet
import com.example.sparely.ui.screens.AssetManagementScreen
import com.example.sparely.ui.screens.BudgetScreen
import com.example.sparely.ui.screens.ChallengesScreen
import com.example.sparely.ui.screens.DashboardScreen
import com.example.sparely.ui.screens.ExpenseEntryScreen
import com.example.sparely.ui.screens.FinancialHealthScreen
import com.example.sparely.ui.screens.PurchaseCheckScreen
import com.example.sparely.ui.screens.WishDetailScreen
import com.example.sparely.ui.screens.WishlistScreen
import com.example.sparely.ui.screens.HistoryScreen
import com.example.sparely.ui.screens.MainAccountScreen
import com.example.sparely.ui.screens.OnboardingScreen
import com.example.sparely.ui.screens.RecurringExpensesScreen
import com.example.sparely.ui.screens.SettingsScreen
import com.example.sparely.ui.screens.SavingsAccountsScreen
import com.example.sparely.ui.screens.SavingsHistoryScreen
import com.example.sparely.ui.screens.VaultHistoryScreen
import com.example.sparely.ui.screens.VaultManagementScreen
import com.example.sparely.ui.screens.VaultTransfersScreen
import com.example.sparely.ui.screens.CreditCardsScreen
import com.example.sparely.ui.screens.InsightsScreen
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.viewmodel.VaultViewModel
import com.example.sparely.ui.viewmodel.VaultViewModelFactory
import com.example.sparely.ui.viewmodel.SettingsViewModel
import com.example.sparely.ui.viewmodel.SettingsViewModelFactory
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import kotlinx.coroutines.launch
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.ui.utils.formatCurrency
import com.sparely.app.R
import com.example.sparely.domain.model.IncomeCategory
import com.example.sparely.ui.utils.localizedName
import com.example.sparely.ui.components.SparelyExpressiveDropdown
import com.example.sparely.ui.components.SparelyTextField
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelyTextButton

@Composable
fun SparelyApp(
    viewModel: SparelyViewModel,
    deepLinkDestination: String? = null,
    onDeepLinkHandled: () -> Unit = {},
    onAuthenticateUser: ((Boolean) -> Unit) -> Unit = {}
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val context = LocalContext.current
    val app = context.applicationContext as com.example.sparely.SparelyApplication
    val settingsViewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = SettingsViewModelFactory(app.container)
    )
    val vaultViewModel: VaultViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = VaultViewModelFactory(app.container)
    )
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val vaultUiState by vaultViewModel.uiState.collectAsStateWithLifecycle()
    val allVaults by vaultViewModel.smartVaults.collectAsStateWithLifecycle()

    LaunchedEffect(deepLinkDestination) {
        deepLinkDestination?.let { destination ->
            // Map notification routes to valid SparelyDestination routes
            val actualRoute = when (destination) {
                "paycheck" -> SparelyDestination.Dashboard.createRoute()  // No dedicated paycheck screen, go to Dashboard
                "vaultTransfers" -> SparelyDestination.VaultTransfers.route
                "creditCards" -> {
                    // Check if URI contains card ID parameter
                    if (destination.contains("?cardId=")) destination else SparelyDestination.CreditCards.createRoute() 
                }
                "vaults" -> SparelyDestination.Vaults.route
                "vaultDetails" -> SparelyDestination.Vaults.route  // No dedicated vault details, go to Vaults
                "settings" -> SparelyDestination.Settings.route
                "history" -> SparelyDestination.History.route
                // "Edit amount" on a variable bill's notification: the Recurring page lists it to confirm
                "variableRecurring" -> SparelyDestination.Recurring.route
                else -> destination  // Use as-is for other routes
            }
            
            // Only navigate if the route is valid
            try {
                navController.navigate(actualRoute)
            } catch (e: Exception) {
                android.util.Log.e("SparelyApp", "Failed to navigate to $actualRoute from deep link $destination", e)
            }
            onDeepLinkHandled()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage
        if (!message.isNullOrEmpty()) {
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(uiState.lastDeletedExpense) {
        val deleted = uiState.lastDeletedExpense
        if (deleted != null) {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.history_undo_delete_message),
                actionLabel = context.getString(R.string.history_undo_delete_action),
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDeleteExpense()
            } else {
                viewModel.clearDeletedExpense()
            }
        }
    }

    if (uiState.isLoading) {
        // Persisted state (including onboardingCompleted) hasn't loaded yet; avoid flashing onboarding.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
    } else if (!uiState.onboardingCompleted) {
        OnboardingScreen(
            onComplete = { profile -> viewModel.completeOnboarding(profile) },
            onImportData = { uri ->
                settingsViewModel.importData(uri, context) {
                    // Success callback
                }
            },
            onSkip = viewModel::skipOnboarding,
            snackbarHostState = snackbarHostState
        )
    } else {
        SparelyScaffold(
            navController = navController,
            snackbarHostState = snackbarHostState,
            uiState = uiState,
            viewModel = viewModel,
            settingsViewModel = settingsViewModel,
            settingsUiState = settingsUiState,
            vaultViewModel = vaultViewModel,
            vaultUiState = vaultUiState,
            allVaults = allVaults,
            onAuthenticateUser = onAuthenticateUser
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SparelyScaffold(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    uiState: com.example.sparely.ui.state.SparelyUiState,
    viewModel: SparelyViewModel,
    settingsViewModel: SettingsViewModel,
    settingsUiState: com.example.sparely.ui.viewmodel.SettingsUiState,
    vaultViewModel: VaultViewModel,
    vaultUiState: com.example.sparely.ui.viewmodel.VaultUiState,
    allVaults: List<com.example.sparely.domain.model.SmartVault>,
    onAuthenticateUser: ((Boolean) -> Unit) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    


    // Only show the quick actions FAB on the Dashboard screen. Other screens provide their
    // own FABs (Vaults, Recurring, etc.) and we don't want to collide with them.
    val showFab = currentDestination?.route == SparelyDestination.Dashboard.route
    val isExpenseEntry = currentDestination?.route == SparelyDestination.ExpenseEntry.route
    // The header shares the page's background and only tints once content scrolls under it.
    // Keyed by page so a new page doesn't start out tinted from the previous one's scroll.
    // Settings sections show their title and back arrow in this header (see SettingsHeaderState)
    val settingsHeader = remember { com.example.sparely.ui.screens.SettingsHeaderState() }
    val inSettingsSection = currentDestination?.route == SparelyDestination.Settings.route &&
        settingsHeader.sectionTitle != null
    val topBarScrollBehavior = key(currentDestination?.route, inSettingsSection) {
        TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        // Without a bottom bar, the expense form's action bar fills the nav-bar area itself
        contentWindowInsets = if (isExpenseEntry) {
            ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (currentDestination?.route != SparelyDestination.Dashboard.route && 
                currentDestination?.route != SparelyDestination.SavingsHistory.route &&
                currentDestination?.route != SparelyDestination.VaultHistory.route) {
                // The expense form doubles as the editor; say so when editing an existing expense
                val isEditingExpense = currentDestination?.route == SparelyDestination.ExpenseEntry.route &&
                    (uiState.prefillExpense?.id ?: 0L) > 0L
                SparelyTopBar(
                    currentDestination = currentDestination,
                    navController = navController,
                    scrollBehavior = topBarScrollBehavior,
                    titleOverride = when {
                        isEditingExpense -> stringResource(R.string.expense_entry_edit_title)
                        inSettingsSection -> settingsHeader.sectionTitle
                        else -> null
                    },
                    onBack = if (inSettingsSection) settingsHeader.closeSection else null
                )
            }
        },
        bottomBar = {
            // The expense form is a focused task: no tabs to tap away from it mid-entry
            if (currentDestination?.route != SparelyDestination.ExpenseEntry.route) {
                SparelyBottomBar(
                    currentDestination = currentDestination,
                    navController = navController
                )
            }
        },
        floatingActionButton = {
            if (showFab) {
                // Material-like stacked FAB menu: primary FAB toggles expansion; child FABs appear above it.
                val fabExpanded = remember { mutableStateOf(false) }
                val showIncomeDialog = remember { mutableStateOf(false) }
                val manualAmountText = remember { mutableStateOf("") }
                val manualDate = remember { mutableStateOf(LocalDate.now()) }
                val manualDistribute = remember { mutableStateOf(true) }
                val manualPending = remember { mutableStateOf(false) }
                val showDatePicker = remember { mutableStateOf(false) }

                LaunchedEffect(currentDestination) {
                    val entry = navController.currentBackStackEntry
                    val action = entry?.arguments?.getString("action")
                    if (action == "record_income") {
                        showIncomeDialog.value = true
                        // Clear the action so it doesn't reopen on rotation/recomposition
                        entry.arguments?.remove("action") 
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (fabExpanded.value) {
                        // Record income child: pill contains icon + text and is clickable
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        fabExpanded.value = false
                                        showIncomeDialog.value = true
                                    }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 12.dp)) {
                                    MaterialSymbolIcon(icon = MaterialSymbols.ATTACH_MONEY, contentDescription = stringResource(R.string.fab_record_income), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text(
                                        text = stringResource(R.string.fab_record_income),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }

                        // Record expense child: pill contains icon + text and is clickable
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        fabExpanded.value = false
                                        navController.navigate(SparelyDestination.ExpenseEntry.route)
                                    }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 12.dp)) {
                                    MaterialSymbolIcon(icon = MaterialSymbols.RECEIPT, contentDescription = stringResource(R.string.fab_record_expense), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text(
                                        text = stringResource(R.string.fab_record_expense),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }

                    // Primary FAB (close/open) - fixed at 56dp
                    FloatingActionButton(
                        onClick = { fabExpanded.value = !fabExpanded.value }, 
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(16.dp), // Expressive squircle for FAB
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        MaterialSymbolIcon(icon = if (fabExpanded.value) MaterialSymbols.CLOSE else MaterialSymbols.ADD, contentDescription = stringResource(R.string.fab_quick_actions), modifier = Modifier.size(24.dp))
                    }
                }

                // Income (paycheck) dialog – polished with Sparely components
                if (showIncomeDialog.value) {
                    val selectedCategory = remember { mutableStateOf<IncomeCategory?>(IncomeCategory.SALARY) }
                    val descriptionText = remember { mutableStateOf("") }
                    val defaultIncomeDescription = stringResource(R.string.income_default_description)

                    SparelyBottomSheet(
                        isOpen = true,
                        onDismiss = { showIncomeDialog.value = false }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 32.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SparelySheetHeader(
                                title = stringResource(R.string.record_income_title),
                                subtitle = stringResource(R.string.record_income_subtitle),
                                icon = MaterialSymbols.ATTACH_MONEY
                            )

                            com.example.sparely.ui.components.SheetAmountField(
                                value = manualAmountText.value,
                                onValueChange = { v -> manualAmountText.value = v.filterAmountExpressionInput() },
                                label = stringResource(R.string.vault_amount_label),
                                imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                                onImeAction = {}
                            )

                            com.example.sparely.ui.components.SheetSectionLabel(stringResource(R.string.income_category_label))
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(IncomeCategory.entries.size) { index ->
                                    val category = IncomeCategory.entries[index]
                                    com.example.sparely.ui.components.SparelyChip(
                                        selected = selectedCategory.value == category,
                                        onClick = { selectedCategory.value = category },
                                        label = { Text(category.localizedName()) },
                                        leadingIcon = {
                                            MaterialSymbolIcon(
                                                icon = when (category) {
                                                    IncomeCategory.SALARY -> MaterialSymbols.PAYMENTS
                                                    IncomeCategory.FREELANCE -> MaterialSymbols.WORK
                                                    IncomeCategory.GIFT -> MaterialSymbols.CELEBRATION
                                                    IncomeCategory.INVESTMENT -> MaterialSymbols.TRENDING_UP
                                                    IncomeCategory.OTHER -> MaterialSymbols.LIST
                                                },
                                                contentDescription = null,
                                                size = 18.dp
                                            )
                                        }
                                    )
                                }
                            }

                            com.example.sparely.ui.components.SparelyTextField(
                                value = descriptionText.value,
                                onValueChange = { descriptionText.value = it },
                                label = { Text(stringResource(R.string.income_description_label)) },
                                placeholder = { Text(stringResource(R.string.main_account_optional_note)) },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 2
                            )

                            com.example.sparely.ui.components.SheetDateField(
                                label = stringResource(R.string.income_date_label),
                                value = manualDate.value.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                                onClick = { showDatePicker.value = true }
                            )

                            com.example.sparely.ui.components.SheetToggleRow(
                                title = stringResource(R.string.income_distribute_vaults),
                                description = stringResource(R.string.income_distribute_vaults_desc),
                                checked = manualDistribute.value,
                                onCheckedChange = { manualDistribute.value = it }
                            )

                            // Pending transfers only apply to the vault split, so hide the option when it's off.
                            if (manualDistribute.value) {
                                com.example.sparely.ui.components.SheetToggleRow(
                                    title = stringResource(R.string.income_pending_transfers),
                                    description = stringResource(R.string.income_pending_transfers_desc),
                                    checked = manualPending.value,
                                    onCheckedChange = { manualPending.value = it }
                                )
                            }

                            com.example.sparely.ui.components.SheetActions(
                                confirmLabel = stringResource(R.string.record_income_title),
                                onConfirm = {
                                    val amt = manualAmountText.value.toSafeDouble()
                                    if (amt != null && amt > 0.0) {
                                        val desc = descriptionText.value.ifEmpty { defaultIncomeDescription }
                                        viewModel.recordPaycheck(
                                            amt,
                                            manualDate.value,
                                            manualDistribute.value,
                                            manualPending.value,
                                            incomeCategory = selectedCategory.value,
                                            description = desc
                                        )
                                        manualAmountText.value = ""
                                        descriptionText.value = ""
                                        showIncomeDialog.value = false
                                    }
                                },
                                confirmEnabled = manualAmountText.value.toSafeDouble()?.let { it > 0.0 } == true,
                                dismissLabel = stringResource(R.string.common_cancel),
                                onDismiss = { showIncomeDialog.value = false }
                            )
                        }
                    }

                    if (showDatePicker.value) {
                        val initialMillis = DateUtils.toSafeDatePickerMillis(manualDate.value)
                        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
                        DatePickerDialog(
                            onDismissRequest = { showDatePicker.value = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    val selectedMillis = datePickerState.selectedDateMillis
                                    val selected = selectedMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                                    if (selected != null) manualDate.value = selected
                                    showDatePicker.value = false
                                }) { Text(stringResource(R.string.common_save)) }
                            },
                            dismissButton = { TextButton(onClick = { showDatePicker.value = false }) { Text(stringResource(R.string.common_cancel)) } }
                        ) {
                            DatePicker(state = datePickerState)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val app = LocalContext.current.applicationContext as com.example.sparely.SparelyApplication
        val aiEnabled = uiState.settings.onDeviceAiEnabled
        val languageCode = uiState.settings.regionalSettings.languageCode
        val onDeviceAi = remember(aiEnabled, languageCode) {
            com.example.sparely.ui.components.OnDeviceAi(
                manager = app.container.localAiManager,
                enabled = aiEnabled,
                language = com.example.sparely.ai.PromptLanguage.fromCode(languageCode)
            )
        }
        // AI cards only appear once the model is known to be ready, so check as soon as it's on.
        LaunchedEffect(aiEnabled) {
            if (!aiEnabled) return@LaunchedEffect
            val manager = app.container.localAiManager
            manager.refresh()
            // Load the model into memory before a card needs it: on the CPU that alone takes seconds.
            manager.state.first { it == com.example.sparely.ai.LocalAiState.Ready }
            manager.warmup()
        }
        androidx.compose.runtime.CompositionLocalProvider(com.example.sparely.ui.components.LocalOnDeviceAi provides onDeviceAi) {
            SparelyNavHost(
                navController = navController,
                innerPadding = innerPadding,
                viewModel = viewModel,
                uiState = uiState,
                settingsViewModel = settingsViewModel,
                settingsUiState = settingsUiState,
                vaultViewModel = vaultViewModel,
                vaultUiState = vaultUiState,
                allVaults = allVaults,
                snackbarHostState = snackbarHostState,
                onAuthenticateUser = onAuthenticateUser,
                settingsHeader = settingsHeader
            )
        }
    }
    
    // Vault archive confirmation dialog
    uiState.vaultArchivePrompt?.let { prompt ->
    // UI observed a prompt
        VaultArchiveConfirmationDialog(
            prompt = prompt,
            onConfirmArchive = { 
                viewModel.archiveVaultFromPrompt(prompt.vaultId)
                // Navigate back to previous screen after archiving
                if (currentDestination?.route == SparelyDestination.ExpenseEntry.route) {
                    navController.popBackStack()
                }
            },
            onDismiss = {
                viewModel.dismissVaultArchivePrompt()
                // Navigate back to previous screen after dismissing
                if (currentDestination?.route == SparelyDestination.ExpenseEntry.route) {
                    navController.popBackStack()
                }
            }
        )
        // No fallback overlay; AlertDialog is the primary UI for this prompt
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SparelyTopBar(
    currentDestination: NavDestination?,
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    titleOverride: String? = null,
    // Replaces the usual back navigation, e.g. closing an open settings section
    onBack: (() -> Unit)? = null
) {
    val destination = SparelyDestination.fromRoute(currentDestination?.route)
    val title = titleOverride
        ?: destination?.labelRes?.let { stringResource(it) }
        ?: stringResource(R.string.app_name)
    val isTopLevel = destination != null && destination in bottomBarDestinations
    // Left-aligned like the dashboard greeting and page content, a step smaller than titleLarge
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (!isTopLevel || onBack != null) {
                // Through the dispatcher so a screen's BackHandler (e.g. unsaved changes) sees it too
                val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                IconButton(onClick = { onBack?.invoke() ?: backDispatcher?.onBackPressed() ?: navController.popBackStack() }) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.ARROW_BACK,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        scrollBehavior = scrollBehavior
    )
}


private val bottomBarDestinations = setOf(
    SparelyDestination.Dashboard,
    SparelyDestination.History,
    SparelyDestination.Vaults,
    SparelyDestination.Settings
)

@Composable
private fun SparelyBottomBar(
    currentDestination: NavDestination?,
    navController: NavHostController
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp // M3 Expressive prefers flat/tonal without shadow
    ) {
        for (destination in bottomBarDestinations) {
            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    // Always land on the tab's root page, never on a subpage restored from an earlier visit.
                    val returnedToRoot = navController.popBackStack(destination.route, inclusive = false)
                    if (!returnedToRoot) {
                        navController.navigate(destination.createRoute()) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                },
                icon = {
                    val icon = destination.iconDrawable
                    if (icon != null) {
                        // Animate icon scale/weight on selection if possible, currently just icon
                         MaterialSymbolIcon(
                            icon = icon,
                            contentDescription = destination.labelRes?.let { stringResource(it) } ?: ""
                        )
                    }
                },
                label = { 
                    Text(
                        text = destination.labelRes?.let { stringResource(it) } ?: "",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    ) 
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun SparelyNavHost(
    navController: NavHostController,
    innerPadding: PaddingValues,
    viewModel: SparelyViewModel,
    uiState: com.example.sparely.ui.state.SparelyUiState,
    settingsViewModel: SettingsViewModel,
    settingsUiState: com.example.sparely.ui.viewmodel.SettingsUiState,
    vaultViewModel: VaultViewModel,
    vaultUiState: com.example.sparely.ui.viewmodel.VaultUiState,
    allVaults: List<com.example.sparely.domain.model.SmartVault>,
    snackbarHostState: SnackbarHostState,
    onAuthenticateUser: ((Boolean) -> Unit) -> Unit,
    settingsHeader: com.example.sparely.ui.screens.SettingsHeaderState
) {
    val pendingVariableBills by viewModel.pendingVariableBills.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    NavHost(
        navController = navController,
        startDestination = SparelyDestination.Dashboard.route,
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        composable(
            route = SparelyDestination.Dashboard.route,
            arguments = listOf(
                androidx.navigation.navArgument("action") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            DashboardScreen(
                uiState = uiState,
                pendingVaultContributions = vaultUiState.pendingVaultContributions,
                pendingVariableBills = pendingVariableBills,
                onConfirmVariableBill = viewModel::confirmVariableBill,
                onAddExpense = { navController.navigate(SparelyDestination.ExpenseEntry.route) },
                onRepeatLastExpense = { expense ->
                    viewModel.setPrefillExpense(expense.copy(id = 0L))
                    navController.navigate(SparelyDestination.ExpenseEntry.route)
                },
                onNavigateToHistory = { navController.navigate(SparelyDestination.History.route) },
                onNavigateToBudgets = { navController.navigate(SparelyDestination.Budgets.route) },
                onNavigateToChallenges = { navController.navigate(SparelyDestination.Challenges.route) },
                onNavigateToHealth = { navController.navigate(SparelyDestination.Health.route) },
                onNavigateToPurchaseCheck = { navController.navigate(SparelyDestination.PurchaseCheck.route) },
                onNavigateToRecurring = { navController.navigate(SparelyDestination.Recurring.route) },
                onManageVaults = { navController.navigate(SparelyDestination.Vaults.route) },
                onManageSavingsAccounts = { navController.navigate(SparelyDestination.ManageSavingsAccounts.route) },
                savingsAccounts = uiState.savingsAccounts,
                onNavigateToVaultTransfers = { navController.navigate(SparelyDestination.VaultTransfers.route) },
                onNavigateToMainAccount = { navController.navigate(SparelyDestination.MainAccount.route) },
                onNavigateToCreditCards = { navController.navigate(SparelyDestination.CreditCards.route) },
                onNavigateToInsights = { navController.navigate(SparelyDestination.Insights.route) },
                onConvertRecurringInsight = { insight ->
                    viewModel.startRecurringFromInsight(insight)
                    navController.navigate(SparelyDestination.Recurring.route)
                },
                onTransferIdleToSavings = { amount ->
                    // Create a pending contribution for the suggested idle money transfer
                    uiState.idleMoneyInsight?.suggestedVault?.id?.let { vaultId ->
                        vaultViewModel.createIdleMoneyPendingTransfer(amount, vaultId)
                    }
                    navController.navigate(SparelyDestination.VaultTransfers.route)
                },
                onManageAssets = { navController.navigate(SparelyDestination.Assets.route) },
                // the global FAB/menu will be shown by the scaffold, so hide dashboard's own FAB
                showFloatingFab = false
            )
        }
        composable(
            route = SparelyDestination.History.route,
            arguments = listOf(
                androidx.navigation.navArgument("highlightExpenseId") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val brandSearchResults by viewModel.brandSearchResults.collectAsStateWithLifecycle()
            val highlightExpenseId = backStackEntry.arguments?.getString("highlightExpenseId")?.toLongOrNull()
            HistoryScreen(
                expenses = uiState.expenses,
                pagedExpenses = uiState.pagedExpenses,
                canLoadMore = uiState.canLoadMoreExpenses,
                onLoadMore = viewModel::loadMoreExpenses,
                selectedStore = uiState.selectedStore,
                selectedStoreHistory = uiState.selectedStoreHistory,
                isStoreHistoryLoading = uiState.isStoreHistoryLoading,
                onStoreSelected = viewModel::selectStore,
                onClearSelectedStore = viewModel::clearSelectedStore,
                stores = uiState.stores,
                onDeleteExpense = { expense -> viewModel.deleteExpense(expense.id) },
                onDeleteExpenses = { expenses -> expenses.forEach { viewModel.deleteExpense(it.id) } },
                onDuplicateExpense = { expense ->
                    viewModel.setPrefillExpense(expense.copy(id = 0L))
                    navController.navigate(SparelyDestination.ExpenseEntry.route)
                },
                onEditExpense = { expense ->
                    viewModel.setPrefillExpense(expense)
                    navController.navigate(SparelyDestination.ExpenseEntry.route)
                },
                onEditExpenseWithAssets = { expense, assetAllocations ->
                    // Direct edit via popup - don't navigate
                    viewModel.updateExpenseWithAssets(expense, assetAllocations)
                },
                onNavigateBack = { navController.popBackStack() },
                onAddExpense = { navController.navigate(SparelyDestination.ExpenseEntry.route) },
                onCreateStore = { input ->
                    val storeId = viewModel.addStore(input).await()
                    viewModel.getStoreById(storeId)
                },
                onEditStore = viewModel::updateStore,
                onDeleteStore = viewModel::deleteStore,
                brandfetchClientId = uiState.settings.brandfetchClientId,
                brandSearchResults = brandSearchResults,
                onBrandSearch = viewModel::searchBrands,
                onRefundExpense = viewModel::refundExpense,
                paymentMethods = uiState.paymentMethods,
                vaults = uiState.smartVaults,
                assets = uiState.assets,
                onLoadAssetAllocationsForExpense = { expenseId ->
                    viewModel.getAssetAllocationsForExpense(expenseId)
                },
                highlightExpenseId = highlightExpenseId
            )
        }
        composable(SparelyDestination.Vaults.route) {
            val vaultAiLanguage = com.example.sparely.ui.components.LocalOnDeviceAi.current.language
            val vaultSymbol = uiState.settings.regionalSettings.getCurrencySymbol()
            val vaultAiFacts = remember(allVaults, uiState.vaultProjections, uiState.settings.monthlyIncome, vaultAiLanguage, vaultSymbol) {
                com.example.sparely.ai.FinanceFacts.vaults(
                    vaults = allVaults,
                    projections = uiState.vaultProjections,
                    monthlyIncome = uiState.settings.monthlyIncome,
                    lang = vaultAiLanguage,
                    money = { vaultAiLanguage.money(it, vaultSymbol) }
                )
            }
            VaultManagementScreen(
                aiFacts = vaultAiFacts,
                vaults = allVaults,
                monthlyIncome = uiState.settings.monthlyIncome,
                recentMonthlyExpenses = uiState.analytics.totalSpent,
                savingsRate = uiState.smartSavingSummary?.actualSavingsRate ?: 0.0,
                onAddVault = vaultViewModel::addSmartVault,
                onUpdateVault = vaultViewModel::updateSmartVault,
                onDeleteVault = vaultViewModel::deleteSmartVault,
                onNavigateBack = { navController.popBackStack() },
                onManualDeposit = { vaultId, amount, reason, adjustMainAccount ->
                    vaultViewModel.depositToVault(vaultId, amount, reason, adjustMainAccount)
                },
                onManualWithdrawal = { vaultId, amount, reason, creditMainAccount ->
                    vaultViewModel.deductFromVault(vaultId, amount, reason, creditMainAccount)
                },
                onViewHistory = { vaultId ->
                    // Navigation only, history load happens in destination
                    navController.navigate(SparelyDestination.VaultHistory.createRoute(vaultId))
                },
                onBalanceOverride = { vaultId, newBalance, reason ->
                    vaultViewModel.overrideVaultBalance(vaultId, newBalance, reason)
                }
            )

        }
        composable(SparelyDestination.Budgets.route) {
            BudgetScreen(
                uiState = uiState,
                onAddBudget = viewModel::addBudget,
                onUpdateBudget = viewModel::updateBudget,
                onDeleteBudget = viewModel::deleteBudget,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(SparelyDestination.Challenges.route) {
            ChallengesScreen(
                uiState = uiState,
                onStartChallenge = viewModel::startChallenge,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(SparelyDestination.Recurring.route) {
            val brandSearchResults by viewModel.brandSearchResults.collectAsStateWithLifecycle()
            val recurringPaymentUndos by viewModel.recurringPaymentUndos.collectAsStateWithLifecycle()
            val undoRecurringPayment: (Long) -> Unit = { id ->
                viewModel.undoRecurringPayment(id) { vaultViewModel.refreshPendingContributions() }
            }
            // A one-tap way back from a payment marked by mistake
            val offerRecurringPaymentUndo: (Long) -> Unit = { id ->
                vaultViewModel.refreshPendingContributions()
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.recurring_marked_paid_message),
                        actionLabel = context.getString(R.string.recurring_undo_paid_action),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) undoRecurringPayment(id)
                }
            }
            val recurringAiLanguage = com.example.sparely.ui.components.LocalOnDeviceAi.current.language
            val recurringSymbol = uiState.settings.regionalSettings.getCurrencySymbol()
            val recurringAiFacts = remember(uiState.recurringExpenses, uiState.upcomingRecurring, uiState.settings.monthlyIncome, recurringAiLanguage, recurringSymbol) {
                com.example.sparely.ai.FinanceFacts.recurring(
                    expenses = uiState.recurringExpenses,
                    upcoming = uiState.upcomingRecurring,
                    monthlyIncome = uiState.settings.monthlyIncome,
                    lang = recurringAiLanguage
                ) { recurringAiLanguage.money(it, recurringSymbol) }
            }
            RecurringExpensesScreen(
                aiFacts = recurringAiFacts,
                recurringExpenses = uiState.recurringExpenses,
                recurringPaidRecords = uiState.recurringPaidRecords,
                smartVaults = uiState.smartVaults,
                stores = uiState.stores,
                assets = uiState.assets,
                onAddRecurring = viewModel::addRecurringExpense,
                pendingDetectedRecurring = uiState.pendingDetectedRecurring,
                detectedRecurring = uiState.detectedRecurringTransactions,
                // Opens the add form prefilled from the pattern (same flow as the dashboard card)
                onAddDetected = viewModel::startRecurringFromInsight,
                onAddDetectedRecurring = viewModel::addDetectedRecurring,
                onClearPendingDetectedRecurring = viewModel::clearPendingDetectedRecurring,
                onUpdateRecurring = viewModel::updateRecurringExpense,
                onDeleteRecurring = viewModel::deleteRecurringExpense,
                onMarkProcessed = { id -> viewModel.markRecurringProcessed(id) { offerRecurringPaymentUndo(id) } },
                onPayEarly = { id, dueDate, amountPaid, paidDate, notes ->
                    viewModel.payRecurringExpenseEarly(id, dueDate, amountPaid, paidDate, notes) { offerRecurringPaymentUndo(id) }
                },
                recurringPaymentUndos = recurringPaymentUndos,
                onUndoPayment = undoRecurringPayment,
                onCreateStore = { input ->
                    val storeId = viewModel.addStore(input).await()
                    viewModel.getStoreById(storeId)
                },
                onEditStore = viewModel::updateStore,
                onDeleteStore = viewModel::deleteStore,
                brandfetchClientId = uiState.settings.brandfetchClientId,
                paymentMethods = uiState.paymentMethods,
                onManagePaymentMethods = { navController.navigate(SparelyDestination.Settings.route) },
                brandSearchResults = brandSearchResults,
                onBrandSearch = viewModel::searchBrands,
                pendingVariableBills = pendingVariableBills,
                currencySymbol = recurringSymbol,
                onConfirmVariableBill = viewModel::confirmVariableBill
            )
        }
        composable(SparelyDestination.PurchaseCheck.route) {
            PurchaseCheckScreen(
                uiState = uiState,
                onOpenWishlist = { navController.navigate(SparelyDestination.Wishlist.route) },
                onSaveToWishlist = viewModel::addWishlistItem
            )
        }
        composable(SparelyDestination.WishDetail.route) { backStackEntry ->
            val wishId = backStackEntry.arguments?.getString("wishId")?.toLongOrNull() ?: 0L
            WishDetailScreen(
                uiState = uiState,
                wishId = wishId,
                onNavigateBack = { navController.popBackStack() },
                onAddSavings = viewModel::addSavingsToWishlist,
                onSetTargetDate = viewModel::setWishlistTargetDate,
                onStillWant = viewModel::confirmWishlistStillWanted,
                onNotAnymore = viewModel::archiveWishlistItem,
                onBought = viewModel::archiveWishlistItem,
                onDelete = viewModel::deleteWishlistItem
            )
        }
        composable(SparelyDestination.Wishlist.route) {
            WishlistScreen(
                uiState = uiState,
                onQuickCheck = { navController.navigate(SparelyDestination.PurchaseCheck.route) },
                onAddSavings = viewModel::addSavingsToWishlist,
                onBought = viewModel::archiveWishlistItem,
                onStillWant = viewModel::confirmWishlistStillWanted,
                onNotAnymore = viewModel::archiveWishlistItem,
                onOpenWish = { id -> navController.navigate(SparelyDestination.WishDetail.createRoute(id)) },
                onSetTargetDate = viewModel::setWishlistTargetDate,
                onDelete = viewModel::deleteWishlistItem
            )
        }
        composable(SparelyDestination.Health.route) {
            FinancialHealthScreen(
                uiState = uiState,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(SparelyDestination.Settings.route) {
            // Collected here, not at the app root, so download progress only recomposes Settings
            val localAiState by settingsViewModel.localAiState.collectAsStateWithLifecycle()
            val localAiPromptState by settingsViewModel.localAiPromptState.collectAsStateWithLifecycle()
            // Observe the result of file exports/imports to show snackbars
            LaunchedEffect(settingsUiState.errorMessage) {
                settingsUiState.errorMessage?.let { msg ->
                    if (msg.isNotEmpty()) {
                        snackbarHostState.showSnackbar(msg)
                        settingsViewModel.clearErrorMessage()
                    }
                }
            }
            
            SettingsScreen(
                settings = settingsUiState.settings,
                activeSaveRate = uiState.activeSaveRate,
                activeSavingTaxRate = uiState.activeSavingTaxRate,
                automationNotes = uiState.automationRationale,
                autoModeEnabled = settingsUiState.settings.autoRecommendationsEnabled,
                recommendation = uiState.recommendation,
                alerts = uiState.alerts,
                onPercentagesChange = settingsViewModel::updatePercentages,
                onAutoToggle = settingsViewModel::toggleAutoMode,
                onRiskChange = settingsViewModel::updateRiskLevel,
                onAgeChange = settingsViewModel::updateAge,
                onEducationStatusChange = settingsViewModel::updateEducationStatus,
                onEmploymentStatusChange = settingsViewModel::updateEmploymentStatus,
                onHasDebtsChange = settingsViewModel::updateHasDebts,
                onEmergencyFundChange = settingsViewModel::updateEmergencyFund,
                onPrimaryGoalChange = settingsViewModel::updatePrimaryGoal,
                onDisplayNameChange = settingsViewModel::updateDisplayName,
                onBirthdayChange = settingsViewModel::updateBirthday,
                onMonthlyIncomeChange = settingsViewModel::updateMonthlyIncome,
                onVaultAllocationModeChange = settingsViewModel::updateVaultAllocationMode,
                onSavingTaxRateChange = settingsViewModel::updateSavingTaxRate,
                onDynamicSavingTaxToggle = settingsViewModel::updateDynamicSavingTaxEnabled,
                onReminderChange = settingsViewModel::updateReminderSettings,
                onResetHistory = settingsViewModel::resetHistory,
                onPayScheduleChange = settingsViewModel::updatePaySchedule,
                onRecordPaycheck = viewModel::recordPaycheck, // Still in SparelyViewModel for now as it involves Transactions
                onAutoDepositsEnabledChange = settingsViewModel::updateAutoDepositsEnabled,
                onAutoDepositCheckHourChange = settingsViewModel::updateAutoDepositCheckHour,
                onManualAutoDepositTrigger = settingsViewModel::triggerManualAutoDepositCheck,
                autoDepositsEnabled = settingsUiState.settings.paySchedule.autoDistributeToVaults,
                autoDepositCheckHour = settingsUiState.autoDepositCheckHour,
                onRegionalSettingsChange = settingsViewModel::updateRegionalSettings,
                onMainAccountBalanceChange = settingsViewModel::updateMainAccountBalance,
                onExportData = { uri -> settingsViewModel.exportData(uri, context) },
                onImportData = { uri ->
                    settingsViewModel.importData(uri, context) {
                        // Data refreshed automatically via Flow
                    }
                },
                expenses = uiState.expenses,
                stores = uiState.stores,
                onExportExpensesToCsv = settingsViewModel::exportExpensesToCsv,
                onExpenseHistoryRetentionChange = settingsViewModel::updateExpenseHistoryRetention,
                paymentMethods = settingsUiState.paymentMethods,
                onAddPaymentMethod = settingsViewModel::addPaymentMethod,
                onEditPaymentMethod = settingsViewModel::updatePaymentMethod,
                onDeletePaymentMethod = settingsViewModel::deletePaymentMethod,
                onCreditCardReminderChange = settingsViewModel::updateCreditCardReminderSettings,
                onCreditCardUtilizationChange = settingsViewModel::updateCreditCardUtilizationAlert,
                onBiometricEnabledChange = settingsViewModel::updateBiometricEnabled,
                onAiAssistantAccessEnabledChange = settingsViewModel::updateAiAssistantAccessEnabled,
                onAiAssistantWriteEnabledChange = settingsViewModel::updateAiAssistantWriteEnabled,
                localAiState = localAiState,
                localAiCanAnswerQuestions = localAiPromptState == com.example.sparely.ai.LocalAiState.Ready,
                onOnDeviceAiEnabledChange = settingsViewModel::updateOnDeviceAiEnabled,
                onDynamicColorEnabledChange = settingsViewModel::updateDynamicColorEnabled,
                onPaymentDetectionEnabledChange = settingsViewModel::updatePaymentDetectionEnabled,
                onPaymentDetectionAppsChange = settingsViewModel::updatePaymentDetectionApps,
                headerState = settingsHeader,
                onRefreshLocalAi = settingsViewModel::refreshLocalAi,
                onDownloadLocalAi = settingsViewModel::startLocalAiDownload,
                onTestLocalAi = settingsViewModel::testLocalAi,
                assistantActions = settingsUiState.assistantActions,
                onUndoAssistantAction = settingsViewModel::undoAssistantAction,
                onAuthenticateUser = onAuthenticateUser,
                onSmartTransferMinimumChange = settingsViewModel::updateSmartTransferMinimumAmount,
                onAutoBackupSettingsChange = settingsViewModel::updateAutoBackupSettings,
                onBackupNowClick = {
                    settingsViewModel.triggerManualBackup(context) { success, message ->
                        scope.launch {
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                },
                smartVaults = settingsUiState.smartVaults,
                savingsAccounts = settingsUiState.savingsAccounts,
                onMainOverflowAccountIdChange = settingsViewModel::updateMainOverflowAccountId,
                onMinMainAccountBalanceChange = settingsViewModel::updateMinMainAccountBalance,
                onManageSavingsAccounts = { navController.navigate(SparelyDestination.ManageSavingsAccounts.route) }
            )
        }
        composable(SparelyDestination.ManageSavingsAccounts.route) {
            SavingsAccountsScreen(
                savingsAccounts = settingsUiState.savingsAccounts,
                onNavigateBack = { navController.popBackStack() },
                onAddAccount = viewModel::addSavingsAccount,
                onUpdateAccount = viewModel::updateSavingsAccount,
                onArchiveAccount = viewModel::archiveSavingsAccount,
                onAddInterest = viewModel::recordSavingsAccountInterest,
                onViewHistory = { accountId, accountName ->
                    navController.navigate(SparelyDestination.SavingsHistory.createRoute(accountId, accountName))
                },
                mainAccountBalance = uiState.settings.mainAccountBalance,
                onCorrectBalance = viewModel::correctSavingsAccountBalance,
                onDeposit = viewModel::depositToSavingsAccount,
                onWithdraw = viewModel::withdrawFromSavingsAccount,
                onRequestNotice = viewModel::requestSavingsWithdrawalNotice,
                onCancelNotice = viewModel::cancelSavingsWithdrawalNotice
            )
        }
        composable(SparelyDestination.SavingsHistory.route) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId")?.toLongOrNull() ?: 0L
            val accountName = backStackEntry.arguments?.getString("accountName") ?: stringResource(R.string.savings_account_fallback_name)

            SavingsHistoryScreen(
                accountId = accountId,
                accountName = accountName,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(SparelyDestination.Assets.route) {
            AssetManagementScreen(
                assets = uiState.assets,
                onNavigateBack = { navController.popBackStack() },
                onAddAsset = viewModel::addAsset,
                onUpdateAsset = viewModel::updateAsset,
                onDeleteAsset = viewModel::deleteAsset,
                onLoadLinkedExpenses = viewModel::getExpensesLinkedToAsset,
                onLoadAllExpenses = { uiState.expenses },
                onLoadAssetCostProjection = viewModel::getAssetCostProjection,
                onLinkCreatorExpense = { assetId, expenseId ->
                    viewModel.linkCreatorExpenseToAsset(assetId, expenseId, updateAssetPrice = true)
                },
                onUnlinkCreatorExpense = { assetId ->
                    // Unlink by setting creatorExpenseId to null
                    uiState.assets.find { it.id == assetId }?.let { asset ->
                        viewModel.updateAsset(asset.copy(creatorExpenseId = null))
                    }
                }
            )
        }
        composable(SparelyDestination.ExpenseEntry.route) {
            val brandSearchResults by viewModel.brandSearchResults.collectAsStateWithLifecycle()
            var shouldNavigateBack by remember { mutableStateOf(false) }
            var hasVaultDeduction by remember { mutableStateOf(false) }
            
            // Handle navigation after expense is saved
            // Handle navigation after expense is saved
            LaunchedEffect(shouldNavigateBack, uiState.vaultArchivePrompt) {
                if (shouldNavigateBack) {
                    if (hasVaultDeduction) {
                        // Wait a bit for the prompt to be set if there's vault deduction
                        kotlinx.coroutines.delay(300)
                        // Navigate only if no prompt appeared
                        if (uiState.vaultArchivePrompt == null) {
                            navController.popBackStack()
                        }
                    } else {
                        // No vault deduction, navigate immediately
                        navController.popBackStack()
                    }
                }
            }
            
            // Lets the form's keyboard padding know the Scaffold already padded for the system bars
            Box(modifier = Modifier.consumeWindowInsets(innerPadding)) {
            ExpenseEntryScreen(
                settings = uiState.settings,
                recommendation = uiState.recommendation,
                vaults = uiState.smartVaults,
                stores = uiState.stores,
                assets = uiState.assets,
                onSave = { input ->
                    hasVaultDeduction = input.deductFromVaultId != null
                    if (input.id != null) {
                        // Editing existing expense - create modified version and update with assets
                        val updatedExpense = uiState.expenses.find { it.id == input.id }?.copy(
                            description = input.description,
                            amount = input.amount,
                            category = input.category,
                            date = input.date,
                            includesTax = input.includesTax,
                            deductedFromVaultId = input.deductFromVaultId,
                            storeId = input.storeId,
                            paymentMethodId = input.paymentMethodId,
                            notes = input.notes,
                            orderNumber = input.orderNumber,
                            type = input.type,
                            items = input.items,
                            isIgnored = input.isIgnored
                        )
                        updatedExpense?.let {
                            viewModel.updateExpenseWithAssets(it, input.assetAllocations)
                        }
                    } else {
                        // Creating new expense
                        viewModel.addExpense(input) { expenseId ->
                            vaultViewModel.refreshPendingContributions()
                            // Confirms the save on the screen we return to, with a one-tap way back
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.expense_entry_saved_message),
                                    actionLabel = context.getString(R.string.expense_entry_saved_undo),
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.discardAddedExpense(expenseId)
                                    vaultViewModel.refreshPendingContributions()
                                }
                            }
                        }
                    }
                    viewModel.clearPrefillExpense() // Clear prefill after saving
                    shouldNavigateBack = true
                },
                onCancel = {
                    viewModel.clearPrefillExpense() // Clear prefill on cancel
                    navController.popBackStack()
                },
                onCreateStore = { input ->
                    val storeId = viewModel.addStore(input).await()
                    viewModel.getStoreById(storeId)
                },
                onEditStore = viewModel::updateStore,
                onDeleteStore = viewModel::deleteStore,
                brandfetchClientId = uiState.settings.brandfetchClientId,
                paymentMethods = uiState.paymentMethods,
                onManagePaymentMethods = { navController.navigate(SparelyDestination.Settings.route) },
                brandSearchResults = brandSearchResults,
                onBrandSearch = viewModel::searchBrands,
                onCreateAsset = { name, category, description, price ->
                    viewModel.createAssetFromExpense(
                        name = name,
                        category = category,
                        description = description,
                        assetPrice = price,
                        creatorExpenseId = 0L // Will be linked after expense is created
                    )
                },
                prefillExpense = uiState.prefillExpense,
                prefillAssetAllocations = uiState.prefillAssetAllocations,
                knownTitles = uiState.knownTitles
            )
            }
        }
        composable(SparelyDestination.VaultTransfers.route) {
            VaultTransfersScreen(
                vaults = allVaults,
                savingsAccounts = uiState.savingsAccounts,
                pendingContributions = vaultUiState.pendingVaultContributions,
                onApproveContribution = vaultViewModel::approvePendingVaultContribution,
                onApproveGroup = vaultViewModel::approvePendingVaultContributions,
                onCancelContribution = vaultViewModel::cancelPendingVaultContribution,
                onUpdateContributionAmount = vaultViewModel::updatePendingVaultContributionAmount,
                onStartNotificationWorkflow = vaultViewModel::startVaultTransferNotificationWorkflow,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(SparelyDestination.VaultHistory.route) { backStackEntry ->
            val vaultId = backStackEntry.arguments?.getString("vaultId")?.toLongOrNull() ?: 0L
            
            LaunchedEffect(vaultId) {
                vaultViewModel.loadVaultHistory(vaultId)
            }
            
            val vault = allVaults.find { it.id == vaultId }
            val historyItems = vaultUiState.vaultHistory[vaultId] ?: emptyList()
            
            VaultHistoryScreen(
                vaultName = vault?.name ?: stringResource(R.string.vault_fallback_name),
                historyItems = historyItems,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(SparelyDestination.MainAccount.route) {
            MainAccountScreen(
                currentBalance = uiState.settings.mainAccountBalance,
                transactions = uiState.mainAccountTransactions,
                onDeposit = viewModel::depositToMainAccount,
                onWithdraw = viewModel::withdrawFromMainAccount,
                onAdjust = viewModel::adjustMainAccountBalance,
                onNavigateBack = { navController.popBackStack() },
                onTransactionNavigate = { txn ->
                    txn.relatedExpenseId?.let { expenseId ->
                        navController.navigate(SparelyDestination.History.createRoute(expenseId))
                        return@MainAccountScreen
                    }
                    if (txn.type == com.example.sparely.data.local.MainAccountTransactionType.VAULT_CONTRIBUTION || txn.type == com.example.sparely.data.local.MainAccountTransactionType.HISA_TRANSFER) {
                        txn.relatedVaultContributionIds?.firstOrNull()?.let { contributionId ->
                            scope.launch {
                                val vaultId = viewModel.findVaultIdForContribution(contributionId)
                                if (vaultId != null) {
                                    navController.navigate(SparelyDestination.VaultHistory.createRoute(vaultId))
                                } else {
                                    navController.navigate(SparelyDestination.Vaults.route)
                                }
                            }
                        } ?: navController.navigate(SparelyDestination.Vaults.route)
                    }
                }
            )
        }
        composable(
            route = SparelyDestination.CreditCards.route,
            arguments = listOf(
                androidx.navigation.navArgument("cardId") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val cardId = backStackEntry.arguments?.getString("cardId")?.toLongOrNull()
            val creditCards = uiState.paymentMethods.filter { it.isCreditCard }
            CreditCardsScreen(
                creditCards = creditCards,
                mainAccountBalance = uiState.settings.mainAccountBalance,
                recentPayments = uiState.creditCardPayments,
                initialExpandedCardId = cardId,
                onPayBill = { paymentMethodId, amount, note, deductFromMainAccount ->
                    viewModel.payCreditCardBill(paymentMethodId, amount, note, deductFromMainAccount)
                    // Optional: Refresh data or show success message if not reactive
                },
                onNavigateBack = { navController.popBackStack() },
                // Payment methods (including credit cards) are managed in Settings
                onAddCard = { navController.navigate(SparelyDestination.Settings.route) },
                expenses = uiState.expenses
            )
        }
        composable(SparelyDestination.Insights.route) {
            val symbol = uiState.settings.regionalSettings.getCurrencySymbol()
            val aiLanguage = com.example.sparely.ui.components.LocalOnDeviceAi.current.language
            val aiFacts = remember(uiState, symbol, aiLanguage) {
                com.example.sparely.ai.FinanceFacts.overview(uiState, aiLanguage) { aiLanguage.money(it, symbol) }
            }
            InsightsScreen(
                aiFacts = aiFacts,
                cashflowForecast = uiState.cashflowForecast,
                spendingPatterns = uiState.spendingPatterns,
                recurringPatterns = uiState.recurringPatterns,
                seasonalInsights = uiState.seasonalInsights,
                idleMoneyInsight = uiState.idleMoneyInsight,
                uniqueExpenses = uiState.uniqueExpenses,
                onTransferToSavings = { amount ->
                    // Create a pending contribution for the suggested idle money transfer
                    uiState.idleMoneyInsight?.suggestedVault?.id?.let { vaultId ->
                        vaultViewModel.createIdleMoneyPendingTransfer(amount, vaultId)
                    }
                    navController.navigate(SparelyDestination.VaultTransfers.route)
                },
                onNavigateBack = { navController.popBackStack() },
                onAnomalyClick = { expense ->
                    navController.navigate(SparelyDestination.History.createRoute(expense.id))
                }
            )
        }
    }
}

private enum class SparelyDestination(
    val route: String,
    val icon: ImageVector?,
    val iconDrawable: Int?,
    @StringRes val labelRes: Int?
) {
    Dashboard("dashboard?action={action}", null, MaterialSymbols.HOME, R.string.dashboard_title),
    History("history?highlightExpenseId={highlightExpenseId}", null, MaterialSymbols.BAR_CHART, R.string.history_title),
    Vaults("vaults", null, MaterialSymbols.ACCOUNT_BALANCE_WALLET, R.string.vaults_title),
    Budgets("budgets", null, MaterialSymbols.ACCOUNT_BALANCE, R.string.budgets_title),
    Challenges("challenges", null, MaterialSymbols.TROPHY, R.string.challenges_title),
    Recurring("recurring", null, MaterialSymbols.SCHEDULE, R.string.recurring_title),
    Health("health", null, MaterialSymbols.FAVORITE, R.string.health_title),
    PurchaseCheck("purchaseCheck", null, MaterialSymbols.SHOPPING_BAG, R.string.purchase_check_title),
    Wishlist("wishlist", null, MaterialSymbols.SHOPPING_CART, R.string.wishlist_title),
    WishDetail("wishlist/{wishId}", null, MaterialSymbols.SHOPPING_BAG, R.string.wishlist_detail_title),
    Settings("settings", null, MaterialSymbols.SETTINGS, R.string.settings_title),
    ExpenseEntry("expense", null, MaterialSymbols.SAVINGS, R.string.expense_entry_title),
    CreditCards("creditCards?cardId={cardId}", null, MaterialSymbols.CREDIT_CARD, R.string.dashboard_credit_cards_title),
    VaultTransfers("vaultTransfers", null, MaterialSymbols.SWAP_HORIZ, R.string.vault_transfers_title),
    VaultHistory("vaultHistory/{vaultId}", null, MaterialSymbols.HISTORY, R.string.vault_history_screen_title),

    MainAccount("mainAccount", null, MaterialSymbols.ACCOUNT_BALANCE_WALLET, R.string.dashboard_main_account_title),
    Insights("insights", null, MaterialSymbols.LIGHTBULB, R.string.insights_title),
    ManageSavingsAccounts("manage_savings", null, MaterialSymbols.SAVINGS, R.string.savings_accounts_title),
    SavingsHistory("savingsHistory/{accountId}/{accountName}", null, MaterialSymbols.HISTORY, R.string.history_title),
    Assets("assets", null, MaterialSymbols.SHOPPING_BAG, R.string.assets_title);

    fun createRoute(vararg args: Any): String {
        var result = route
        args.forEach { arg ->
            result = result.replaceFirst(Regex("\\{[^}]+\\}"), arg.toString())
        }
        // Remove unresolved optional query parameters (e.g. "?action={action}")
        // This regex looks for ?key={val} or &key={val} at the end or proper positions
        // Simple approach: remove any param still containing definition braces
        result = result.replace(Regex("[?&][^=]+=\\{[^}]+\\}"), "")
        return result
    }

    companion object {
        fun fromRoute(route: String?): SparelyDestination? {
            val candidate = route?.substringBefore("?")
            return entries.find { it.route.substringBefore("?") == candidate }
        }
    }
}

@Composable
private fun VaultArchiveConfirmationDialog(
    prompt: VaultArchivePrompt,
    onConfirmArchive: () -> Unit,
    onDismiss: () -> Unit
) {
    // dialog composed for vault archive prompt
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = stringResource(R.string.vault_archive_prompt_title, prompt.vaultName),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.vault_archive_prompt_used, (prompt.expenseAmount / prompt.vaultBalanceBefore.coerceAtLeast(0.01)).formatPercent(0)),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.vault_archive_prompt_expense, prompt.expenseAmount.formatCurrency()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.vault_archive_prompt_balance_before, prompt.vaultBalanceBefore.formatCurrency()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (prompt.overflowToMainAccount > 0) {
                        Text(
                            text = stringResource(R.string.vault_archive_prompt_overflow, prompt.overflowToMainAccount.formatCurrency()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Text(
                    text = stringResource(R.string.vault_archive_prompt_question),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = { 
            TextButton(onClick = { onConfirmArchive() }) {
                Text(stringResource(R.string.vault_archive_prompt_confirm))
            }
        },
        dismissButton = { 
            TextButton(onClick = { onDismiss() }) {
                Text(stringResource(R.string.vault_archive_prompt_keep))
            }
        }
    )
}

@Composable
private fun IncomeToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
