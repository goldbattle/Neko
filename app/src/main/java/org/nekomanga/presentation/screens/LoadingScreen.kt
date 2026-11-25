package org.nekomanga.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import kotlinx.coroutines.delay
import org.nekomanga.presentation.theme.ThemeConfig
import org.nekomanga.presentation.theme.ThemeConfigProvider
import org.nekomanga.presentation.theme.ThemedPreviews

@Composable
fun LoadingScreen(showLoadingIcon: Boolean = true) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (showLoadingIcon) {
            ContainedLoadingIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

/**
 * Spinner for list screens that are about to show content: invisible for the first [delayMs] (so a
 * quick load or a tab crossfade never flashes a spinner under content), then fades in only if
 * loading is genuinely slow.
 */
@Composable
fun DelayedLoadingIndicator(modifier: Modifier = Modifier, delayMs: Long = 600) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        show = true
    }
    AnimatedVisibility(visible = show, enter = fadeIn(tween(200)), modifier = modifier) {
        ContainedLoadingIndicator()
    }
}

@Preview
@Composable
private fun LoadingScreenPreview(
    @PreviewParameter(ThemeConfigProvider::class) themeConfig: ThemeConfig,
) {
    ThemedPreviews(themeConfig) { LoadingScreen() }
}
