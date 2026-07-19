package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.auth.AuthManager
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.MainScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

import android.os.Build
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.data.notification.NotificationTriggerManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.app.AlertDialog

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            showPermissionDeniedDialog()
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notificações Desativadas")
            .setMessage("Sem a permissão de notificações, os alertas críticos (como categoria com limite estourado, faturas e parcelas vencendo, ou metas concluídas) não poderão ser exibidos. Deseja ativar agora nas configurações?")
            .setPositiveButton("Ir para Configurações") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Agora Não", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            // Request POST_NOTIFICATIONS permission for Android 13+ (API 33+) safely on launch
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permission = android.Manifest.permission.POST_NOTIFICATIONS
                    if (androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestPermissionLauncher.launch(permission)
                    }
                }
            }

            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val authState by viewModel.authState.collectAsStateWithLifecycle()
            val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()

            val useDarkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        authState is AuthManager.AuthState.Unauthenticated -> {
                            LoginScreen(
                                viewModel = viewModel,
                                onLoginSuccess = {
                                    // Reactively handled by state updates
                                }
                            )
                        }
                        !onboardingCompleted -> {
                            OnboardingScreen(
                                viewModel = viewModel,
                                onOnboardingFinished = {
                                    // Reactively handled by state updates
                                }
                            )
                        }
                        else -> {
                            MainScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val userId = viewModel.currentUserId
        if (userId.isNotBlank()) {
            val app = application as MeuFinanceiroApplication
            lifecycleScope.launch {
                try {
                    NotificationTriggerManager.checkAndTriggerNotifications(
                        context = this@MainActivity,
                        repository = app.repository,
                        userPreferences = app.userPreferences,
                        userId = userId
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val type = intent?.getStringExtra("notification_type") ?: return
        val referenceId = intent.getStringExtra("reference_id")
        val referenceMonth = intent.getStringExtra("reference_month")
        viewModel.handleDeepLink(type, referenceId, referenceMonth)
    }
}
