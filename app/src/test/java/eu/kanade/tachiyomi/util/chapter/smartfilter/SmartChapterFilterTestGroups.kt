package eu.kanade.tachiyomi.util.chapter.smartfilter

import eu.kanade.tachiyomi.source.online.merged.weebcentral.WeebCentral
import org.nekomanga.constants.Constants
import org.nekomanga.constants.MdConstants

/**
 * Generic scanlator labels for smart-filter tests. Merge-related strings must stay recognizable by
 * [eu.kanade.tachiyomi.data.database.models.MergeType.containsMergeSourceName].
 */
object SmartChapterFilterTestGroups {
    const val FAN_A = "Fan Group A"
    const val FAN_B = "Fan Group B"
    const val FAN_C = "Fan Group C"

    const val SCAN_A = "Scan Group A"
    const val SCAN_B = "Scan Group B"
    const val SCAN_C = "Scan Group C"
    const val SCAN_D = "Scan Group D"
    const val SCAN_E = "Scan Group E"
    const val SPLIT_GROUP = "Split Group"

    const val NO_UPLOADER = Constants.NO_GROUP
    const val OTHER_UPLOADER = "Other Uploader"

    /** Primary merge source row (Weeb Central, Komga, etc.). */
    val MERGE: String = WeebCentral.name

    /** Aggregator-style merge label (Manga Ball pattern). */
    const val MERGE_AGGREGATOR = "Manga Ball & Mirror & (host)"

    val LOCKED_OFFICIAL: String = MdConstants.UnsupportedOfficialGroupList.first()

    const val BLOCKED = "Blocked Group"
    const val ALLOWED = "Allowed Group"
}
