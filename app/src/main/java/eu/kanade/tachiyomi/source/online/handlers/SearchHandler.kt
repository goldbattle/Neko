package eu.kanade.tachiyomi.source.online.handlers

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.online.handlers.serializers.MangaListResponse
import eu.kanade.tachiyomi.source.online.utils.MdUtil
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy

class SearchHandler(val client: OkHttpClient, private val headers: Headers, val langs: List<String>, val filterHandler: FilterHandler) {

    private val preferences: PreferencesHelper by injectLazy()

    fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {

        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val realQuery = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(searchMangaByIdRequest(realQuery))
                .asObservableSuccess()
                .map { response ->
                    val details = ApiMangaParser(client, filterHandler).mangaDetailsParse(response, emptyList())
                    details.url = "/manga/$realQuery/"
                    MangasPage(listOf(details), false)
                }
        } else {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response)
                }
        }
    }

    private fun searchMangaParse(response: Response): MangasPage {
        if (response.isSuccessful.not()) {
            throw Exception("Error getting search manga http code: ${response.code}")
        }

        val mlResponse = MdUtil.jsonParser.decodeFromString(MangaListResponse.serializer(), response.body!!.string())
        val hasMoreResults = mlResponse.limit + mlResponse.offset < mlResponse.total
        val mangaList = mlResponse.results.map { MdUtil.createMangaEntry(it, preferences) }
        return MangasPage(mangaList, hasMoreResults)
    }

    private fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {

        val tempUrl = MdUtil.mangaUrl.toHttpUrlOrNull()!!.newBuilder()

        tempUrl.apply {
            addQueryParameter("limit", MdUtil.mangaLimit.toString())
            addQueryParameter("offset", (MdUtil.getMangaListOffset(page)))
            val actualQuery = query.replace(WHITESPACE_REGEX, " ")
            if (actualQuery.isNotBlank()) {
                addQueryParameter("title", actualQuery)
            }
        }

        val finalUrl = filterHandler.addFiltersToUrl(tempUrl, filters)

        return GET(finalUrl, headers, CacheControl.FORCE_NETWORK)
    }

    private fun searchMangaByIdRequest(id: String): Request {
        return GET(MdUtil.mangaUrl + "/" + id, headers, CacheControl.FORCE_NETWORK)
    }

    private fun searchMangaByGroupRequest(group: String): Request {
        return GET(MdUtil.groupSearchUrl + group, headers, CacheControl.FORCE_NETWORK)
    }

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
        const val PREFIX_GROUP_SEARCH = "group:"
        val WHITESPACE_REGEX = "\\s".toRegex()
    }
}
