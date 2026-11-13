package org.nekomanga.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.zedlabs.pastelplaceholder.Pastel
import eu.kanade.tachiyomi.data.coil.dynamicCover
import org.nekomanga.domain.manga.Artwork
import org.nekomanga.presentation.extensions.conditional
import org.nekomanga.presentation.theme.Shapes

/**
 * Taken from main app then modified
 * https://github.com/tachiyomiorg/tachiyomi/blob/cd3cb72b65d2f5f6ec038eb319717e21e3a1731e/app/src/main/java/eu/kanade/presentation/components/MangaCover.kt
 */
enum class MangaCover(val ratio: Float) {
    Square(1f / 1f),
    Book(2f / 3f);

    @Composable
    operator fun invoke(
        modifier: Modifier = Modifier,
        artwork: Artwork,
        contentDescription: String = "",
        shape: Shape = RoundedCornerShape(Shapes.coverRadius),
        shouldOutlineCover: Boolean = true,
        dynamicCover: Boolean,
        shoulderOverlayCover: Boolean = false,
    ) {
        val color by remember { mutableIntStateOf(Pastel.getColorLight()) }

        val alpha =
            when (shoulderOverlayCover) {
                true -> NekoColors.disabledAlphaLowContrast
                false -> NekoColors.highAlphaHighContrast
            }

        val context = LocalContext.current
        val request =
            remember(artwork, dynamicCover) {
                ImageRequest.Builder(context).data(artwork).dynamicCover(dynamicCover).build()
            }
        val placeholderColor = colorResource(color)
        val placeholder = remember(placeholderColor) { ColorPainter(placeholderColor) }

        AsyncImage(
            model = request,
            placeholder = placeholder,
            contentDescription = contentDescription,
            alpha = alpha,
            contentScale = ContentScale.Crop,
            modifier =
                modifier
                    .aspectRatio(ratio)
                    .clip(shape)
                    .conditional(
                        shouldOutlineCover,
                        {
                            this.border(
                                width = Outline.thickness,
                                color = Outline.color,
                                shape = RoundedCornerShape(Shapes.coverRadius),
                            )
                        },
                    ),
        )
    }
}
