package eu.kanade.tachiyomi.extension.en.manmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat

class ManManga : ParsedHttpSource() {
    override val name = "Man Manga"

    override val baseUrl = "https://m.manmanga.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("MMM dd, yyyy")
        }
    }

    override fun popularMangaSelector() = "#scrollBox > #scrollContent > li > a"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/category?sort=hot&page=$page", headers)

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/category?sort=new&page=$page", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$baseUrl/search?keyword=$query&page=$page", headers)

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.select("div.text > h4").text().trim()
    }

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.select("div.text > div.name > h4").text().trim()
    }

    override fun popularMangaNextPageSelector() = "script:containsData(next_page_url):not(:containsData(false))"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }

        val hasNextPage = searchMangaNextPageSelector().let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response)
            }
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val getThumbnailUrl = document.select(".bg-box .bg").attr("style")

        author = document.select(".author").text().replace("Author：", "").trim()
        genre = document.select(".tags span").joinToString(", ") {
            it.text().trim()
        }
        status = document.select(".type").text().replace("Status：", "").trim().let {
            parseStatus(it)
        }
        description = document.select(".inner-text").text().trim()
        thumbnail_url = getThumbnailUrl.substring(getThumbnailUrl.indexOf("https://"), getThumbnailUrl.indexOf("')"))
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun chapterListSelector() = "dl.chapter-list > dd > ul > li > a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.attr("alt").trim()
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        if (document.select("ul.img-list > li.unloaded > img").toString().isNotEmpty()) {
            document.select("ul.img-list > li.unloaded > img").forEach {
                val imgUrl = it.attr("data-src")
                pages.add(Page(pages.size, "", imgUrl))
            }
        } else {
            document.select("ul.img-list > li.loaded > img").forEach {
                val imgUrl = it.attr("data-src")
                pages.add(Page(pages.size, "", imgUrl))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}
