package eu.kanade.tachiyomi.extension.vi.blogtruyen

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class BlogTruyen : ParsedHttpSource() {

    override val name = "BlogTruyen"

    override val baseUrl = "https://blogtruyen.vn"

    override val lang = "vi"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", baseUrl)

    override fun popularMangaSelector() = "div.list span.tiptip.fs-12.ellipsis"

    override fun latestUpdatesSelector() = "section.list-mainpage.listview > div > div > div > div.fl-l"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/ajax/Search/AjaxLoadListManga?key=tatca&orderBy=3&p=$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/page-$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val imgURL = document.select("img").map { it.attr("abs:src") }
        val mangas = document.select(popularMangaSelector()).mapIndexed { index, element -> popularMangaFromElement(element, imgURL[index]) }

        val hasNextPage = popularMangaNextPageSelector().let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaFromElement(element: Element, imgURL: String): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text().trim()
            manga.thumbnail_url = imgURL
        }
        return manga
    }

    override fun popularMangaFromElement(element: Element): SManga = throw Exception("Not Used")

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = element.select("img").first().attr("alt").toString().trim()
            manga.thumbnail_url = element.select("img").first().attr("abs:src")
        }
        return manga
    }

    override fun popularMangaNextPageSelector() = "div.paging:last-child:not(.current_page)"

    override fun latestUpdatesNextPageSelector() = "ul.pagination.paging.list-unstyled > li:nth-last-child(2) > a"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var temp = "$baseUrl/timkiem/nangcao/1/0"
        val genres = mutableListOf<Int>()
        val genresEx = mutableListOf<Int>()
        var aut = ""
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is GenreList -> filter.state.forEach {
                    genre ->
                    when (genre.state) {
                        Filter.TriState.STATE_INCLUDE -> genres.add(genre.id)
                        Filter.TriState.STATE_EXCLUDE -> genresEx.add(genre.id)
                    }
                }
                is Author -> {
                    if (filter.state.isNotEmpty()) {
                        aut = filter.state
                    }
                }
            }
        }
        temp = if (genres.isNotEmpty()) temp + "/" + genres.joinToString(",")
        else "$temp/-1"
        temp = if (genresEx.isNotEmpty()) temp + "/" + genresEx.joinToString(",")
        else "$temp/-1"
        val url = HttpUrl.parse(temp)!!.newBuilder()
        url.addQueryParameter("txt", query)
        if (aut.isNotEmpty()) url.addQueryParameter("aut", aut)
        url.addQueryParameter("p", page.toString())
        return GET(url.toString().replace("m.", ""), headers)
    }

    override fun searchMangaSelector() = "div.list > p:has(a)"

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("a").let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.text()
            }
            thumbnail_url = element.nextElementSibling().select("img").attr("abs:src")
        }
    }

    override fun searchMangaNextPageSelector() = "ul.pagination i.glyphicon.glyphicon-step-forward.red"

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.description").first()

        val manga = SManga.create()
        manga.author = infoElement.select("p:contains(Tác giả) > a").first()?.text()
        manga.genre = infoElement.select("span.category a").joinToString { it.text() }
        manga.description = document.select("div.detail > div.content").text()
        manga.status = infoElement.select("p:contains(Trạng thái) > span.color-red").first()?.text().orEmpty().let { parseStatus(it) }
        manga.thumbnail_url = document.select("div.thumbnail > img").first()?.attr("src")
        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("Đang tiến hành") -> SManga.ONGOING
        status.contains("Đã hoàn thành") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.list-wrap > p"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("span > a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.attr("title").trim()
        chapter.date_upload = element.select("span.publishedDate").first()?.text()?.let {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH).parse(it)?.time ?: 0
        } ?: 0
        return chapter
    }

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val pageUrl = document.select("link[rel=canonical]").attr("href")
        document.select("article#content > img").forEachIndexed { i, e ->
            pages.add(Page(i, pageUrl, e.attr("src")))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    private class Status : Filter.Select<String>("Status", arrayOf("Sao cũng được", "Đang tiến hành", "Đã hoàn thành", "Tạm ngưng"))
    private class Author : Filter.Text("Tác giả")
    private class Genre(name: String, val id: Int) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

    override fun getFilterList() = FilterList(
        Status(),
        GenreList(getGenreList()),
        Author()
    )

    private fun getGenreList() = listOf(
        Genre("16+", 54),
        Genre("18+", 45),
        Genre("Action", 1),
        Genre("Adult", 2),
        Genre("Adventure", 3),
        Genre("Anime", 4),
        Genre("Comedy", 5),
        Genre("Comic", 6),
        Genre("Doujinshi", 7),
        Genre("Drama", 49),
        Genre("Ecchi", 48),
        Genre("Even BT", 60),
        Genre("Fantasy", 50),
        Genre("Game", 61),
        Genre("Gender Bender", 51),
        Genre("Harem", 12),
        Genre("Historical", 13),
        Genre("Horror", 14),
        Genre("Isekai/Dị Giới", 63),
        Genre("Josei", 15),
        Genre("Live Action", 16),
        Genre("Magic", 46),
        Genre("Manga", 55),
        Genre("Manhua", 17),
        Genre("Manhwa", 18),
        Genre("Martial Arts", 19),
        Genre("Mature", 20),
        Genre("Mecha", 21),
        Genre("Mystery", 22),
        Genre("Nấu ăn", 56),
        Genre("NTR", 62),
        Genre("One shot", 23),
        Genre("Psychological", 24),
        Genre("Romance", 25),
        Genre("School Life", 26),
        Genre("Sci-fi", 27),
        Genre("Seinen", 28),
        Genre("Shoujo", 29),
        Genre("Shoujo Ai", 30),
        Genre("Shounen", 31),
        Genre("Shounen Ai", 32),
        Genre("Slice of Life", 33),
        Genre("Smut", 34),
        Genre("Soft Yaoi", 35),
        Genre("Soft Yuri", 36),
        Genre("Sports", 37),
        Genre("Supernatural", 38),
        Genre("Tạp chí truyện tranh", 39),
        Genre("Tragedy", 40),
        Genre("Trap", 58),
        Genre("Trinh thám", 57),
        Genre("Truyện scan", 41),
        Genre("Video clip", 53),
        Genre("VnComic", 42),
        Genre("Webtoon", 52),
        Genre("Yuri", 59)
    )
}
