package com.example.sparely

import android.content.Context
import com.example.sparely.data.local.SparelyDatabase
import com.example.sparely.data.preferences.UserPreferencesRepository
import com.example.sparely.data.repository.BackupRepository
import com.example.sparely.data.repository.SavingsRepository
import com.example.sparely.domain.logic.RecommendationEngine
import com.example.sparely.notifications.NotificationScheduler
import com.example.sparely.workers.VaultAutoDepositScheduler
import com.example.sparely.workers.MonthlyAllocationScheduler
import com.example.sparely.workers.AutoBackupScheduler

interface AppContainer {
    val context: Context
    val savingsRepository: SavingsRepository
    val backupRepository: BackupRepository
    val preferencesRepository: UserPreferencesRepository
    val recommendationEngine: RecommendationEngine
    val notificationScheduler: NotificationScheduler
    val vaultAutoDepositScheduler: VaultAutoDepositScheduler
    val monthlyAllocationScheduler: MonthlyAllocationScheduler
    val autoBackupScheduler: AutoBackupScheduler
    // Expose smart allocation service for callers that need it
    val smartAllocationService: com.example.sparely.domain.allocation.SmartAllocationService
    val brandfetchRepository: com.example.sparely.data.repository.BrandfetchRepository
    val assistantActionRepository: com.example.sparely.data.repository.AssistantActionRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    override val context: Context get() = appContext
    private val database by lazy { SparelyDatabase.getInstance(appContext) }

    override val savingsRepository: SavingsRepository by lazy {
        SavingsRepository(
            expenseDao = database.expenseDao(),
            transferDao = database.transferDao(),
            budgetDao = database.budgetDao(),
            recurringExpenseDao = database.recurringExpenseDao(),
            challengeDao = database.challengeDao(),
            achievementDao = database.achievementDao(),
            savingsAccountDao = database.savingsAccountDao(),
            savingsAccountTransactionDao = database.savingsAccountTransactionDao(),
            smartVaultDao = database.smartVaultDao(),
            mainAccountDao = database.mainAccountDao(),
            frozenFundDao = database.frozenFundDao(),
            allocationHistoryDao = database.allocationHistoryDao(),
            storeDao = database.storeDao(),
            paymentMethodDao = database.paymentMethodDao(),
            creditCardPaymentDao = database.creditCardPaymentDao(),
            expenseItemDao = database.expenseItemDao(),
            expenseRefundDao = database.expenseRefundDao(),
            assetDao = database.assetDao(),
            assetExpenseLinkDao = database.assetExpenseLinkDao(),
            wishlistDao = database.wishlistDao(),
            wishlistSavingsDao = database.wishlistSavingsDao(),
            pendingVariableRecurringExpenseDao = database.pendingVariableRecurringExpenseDao(),
            recurringExpensePaidDao = database.recurringExpensePaidDao(),
            preferencesRepository = preferencesRepository,
            database = database
        )
    }

    override val assistantActionRepository by lazy {
        com.example.sparely.data.repository.AssistantActionRepository(database.assistantActionDao())
    }

    override val backupRepository: BackupRepository by lazy {
        BackupRepository(savingsRepository, preferencesRepository)
    }

    override val preferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(appContext)
    }

    override val recommendationEngine: RecommendationEngine by lazy {
        RecommendationEngine()
    }

    override val notificationScheduler: NotificationScheduler by lazy {
        NotificationScheduler(appContext)
    }
    
    override val vaultAutoDepositScheduler: VaultAutoDepositScheduler by lazy {
        VaultAutoDepositScheduler(appContext)
    }

    override val monthlyAllocationScheduler: MonthlyAllocationScheduler by lazy {
        MonthlyAllocationScheduler(appContext)
    }

    override val autoBackupScheduler: AutoBackupScheduler by lazy {
        AutoBackupScheduler(appContext)
    }

    // Smart allocation service (phase 2)
    override val smartAllocationService by lazy {
        com.example.sparely.domain.allocation.SmartAllocationService(
            smartVaultDao = database.smartVaultDao(),
            allocationHistoryDao = database.allocationHistoryDao()
        )
    }

    override val brandfetchRepository: com.example.sparely.data.repository.BrandfetchRepository by lazy {
        val loggingInterceptor = okhttp3.logging.HttpLoggingInterceptor().apply {
            level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
        }
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl("https://api.brandfetch.io/")
            .client(client)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()

        val api = retrofit.create(com.example.sparely.data.remote.BrandfetchApi::class.java)
        com.example.sparely.data.repository.BrandfetchRepositoryImpl(api)
    }
}

