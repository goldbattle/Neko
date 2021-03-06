package eu.kanade.tachiyomi.source.model

import eu.kanade.tachiyomi.source.online.utils.FollowStatus

class SMangaImpl : SManga {

    override lateinit var url: String

    override lateinit var title: String

    override var artist: String? = null

    override var author: String? = null

    override var description: String? = null

    override var genre: String? = null

    override var lang_flag: String? = null

    override var anilist_id: String? = null

    override var kitsu_id: String? = null

    override var my_anime_list_id: String? = null

    override var manga_updates_id: String? = null

    override var anime_planet_id: String? = null

    override var status: Int = 0

    override var follow_status: FollowStatus? = null

    override var thumbnail_url: String? = null

    override var initialized: Boolean = false

}