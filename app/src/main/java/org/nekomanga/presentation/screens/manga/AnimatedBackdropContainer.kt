package org.nekomanga.presentation.screens.manga

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.manga.MangaConstants
import org.nekomanga.domain.manga.Artwork
import org.nekomanga.presentation.components.theme.ThemeColorState

@Composable
fun AnimatedBackdropContainer(
    isSearching: Boolean,
    backdropSize: MangaConstants.BackdropSize,
    themeColorState: ThemeColorState,
    artwork: Artwork,
    showBackdropOverlay: Boolean,
    dynamicCovers: Boolean,
    needsPalette: Boolean = true,
    generatePalette: (drawable: Drawable) -> Unit = {},
) {

    val screenHeight = LocalConfiguration.current.screenHeightDp

    val targetHeight: Dp by
        remember(isSearching, backdropSize, screenHeight) {
            derivedStateOf {
                when {
                    isSearching -> (screenHeight / 4).dp
                    else ->
                        when (backdropSize) {
                            MangaConstants.BackdropSize.Small -> (screenHeight / 2.8).dp
                            MangaConstants.BackdropSize.Large -> (screenHeight / 1.2).dp
                            else -> (screenHeight / 2.1).dp
                        }
                }
            }
        }

    val transition = updateTransition(targetHeight, label = "BackdropHeightTransition")

    // 3. Animate the height Dp value
    val animatedHeight by
        transition.animateDp(
            label = "BoxHeightAnimation",
            transitionSpec = {
                // Leaving the full-screen "loading" state snaps: the details should appear at once.
                if (this.initialState == screenHeight.dp) snap() else tween(durationMillis = 600)
            },
        ) { heightValue ->
            heightValue
        }

    val animatedGradientAlpha by
        transition.animateFloat(
            label = "GradientAlphaAnimation",
            transitionSpec = {
                if (this.initialState == screenHeight.dp) snap() else tween(durationMillis = 600)
            },
        ) { targetDp ->
            if (targetDp == screenHeight.dp) 0f else 1f
        }

    val baseModifier = Modifier.fillMaxWidth()

    // Opens directly at its final size (no full-screen loading state), so nothing below it moves.
    val heightModifier = baseModifier.height(animatedHeight)

    val animatedOverlayAlpha by
        transition.animateFloat(
            label = "OverlayAlphaAnimation",
            transitionSpec = {
                // Case 1: Shrinking from full-screen
                if (this.initialState == screenHeight.dp && this.targetState < this.initialState) {
                    snap()
                } else {
                    // Fade out immediately (or cross-fade)
                    tween(durationMillis = 200)
                }
            },
        ) { targetDp ->
            // The overlay should show IF:
            // 1. The master boolean `showBackdropOverlay` is true
            // 2. The target height is NOT full-screen
            // 3. The target height is NOT hidden (250.dp)
            if (showBackdropOverlay && targetDp != screenHeight.dp && targetDp != 250.dp) {
                1f
            } else {
                // Hide if disabled, or full-screen, or hidden.
                0f
            }
        }

    // 4. Apply the animated height to the BackDrop modifier
    Box(modifier = heightModifier) {
        BackDrop(
            themeColorState = themeColorState,
            dynamicCovers = dynamicCovers,
            artwork = artwork,
            backdropOverlayAlpha = animatedOverlayAlpha,
            modifier = Modifier.matchParentSize(),
            needsPalette = needsPalette,
            generatePalette = generatePalette,
        )

        Box(
            modifier =
                Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(200.dp)
                    .alpha(animatedGradientAlpha) // <-- Use the new animated alpha
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
                        ),
                    ),
        )
    }
}
