package eu.kanade.tachiyomi.extension.id.bacamanga
import android.util.Base64
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
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
import java.io.UnsupportedEncodingException
import java.text.SimpleDateFormat
import java.util.Locale

class BacaManga : ParsedHttpSource() {

    override val name = "Baca Manga"
    override val baseUrl = "https://bacamanga.co"
    override val lang = "id"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga/page/$page/?order=popular", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga/page/$page/?order=update", headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val builtUrl = "$baseUrl/manga/page/$page/"
        val url = HttpUrl.parse(builtUrl)!!.newBuilder()
        url.addQueryParameter("title", query)
        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    url.addQueryParameter("author", filter.state)
                }
                is YearFilter -> {
                    url.addQueryParameter("yearx", filter.state)
                }
                is StatusFilter -> {
                    val status = when (filter.state) {
                        Filter.TriState.STATE_INCLUDE -> "completed"
                        Filter.TriState.STATE_EXCLUDE -> "ongoing"
                        else -> ""
                    }
                    url.addQueryParameter("status", status)
                }
                is TypeFilter -> {
                    url.addQueryParameter("type", filter.toUriPart())
                }
                is OrderByFilter -> {
                    url.addQueryParameter("order", filter.toUriPart())
                }
                is GenreList -> {
                    filter.state.forEach {
                        if (it.state) {
                            url.addQueryParameter("genre[]", it.id)
                        }
                    }
                }
            }
        }
        return GET(url.build().toString(), headers)
    }

    override fun popularMangaSelector() = "div.bs"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.limit img").attr("src")
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        return manga
    }

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a.next.page-numbers"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select(".spe")
        val sepName = infoElement.select("span:nth-child(4)").last()
        val manga = SManga.create()
        manga.author = sepName.ownText()
        manga.artist = sepName.ownText()
        manga.genre = infoElement.select("span:nth-child(1) > a")
            .joinToString(", ") { it.text() }
        manga.status = parseStatus(infoElement.select("span:nth-child(2)").text())
        manga.description = document.select("div[itemprop=articleBody]").last().text()
        manga.thumbnail_url = document.select(".thumb > img:nth-child(1)").attr("src")

        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("ongoing", ignoreCase = true) -> SManga.ONGOING
        element.contains("completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select(chapterListSelector()).map { chapterFromElement(it) }
        // Add date for latest chapter only
        chapters[0].date_upload = parseDate(document.select(".lchx+span.dt .dt-small").first().text())
        return chapters
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(date)?.time ?: 0L
    }

    override fun chapterListSelector() = ".lchx"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        val script = document.select("div#content script:nth-child(3)").html()
        val encodedImagesList = script.substringAfter("JSON['parse'](window[").substringAfter("\"").substringBefore("\"")
        val decodedImagesList = decodeBase64(encodedImagesList.rot13Decode())
        val json = JsonParser().parse(decodedImagesList).asJsonArray
        json.forEachIndexed { i, url ->
            /* REMOVING QUOTES AROUND STRING */
            val url_clean = url.toString().removeSurrounding("\"")
            pages.add(Page(i, "", url_clean))
        }
        return pages
    }

    private fun decodeBase64(coded: String): String {
        var valueDecoded = ByteArray(0)
        try {
            valueDecoded = Base64.decode(coded.toByteArray(charset("UTF-8")), Base64.DEFAULT)
        } catch (e: UnsupportedEncodingException) {
        }

        return String(valueDecoded)
    }

    /**
     * rot13 decoding
     * More aboure rot13 https://rosettacode.org/wiki/Rot-13
     * Kotlin implementation https://rosettacode.org/wiki/Rot-13#Kotlin
     */

    private fun String.rot13Decode() = map {
        when {
            it.isUpperCase() -> { val x = it + 13; if (x > 'Z') x - 26 else x }
            it.isLowerCase() -> { val x = it + 13; if (x > 'z') x - 26 else x }
            else -> it
        }
    }.toCharArray().joinToString("")

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        return if (page.imageUrl!!.contains("i0.wp.com")) {
            val headers = Headers.Builder()
            headers.apply {
                add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3")
            }
            GET(page.imageUrl!!, headers.build())
        } else GET(page.imageUrl!!, headers)
    }

    private class AuthorFilter : Filter.Text("Author")

    private class YearFilter : Filter.Text("Year")

    private class OrderByFilter : UriPartFilter(
        "Sort by",
        arrayOf(
            Pair("Default", ""),
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
            Pair("Latest Update", "update"),
            Pair("Latest Added", "latest"),
            Pair("Popular", "popular")
        )
    )

    private class StatusFilter : Filter.TriState("Completed")

    private class TypeFilter : UriPartFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("Manga", "Manga"),
            Pair("Manhua", "Manhua"),
            Pair("Manhwa", "Manhwa")
        )
    )

    private class Genre(name: String, val id: String = name) : Filter.CheckBox(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        AuthorFilter(),
        Filter.Separator(),
        YearFilter(),
        Filter.Separator(),
        StatusFilter(),
        Filter.Separator(),
        OrderByFilter(),
        Filter.Separator(),
        TypeFilter(),
        Filter.Separator(),
        GenreList(getGenreList())
    )

    private fun getGenreList() = listOf(
        Genre("Action", "action"),
        Genre("Adult", "adult"),
        Genre("Adventure", "adventure"),
        Genre("Comedy", "comedy"),
        Genre("Crime", "crime"),
        Genre("Demons", "demons"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Echi", "echi"),
        Genre("Fantasy", "fantasy"),
        Genre("Game", "game"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horor", "horor"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Magic", "magic"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial Art", "martial-art"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Military", "military"),
        Genre("Monster", "monster"),
        Genre("Monster Girls", "monster-girls"),
        Genre("Music", "music"),
        Genre("Mystery", "mystery"),
        Genre("Post-Apocalyptic", "post-apocalyptic"),
        Genre("Psychological", "psychological"),
        Genre("Romance", "romance"),
        Genre("School", "school"),
        Genre("School Life", "school-life"),
        Genre("Sci-Fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shoujo", "shoujo"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Shounen", "shounen"),
        Genre("Si-fi", "si-fi"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Sports", "sports"),
        Genre("Super Power", "super-power"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Vampire", "vampire"),
        Genre("Webtoon", "webtoon"),
        Genre("Webtoons", "webtoons"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri"),
        Genre("Zombies", "zombies")
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
