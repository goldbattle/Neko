package eu.kanade.tachiyomi.ui.library

import android.content.Context
import android.util.AttributeSet
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.widget.ExtendedNavigationView
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.MultiSort.Companion.SORT_ASC
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.MultiSort.Companion.SORT_DESC
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.MultiSort.Companion.SORT_NONE
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.TriStateGroup.Companion.STATE_EXCLUDE
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.TriStateGroup.Companion.STATE_IGNORE
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.TriStateGroup.Companion.STATE_INCLUDE
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

/**
 * The navigation view shown in a drawer with the different options to show the library.
 */
class LibraryNavigationView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null)
    : ExtendedNavigationView(context, attrs) {

    /**
     * Preferences helper.
     */
    private val preferences: PreferencesHelper by injectLazy()

    private val loggedServices by lazy { Injekt.get<TrackManager>().services.filter { it.isLogged && !it.isMdList() } }


    /**
     * List of groups shown in the view.
     */
    private val groups = listOf(FilterGroup(), SortGroup())

    /**
     * Adapter instance.
     */
    private val adapter = Adapter(groups.map { it.createItems() }.flatten())

    /**
     * Click listener to notify the parent fragment when an item from a group is clicked.
     */
    var onGroupClicked: (Group) -> Unit = {}

    init {
        recycler.adapter = adapter
        addView(recycler)

        groups.forEach { it.initModels() }
    }

    /**
     * Returns true if there's at least one filter from [FilterGroup] active.
     */
    fun hasActiveFilters(): Boolean {
        return (groups[0] as FilterGroup).items.any {
            when (it) {
                is Item.TriStateGroup ->
                    if (it.resTitle == R.string.categories) it.state == STATE_IGNORE
                    else it.state != STATE_IGNORE
                is Item.CheckboxGroup -> it.checked
                else -> false
            }
        }
    }

    /**
     * Adapter of the recycler view.
     */
    inner class Adapter(items: List<Item>) : ExtendedNavigationView.Adapter(items) {

        override fun onItemClicked(item: Item) {
            if (item is GroupedItem) {
                item.group.onItemClicked(item)
                onGroupClicked(item.group)
            }
        }
    }

    /**
     * Filters group (unread, downloaded, ...).
     */
    inner class FilterGroup : Group {

        private val downloaded = Item.TriStateGroup(R.string.action_filter_downloaded, this)

        private val unread = Item.TriStateGroup(R.string.action_filter_unread, this)

        private val completed = Item.TriStateGroup(R.string.completed, this)

        private val anilist = Item.TriStateGroup(R.string.action_filter_anilist, this)

        private val myanimelist = Item.TriStateGroup(R.string.action_filter_myanimelist, this)

        private val kitsu = Item.TriStateGroup(R.string.action_filter_kitsu, this)


        override val items: List<Item> = {
            val list = mutableListOf<Item>()
            list.add(downloaded)
            list.add(unread)
            list.add(completed)
            loggedServices.forEach {
                when (it.id) {
                    TrackManager.ANILIST -> list.add(anilist)
                    TrackManager.KITSU -> list.add(kitsu)
                    TrackManager.MYANIMELIST -> list.add(myanimelist)
                }
            }
            list
        }()

        override val header = Item.Header(R.string.action_filter)

        override val footer = null

        override fun initModels() {
            try {
                downloaded.state = preferences.filterDownloaded().getOrDefault()
                unread.state = preferences.filterUnread().getOrDefault()
                completed.state = preferences.filterCompleted().getOrDefault()
                anilist.state = preferences.filterAnilist().getOrDefault()
                myanimelist.state = preferences.filterMyAnimeList().getOrDefault()
                kitsu.state = preferences.filterKitsu().getOrDefault()
            } catch (e: Exception) {
                preferences.upgradeFilters()
            }
        }

        override fun onItemClicked(item: Item) {
            item as Item.TriStateGroup
            val newState = when (item.state) {
                STATE_IGNORE -> STATE_INCLUDE
                STATE_INCLUDE -> STATE_EXCLUDE
                else -> STATE_IGNORE
            }
            item.state = newState
            when (item) {
                downloaded -> preferences.filterDownloaded().set(item.state)
                unread -> preferences.filterUnread().set(item.state)
                completed -> preferences.filterCompleted().set(item.state)
                anilist -> preferences.filterAnilist().set(item.state)
                kitsu -> preferences.filterKitsu().set(item.state)
                myanimelist -> preferences.filterMyAnimeList().set(item.state)
            }

            adapter.notifyItemChanged(item)
        }
    }

    /**
     * Sorting group (alphabetically, by last read, ...) and ascending or descending.
     */
    inner class SortGroup : Group {

        private val alphabetically = Item.MultiSort(R.string.action_sort_alpha, this)

        private val total = Item.MultiSort(R.string.action_sort_total, this)

        private val lastRead = Item.MultiSort(R.string.action_sort_last_read, this)

        private val lastUpdated = Item.MultiSort(R.string.action_sort_last_updated, this)

        private val unread = Item.MultiSort(R.string.action_filter_unread, this)

        private val dateAdded = Item.MultiSort(R.string.manga_info_date_added_label, this)

        override val items = listOf(alphabetically, lastRead, lastUpdated, unread, total, dateAdded)

        override val header = Item.Header(R.string.action_sort)

        override val footer = null

        override fun initModels() {
            val sorting = preferences.librarySortingMode().getOrDefault()
            val order = if (preferences.librarySortingAscending().getOrDefault())
                SORT_ASC else SORT_DESC

            alphabetically.state = if (sorting == LibrarySort.ALPHA) order else SORT_NONE
            lastRead.state = if (sorting == LibrarySort.LAST_READ) order else SORT_NONE
            lastUpdated.state = if (sorting == LibrarySort.LAST_UPDATED) order else SORT_NONE
            unread.state = if (sorting == LibrarySort.UNREAD) order else SORT_NONE
            total.state = if (sorting == LibrarySort.TOTAL) order else SORT_NONE
            dateAdded.state = if (sorting == LibrarySort.DATE_ADDED) order else SORT_NONE
        }

        override fun onItemClicked(item: Item) {
            item as Item.MultiStateGroup
            val prevState = item.state

            item.group.items.forEach { (it as Item.MultiStateGroup).state = SORT_NONE }
            item.state = when (prevState) {
                SORT_NONE -> SORT_ASC
                SORT_ASC -> SORT_DESC
                SORT_DESC -> SORT_ASC
                else -> throw Exception("Unknown state")
            }

            preferences.librarySortingMode().set(when (item) {
                alphabetically -> LibrarySort.ALPHA
                lastRead -> LibrarySort.LAST_READ
                lastUpdated -> LibrarySort.LAST_UPDATED
                unread -> LibrarySort.UNREAD
                total -> LibrarySort.TOTAL
                dateAdded -> LibrarySort.DATE_ADDED
                else -> throw Exception("Unknown sorting")
            })
            preferences.librarySortingAscending().set(item.state == SORT_ASC)

            item.group.items.forEach { adapter.notifyItemChanged(it) }
        }

    }
}