package org.nekomanga.presentation.screens.main

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun PulsingIcon(
    isPulsing: Boolean,
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    if (!isPulsing) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = modifier,
        )
    } else {
        AnimatedPulsingIcon(imageVector, contentDescription, modifier)
    }
}

@Composable
private fun AnimatedPulsingIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_tilt")

    // Scale animation (pulsing)
    val scale =
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.2f,
            animationSpec =
                infiniteRepeatable(animation = tween(500), repeatMode = RepeatMode.Reverse),
            label = "pulse",
        )

    // Rotation animation (tilting)
    val rotation =
        infiniteTransition.animateFloat(
            initialValue = -10f, // Start tilted left
            targetValue = 10f, // End tilted right
            animationSpec =
                infiniteRepeatable(
                    animation = tween(300), // Make it a bit faster for a "wobble"
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "tilt",
        )

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier =
            modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = rotation.value
            },
    )
}
