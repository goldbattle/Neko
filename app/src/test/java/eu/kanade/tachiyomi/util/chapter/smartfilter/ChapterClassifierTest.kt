package eu.kanade.tachiyomi.util.chapter.smartfilter

import org.junit.Assert.assertEquals
import org.junit.Test
import org.nekomanga.domain.chapter.ChapterItem
import org.nekomanga.domain.chapter.SimpleChapter

class ChapterClassifierTest {

    @Test
    fun `classifies whole split and extra`() {
        assertEquals(ChapterKind.Whole, ChapterClassifier.classifyKind(ch(6f)))
        assertEquals(ChapterKind.Split, ChapterClassifier.classifyKind(ch(6.1f)))
        assertEquals(ChapterKind.Extra, ChapterClassifier.classifyKind(ch(6.5f)))
        assertEquals(ChapterKind.Extra, ChapterClassifier.classifyKind(ch(6.99f)))
    }

    @Test
    fun `family key ignores volume for wholes and splits`() {
        val whole = ChapterClassifier.classify(ch(1f, volume = ""))
        val split = ChapterClassifier.classify(ch(1.3f, volume = "1"))

        assertEquals(ChapterClassifier.familyKey(whole.key), ChapterClassifier.familyKey(split.key))
    }

    @Test
    fun `deduplication key ignores volume for wholes not extras`() {
        val wholeVol5 = ChapterClassifier.classify(ch(29f, volume = "5"))
        val wholeVolLabel = ChapterClassifier.classify(ch(29f, volume = "Vol.5"))
        val extraVol5 = ChapterClassifier.classify(ch(29.5f, volume = "5"))
        val extraVolLabel = ChapterClassifier.classify(ch(29.5f, volume = "Vol.5"))

        assertEquals(
            ChapterClassifier.deduplicationKey(wholeVol5),
            ChapterClassifier.deduplicationKey(wholeVolLabel),
        )
        assertEquals(ChapterKind.Whole, wholeVol5.kind)
        assertEquals(ChapterKind.Extra, extraVol5.kind)
        // Extras still key on volume (logcat: 29.5 dedupes separately for "5" vs "Vol.5").
        assertEquals("5", extraVol5.key.volume)
        assertEquals("Vol.5", extraVolLabel.key.volume)
    }

    private fun ch(number: Float, volume: String = ""): ChapterItem =
        ChapterItem(
            chapter =
                SimpleChapter(
                    id = 1L,
                    mangaId = 1L,
                    read = false,
                    bookmark = false,
                    lastPageRead = 0,
                    dateFetch = 0L,
                    sourceOrder = 0,
                    smartOrder = 0,
                    url = "u",
                    name = "n",
                    dateUpload = 0L,
                    chapterNumber = number,
                    pagesLeft = 0,
                    volume = volume,
                    chapterText = "",
                    chapterTitle = "",
                    language = "",
                    mangaDexChapterId = "",
                    oldMangaDexChapterId = null,
                    scanlator = "",
                    uploader = "",
                    isUnavailable = false,
                )
        )
}
