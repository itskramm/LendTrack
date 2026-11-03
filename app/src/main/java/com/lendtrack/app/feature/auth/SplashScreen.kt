package com.lendtrack.app.feature.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lendtrack.app.core.ui.theme.*

/**
 * Splash screen — shown while FirebaseAuth resolves the current user.
 * Displays the LendTrack logo and branding with a fade-in animation.
 * Typically visible for < 1 second.
 */
@Composable
fun SplashScreen() {
    val alpha by remember {
        val anim = Animatable(0f)
        anim
    }.let { anim ->
        LaunchedEffect(Unit) {
            anim.animateTo(1f, animationSpec = tween(600, easing = EaseOut))
        }
        anim.asState()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Heart logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("♥", fontSize = 40.sp, color = LtRose)
            }

            Text(
                "LendTrack",
                style = MaterialTheme.typography.displayLarge,
                color = LtBurgundy,
            )
            Text(
                "Your Lending, Organized.",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = PlayfairDisplay,
                    color      = LtRose,
                ),
            )
        }
    }
}
