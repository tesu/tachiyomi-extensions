package eu.kanade.tachiyomi.extension.all.ninehentai

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import rx.Observable
import java.util.Date

@Nsfw
class NineHentai : HttpSource() {

    override val baseUrl = "https://9hentai.ru"

    override val name = "NineHentai"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val gson = Gson()

    override fun popularMangaRequest(page: Int): Request {
        return POST(baseUrl + SEARCH_URL, headers, buildRequestBody(page = page, sort = 1))
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return POST(baseUrl + SEARCH_URL, headers, buildRequestBody(page = page))
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .map { response ->
                getMangaList(response, page)
            }
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .map { response ->
                getMangaList(response, page)
            }
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                getMangaList(response, page)
            }
    }

    private fun getMangaList(response: Response, page: Int): MangasPage {
        val jsonData = response.body()!!.string()
        val jsonObject = JsonParser().parse(jsonData).asJsonObject
        val totalPages = jsonObject["total_count"].int
        val results = jsonObject["results"].array
        return MangasPage(parseSearch(results.toList()), page < totalPages)
    }

    private fun parseSearch(jsonArray: List<JsonElement>): List<SManga> {
        val mutableList = mutableListOf<SManga>()
        jsonArray.forEach { json ->
            val manga = SManga.create()
            val gsonManga = gson.fromJson<Manga>(json)
            manga.url = "/g/${gsonManga.id}"
            manga.title = gsonManga.title
            manga.thumbnail_url = gsonManga.image_server + gsonManga.id + "/cover.jpg"
            manga.genre = gsonManga.tags.filter { it.type == 1 }.joinToString { it.name }
            manga.artist = gsonManga.tags.firstOrNull { it.type == 4 }?.name
            manga.initialized = true
            mutableList.add(manga)
        }
        return mutableList
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapter = SChapter.create()
        val chapterId = manga.url.substringAfter("/g/").toInt()
        chapter.url = "/g/$chapterId"
        chapter.name = "chapter"
        // api doesnt return date so setting to current date for now
        chapter.date_upload = Date().time

        return Observable.just(listOf(chapter))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val includedTags = mutableListOf<Tag>()
        val excludedTags = mutableListOf<Tag>()
        var sort = 0
        for (filter in if (filters.isEmpty()) getFilterList() else filters) {
            when (filter) {
                is GenreList -> {
                    filter.state.forEach { f ->
                        if (!f.isIgnored()) {
                            if (f.isExcluded())
                                excludedTags.add(f)
                            else
                                includedTags.add(f)
                        }
                    }
                }
                is Sorting -> {
                    sort = filter.state!!.index
                }
            }
        }
        return POST(baseUrl + SEARCH_URL, headers, buildRequestBody(query, page, sort, includedTags, excludedTags))
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return SManga.create().apply {
            response.asJsoup().select("div.card-body").firstOrNull()?.let { info ->
                title = info.select("h1").text()
                genre = info.select("div.field-name:contains(Tag:) a.tag").joinToString { it.text() }
                artist = info.select("div.field-name:contains(Artist:) a.tag").joinToString { it.text() }
                thumbnail_url = info.select("div#cover v-lazy-image").attr("abs:src")
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val mangaId = chapter.url.substringAfter("/g/").toInt()
        return POST(baseUrl + MANGA_URL, headers, buildIdBody(mangaId))
    }

    override fun pageListParse(response: Response): List<Page> {
        val jsonData = response.body()!!.string()
        val jsonObject = JsonParser().parse(jsonData).asJsonObject
        val jsonArray = jsonObject.getAsJsonObject("results")
        var imageUrl: String
        var totalPages: Int
        var mangaId: String
        jsonArray.let { json ->
            mangaId = json["id"].string
            imageUrl = json["image_server"].string + mangaId
            totalPages = json["total_page"].int
        }
        val pages = mutableListOf<Page>()

        for (i in 1..totalPages) {
            pages.add(Page(pages.size, "", "$imageUrl/$i.jpg"))
        }

        return pages
    }

    private fun buildRequestBody(searchText: String = "", page: Int, sort: Int = 0, includedTags: MutableList<Tag> = arrayListOf(), excludedTags: MutableList<Tag> = arrayListOf()): RequestBody {
        val json = gson.toJson(mapOf("search" to SearchRequest(text = searchText, page = page - 1, sort = sort, tag = mapOf("items" to Items(includedTags, excludedTags)))))
        return RequestBody.create(MEDIA_TYPE, json)
    }

    private fun buildIdBody(id: Int): RequestBody {
        return RequestBody.create(MEDIA_TYPE, gson.toJson(mapOf("id" to id)))
    }

    private class GenreList(tags: List<Tag>) : Filter.Group<Tag>("Tags", tags)

    private class Sorting : Filter.Sort(
        "Sorting",
        arrayOf("Newest", "Popular Right now", "Most Fapped", "Most Viewed", "By Title"),
        Selection(1, false)
    )

    override fun getFilterList() = FilterList(
        Sorting(),
        GenreList(NHTags.getTagsList())
    )

    override fun imageUrlParse(response: Response): String = throw Exception("Not Used")

    override fun chapterListRequest(manga: SManga): Request = throw Exception("Not Used")

    override fun popularMangaParse(response: Response): MangasPage = throw Exception("Not Used")

    override fun latestUpdatesParse(response: Response): MangasPage = throw Exception("Not Used")

    override fun searchMangaParse(response: Response): MangasPage = throw Exception("Not Used")

    override fun chapterListParse(response: Response): List<SChapter> = throw Exception("Not Used")

    companion object {
        private val MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8")
        private const val SEARCH_URL = "/api/getBook"
        private const val MANGA_URL = "/api/getBookByID"
    }
}
