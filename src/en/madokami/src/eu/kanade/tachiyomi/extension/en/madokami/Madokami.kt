package eu.kanade.tachiyomi.extension.en.madokami

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Madokami : ConfigurableSource, ParsedHttpSource() {
    override val name = "Madokami"
    override val baseUrl = "https://manga.madokami.al"
    override val lang = "en"
    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private fun authenticate(request: Request): Request {
        val credential = Credentials.basic(preferences.getString("username", ""), preferences.getString("password", ""))
        return request.newBuilder().header("Authorization", credential).build()
    }

    override fun latestUpdatesSelector() = "table.mobile-files-table tbody tr td:nth-child(1) a:nth-child(1)"

    override fun latestUpdatesFromElement(element: Element): SManga {
        var manga = SManga.create()
        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.text().split("/").last()
        return manga
    }

    override fun latestUpdatesNextPageSelector() = null

    override fun latestUpdatesRequest(page: Int) = authenticate(GET("$baseUrl/recent", headers))

    override fun popularMangaSelector(): String { return latestUpdatesSelector() }

    override fun popularMangaFromElement(element: Element): SManga { return latestUpdatesFromElement(element) }

    override fun popularMangaNextPageSelector(): String? { return latestUpdatesNextPageSelector() }

    override fun popularMangaRequest(page: Int): Request { return latestUpdatesRequest(page) }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return authenticate(GET("$baseUrl/search?q=$query"))
    }

    override fun searchMangaSelector() = "div.container table tbody tr td:nth-child(1) a:nth-child(1)"

    override fun searchMangaFromElement(element: Element): SManga { return latestUpdatesFromElement(element) }

    override fun searchMangaNextPageSelector(): String? { return latestUpdatesNextPageSelector() }

    /**
     * Returns the details of the manga from the given [document].
     *
     * @param document the parsed document.
     */
    override fun mangaDetailsParse(document: Document): SManga {
        var manga = SManga.create()
        manga.url = document.location()
        manga.title = document.select("span.title").text()
        manga.author = document.select("a[itemprop=\"author\"]").map { it.text() }.joinToString(", ")
        manga.description = "Tags: " + document.select("div.genres[itemprop=\"keywords\"] a.tag.tag-category").map { it.text() }.joinToString(", ")
        manga.genre = document.select("div.genres a.tag[itemprop=\"genre\"]").map { it.text() }.joinToString(", ")
        manga.status = if (document.select("span.scanstatus").text() == "Yes") SManga.COMPLETED else SManga.UNKNOWN
        manga.thumbnail_url = document.select("div.manga-info img[itemprop=\"image\"]").attr("src")
        return manga
    }

    override fun chapterListRequest(manga: SManga) = authenticate(GET("$baseUrl/" + manga.url, headers))

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each chapter.
     */
    override fun chapterListSelector() = "table#index-table > tbody > tr > td:nth-child(6) > a"

    /**
     * Returns a chapter from the given element.
     *
     * @param element an element obtained from [chapterListSelector].
     */
    override fun chapterFromElement(element: Element): SChapter {
        val el = element.parent().parent()
        val chapter = SChapter.create()
        chapter.url = "${el.select("td:nth-child(6) a").attr("href")}"
        chapter.name = el.select("td:nth-child(1) a").text()
        val date = el.select("td:nth-child(3)").text()
        if (date.endsWith("ago")) {
            val splitDate = date.split(" ")
            val newDate = Calendar.getInstance()
            val amount = splitDate[0].toInt()
            when {
                splitDate[1].startsWith("min") -> {
                    newDate.add(Calendar.MINUTE, -amount)
                }
                splitDate[1].startsWith("sec") -> {
                    newDate.add(Calendar.SECOND, -amount)
                }
                splitDate[1].startsWith("hour") -> {
                    newDate.add(Calendar.HOUR, -amount)
                }
            }
            chapter.date_upload = newDate.time.time
        } else {
            chapter.date_upload = dateFormat.parse(date).time
        }
        return chapter
    }

    override fun pageListRequest(chapter: SChapter) = authenticate(GET(chapter.url, headers))

    override fun pageListParse(document: Document): List<Page> {
        val element = document.select("div#reader")
        val path = element.attr("data-path")
        val filestring = element.attr("data-files")
        val files = filestring.trim('[', ']').split(",")
        val pages = ArrayList<Page>()
        for ((index, filename) in files.withIndex()) {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("manga.madokami.al")
                .addPathSegments("reader")
                .addPathSegment("image")
                .addEncodedQueryParameter("path", URLEncoder.encode(path, "UTF-8"))
                .addEncodedQueryParameter("file", URLEncoder.encode(filename.trim('"').replace("&quot;", "").replace("\\/", "/"), "UTF-8"))
                .build().url()
            pages.add(Page(index, url.toExternalForm(), url.toExternalForm()))
        }
        return pages
    }

    override fun imageRequest(page: Page) = authenticate(GET(page.url, headers))

    /**
     * Returns the absolute url to the source image from the document.
     *
     * @param document the parsed document.
     */
    override fun imageUrlParse(document: Document) = ""

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val username = androidx.preference.EditTextPreference(screen.context).apply {
            key = "username"
            title = "Username"

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue as String).commit()
            }
        }
        val password = androidx.preference.EditTextPreference(screen.context).apply {
            key = "password"
            title = "Password"

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue as String).commit()
            }
        }

        screen.addPreference(username)
        screen.addPreference(password)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val username = EditTextPreference(screen.context).apply {
            key = "username"
            title = "Username"

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue as String).commit()
            }
        }
        val password = EditTextPreference(screen.context).apply {
            key = "password"
            title = "Password"

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue as String).commit()
            }
        }

        screen.addPreference(username)
        screen.addPreference(password)
    }
}
