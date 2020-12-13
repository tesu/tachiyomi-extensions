package eu.kanade.tachiyomi.extension.en.honkaiimpact

import com.github.salomonbrys.kotson.float
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Honkaiimpact : ParsedHttpSource() {

    // Info - Based of BH3
    // This is the english version of the site
    override val name = "Honkai Impact 3rd"
    override val baseUrl = "https://manga.honkaiimpact3.com"
    override val lang = "en"
    override val supportsLatest = false
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()!!

    // Popular
    override fun popularMangaSelector() = "a[href*=book]"

    override fun popularMangaNextPageSelector(): String? = null
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/book", headers)
    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)

    // Latest
    override fun latestUpdatesSelector() = throw Exception("Not Used")

    override fun latestUpdatesNextPageSelector(): String? = null
    override fun latestUpdatesRequest(page: Int) = throw Exception("Not Used")
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)

    // Search
    override fun searchMangaSelector() = throw Exception("Not Used")

    override fun searchMangaNextPageSelector(): String? = null
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw Exception("No search")
    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)

    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select("a").attr("abs:href"))
        manga.title = element.select("div.container-title").text().trim()
        manga.thumbnail_url = element.select("img").attr("abs:src")
        return manga
    }

    // Manga Details
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = document.select("img.cover").attr("abs:src")
        manga.description = document.select("div.detail_info1").text().trim()
        manga.title = document.select("div.title").text().trim()
        return manga
    }

    // Chapters
    override fun chapterListSelector() = throw Exception("Not Used")

    override fun chapterFromElement(element: Element) = throw Exception("Not Used")
    override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url + "/get_chapter", headers)
    override fun chapterListParse(response: Response): List<SChapter> {
        val jsondata = response.body()!!.string()
        val json = JsonParser().parse(jsondata).asJsonArray
        val chapters = mutableListOf<SChapter>()
        json.forEach {
            chapters.add(createChapter(it))
        }
        return chapters
    }

    private fun createChapter(json: JsonElement) = SChapter.create().apply {
        name = json["title"].string
        url = "/book/${json["bookid"].int}/${json["chapterid"].int}"
        date_upload = parseDate(json["timestamp"].string)
        chapter_number = json["chapterid"].float
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(date)?.time ?: 0
    }

    // Manga Pages
    override fun pageListParse(response: Response): List<Page> = mutableListOf<Page>().apply {
        val body = response.asJsoup()
        body.select("img.lazy.comic_img")?.forEach {
            add(Page(size, "", it.attr("data-original")))
        }
    }

    override fun pageListParse(document: Document) = throw Exception("Not Used")
    override fun imageUrlParse(document: Document) = throw Exception("Not Used")
}
