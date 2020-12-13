package eu.kanade.tachiyomi.extension.en.comicpunch

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
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class Comicpunch : ParsedHttpSource() {

    override val name = "Comicpunch"

    override val baseUrl = "https://comicpunch.net"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/catalogue?page=${page - 1}", headers)
    }

    override fun popularMangaSelector() = "div#content span.field-content a"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.text()

        return manga
    }

    override fun popularMangaNextPageSelector() = "li.pager-next"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest-issues", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return MangasPage(super.latestUpdatesParse(response).mangas.distinctBy { it.url }, false)
    }

    override fun latestUpdatesSelector() = "div#Comics li.mtitle a:last-child"

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response, query)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/comics-list", headers)
    }

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val mangas = response.asJsoup().select(searchMangaSelector())
            .filter { it.text().contains(query, ignoreCase = true) }
            .map { searchMangaFromElement(it) }

        return MangasPage(mangas, false)
    }

    override fun searchMangaSelector() = "table.cols-2 td a"

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        // Website uses at least 2 different formats plus other inconsistencies, alter at your own peril
        document.select("div.content.node-comic, div.content.node-comic-ii").let { details ->
            manga.author = details.select("div.field-label:contains(publisher:) + div a").text()
            manga.genre = details.select("div.field-label:contains(genres:) + div a").joinToString { it.text() }
            manga.description = details.select("div.field-type-text-with-summary:not(:has(ul.splash))").text().let { desc ->
                if (desc.isNotEmpty()) desc else document.select("ul.splash li.summary").first()?.ownText()
            }
            manga.thumbnail_url = details.select("img").attr("abs:src") ?: document.select("li.pic img").attr("abs:src")
        }

        return manga
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var elements = response.asJsoup().select(chapterListSelector()).toList()

        // Check if latest chapter is just a placeholder, drop it if it is
        client.newCall(GET(elements[0].attr("abs:href"), headers)).execute().asJsoup().select("img").last().attr("src").let { img ->
            if (img.contains("placeholder", ignoreCase = true)) elements = elements.drop(1)
        }
        elements.map { chapters.add(chapterFromElement(it)) }

        return chapters
    }

    override fun chapterListSelector() = "li.chapter a"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        chapter.setUrlWithoutDomain(element.attr("href"))
        chapter.name = element.text()

        return chapter
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        val pageUrls = document.select("div#code_contain > script")
            .eq(1).first().data()
            .substringAfter("= [").substringBefore("]").split(",")
        pageUrls.forEachIndexed { i, img ->
            pages.add(Page(i, "", img.removeSurrounding("\"")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}
