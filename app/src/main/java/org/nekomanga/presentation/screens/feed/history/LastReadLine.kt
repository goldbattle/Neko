package org.nekomanga.presentation.screens.feed.history

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import eu.kanade.tachiyomi.util.system.timeSpanFromNow
import org.nekomanga.R
import org.nekomanga.constants.Constants

@Composable
fun LastReadLine(
    lastRead: Long,
    hasPagesLeft: Boolean,
    pagesLeft: Int,
    style: TextStyle,
    lastReadPreviousChapter: String = "",
    textColor: Color,
) {
    val context = LocalContext.current
    val statusText =
        remember(context, lastRead, hasPagesLeft, pagesLeft, lastReadPreviousChapter) {
            val statuses = mutableListOf<String>()
            if (lastRead != 0L) {
                statuses.add(context.getString(R.string.read_, lastRead.timeSpanFromNow))
            }
            if (hasPagesLeft) {
                statuses.add(
                    context.resources.getQuantityString(
                        R.plurals.pages_left,
                        pagesLeft,
                        pagesLeft,
                    ),
                )
            }
            if (lastReadPreviousChapter.isNotEmpty()) {
                statuses.add(context.getString(R.string.last_read_, lastReadPreviousChapter))
            }
            statuses.joinToString(Constants.SEPARATOR)
        }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = statusText,
            style = style.copy(color = textColor, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
