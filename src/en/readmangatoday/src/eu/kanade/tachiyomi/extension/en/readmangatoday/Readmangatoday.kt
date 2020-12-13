package eu.kanade.tachiyomi.extension.en.readmangatoday

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

class Readmangatoday : ParsedHttpSource() {

    override val id: Long = 8

    override val name = "ReadMangaToday"

    override val baseUrl = "https://www.readmng.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient get() = network.cloudflareClient

    /**
     * Search only returns data with user-agent and x-requeted-with set
     * Referer needed due to some chapters linking images from other domains
     */
    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")
        add("X-Requested-With", "XMLHttpRequest")
        add("Referer", baseUrl)
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/hot-manga/$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest-releases/$page", headers)
    }

    override fun popularMangaSelector() = "div.hot-manga > div.style-list > div.box"

    override fun latestUpdatesSelector() = "div.hot-manga > div.style-grid > div.box"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("div.title > h2 > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        manga.thumbnail_url = element.select("img").attr("src")
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun popularMangaNextPageSelector() = "div.hot-manga > ul.pagination > li > a:contains(»)"

    override fun latestUpdatesNextPageSelector() = "div.hot-manga > ul.pagination > li > a:contains(»)"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val builder = okhttp3.FormBody.Builder()
        builder.add("manga-name", query)
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is TextField -> builder.add(filter.key, filter.state)
                is Type -> builder.add("type", arrayOf("all", "japanese", "korean", "chinese")[filter.state])
                is Status -> builder.add("status", arrayOf("both", "completed", "ongoing")[filter.state])
                is GenreList -> filter.state.forEach { genre ->
                    when (genre.state) {
                        Filter.TriState.STATE_INCLUDE -> builder.add("include[]", genre.id.toString())
                        Filter.TriState.STATE_EXCLUDE -> builder.add("exclude[]", genre.id.toString())
                    }
                }
            }
        }
        return POST("$baseUrl/service/advanced_search", headers, builder.build())
    }

    override fun searchMangaSelector() = "div.style-list > div.box"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("div.title > h2 > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        return manga
    }

    override fun searchMangaNextPageSelector() = "div.next-page > a.next"

    override fun mangaDetailsParse(document: Document): SManga {
        val detailElement = document.select("div.movie-meta").first()
        val genreElement = detailElement.select("dl.dl-horizontal > dd:eq(5) a")

        val manga = SManga.create()
        manga.author = document.select("ul.cast-list li.director > ul a").first()?.text()
        manga.artist = document.select("ul.cast-list li:not(.director) > ul a").first()?.text()
        manga.description = detailElement.select("li.movie-detail").first()?.text()
        manga.status = detailElement.select("dl.dl-horizontal > dd:eq(3)").first()?.text().orEmpty().let { parseStatus(it) }
        manga.thumbnail_url = detailElement.select("img.img-responsive").first()?.attr("src")

        val genres = mutableListOf<String>()
        genreElement?.forEach { genres.add(it.text()) }
        manga.genre = genres.joinToString(", ")

        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "ul.chp_lst > li"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.select("span.val").text()
        chapter.date_upload = element.select("span.dte").first()?.text()?.let { parseChapterDate(it) } ?: 0
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val dateWords: List<String> = date.split(" ")

        if (dateWords.size == 3) {
            val timeAgo = Integer.parseInt(dateWords[0])
            val calendar = Calendar.getInstance()

            when {
                dateWords[1].contains("Minute") -> {
                    calendar.add(Calendar.MINUTE, -timeAgo)
                }
                dateWords[1].contains("Hour") -> {
                    calendar.add(Calendar.HOUR_OF_DAY, -timeAgo)
                }
                dateWords[1].contains("Day") -> {
                    calendar.add(Calendar.DAY_OF_YEAR, -timeAgo)
                }
                dateWords[1].contains("Week") -> {
                    calendar.add(Calendar.WEEK_OF_YEAR, -timeAgo)
                }
                dateWords[1].contains("Month") -> {
                    calendar.add(Calendar.MONTH, -timeAgo)
                }
                dateWords[1].contains("Year") -> {
                    calendar.add(Calendar.YEAR, -timeAgo)
                }
            }

            return calendar.timeInMillis
        }

        return 0L
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl/${chapter.url}/all-pages", headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.content-list > img").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    private class Status : Filter.TriState("Completed")
    private class Genre(name: String, val id: Int) : Filter.TriState(name)
    private class TextField(name: String, val key: String) : Filter.Text(name)
    private class Type : Filter.Select<String>("Type", arrayOf("All", "Japanese Manga", "Korean Manhwa", "Chinese Manhua"))
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    override fun getFilterList() = FilterList(
        TextField("Author", "author-name"),
        TextField("Artist", "artist-name"),
        Type(),
        Status(),
        GenreList(getGenreList())
    )

    // [...document.querySelectorAll("ul.manga-cat span")].map(el => `Genre("${el.nextSibling.textContent.trim()}", ${el.getAttribute('data-id')})`).join(',\n')
    // https://www.readmng.com/advanced-search
    private fun getGenreList() = listOf(
        Genre("Action", 2),
        Genre("Adventure", 4),
        Genre("Comedy", 5),
        Genre("Doujinshi", 6),
        Genre("Drama", 7),
        Genre("Ecchi", 8),
        Genre("Fantasy", 9),
        Genre("Gender Bender", 10),
        Genre("Harem", 11),
        Genre("Historical", 12),
        Genre("Horror", 13),
        Genre("Josei", 14),
        Genre("Lolicon", 15),
        Genre("Martial Arts", 16),
        Genre("Mature", 17),
        Genre("Mecha", 18),
        Genre("Mystery", 19),
        Genre("One shot", 20),
        Genre("Psychological", 21),
        Genre("Romance", 22),
        Genre("School Life", 23),
        Genre("Sci-fi", 24),
        Genre("Seinen", 25),
        Genre("Shotacon", 26),
        Genre("Shoujo", 27),
        Genre("Shoujo Ai", 28),
        Genre("Shounen", 29),
        Genre("Shounen Ai", 30),
        Genre("Slice of Life", 31),
        Genre("Smut", 32),
        Genre("Sports", 33),
        Genre("Supernatural", 34),
        Genre("Tragedy", 35),
        Genre("Yaoi", 36),
        Genre("Yuri", 37)
    )
}
