package com.example.ui.screens

import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.ui.viewmodel.MainViewModel

@Composable
fun AppLockScreen(
    viewModel: MainViewModel,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val authMethod by viewModel.authMethod.collectAsState()
    val isBiometricAvailable = remember { viewModel.isBiometricAvailable() }

    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPinInputFallback by remember { mutableStateOf(authMethod == "PIN" || !isBiometricAvailable) }

    fun launchBiometric() {
        if (activity != null && isBiometricAvailable) {
            val executor = ContextCompat.getMainExecutor(activity)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Meu Financeiro")
                .setSubtitle("Autenticação necessária para acessar o app")
                .setNegativeButtonText("Usar PIN")
                .build()

            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        errorMessage = null
                        onUnlocked()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        errorMessage = errString.toString()
                        showPinInputFallback = true
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        errorMessage = "Biometria não reconhecida. Tente novamente."
                        showPinInputFallback = true
                    }
                }
            )

            try {
                biometricPrompt.authenticate(promptInfo)
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Erro ao iniciar biometria. Use o PIN."
                showPinInputFallback = true
            }
        } else {
            showPinInputFallback = true
        }
    }

    // Launch biometric automatically if configured and available
    LaunchedEffect(Unit) {
        if (authMethod == "BIOMETRIC" && isBiometricAvailable) {
            launchBiometric()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // App Header / Shield Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Acesso Bloqueado",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Meu Financeiro",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Acesso Protegido",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Error Message
            AnimatedVisibility(visible = errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }

            if (authMethod == "BIOMETRIC" && isBiometricAvailable && !showPinInputFallback) {
                // Biometric mode screen
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(
                        onClick = { launchBiometric() },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Usar Biometria",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Text(
                        text = "Toque no ícone para autenticar por biometria",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    OutlinedButton(
                        onClick = { showPinInputFallback = true },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(Icons.Default.Pin, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Usar PIN de Acesso")
                    }
                }
            } else {
                // PIN input mode screen
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "Digite seu PIN de 4 a 6 dígitos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )

                    // PIN Dots (4 to 6 indicator circles)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val maxDisplayDots = maxOf(6, pinInput.length)
                        for (i in 0 until 6) {
                            val isFilled = i < pinInput.length
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isFilled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Numeric Keypad
                    NumericKeypad(
                        onDigitClick = { digit ->
                            if (pinInput.length < 6) {
                                val newInput = pinInput + digit
                                pinInput = newInput
                                errorMessage = null

                                // Auto verify if pin length is 4, 5, or 6
                                if (newInput.length in 4..6) {
                                    if (viewModel.verifyPin(newInput)) {
                                        onUnlocked()
                                    } else if (newInput.length == 6) {
                                        errorMessage = "PIN incorreto. Tente novamente."
                                        pinInput = ""
                                    }
                                }
                            }
                        },
                        onBackspaceClick = {
                            if (pinInput.isNotEmpty()) {
                                pinInput = pinInput.dropLast(1)
                                errorMessage = null
                            }
                        },
                        onConfirmClick = {
                            if (pinInput.length in 4..6) {
                                if (viewModel.verifyPin(pinInput)) {
                                    errorMessage = null
                                    onUnlocked()
                                } else {
                                    errorMessage = "PIN incorreto. Tente novamente."
                                    pinInput = ""
                                }
                            } else {
                                errorMessage = "O PIN deve ter entre 4 e 6 dígitos."
                            }
                        }
                    )

                    if (isBiometricAvailable && authMethod == "BIOMETRIC") {
                        TextButton(onClick = { launchBiometric() }) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Usar Biometria")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NumericKeypad(
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onConfirmClick: () -> Unit
) {
    val buttons = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("back", "0", "ok")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        buttons.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    KeypadButton(
                        key = key,
                        onClick = {
                            when (key) {
                                "back" -> onBackspaceClick()
                                "ok" -> onConfirmClick()
                                else -> onDigitClick(key)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    key: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(
                when (key) {
                    "ok" -> MaterialTheme.colorScheme.primaryContainer
                    "back" -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
            .clickable { onClick() }
            .testTag("keypad_$key"),
        contentAlignment = Alignment.Center
    ) {
        when (key) {
            "back" -> Icon(
                imageVector = Icons.Default.Backspace,
                contentDescription = "Apagar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            "ok" -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Confirmar",
                tint = MaterialTheme.colorScheme.primary
            )
            else -> Text(
                text = key,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
