package com.example.sparely

import android.Manifest

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.SparelyTheme
import kotlinx.coroutines.launch
import com.example.sparely.ui.SparelyApp
import com.example.sparely.ui.SparelyViewModel
import com.example.sparely.ui.SparelyViewModelFactory
import com.example.sparely.ui.splash.SPLASH_FADE_MS
import com.example.sparely.ui.splash.SplashHandoff
import com.example.sparely.ui.splash.SplashIntro
import android.util.Log
import com.sparely.app.R

private const val TAG = "MainActivity"

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val viewModel: SparelyViewModel by viewModels {
        SparelyViewModelFactory((application as SparelyApplication).container)
    }

    private val requestNotificationsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    val deepLinkDestination = mutableStateOf<String?>(null)
    
    // Auth state
    private var isAuthRequired = mutableStateOf(false)
    private var isAuthSuccessful = mutableStateOf(false)

    // Brand intro after the system splash; only on a cold start.
    private val showIntro = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the system splash only until the database and settings have loaded.
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value.isLoading }

        val handoff = SplashHandoff(createdAtUptimeMs = SystemClock.uptimeMillis())
        val playIntro = !introPlayedInProcess && savedInstanceState == null && !animationsDisabled()
        introPlayedInProcess = true
        showIntro.value = playIntro
        if (playIntro) {
            splashScreen.setOnExitAnimationListener { provider ->
                handoff.iconBounds = runCatching {
                    val icon = provider.iconView
                    val location = IntArray(2).also { icon.getLocationInWindow(it) }
                    Rect(
                        location[0].toFloat(),
                        location[1].toFloat(),
                        (location[0] + icon.width).toFloat(),
                        (location[1] + icon.height).toFloat()
                    )
                }.getOrNull()
                // Let the animated icon finish (Android 12+), then fade into the intro underneath.
                // iconAnimationStartMillis is wall-clock time (0 when there's no animation). Clamp so
                // a bad timestamp can never hold the splash longer than the animation itself.
                val iconAnimationDuration = provider.iconAnimationDurationMillis.coerceAtLeast(0L)
                val iconAnimationLeft = provider.iconAnimationStartMillis.takeIf { it > 0L }
                    ?.let { it + iconAnimationDuration - System.currentTimeMillis() }
                    ?: 0L
                provider.view.animate()
                    .alpha(0f)
                    .setStartDelay(iconAnimationLeft.coerceIn(0L, iconAnimationDuration))
                    .setDuration(SPLASH_FADE_MS)
                    .withStartAction { handoff.started = true }
                    .withEndAction { provider.remove() }
                    .start()
            }
        }
        
        // Apply locale before setting content
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationsPermission.launch(permission)
            }
        }
        
        handleDeepLink(intent)
        
        if (playIntro) {
            // Light system bar icons over the spruce splash and intro.
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            )
        } else {
            enableEdgeToEdge()
        }
        setContent {
            SparelyTheme {
                if (isAuthRequired.value && !isAuthSuccessful.value) {
                    // Show a simple lock screen or empty surface while auth prompt is active
                    androidx.compose.material3.Surface(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.background
                    ) {
                        androidx.compose.foundation.layout.Box(
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            androidx.compose.foundation.layout.Column(
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                            ) {
                                MaterialSymbolIcon(
                                    icon = MaterialSymbols.LOCK,
                                    contentDescription = null,
                                    modifier = androidx.compose.ui.Modifier.size(48.dp),
                                    tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                    size = 48.dp
                                )
                                androidx.compose.material3.Text(
                                    "Locked",
                                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                                )
                                androidx.compose.material3.Button(onClick = { checkBiometricAuth() }) {
                                    androidx.compose.material3.Text("Unlock")
                                }
                            }
                        }
                    }
                } else {
                    SparelyApp(
                        viewModel = viewModel,
                        deepLinkDestination = deepLinkDestination.value,
                        onDeepLinkHandled = { deepLinkDestination.value = null },
                        onAuthenticateUser = { onResult -> authenticateUser(onResult) }
                    )
                }
            }
            if (showIntro.value) {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                SplashIntro(
                    handoff = handoff,
                    appReady = !uiState.isLoading,
                    onFinished = {
                        showIntro.value = false
                        enableEdgeToEdge()
                    }
                )
            }
        }
        
        // Initial check
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val enabled = state.settings.biometricEnabled
                    Log.d(TAG, "UI State collected: biometricEnabled=$enabled")
                    if (enabled) {
                        isAuthRequired.value = true
                        if (!isAuthSuccessful.value) {
                            checkBiometricAuth()
                        }
                    } else {
                        // If biometric is disabled in settings, we don't require auth
                        isAuthRequired.value = false
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Re-check auth when returning to app if it's required but not successful
        if (isAuthRequired.value && !isAuthSuccessful.value) {
            Log.d(TAG, "onStart: biometric required and not successful, prompting")
            checkBiometricAuth()
        }
    }

    private fun checkBiometricAuth() {
        val biometricManager = androidx.biometric.BiometricManager.from(this)
        val authenticators = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                             androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        
        val canAuth = biometricManager.canAuthenticate(authenticators)
        Log.d(TAG, "canAuthenticate result: $canAuth")
        
        if (canAuth != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
            Log.w(TAG, "Biometric not available, skipping auth requirement")
            // If we can't authenticate, don't lock the user out
            isAuthSuccessful.value = true
            return
        }

        val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
        val biometricPrompt = androidx.biometric.BiometricPrompt(this, executor,
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.e(TAG, "Authentication error: $errorCode - $errString")
                }

                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG, "Authentication succeeded")
                    isAuthSuccessful.value = true
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w(TAG, "Authentication failed")
                }
            })

        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.auth_unlock_title))
            .setSubtitle(getString(R.string.auth_unlock_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build()

        Log.d(TAG, "Calling authenticate()")
        biometricPrompt.authenticate(promptInfo)
    }

    private fun authenticateUser(onResult: (Boolean) -> Unit) {
        val biometricManager = androidx.biometric.BiometricManager.from(this)
        val authenticators = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                             androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        
        val canAuth = biometricManager.canAuthenticate(authenticators)
        if (canAuth != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
            // If biometric not available, treat as success to avoid locking user out of settings
            onResult(true)
            return
        }

        val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
        val biometricPrompt = androidx.biometric.BiometricPrompt(this, executor,
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onResult(false)
                }

                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onResult(true)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Don't call onResult(false) yet, user can try again
                }
            })

        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.auth_confirm_identity))
            .setSubtitle(getString(R.string.auth_security_settings_desc))
            .setAllowedAuthenticators(authenticators)
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
    
    companion object {
        /** The intro plays once per process, so returning from the background or recreating skips it. */
        private var introPlayedInProcess = false
    }

    // Logic for returning to app - simple version: require auth again if we were backgrounded?
    // For MVP, simplistic "onCreate" check + state flow collection is a good start. 
    // Ideally we'd hook into onStop/onStart to reset auth if duration passed. 
    // Keeping it simple for now as requested.
    
    /** True when the user turned on "Remove animations" (animator duration scale 0). */
    private fun animationsDisabled(): Boolean =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
    }
    
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }
    
    private fun handleDeepLink(intent: android.content.Intent?) {
        val navigateTo = intent?.getStringExtra("navigate_to")
        if (navigateTo != null) {
            val destination = when (navigateTo) {
                "paycheck" -> "dashboard?action=record_income"
                "vaultDetails" -> {
                    val vaultId = intent.getLongExtra("vault_id", -1L)
                    if (vaultId != -1L) "vaultHistory/$vaultId" else "vaults"
                }
                "creditCards" -> {
                    val cardId = intent.getLongExtra("cardId", -1L)
                    if (cardId != -1L) "creditCards?cardId=$cardId" else "creditCards"
                }
                else -> navigateTo
            }
            deepLinkDestination.value = destination
        }
    }
}
