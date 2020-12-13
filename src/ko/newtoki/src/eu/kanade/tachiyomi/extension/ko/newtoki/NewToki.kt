package eu.kanade.tachiyomi.extension.ko.newtoki

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.CheckBoxPreference
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceScreen
import android.widget.Toast
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URI
import java.net.URISyntaxException
import java.text.SimpleDateFormat
import java.util.Calendar

/**
 * NewToki Source
 **/
open class NewToki(override val name: String, private val defaultBaseUrl: String, private val boardName: String) : ConfigurableSource, ParsedHttpSource() {
    override val baseUrl by lazy { getPrefBaseUrl() }
    override val lang: String = "ko"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div#webtoon-list > ul > li"

    override fun popularMangaFromElement(element: Element): SManga {
        val linkElement = element.getElementsByTag("a").first()

        val manga = SManga.create()
        manga.setUrlWithoutDomain(linkElement.attr("href").substringBefore("?"))
        manga.title = element.select("span.title").first().ownText()
        manga.thumbnail_url = linkElement.getElementsByTag("img").attr("src")
        return manga
    }

    override fun popularMangaNextPageSelector() = "ul.pagination > li:not(.disabled)"

    // Do not add page parameter if page is 1 to prevent tracking.
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/$boardName" + if (page > 1) "/p$page" else "")

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        val hasNextPage = try {
            !document.select(popularMangaNextPageSelector()).last().hasClass("active")
        } catch (_: Exception) {
            false
        }

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaSelector()
    override fun searchMangaParse(response: Response) = popularMangaParse(response)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/$boardName" + (if (page > 1) "/p$page" else "") + "?stx=$query")
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val realQuery = query.removePrefix(PREFIX_ID_SEARCH)
            val urlPath = "/$boardName/$realQuery"
            client.newCall(GET("$baseUrl$urlPath"))
                .asObservableSuccess()
                .map { response ->
                    // the id is matches any of 'post' from their CMS board.
                    // Includes Manga Details Page, Chapters, Comments, and etcs...
                    actualMangaParseById(urlPath, response)
                }
        } else super.fetchSearchManga(page, query, filters)
    }

    private fun actualMangaParseById(urlPath: String, response: Response): MangasPage {
        val document = response.asJsoup()

        // Only exists on detail page.
        val firstChapterButton = document.select("tr > th > button.btn-blue").first()
        // only exists on chapter with proper manga detail page.
        val fullListButton = document.select(".comic-navbar .toon-nav a").last()

        val list: List<SManga> = if (firstChapterButton?.text()?.contains("첫회보기") ?: false) { // Check this page is detail page
            val details = mangaDetailsParse(document)
            details.url = urlPath
            listOf(details)
        } else if (fullListButton?.text()?.contains("전체목록") ?: false) { // Check this page is chapter page
            val url = fullListButton.attr("abs:href")
            val details = mangaDetailsParse(client.newCall(GET(url)).execute())
            details.url = getUrlPath(url)
            listOf(details)
        } else emptyList()

        return MangasPage(list, false)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val info = document.select("div.view-title > .view-content").first()
        val title = document.select("div.view-content > span > b").text()
        val thumbnail = document.select("div.row div.view-img > img").attr("src")
        val descriptionElement = info.select("div.row div.view-content:not([style])")
        val description = descriptionElement.map {
            it.text().trim()
        }

        val manga = SManga.create()
        manga.title = title
        manga.description = description.joinToString("\n")
        manga.thumbnail_url = thumbnail
        descriptionElement.forEach {
            val text = it.text()
            when {
                "작가" in text -> manga.author = it.getElementsByTag("a").text()
                "분류" in text -> {
                    val genres = mutableListOf<String>()
                    it.getElementsByTag("a").forEach { item ->
                        genres.add(item.text())
                    }
                    manga.genre = genres.joinToString(", ")
                }
                "발행구분" in text -> manga.status = parseStatus(it.getElementsByTag("a").text())
            }
        }
        return manga
    }

    private fun parseStatus(status: String) = when (status.trim()) {
        "주간", "격주", "월간", "격월/비정기", "단행본" -> SManga.ONGOING
        "단편", "완결" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.serial-list > ul.list-body > li.list-item"

    override fun chapterFromElement(element: Element): SChapter {
        val linkElement = element.select(".wr-subject > a.item-subject").last()
        val rawName = linkElement.ownText().trim()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(linkElement.attr("href"))
        chapter.chapter_number = parseChapterNumber(rawName)
        chapter.name = rawName
        chapter.date_upload = parseChapterDate(element.select(".wr-date").last().text().trim())
        return chapter
    }

    private fun parseChapterNumber(name: String): Float {
        try {
            if (name.contains("[단편]")) return 1f
            // `특별` means `Special`, so It can be buggy. so pad `편`(Chapter) to prevent false return
            if (name.contains("번외") || name.contains("특별편")) return -2f
            val regex = Regex("([0-9]+)(?:[-.]([0-9]+))?(?:화)")
            val (ch_primal, ch_second) = regex.find(name)!!.destructured
            return (ch_primal + if (ch_second.isBlank()) "" else ".$ch_second").toFloatOrNull() ?: -1f
        } catch (e: Exception) {
            e.printStackTrace()
            return -1f
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun parseChapterDate(date: String): Long {
        return try {
            if (date.contains(":")) {
                val calendar = Calendar.getInstance()
                val splitDate = date.split(":")

                val hours = splitDate.first().toInt()
                val minutes = splitDate.last().toInt()

                val calendarHours = calendar.get(Calendar.HOUR)
                val calendarMinutes = calendar.get(Calendar.MINUTE)

                if (calendarHours >= hours && calendarMinutes > minutes) {
                    calendar.add(Calendar.DATE, -1)
                }

                calendar.timeInMillis
            } else {
                SimpleDateFormat("yyyy.MM.dd").parse(date)?.time ?: 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    private val htmlDataRegex = Regex("""html_data\+='([^']+)'""")

    override fun pageListParse(document: Document): List<Page> {
        val script = document.select("script:containsData(html_data)").firstOrNull()?.data() ?: throw Exception("data script not found")
        val loadScript = document.select("script:containsData(data_attribute)").firstOrNull()?.data() ?: throw Exception("load script not found")
        val dataAttr = "abs:data-" + loadScript.substringAfter("data_attribute: '").substringBefore("',")

        return htmlDataRegex.findAll(script).map { it.groupValues[1] }
            .asIterable()
            .flatMap { it.split(".") }
            .joinToString("") { it.toIntOrNull(16)?.toChar()?.toString() ?: "" }
            .let { Jsoup.parse(it) }
            .select("img[src=/img/loading-image.gif]")
            .mapIndexed { i, img -> Page(i, "", if (img.hasAttr(dataAttr)) img.attr(dataAttr) else img.attr("abs:content")) }
    }

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // We are able to get the image URL directly from the page list
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("This method should not be called!")

    override fun getFilterList() = FilterList()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val baseUrlPref = androidx.preference.EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF_TITLE
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            this.setDefaultValue(defaultBaseUrl)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $defaultBaseUrl"

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(BASE_URL_PREF, newValue as String).commit()
                    Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        val latestExperimentPref = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = EXPERIMENTAL_LATEST_PREF_TITLE
            title = EXPERIMENTAL_LATEST_PREF_TITLE
            summary = EXPERIMENTAL_LATEST_PREF_SUMMARY

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putBoolean(EXPERIMENTAL_LATEST_PREF, newValue as Boolean).commit()
                    Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(baseUrlPref)
        if (name == "ManaToki") {
            screen.addPreference(latestExperimentPref)
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val baseUrlPref = EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF_TITLE
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            this.setDefaultValue(defaultBaseUrl)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $defaultBaseUrl"

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(BASE_URL_PREF, newValue as String).commit()
                    Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        val latestExperimentPref = CheckBoxPreference(screen.context).apply {
            key = EXPERIMENTAL_LATEST_PREF_TITLE
            title = EXPERIMENTAL_LATEST_PREF_TITLE
            summary = EXPERIMENTAL_LATEST_PREF_SUMMARY

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putBoolean(EXPERIMENTAL_LATEST_PREF, newValue as Boolean).commit()
                    Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(baseUrlPref)
        if (name == "ManaToki") {
            screen.addPreference(latestExperimentPref)
        }
    }

    protected fun getUrlPath(orig: String): String {
        return try {
            URI(orig).path
        } catch (e: URISyntaxException) {
            orig
        }
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!
    protected fun getExperimentLatest(): Boolean = preferences.getBoolean(EXPERIMENTAL_LATEST_PREF, false)

    companion object {
        private const val BASE_URL_PREF_TITLE = "Override BaseUrl"
        private const val BASE_URL_PREF = "overrideBaseUrl_v${BuildConfig.VERSION_NAME}"
        private const val BASE_URL_PREF_SUMMARY = "For temporary uses. Update extension will erase this setting."
        private const val RESTART_TACHIYOMI = "Restart Tachiyomi to apply new setting."

        // Setting: Experimental Latest Fetcher
        private const val EXPERIMENTAL_LATEST_PREF_TITLE = "Enable Latest (Experimental)"
        private const val EXPERIMENTAL_LATEST_PREF = "fetchLatestExperiment"
        private const val EXPERIMENTAL_LATEST_PREF_SUMMARY = "Fetch Latest Manga using Latest Chapters. May has duplicates, Also requires LOTS OF requests (70 per page)"

        const val PREFIX_ID_SEARCH = "id:"
    }
}
