package com.lendtrack.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lendtrack.app.core.ui.theme.*

/**
 * Login screen — shown when no Firebase user is authenticated.
 *
 * Single tablet, single lender: no registration flow.
 * The lender account is pre-created in the Firebase Console.
 *
 * Design follows the LendTrack pink/cream/burgundy identity.
 */
@Composable
fun LoginScreen(
    uiState:    AuthUiState,
    onSignIn:   (email: String, password: String) -> Unit,
    onClearError: () -> Unit,
) {
    var email       by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var showPass    by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val isLoading    = uiState is AuthUiState.Loading

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        // Tablet-friendly card — centred, max 480dp wide
        Card(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(32.dp),
            shape  = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 32.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // ── Branding ───────────────────────────────────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("♥", fontSize = 32.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        "LendTrack",
                        style = MaterialTheme.typography.displayMedium,
                    )
                    Text(
                        "Your Lending, Organized.",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = PlayfairDisplay,
                            fontSize   = 15.sp,
                            color      = LtRose,
                        ),
                    )
                }

                HorizontalDivider(color = LtDivider)

                Text(
                    "Sign In",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                // ── Email ──────────────────────────────────────────────────
                OutlinedTextField(
                    value         = email,
                    onValueChange = { email = it; if (uiState is AuthUiState.Error) onClearError() },
                    label         = { Text("Email address") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction    = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    colors = outlinedFieldColors(),
                    enabled = !isLoading,
                )

                // ── Password ───────────────────────────────────────────────
                OutlinedTextField(
                    value         = password,
                    onValueChange = { password = it; if (uiState is AuthUiState.Error) onClearError() },
                    label         = { Text("Password") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    visualTransformation = if (showPass) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction    = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            onSignIn(email, password)
                        }
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(
                                if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPass) "Hide password" else "Show password",
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                    },
                    colors  = outlinedFieldColors(),
                    enabled = !isLoading,
                )

                // ── Error message ──────────────────────────────────────────
                if (uiState is AuthUiState.Error) {
                    Text(
                        uiState.message,
                        color    = LtRed,
                        style    = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ── Sign in button ─────────────────────────────────────────
                Button(
                    onClick  = { onSignIn(email, password) },
                    enabled  = !isLoading,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = LtRose,
                        contentColor   = Color.White,
                    ),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color     = Color.White,
                            modifier  = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            "Sign In",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 16.sp,
                            ),
                        )
                    }
                }

                Text(
                    "Single-device access — account managed in the Firebase Console.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = LtRose,
    unfocusedBorderColor    = MaterialTheme.colorScheme.outline,
    focusedLabelColor       = LtRose,
    unfocusedLabelColor     = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedContainerColor   = MaterialTheme.colorScheme.background,
    unfocusedContainerColor = MaterialTheme.colorScheme.background,
    cursorColor             = LtRose,
)

// Re-use BorderStroke from components
fun BorderStroke(width: androidx.compose.ui.unit.Dp, color: Color) =
    androidx.compose.foundation.BorderStroke(width, color)
