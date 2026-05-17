package eu.kanade.tachiyomi.util.chapter

import org.nekomanga.constants.Constants
import org.nekomanga.constants.MdConstants

/**
 * Generic scanlator labels for [ExampleDbChapterData] and [SmartChapterEngineExampleDbFixtureTest].
 * Mapping from the exported DB snapshot (stable per group number).
 */
object ExampleDbGroups {
    const val GROUP_1 = "Group 1"
    const val GROUP_2 = "Group 2"
    const val GROUP_3 = "Group 3"
    const val GROUP_4 = "Group 4"
    const val GROUP_5 = "Group 5"
    const val GROUP_6 = "Group 6"
    const val GROUP_7 = "Group 7"
    const val GROUP_8 = "Group 8"
    const val GROUP_9 = "Group 9"
    const val GROUP_10 = "Group 10"
    const val GROUP_11 = "Group 11"
    const val GROUP_12 = "Group 12"

    /** Former J-Novel Club rows — excluded from ranking when [isLockedOfficial]. */
    fun isLockedOfficial(scanlator: String): Boolean = scanlator == GROUP_4

    const val NO_UPLOADER = Constants.NO_GROUP
    const val BLOCKED = "Blocked Group"
    const val MANGA_DEX = MdConstants.name
}
