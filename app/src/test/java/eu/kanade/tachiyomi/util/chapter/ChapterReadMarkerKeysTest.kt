package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterReadMarkerKeysTest {

    @Test
    fun `whole chapters share marker across volume labels`() {
        val a = chapter(number = 29f, vol = "5")
        val b = chapter(number = 29f, vol = "Vol.5")
        val c = chapter(number = 29f, vol = "")

        assertEquals(ChapterReadMarkerKeys.fromChapter(a), ChapterReadMarkerKeys.fromChapter(b))
        assertEquals(ChapterReadMarkerKeys.fromChapter(b), ChapterReadMarkerKeys.fromChapter(c))
    }

    @Test
    fun `extra chapter has distinct marker from whole`() {
        val whole = chapter(number = 29f, vol = "5")
        val extra = chapter(number = 29.5f, vol = "5")

        assertEquals("w:29", ChapterReadMarkerKeys.fromChapter(whole))
        assertEquals("e:5:29.5", ChapterReadMarkerKeys.fromChapter(extra))
    }

    @Test
    fun `matches set lookup`() {
        val ch = chapter(number = 6f, vol = "")
        assertTrue(ChapterReadMarkerKeys.matches(ch, setOf("w:6", "w:7")))
    }

    private fun chapter(number: Float, vol: String): Chapter =
        Chapter.create().apply {
            chapter_number = number
            this.vol = vol
            url = "https://example.com/ch"
        }
}
