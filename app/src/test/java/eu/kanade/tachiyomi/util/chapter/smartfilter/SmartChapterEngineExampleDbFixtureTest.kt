package eu.kanade.tachiyomi.util.chapter.smartfilter

import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.util.chapter.ExampleDbGroups
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.nekomanga.domain.chapter.ChapterItem
import org.nekomanga.domain.chapter.SimpleChapter

/**
 * Regression from example DB export (manga id=58): smart filter must not hide whole chapters 8–20
 * while .5 extras remain. Scanlator strings use generic [ExampleDbGroups] labels. Prefer
 * [SmartChapterEngineDenseLibraryFixtureTest] for hand-authored role fixtures.
 */
class SmartChapterEngineExampleDbFixtureTest {

    private val engine = SmartChapterEngine()
    private val downloadManager = mockk<DownloadManager>(relaxed = true)
    private val manga =
        MangaImpl().apply {
            id = 58L
            title = "Example DB manga"
        }

    @Before
    fun clearCache() {
        SmartChapterEngine.clearCache()
        every { downloadManager.isChapterDownloaded(any(), any()) } returns false
        every { downloadManager.isChapterDownloaded(any(), any(), any()) } returns false
    }

    @Test
    fun `example db chapters 8 to 20 visible alongside dot5 extras`() {
        val result =
            engine.process(
                SmartChapterInput(
                    manga = manga,
                    chapters = exampleDbChapters7Through21(),
                    downloadManager = downloadManager,
                )
            )

        val visibleNumbers = result.visibleChapters.map { it.chapter.chapterNumber }.toSet()

        val expectedWholes = (8..20).map { it.toFloat() }.toSet()
        val missingWholes = expectedWholes - visibleNumbers
        assertTrue(
            "Missing whole chapters: $missingWholes (visible extras only bug). " +
                "Visible numbers: ${visibleNumbers.sorted()}",
            missingWholes.isEmpty(),
        )

        assertTrue(7.5f in visibleNumbers)
        assertTrue(13.5f in visibleNumbers)
        assertTrue(19.5f in visibleNumbers)

        // Splits suppressed when whole exists (e.g. 20.1, 20.2 when 20.0 present)
        assertTrue(20.1f !in visibleNumbers)
        assertTrue(20.2f !in visibleNumbers)
    }

    fun exampleDbChapters7Through21(): List<ChapterItem> =
        listOf(
            chapter(id = 1479L, number = 21.0f, scanlator = "Group 8", chapterText = "Ch.21"),
            chapter(
                id = 1480L,
                number = 20.200000762939453f,
                scanlator = "Group 8",
                chapterText = "Ch.20.2",
            ),
            chapter(
                id = 1481L,
                number = 20.100000381469727f,
                scanlator = "Group 8",
                chapterText = "Ch.20.1",
            ),
            chapter(id = 1482L, number = 20.0f, scanlator = "Group 4", chapterText = "Ch.20"),
            chapter(
                id = 1483L,
                number = 19.600000381469727f,
                scanlator = "Group 2",
                chapterText = "Ch.19.6",
            ),
            chapter(id = 1484L, number = 19.0f, scanlator = "Group 8", chapterText = "Ch.19"),
            chapter(id = 1485L, number = 18.0f, scanlator = "Group 8", chapterText = "Ch.18"),
            chapter(id = 1486L, number = 17.0f, scanlator = "Group 8", chapterText = "Ch.17"),
            chapter(id = 1487L, number = 16.0f, scanlator = "Group 8", chapterText = "Ch.16"),
            chapter(id = 1488L, number = 15.0f, scanlator = "Group 8", chapterText = "Ch.15"),
            chapter(id = 1489L, number = 14.0f, scanlator = "Group 4", chapterText = "Ch.14"),
            chapter(id = 1490L, number = 14.0f, scanlator = "Group 8", chapterText = "Ch.14"),
            chapter(
                id = 1491L,
                number = 13.600000381469727f,
                scanlator = "Group 2",
                chapterText = "Ch.13.6",
            ),
            chapter(id = 1492L, number = 13.0f, scanlator = "Group 3", chapterText = "Ch.13"),
            chapter(id = 1493L, number = 12.0f, scanlator = "Group 3", chapterText = "Ch.12"),
            chapter(id = 1494L, number = 11.0f, scanlator = "Group 3", chapterText = "Ch.11"),
            chapter(id = 1495L, number = 10.0f, scanlator = "Group 3", chapterText = "Ch.10"),
            chapter(id = 1496L, number = 9.0f, scanlator = "Group 3", chapterText = "Ch.9"),
            chapter(id = 1497L, number = 8.0f, scanlator = "Group 4", chapterText = "Ch.8"),
            chapter(id = 1498L, number = 8.0f, scanlator = "Group 3", chapterText = "Ch.8"),
            chapter(
                id = 1500L,
                number = 7.599999904632568f,
                scanlator = "Group 2",
                chapterText = "Ch.7.6",
            ),
            chapter(id = 1501L, number = 7.0f, scanlator = "Group 3", chapterText = "Ch.7"),
            chapter(id = 1623L, number = 21.0f, scanlator = "Group 6", chapterText = "Ch.21"),
            chapter(id = 1624L, number = 21.0f, scanlator = "Group 6", chapterText = "Ch.21"),
            chapter(id = 1625L, number = 21.0f, scanlator = "Group 6", chapterText = "Ch.21"),
            chapter(
                id = 1626L,
                number = 20.200000762939453f,
                scanlator = "Group 6",
                chapterText = "Ch.20.2",
            ),
            chapter(
                id = 1627L,
                number = 20.200000762939453f,
                scanlator = "Group 6",
                chapterText = "Ch.20.2",
            ),
            chapter(
                id = 1628L,
                number = 20.200000762939453f,
                scanlator = "Group 6",
                chapterText = "Ch.20.2",
            ),
            chapter(
                id = 1629L,
                number = 20.100000381469727f,
                scanlator = "Group 6",
                chapterText = "Ch.20.1",
            ),
            chapter(
                id = 1630L,
                number = 20.100000381469727f,
                scanlator = "Group 6",
                chapterText = "Ch.20.1",
            ),
            chapter(
                id = 1631L,
                number = 20.100000381469727f,
                scanlator = "Group 6",
                chapterText = "Ch.20.1",
            ),
            chapter(id = 1632L, number = 20.0f, scanlator = "Group 6", chapterText = "Ch.20"),
            chapter(
                id = 1633L,
                number = 19.600000381469727f,
                scanlator = "Group 6",
                chapterText = "Ch.19.6",
            ),
            chapter(
                id = 1634L,
                number = 19.600000381469727f,
                scanlator = "Group 6",
                chapterText = "Ch.19.6",
            ),
            chapter(
                id = 1635L,
                number = 19.600000381469727f,
                scanlator = "Group 6",
                chapterText = "Ch.19.6",
            ),
            chapter(id = 1636L, number = 19.5f, scanlator = "Group 6", chapterText = "Ch.19.5"),
            chapter(id = 1637L, number = 19.5f, scanlator = "Group 6", chapterText = "Ch.19.5"),
            chapter(id = 1638L, number = 19.0f, scanlator = "Group 6", chapterText = "Ch.19"),
            chapter(id = 1639L, number = 19.0f, scanlator = "Group 6", chapterText = "Ch.19"),
            chapter(id = 1640L, number = 19.0f, scanlator = "Group 6", chapterText = "Ch.19"),
            chapter(id = 1641L, number = 18.0f, scanlator = "Group 6", chapterText = "Ch.18"),
            chapter(id = 1642L, number = 18.0f, scanlator = "Group 6", chapterText = "Ch.18"),
            chapter(id = 1643L, number = 18.0f, scanlator = "Group 6", chapterText = "Ch.18"),
            chapter(id = 1644L, number = 17.0f, scanlator = "Group 6", chapterText = "Ch.17"),
            chapter(id = 1645L, number = 17.0f, scanlator = "Group 6", chapterText = "Ch.17"),
            chapter(id = 1646L, number = 17.0f, scanlator = "Group 6", chapterText = "Ch.17"),
            chapter(id = 1647L, number = 16.0f, scanlator = "Group 6", chapterText = "Ch.16"),
            chapter(id = 1648L, number = 16.0f, scanlator = "Group 6", chapterText = "Ch.16"),
            chapter(id = 1649L, number = 16.0f, scanlator = "Group 6", chapterText = "Ch.16"),
            chapter(id = 1650L, number = 15.0f, scanlator = "Group 6", chapterText = "Ch.15"),
            chapter(id = 1651L, number = 15.0f, scanlator = "Group 6", chapterText = "Ch.15"),
            chapter(id = 1652L, number = 15.0f, scanlator = "Group 6", chapterText = "Ch.15"),
            chapter(id = 1653L, number = 14.0f, scanlator = "Group 6", chapterText = "Ch.14"),
            chapter(id = 1654L, number = 14.0f, scanlator = "Group 6", chapterText = "Ch.14"),
            chapter(id = 1655L, number = 14.0f, scanlator = "Group 6", chapterText = "Ch.14"),
            chapter(
                id = 1656L,
                number = 13.600000381469727f,
                scanlator = "Group 6",
                chapterText = "Ch.13.6",
            ),
            chapter(
                id = 1657L,
                number = 13.600000381469727f,
                scanlator = "Group 6",
                chapterText = "Ch.13.6",
            ),
            chapter(
                id = 1658L,
                number = 13.600000381469727f,
                scanlator = "Group 6",
                chapterText = "Ch.13.6",
            ),
            chapter(id = 1659L, number = 13.5f, scanlator = "Group 6", chapterText = "Ch.13.5"),
            chapter(id = 1660L, number = 13.5f, scanlator = "Group 6", chapterText = "Ch.13.5"),
            chapter(
                id = 1661L,
                number = 13.399999618530273f,
                scanlator = "Group 6",
                chapterText = "Ch.13.4",
            ),
            chapter(
                id = 1662L,
                number = 13.399999618530273f,
                scanlator = "Group 6",
                chapterText = "Ch.13.4",
            ),
            chapter(
                id = 1663L,
                number = 13.300000190734863f,
                scanlator = "Group 6",
                chapterText = "Ch.13.3",
            ),
            chapter(
                id = 1664L,
                number = 13.300000190734863f,
                scanlator = "Group 6",
                chapterText = "Ch.13.3",
            ),
            chapter(
                id = 1665L,
                number = 13.100000381469727f,
                scanlator = "Group 6",
                chapterText = "Ch.13.1",
            ),
            chapter(
                id = 1666L,
                number = 13.100000381469727f,
                scanlator = "Group 6",
                chapterText = "Ch.13.1",
            ),
            chapter(id = 1667L, number = 13.0f, scanlator = "Group 6", chapterText = "Ch.13"),
            chapter(id = 1668L, number = 13.0f, scanlator = "Group 6", chapterText = "Ch.13"),
            chapter(id = 1669L, number = 13.0f, scanlator = "Group 6", chapterText = "Ch.13"),
            chapter(id = 1670L, number = 12.0f, scanlator = "Group 6", chapterText = "Ch.12"),
            chapter(id = 1671L, number = 12.0f, scanlator = "Group 6", chapterText = "Ch.12"),
            chapter(id = 1672L, number = 12.0f, scanlator = "Group 6", chapterText = "Ch.12"),
            chapter(id = 1673L, number = 11.0f, scanlator = "Group 6", chapterText = "Ch.11"),
            chapter(id = 1674L, number = 11.0f, scanlator = "Group 6", chapterText = "Ch.11"),
            chapter(id = 1675L, number = 11.0f, scanlator = "Group 6", chapterText = "Ch.11"),
            chapter(id = 1676L, number = 10.0f, scanlator = "Group 6", chapterText = "Ch.10"),
            chapter(id = 1677L, number = 10.0f, scanlator = "Group 6", chapterText = "Ch.10"),
            chapter(id = 1678L, number = 10.0f, scanlator = "Group 6", chapterText = "Ch.10"),
            chapter(id = 1679L, number = 9.0f, scanlator = "Group 6", chapterText = "Ch.9"),
            chapter(id = 1680L, number = 9.0f, scanlator = "Group 6", chapterText = "Ch.9"),
            chapter(id = 1681L, number = 9.0f, scanlator = "Group 6", chapterText = "Ch.9"),
            chapter(id = 1682L, number = 8.0f, scanlator = "Group 6", chapterText = "Ch.8"),
            chapter(id = 1683L, number = 8.0f, scanlator = "Group 6", chapterText = "Ch.8"),
            chapter(id = 1684L, number = 8.0f, scanlator = "Group 6", chapterText = "Ch.8"),
            chapter(
                id = 1685L,
                number = 7.599999904632568f,
                scanlator = "Group 6",
                chapterText = "Ch.7.6",
            ),
            chapter(
                id = 1686L,
                number = 7.599999904632568f,
                scanlator = "Group 6",
                chapterText = "Ch.7.6",
            ),
            chapter(
                id = 1687L,
                number = 7.599999904632568f,
                scanlator = "Group 6",
                chapterText = "Ch.7.6",
            ),
            chapter(id = 1688L, number = 7.5f, scanlator = "Group 6", chapterText = "Ch.7.5"),
            chapter(id = 1689L, number = 7.5f, scanlator = "Group 6", chapterText = "Ch.7.5"),
            chapter(
                id = 1690L,
                number = 7.400000095367432f,
                scanlator = "Group 6",
                chapterText = "Ch.7.4",
            ),
            chapter(
                id = 1691L,
                number = 7.400000095367432f,
                scanlator = "Group 6",
                chapterText = "Ch.7.4",
            ),
            chapter(
                id = 1692L,
                number = 7.300000190734863f,
                scanlator = "Group 6",
                chapterText = "Ch.7.3",
            ),
            chapter(
                id = 1693L,
                number = 7.300000190734863f,
                scanlator = "Group 6",
                chapterText = "Ch.7.3",
            ),
            chapter(id = 1694L, number = 7.0f, scanlator = "Group 6", chapterText = "Ch.7"),
            chapter(id = 1695L, number = 7.0f, scanlator = "Group 6", chapterText = "Ch.7"),
            chapter(id = 1696L, number = 7.0f, scanlator = "Group 6", chapterText = "Ch.7"),
            chapter(id = 1749L, number = 21.0f, scanlator = "Group 11", chapterText = "Ch.21"),
            chapter(id = 1750L, number = 20.0f, scanlator = "Group 11", chapterText = "Ch.20"),
            chapter(id = 1751L, number = 19.0f, scanlator = "Group 11", chapterText = "Ch.19"),
            chapter(id = 1752L, number = 18.0f, scanlator = "Group 11", chapterText = "Ch.18"),
            chapter(id = 1753L, number = 17.0f, scanlator = "Group 11", chapterText = "Ch.17"),
            chapter(id = 1754L, number = 16.0f, scanlator = "Group 11", chapterText = "Ch.16"),
            chapter(id = 1755L, number = 15.0f, scanlator = "Group 11", chapterText = "Ch.15"),
            chapter(id = 1756L, number = 14.0f, scanlator = "Group 11", chapterText = "Ch.14"),
            chapter(id = 1757L, number = 13.0f, scanlator = "Group 11", chapterText = "Ch.13"),
            chapter(id = 1758L, number = 12.0f, scanlator = "Group 11", chapterText = "Ch.12"),
            chapter(id = 1759L, number = 11.0f, scanlator = "Group 11", chapterText = "Ch.11"),
            chapter(id = 1760L, number = 10.0f, scanlator = "Group 11", chapterText = "Ch.10"),
            chapter(id = 1761L, number = 9.0f, scanlator = "Group 11", chapterText = "Ch.9"),
            chapter(id = 1762L, number = 8.0f, scanlator = "Group 11", chapterText = "Ch.8"),
            chapter(id = 1763L, number = 7.5f, scanlator = "Group 11", chapterText = "Ch.7.5"),
            chapter(id = 1764L, number = 7.0f, scanlator = "Group 11", chapterText = "Ch.7"),
        )

    fun chapter(
        id: Long,
        number: Float,
        scanlator: String,
        chapterText: String,
        isUnavailable: Boolean = false,
    ): ChapterItem {
        val unavailable = isUnavailable || ExampleDbGroups.isLockedOfficial(scanlator)
        return ChapterItem(
            chapter =
                SimpleChapter(
                    id = id,
                    mangaId = 58L,
                    read = false,
                    bookmark = false,
                    lastPageRead = 0,
                    dateFetch = 0L,
                    sourceOrder = 0,
                    smartOrder = 0,
                    url = "url/$id",
                    name = chapterText,
                    dateUpload = 1000L,
                    chapterNumber = number,
                    pagesLeft = 0,
                    volume = "",
                    chapterText = chapterText,
                    chapterTitle = "",
                    language = "",
                    mangaDexChapterId = "",
                    oldMangaDexChapterId = null,
                    scanlator = scanlator,
                    uploader = "",
                    isUnavailable = unavailable,
                )
        )
    }
}
