package eu.kanade.tachiyomi.extension.all.mangaplus

import android.app.Application
import android.content.SharedPreferences
import android.os.Build
import android.support.v7.preference.CheckBoxPreference
import android.support.v7.preference.ListPreference
import android.support.v7.preference.PreferenceScreen
import com.google.gson.Gson
import com.squareup.duktape.Duktape
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID
import androidx.preference.CheckBoxPreference as AndroidXCheckBoxPreference
import androidx.preference.ListPreference as AndroidXListPreference
import androidx.preference.PreferenceScreen as AndroidXPreferenceScreen

abstract class MangaPlus(
    override val lang: String,
    private val internalLang: String,
    private val langCode: Language
) : HttpSource(), ConfigurableSource {

    override val name = "MANGA Plus by SHUEISHA"

    override val baseUrl = "https://mangaplus.shueisha.co.jp"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)
        .add("User-Agent", USER_AGENT)
        .add("Session-Token", UUID.randomUUID().toString())

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { imageIntercept(it) }
        .addInterceptor { thumbnailIntercept(it) }
        .build()

    private val protobufJs: String by lazy {
        val request = GET(PROTOBUFJS_CDN, headers)
        client.newCall(request).execute().body()!!.string()
    }

    private val gson: Gson by lazy { Gson() }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val imageResolution: String
        get() = preferences.getString("${RESOLUTION_PREF_KEY}_$lang", RESOLUTION_PREF_DEFAULT_VALUE)!!

    private val splitImages: String
        get() = if (preferences.getBoolean("${SPLIT_PREF_KEY}_$lang", SPLIT_PREF_DEFAULT_VALUE)) "yes" else "no"

    private var titleList: List<Title>? = null

    override fun popularMangaRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/manga_list/hot")
            .build()

        return GET("$API_URL/title_list/ranking", newHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.asProto()

        if (result.success == null)
            throw Exception(result.error!!.langPopup.body)

        titleList = result.success.titleRankingView!!.titles
            .filter { it.language == langCode }

        val mangas = titleList!!.map {
            SManga.create().apply {
                title = it.name
                thumbnail_url = it.portraitImageUrl
                url = "#/titles/${it.titleId}"
            }
        }

        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/updates")
            .build()

        return GET("$API_URL/web/web_home?lang=$internalLang", newHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.asProto()

        if (result.success == null)
            throw Exception(result.error!!.langPopup.body)

        // Fetch all titles to get newer thumbnail urls at the interceptor.
        val popularResponse = client.newCall(popularMangaRequest(1)).execute().asProto()

        if (popularResponse.success != null) {
            titleList = popularResponse.success.titleRankingView!!.titles
                .filter { it.language == langCode }
        }

        val mangas = result.success.webHomeView!!.groups
            .flatMap { it.titles }
            .mapNotNull { it.title }
            .filter { it.language == langCode }
            .map {
                SManga.create().apply {
                    title = it.name
                    thumbnail_url = it.portraitImageUrl
                    url = "#/titles/${it.titleId}"
                }
            }
            .distinctBy { it.title }

        return MangasPage(mangas, false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return super.fetchSearchManga(page, query, filters)
            .map { MangasPage(it.mangas.filter { m -> m.title.contains(query, true) }, it.hasNextPage) }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/manga_list/all")
            .build()

        return GET("$API_URL/title_list/all", newHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.asProto()

        if (result.success == null)
            throw Exception(result.error!!.langPopup.body)

        titleList = result.success.allTitlesView!!.titles
            .filter { it.language == langCode }

        val mangas = titleList!!.map {
            SManga.create().apply {
                title = it.name
                thumbnail_url = it.portraitImageUrl
                url = "#/titles/${it.titleId}"
            }
        }

        return MangasPage(mangas, false)
    }

    private fun titleDetailsRequest(manga: SManga): Request {
        val titleId = manga.url.substringAfterLast("/")

        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/titles/$titleId")
            .build()

        return GET("$API_URL/title_detail?title_id=$titleId", newHeaders)
    }

    // Workaround to allow "Open in browser" use the real URL.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(titleDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        // Remove the '#' and map to the new url format used in website.
        return GET(baseUrl + manga.url.substring(1), headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.asProto()

        if (result.success == null)
            throw Exception(result.error!!.langPopup.body)

        val details = result.success.titleDetailView!!
        val title = details.title
        val isCompleted = details.nonAppearanceInfo.contains(COMPLETE_REGEX)

        return SManga.create().apply {
            author = title.author.replace(" / ", ", ")
            artist = author
            description = details.overview + "\n\n" + details.viewingPeriodDescription
            status = if (isCompleted) SManga.COMPLETED else SManga.ONGOING
            thumbnail_url = title.portraitImageUrl
        }
    }

    override fun chapterListRequest(manga: SManga): Request = titleDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.asProto()

        if (result.success == null)
            throw Exception(result.error!!.langPopup.body)

        val titleDetailView = result.success.titleDetailView!!

        val chapters = titleDetailView.firstChapterList + titleDetailView.lastChapterList

        return chapters.reversed()
            // If the subTitle is null, then the chapter time expired.
            .filter { it.subTitle != null }
            .map {
                SChapter.create().apply {
                    name = "${it.name} - ${it.subTitle}"
                    scanlator = "Shueisha"
                    date_upload = 1000L * it.startTimeStamp
                    url = "#/viewer/${it.chapterId}"
                    chapter_number = it.name.substringAfter("#").toFloatOrNull() ?: -1f
                }
            }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")

        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/viewer/$chapterId")
            .build()

        val url = HttpUrl.parse("$API_URL/manga_viewer")!!.newBuilder()
            .addQueryParameter("chapter_id", chapterId)
            .addQueryParameter("split", splitImages)
            .addQueryParameter("img_quality", imageResolution)
            .toString()

        return GET(url, newHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.asProto()

        if (result.success == null)
            throw Exception(result.error!!.langPopup.body)

        val referer = response.request().header("Referer")!!

        return result.success.mangaViewer!!.pages
            .mapNotNull { it.page }
            .mapIndexed { i, page ->
                val encryptionKey = if (page.encryptionKey == null) "" else "&encryptionKey=${page.encryptionKey}"
                Page(i, referer, "${page.imageUrl}$encryptionKey")
            }
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .removeAll("Origin")
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun setupPreferenceScreen(screen: AndroidXPreferenceScreen) {
        val resolutionPref = AndroidXListPreference(screen.context).apply {
            key = "${RESOLUTION_PREF_KEY}_$lang"
            title = RESOLUTION_PREF_TITLE
            entries = RESOLUTION_PREF_ENTRIES
            entryValues = RESOLUTION_PREF_ENTRY_VALUES
            setDefaultValue(RESOLUTION_PREF_DEFAULT_VALUE)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString("${RESOLUTION_PREF_KEY}_$lang", entry).commit()
            }
        }
        val splitPref = AndroidXCheckBoxPreference(screen.context).apply {
            key = "${SPLIT_PREF_KEY}_$lang"
            title = SPLIT_PREF_TITLE
            summary = SPLIT_PREF_SUMMARY
            setDefaultValue(SPLIT_PREF_DEFAULT_VALUE)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean("${SPLIT_PREF_KEY}_$lang", checkValue).commit()
            }
        }

        screen.addPreference(resolutionPref)
        screen.addPreference(splitPref)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val resolutionPref = ListPreference(screen.context).apply {
            key = "${RESOLUTION_PREF_KEY}_$lang"
            title = RESOLUTION_PREF_TITLE
            entries = RESOLUTION_PREF_ENTRIES
            entryValues = RESOLUTION_PREF_ENTRY_VALUES
            setDefaultValue(RESOLUTION_PREF_DEFAULT_VALUE)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString("${RESOLUTION_PREF_KEY}_$lang", entry).commit()
            }
        }
        val splitPref = CheckBoxPreference(screen.context).apply {
            key = "${SPLIT_PREF_KEY}_$lang"
            title = SPLIT_PREF_TITLE
            summary = SPLIT_PREF_SUMMARY
            setDefaultValue(SPLIT_PREF_DEFAULT_VALUE)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean("${SPLIT_PREF_KEY}_$lang", checkValue).commit()
            }
        }

        screen.addPreference(resolutionPref)
        screen.addPreference(splitPref)
    }

    private fun imageIntercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        if (request.url().queryParameter("encryptionKey") == null)
            return chain.proceed(request)

        val encryptionKey = request.url().queryParameter("encryptionKey")!!

        // Change the url and remove the encryptionKey to avoid detection.
        val newUrl = request.url().newBuilder()
            .removeAllQueryParameters("encryptionKey")
            .build()
        request = request.newBuilder()
            .url(newUrl)
            .build()

        val response = chain.proceed(request)

        val contentType = response.header("Content-Type", "image/jpeg")!!
        val image = decodeImage(encryptionKey, response.body()!!.bytes())
        val body = ResponseBody.create(MediaType.parse(contentType), image)

        return response.newBuilder()
            .body(body)
            .build()
    }

    private fun decodeImage(encryptionKey: String, image: ByteArray): ByteArray {
        val keyStream = HEX_GROUP
            .findAll(encryptionKey)
            .toList()
            .map { it.groupValues[1].toInt(16) }

        val content = image
            .map { it.toInt() }
            .toMutableList()

        val blockSizeInBytes = keyStream.size

        for ((i, value) in content.iterator().withIndex()) {
            content[i] = value xor keyStream[i % blockSizeInBytes]
        }

        return ByteArray(content.size) { pos -> content[pos].toByte() }
    }

    private fun thumbnailIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // Check if it is 404 to maintain compatibility when the extension used Weserv.
        val isBadCode = (response.code() == 401 || response.code() == 404)

        if (isBadCode && request.url().toString().contains(TITLE_THUMBNAIL_PATH)) {
            val titleId = request.url().toString()
                .substringBefore("/$TITLE_THUMBNAIL_PATH")
                .substringAfterLast("/")
                .toInt()
            val title = titleList?.find { it.titleId == titleId } ?: return response

            response.close()
            val thumbnailRequest = GET(title.portraitImageUrl, request.headers())
            return chain.proceed(thumbnailRequest)
        }

        return response
    }

    private val ErrorResult.langPopup: Popup
        get() = when (lang) {
            "es" -> spanishPopup
            else -> englishPopup
        }

    private fun Response.asProto(): MangaPlusResponse {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M)
            return ProtoBuf.decodeFromByteArray(MangaPlusSerializer, body()!!.bytes())

        // The kotlinx.serialization library eventually always have some issues with
        // devices with Android version below Nougat. So, if the device is running Marshmallow
        // or lower, the deserialization is done using ProtobufJS + Duktape + Gson.

        val bytes = body()!!.bytes()
        val messageBytes = "var BYTE_ARR = new Uint8Array([${bytes.joinToString()}]);"

        val res = Duktape.create().use {
            // The current Kotlin version brokes Duktape's module feature,
            // so we need to provide an workaround to prevent the usage of 'require'.
            it.evaluate("var module = { exports: true };")
            it.evaluate(protobufJs)
            it.evaluate(messageBytes + DECODE_SCRIPT) as String
        }

        return gson.fromJson(res, MangaPlusResponse::class.java)
    }

    companion object {
        private const val API_URL = "https://jumpg-webapi.tokyo-cdn.com/api"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.183 Safari/537.36"

        private val HEX_GROUP = "(.{1,2})".toRegex()

        private const val PROTOBUFJS_CDN = "https://cdn.jsdelivr.net/npm/protobufjs@6.10.1/dist/light/protobuf.js"

        private const val RESOLUTION_PREF_KEY = "imageResolution"
        private const val RESOLUTION_PREF_TITLE = "Image resolution"
        private val RESOLUTION_PREF_ENTRIES = arrayOf("Low resolution", "Medium resolution", "High resolution")
        private val RESOLUTION_PREF_ENTRY_VALUES = arrayOf("low", "high", "super_high")
        private val RESOLUTION_PREF_DEFAULT_VALUE = RESOLUTION_PREF_ENTRY_VALUES[2]

        private const val SPLIT_PREF_KEY = "splitImage"
        private const val SPLIT_PREF_TITLE = "Split double pages"
        private const val SPLIT_PREF_SUMMARY = "Not all titles support disabling this."
        private const val SPLIT_PREF_DEFAULT_VALUE = true

        private val COMPLETE_REGEX = "completado|complete".toRegex()

        private const val TITLE_THUMBNAIL_PATH = "title_thumbnail_portrait_list"
    }
}
