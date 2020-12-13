package eu.kanade.tachiyomi.extension.id.komiku

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Komiku : ParsedHttpSource() {
    override val name = "Komiku"

    override val baseUrl = "https://komiku.id/"

    override val lang = "id"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // popular
    override fun popularMangaSelector() = "div.bge"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/other/hot/page/$page/?orderby=meta_value_num", headers)

    private val coverRegex = Regex("""(/Manga-|/Manhua-|/Manhwa-)""")
    private val coverUploadRegex = Regex("""/uploads/\d\d\d\d/\d\d/""")

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a:has(h3)").attr("href"))
        title = element.select("h3").text().trim()

        // scraped image doesn't make for a good cover; so try to transform it
        // make it take bad cover instead of null if it contains upload date as those URLs aren't very useful
        if (element.select("img").attr("data-src").contains(coverUploadRegex)) {
            thumbnail_url = element.select("img").attr("data-src")
        } else {
            thumbnail_url = element.select("img").attr("data-src").substringBeforeLast("?").replace(coverRegex, "/Komik-")
        }
    }

    override fun popularMangaNextPageSelector() = ".pag-nav a.next"

    // latest
    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/other/hot/page/$page/?orderby=modified", headers)

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // search
    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/cari/page/$page/?post_type=manga&s=$query", headers)

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = "a.next"

    // manga details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        description = document.select("#Sinopsis > p").text().trim()
        author = document.select("table.inftable td:contains(Komikus)+td").text()
        genre = document.select("li[itemprop=genre] > a").joinToString { it.text() }
        status = parseStatus(document.select("table.inftable tr > td:contains(Status) + td").text())
        thumbnail_url = document.select("div.ims > img").attr("abs:src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // chapters
    override fun chapterListSelector() = "#Daftar_Chapter tr:has(td.judulseries)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        name = element.select("a").attr("title")

        val timeStamp = element.select("td.tanggalseries")
        if (timeStamp.text().contains("lalu")) {
            date_upload = parseRelativeDate(timeStamp.text().trim()) ?: 0
        } else {
            date_upload = parseDate(timeStamp.last())
        }
    }

    private fun parseDate(element: Element): Long = SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(element.text())?.time ?: 0

    // Used Google translate here
    private fun parseRelativeDate(date: String): Long? {
        val trimmedDate = date.substringBefore(" lalu").removeSuffix("s").split(" ")

        val calendar = Calendar.getInstance()
        when (trimmedDate[1]) {
            "jam" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }
            "menit" -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }
            "detik" -> calendar.apply { add(Calendar.SECOND, 0) }
        }

        return calendar.timeInMillis
    }

    // pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select("#Baca_Komik img").mapIndexed { i, element ->
            Page(i, "", element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun getFilterList() = FilterList()
}
