package eu.kanade.tachiyomi.ui.recently_read

import android.os.Bundle
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.injectLazy
import java.util.*

/**
 * Presenter of RecentlyReadFragment.
 * Contains information and data for fragment.
 * Observable updates should be called from here.
 */
class RecentlyReadPresenter : BasePresenter<RecentlyReadController>() {

    /**
     * Used to connect to database
     */
    val db: DatabaseHelper by injectLazy()
    var lastCount = 25

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        //pageSubscription?.let { remove(it) }
        // Used to get a list of recently read manga
        updateList()
    }

    fun requestNext(offset: Int) {
        lastCount = offset
        getRecentMangaObservable((offset))
                .subscribeLatestCache({ view, mangas ->
                    view.onNextManga(mangas)
                }, RecentlyReadController::onAddPageError)
    }

    /**
     * Get recent manga observable
     * @return list of history
     */
    private fun getRecentMangaObservable(offset: Int = 0): Observable<List<RecentlyReadItem>> {
        // Set date for recent manga
        val cal = Calendar.getInstance()
        cal.time = Date()
        cal.add(Calendar.YEAR, -50)

        return db.getRecentManga(cal.time, offset).asRxObservable()
                .map { recents -> recents.map(::RecentlyReadItem) }
                .observeOn(AndroidSchedulers.mainThread())
    }

    /**
     * Get recent manga observable
     * @return list of history
     */
    private fun getRecentMangaLimitObservable(offset: Int = 0): Observable<List<RecentlyReadItem>> {
        // Set date for recent manga
        val cal = Calendar.getInstance()
        cal.time = Date()
        cal.add(Calendar.YEAR, -50)

        return db.getRecentMangaLimit(cal.time, offset).asRxObservable()
                .map { recents -> recents.map(::RecentlyReadItem) }
                .observeOn(AndroidSchedulers.mainThread())
    }

    /**
     * Reset last read of chapter to 0L
     * @param history history belonging to chapter
     */
    fun removeFromHistory(history: History) {
        history.last_read = 0L
        db.updateHistoryLastRead(history).executeAsBlocking()
        updateList()
    }


    fun updateList() {
        getRecentMangaLimitObservable(lastCount).take(1)
                .subscribeLatestCache({ view, mangas ->
                    view.onNextManga(mangas, true)
                }, RecentlyReadController::onAddPageError)
    }

    /**
     * Removes all chapters belonging to manga from history.
     * @param mangaId id of manga
     */
    fun removeAllFromHistory(mangaId: Long) {
        val history = db.getHistoryByMangaId(mangaId).executeAsBlocking()
        history.forEach { it.last_read = 0L }
        db.updateHistoryLastRead(history).executeAsBlocking()
        updateList()
    }

    /**
     * Retrieves the next chapter of the given one.
     *
     * @param chapter the chapter of the history object.
     * @param manga the manga of the chapter.
     */
    fun getNextChapter(chapter: Chapter, manga: Manga): Chapter? {
        if (!chapter.read) {
            return chapter
        }

        val sortFunction: (Chapter, Chapter) -> Int = when (manga.sorting) {
            Manga.SORTING_SOURCE -> { c1, c2 -> c2.source_order.compareTo(c1.source_order) }
            Manga.SORTING_NUMBER -> { c1, c2 -> c1.chapter_number.compareTo(c2.chapter_number) }
            else -> throw NotImplementedError("Unknown sorting method")
        }

        val chapters = db.getChapters(manga).executeAsBlocking()
                .sortedWith(Comparator<Chapter> { c1, c2 -> sortFunction(c1, c2) })

        val currChapterIndex = chapters.indexOfFirst { chapter.id == it.id }
        return when (manga.sorting) {
            Manga.SORTING_SOURCE -> chapters.getOrNull(currChapterIndex + 1)
            Manga.SORTING_NUMBER -> {
                val chapterNumber = chapter.chapter_number

                ((currChapterIndex + 1) until chapters.size)
                        .map { chapters[it] }
                        .firstOrNull {
                            it.chapter_number > chapterNumber &&
                                    it.chapter_number <= chapterNumber + 1
                        }
            }
            else -> throw NotImplementedError("Unknown sorting method")
        }
    }

}
