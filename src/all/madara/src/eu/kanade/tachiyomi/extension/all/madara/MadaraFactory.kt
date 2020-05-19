package eu.kanade.tachiyomi.extension.all.madara

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MadaraFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        AdonisFansub(),
        AllPornComic(),
        AoCTranslations(),
        AstralLibrary(),
        ATMSubs(),
        Azora(),
        ChibiManga(),
        DisasterScans(),
        DoujinHentai(),
        FirstKissManga(),
        GetManhwa(),
        GoldenManga(),
        HappyTeaScans(),
        Hiperdex(),
        HunterFansub(),
        IchirinNoHanaYuri(),
        Indiancomicsonline(),
        IsekaiScanCom(),
        JustForFun(),
        KingzManga(),
        KlikManga(),
        KMangaIn(),
        KomikGo(),
        LilyManga(),
        LuxyScans(),
        Manga3asq(),
        Manga68(),
        MangaAction(),
        MangaArabTeam(),
        MangaBob(),
        MangaDods(),
        MangaKiss(),
        MangaKomi(),
        MangaLaw(),
        Mangalek(),
        MangaLord(),
        MangaRead(),
        MangaStream(),
        Mangasushi(),
        MangaSY(),
        MangaTX(),
        MangazukiClubJP(),
        MangazukiClubKO(),
        MangazukiMe(),
        MangazukiOnline(),
        ManhuaBox(),
        Manhuasnet(),
        ManhuaUS(),
        ManwahentaiMe(),
        ManwhaClub(),
        ManyToon(),
        ManyToonClub(),
        Milftoon(),
        MiracleScans(),
        NeoxScanlator(),
        NightComic(),
        NijiTranslations(),
        NinjaScans(),
        NovelFrance(),
        OnManga(),
        PlotTwistScan(),
        PojokManga(),
        PornComix(),
        RaiderScans(),
        ReadManhua(),
        TeabeerComics(),
        ThreeSixtyFiveManga(),
        Toonily(),
        TopManhua(),
        TsubakiNoScan(),
        UnknownScans(),
        Wakamics(),
        WordRain(),
        WuxiaWorld(),
        YaoiToshokan(),
        YokaiJump(),
        YoManga(),
        ZinManga(),
        ZManga(),
        MangaWT(),
        DecadenceScans(),
        MangaRockTeam(),
        MixedManga(),
        ManhuasWorld(),
        ArazNovel(),
        MangaByte(),
        ManhwaRaw(),
        GuncelManga(),
        WeScans(),
        ArangScans(),
        MangaHentai(),
        MangaPhoenix(),
        FirstKissManhua(),
        HeroManhua(),
        MartialScans(),
        MangaYosh(),
        Reisubs(),
        MangaReadOrg(),
        TurkceManga()
        // Removed by request of site owner
        // EarlyManga(),
        // MangaGecesi(),
        // MangaWOW(),
        // MangaStein(),
    )
}

class Mangasushi : Madara("Mangasushi", "https://mangasushi.net", "en")

class NinjaScans : Madara("NinjaScans", "https://ninjascans.com", "en")

class ReadManhua : Madara("ReadManhua", "https://readmanhua.net", "en",
    dateFormat = SimpleDateFormat("dd MMM yy", Locale.US))

class IsekaiScanCom : Madara("IsekaiScan.com", "https://isekaiscan.com", "en")

class HappyTeaScans : Madara("Happy Tea Scans", "https://happyteascans.com", "en")

class JustForFun : Madara("Just For Fun", "https://just-for-fun.ru", "ru",
    dateFormat = SimpleDateFormat("yy.MM.dd", Locale.US))

class AoCTranslations : Madara("Agent of Change Translations", "https://aoc.moe", "en") {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", baseUrl)
    override fun popularMangaSelector() = "div.page-item-detail.manga:has(span.chapter)"
    override fun chapterListSelector() = "li.wp-manga-chapter:has(a)"
    override fun chapterListParse(response: Response): List<SChapter> {
        return response.asJsoup().let { document ->
            document.select(chapterListSelector()).let { normalChapters ->
                if (normalChapters.isNotEmpty()) {
                    normalChapters.map { chapterFromElement(it) }
                } else {
                    // For their "fancy" volume/chapter lists
                    document.select("div.wpb_wrapper:contains(volume) a")
                        .filter { it.attr("href").contains(baseUrl) && !it.attr("href").contains("imgur") }
                        .map { volumeChapter ->
                            SChapter.create().apply {
                                volumeChapter.attr("href").let { url ->
                                    name = if (url.contains("volume")) {
                                        val volume = url.substringAfter("volume-").substringBefore("/")
                                        val volChap = url.substringAfter("volume-$volume/").substringBefore("/").replace("-", " ").capitalize()
                                        "Volume $volume - $volChap"
                                    } else {
                                        url.substringBefore("/p").substringAfterLast("/").replace("-", " ").capitalize()
                                    }
                                    setUrlWithoutDomain(url.substringBefore("?") + "?style=list")
                                }
                            }
                        }
                }.reversed()
            }
        }
    }
}

class KomikGo : Madara("KomikGo", "https://komikgo.com", "id")

class LuxyScans : Madara("Luxy Scans", "https://luxyscans.com", "en")

class TsubakiNoScan : Madara("Tsubaki No Scan", "https://tsubakinoscan.com", "fr",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US))

class YokaiJump : Madara("Yokai Jump", "https://yokaijump.fr", "fr",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US))

class ZManga : Madara("ZManga", "https://zmanga.org", "es")

class MangazukiMe : Madara("Mangazuki.me", "https://mangazuki.me", "en")

class MangazukiOnline : Madara("Mangazuki.online", "https://www.mangazuki.online", "en") {
    override val client: OkHttpClient = super.client.newBuilder().followRedirects(true).build()
}

class MangazukiClubJP : Madara("Mangazuki.club", "https://mangazuki.club", "ja")

class MangazukiClubKO : Madara("Mangazuki.club", "https://mangazuki.club", "ko")

class FirstKissManga : Madara("1st Kiss", "https://1stkissmanga.com", "en",
    dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)) {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", baseUrl)
}

class MangaSY : Madara("Manga SY", "https://www.mangasy.com", "en")

class ManwhaClub : Madara("Manwha Club", "https://manhwa.club", "en")

class WuxiaWorld : Madara("WuxiaWorld", "https://wuxiaworld.site", "en") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/tag/webcomic/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/tag/webcomic/page/$page/?m_orderby=latest", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = super.searchMangaRequest(page, "$query comics", filters)
    override fun popularMangaNextPageSelector() = "div.nav-previous.float-left"
}

class WordRain : Madara("WordRain Translation", "https://wordrain69.com", "en") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga-genre/manga/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga-genre/manga/page/$page/?m_orderby=latest", headers)
    override fun searchMangaParse(response: Response): MangasPage {

        val url = HttpUrl.parse(response.request().url().toString())!!.newBuilder()
            .addQueryParameter("genre[]", "manga").build()
        val request: Request = Request.Builder().url(url).build()
        val call = client.newCall(request)
        val res: Response = call.execute()
        return super.searchMangaParse(res)
    }
}

class YoManga : Madara("Yo Manga", "https://yomanga.info", "en")

class ManyToon : Madara("ManyToon", "https://manytoon.com", "en")

class ChibiManga : Madara("Chibi Manga", "http://www.cmreader.info", "en",
    dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)) {
    override fun chapterListParse(response: Response): List<SChapter> {
        response.asJsoup().let { documet ->
            documet.select("li.parent.has-child").let { volumes ->
                return if (volumes.isNullOrEmpty()) {
                    documet.select(chapterListSelector()).map { chapterFromElement(it) }
                } else {
                    val chapters = mutableListOf<SChapter>()
                    volumes.reversed().forEach { v ->
                        val vName = v.select("a[href^=javascript]").text()
                        v.select(chapterListSelector()).map { chapters.add(chapterFromElement(it).apply { name = "$vName - $name" }) }
                    }
                    chapters
                }
            }
        }
    }
}

class ZinManga : Madara("Zin Translator", "https://zinmanga.com", "en") {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "https://zinmanga.com/")
}

class ManwahentaiMe : Madara("Manwahentai.me", "https://manhwahentai.me", "en")

class Manga3asq : Madara("مانجا العاشق", "https://3asq.org", "ar")

class Indiancomicsonline : Madara("Indian Comics Online", "http://www.indiancomicsonline.com", "hi")

class AdonisFansub : Madara("Adonis Fansub", "https://manga.adonisfansub.com", "tr") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=latest", headers)
}

class GetManhwa : Madara("GetManhwa", "https://getmanhwa.co", "en")

class AllPornComic : Madara("AllPornComic", "https://allporncomic.com", "en") {
    override val client: OkHttpClient = network.client
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.122 Safari/537.36"
    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", userAgent)
        .add("Referer", "$baseUrl/manga/?m_orderby=views")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=latest", headers)
    override fun searchMangaNextPageSelector() = "a[rel=next]"
    override fun getGenreList() = listOf(
        Genre("3D", "3d"),
        Genre("Hentai", "hentai"),
        Genre("Western", "western")
    )
}

class Milftoon : Madara("Milftoon", "https://milftoon.xxx", "en") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page/?m_orderby=latest", headers)
}

class Hiperdex : Madara("Hiperdex", "https://hiperdex.info", "en") {
    override fun getGenreList() = listOf(
        Genre("Adult", "adult"),
        Genre("Action", "action"),
        Genre("Adventure", "adventure"),
        Genre("Bully", "bully"),
        Genre("Comedy", "comedy"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasy", "fantasy"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mystery", "mystery"),
        Genre("Psychological", "psychological"),
        Genre("Romance", "romance"),
        Genre("School Life", "school-life"),
        Genre("Sci-Fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shounen", "shounen"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Sports", "sports"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri")
    )
}

class DoujinHentai : Madara("DoujinHentai", "https://doujinhentai.net", "es", SimpleDateFormat("d MMM. yyyy", Locale.ENGLISH)) {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/lista-manga-hentai?orderby=views&page=$page", headers)
    override fun popularMangaSelector() = "div.col-md-3 a"
    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.select("h5").text()
        manga.thumbnail_url = element.select("img").attr("abs:data-src")

        return manga
    }

    override fun popularMangaNextPageSelector() = "a[rel=next]"
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/lista-manga-hentai?orderby=last&page=$page", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse(baseUrl)!!.newBuilder()
        if (query.isNotBlank()) {
            url.addPathSegment("search")
            url.addQueryParameter("query", query) // query returns results all on one page
        } else {
            filters.forEach { filter ->
                when (filter) {
                    is GenreSelectFilter -> {
                        if (filter.state != 0) {
                            url.addPathSegments("lista-manga-hentai/category/${filter.toUriPart()}")
                            url.addQueryParameter("page", page.toString())
                        }
                    }
                }
            }
        }
        return GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector() = "div.c-tabs-item__content > div.c-tabs-item__content, ${popularMangaSelector()}"
    override fun searchMangaFromElement(element: Element): SManga {
        return if (element.hasAttr("href")) {
            popularMangaFromElement(element) // genre search results
        } else {
            super.searchMangaFromElement(element) // query search results
        }
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun chapterListSelector() = "ul.main.version-chap > li.wp-manga-chapter:not(:last-child)" // removing empty li
    override val pageListParseSelector = "div#all > img.img-responsive"
    override fun getFilterList() = FilterList(
        Filter.Header("Solo funciona si la consulta está en blanco"),
        GenreSelectFilter()
    )

    class GenreSelectFilter : UriPartFilter("Búsqueda de género", arrayOf(
        Pair("<seleccionar>", ""),
        Pair("Ecchi", "ecchi"),
        Pair("Yaoi", "yaoi"),
        Pair("Yuri", "yuri"),
        Pair("Anal", "anal"),
        Pair("Tetonas", "tetonas"),
        Pair("Escolares", "escolares"),
        Pair("Incesto", "incesto"),
        Pair("Virgenes", "virgenes"),
        Pair("Masturbacion", "masturbacion"),
        Pair("Maduras", "maduras"),
        Pair("Lolicon", "lolicon"),
        Pair("Bikini", "bikini"),
        Pair("Sirvientas", "sirvientas"),
        Pair("Enfermera", "enfermera"),
        Pair("Embarazada", "embarazada"),
        Pair("Ahegao", "ahegao"),
        Pair("Casadas", "casadas"),
        Pair("Chica Con Pene", "chica-con-pene"),
        Pair("Juguetes Sexuales", "juguetes-sexuales"),
        Pair("Orgias", "orgias"),
        Pair("Harem", "harem"),
        Pair("Romance", "romance"),
        Pair("Profesores", "profesores"),
        Pair("Tentaculos", "tentaculos"),
        Pair("Mamadas", "mamadas"),
        Pair("Shota", "shota"),
        Pair("Interracial", "interracial"),
        Pair("Full Color", "full-colo"),
        Pair("Sin Censura", "sin-censura"),
        Pair("Futanari", "futanari"),
        Pair("Doble Penetracion", "doble-penetracion"),
        Pair("Cosplay", "cosplay")
    )
    )
}

class Azora : Madara("Azora", "https://www.azoramanga.com", "ar") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page/?m_orderby=views", headers)
    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium-block)" // Filter fake chapters
    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        element.select("a").let {
            chapter.url = it.attr("href").substringAfter(baseUrl)
            chapter.name = it.text()
        }
        return chapter
    }
}

class KMangaIn : Madara("Kissmanga.in", "https://kissmanga.in", "en")

class HunterFansub : Madara("Hunter Fansub", "https://hunterfansub.com", "es") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/biblioteca/page/$page?m_orderby=views", headers)
    override fun popularMangaNextPageSelector() = "div.nav-previous"
    override val popularMangaUrlSelector = "div.post-title a:last-child"
}

class MangaArabTeam : Madara("مانجا عرب تيم Manga Arab Team", "https://mangaarabteam.com", "ar")

class NightComic : Madara("Night Comic", "https://nightcomic.com", "en") {
    override val formHeaders: Headers = headersBuilder()
        .add("Content-Type", "application/x-www-form-urlencoded")
        .add("X-MOD-SBB-CTYPE", "xhr")
        .build()
}

class Toonily : Madara("Toonily", "https://toonily.com", "en")

class PlotTwistScan : Madara("Plot Twist No Fansub", "https://www.plotwistscan.com", "es") {
    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).asReversed()
}

class MangaKomi : Madara("MangaKomi", "https://mangakomi.com", "en", SimpleDateFormat("MM/dd/yyyy", Locale.US))

class Wakamics : Madara("Wakamics", "https://wakamics.com", "en")

class TeabeerComics : Madara("Teabeer Comics", "https://teabeercomics.com", "en")

class KingzManga : Madara("KingzManga", "https://kingzmanga.com", "ar")

class YaoiToshokan : Madara("Yaoi Toshokan", "https://www.yaoitoshokan.com.br", "pt-BR") {
    override val popularMangaUrlSelector = "div.post-title a:not([target])" // Page has custom link to scan website
    override fun chapterListParse(response: Response): List<SChapter> { // Chapters are listed old to new
        return super.chapterListParse(response).reversed()
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListParseSelector).mapIndexed { index, element ->
            Page(index, "", element.select("img").attr("data-src").trim()) // had to add trim because of white space in source
        }
    }
}

class GoldenManga : Madara("موقع لترجمة المانجا", "https://golden-manga.ml", "ar", SimpleDateFormat("yyyy-MM-dd", Locale.US))

class Mangalek : Madara("مانجا ليك", "https://mangalek.com", "ar", SimpleDateFormat("MMMM dd, yyyy", Locale("ar")))

class AstralLibrary : Madara("Astral Library", "https://astrallibrary.net", "en") {
    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).reversed()
}

class NovelFrance : Madara("Novel France", "http://novel-france.fr", "fr", SimpleDateFormat("dd MMM yyyy", Locale.FRENCH))

class KlikManga : Madara("KlikManga", "https://klikmanga.com", "id", SimpleDateFormat("MMMM dd, yyyy", Locale("id")))

class MiracleScans : Madara("Miracle Scans", "https://miraclescans.com", "en")

class Manhuasnet : Madara("Manhuas.net", "https://manhuas.net", "en")

class MangaLaw : Madara("MangaLaw", "https://mangalaw.com", "ja", SimpleDateFormat("MM/dd/yyyy", Locale.US))

class MangaTX : Madara("MangaTX", "https://mangatx.com", "en")

class ATMSubs : Madara("ATM-Subs", "https://atm-subs.fr", "fr", SimpleDateFormat("d MMMM yyyy", Locale("fr")))

class OnManga : Madara("OnManga", "https://onmanga.com", "en")

class MangaAction : Madara("Manga Action", "https://manga-action.com", "ar", SimpleDateFormat("yyyy-MM-dd", Locale("ar")))

class NijiTranslations : Madara("Niji Translations", "https://niji-translations.com", "ar", SimpleDateFormat("MMMM dd, yyyy", Locale("ar")))

class IchirinNoHanaYuri : Madara("Ichirin No Hana Yuri", "https://ichirinnohanayuri.com.br", "pt-BR", SimpleDateFormat("dd/MM/yyyy", Locale("pt")))

class LilyManga : Madara("Lily Manga", "https://lilymanga.com", "en", SimpleDateFormat("yyyy-MM-dd", Locale.US))

class MangaBob : Madara("MangaBob", "https://mangabob.com", "en")

class ThreeSixtyFiveManga : Madara("365Manga", "https://365manga.com", "en") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=latest", headers)
}

class DisasterScans : Madara("Disaster Scans", "https://disasterscans.com", "en") {
    override val popularMangaUrlSelector = "div.post-title a:last-child"

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = super.mangaDetailsParse(document)

        with(document) {
            select("div.post-title h1").first()?.let {
                manga.title = it.ownText()
            }
        }

        return manga
    }
}

class MangaKiss : Madara("MangaKiss", "https://mangakiss.org", "en", SimpleDateFormat("dd/MM/yyyy", Locale.US)) {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", baseUrl)
}

class MangaDods : Madara("MangaDods", "https://www.mangadods.com", "en", SimpleDateFormat("dd/MM/yyyy", Locale.US))

class MangaStream : Madara("MangaStream", "https://www.mangastream.cc", "en") {
    override fun getGenreList() = listOf(
        Genre("Action", "action-manga"),
        Genre("Adventure", "adventure-manga"),
        Genre("Bara", "bara-manga"),
        Genre("BL Manga", "bl-manga"),
        Genre("Comedy", "comedy-manga"),
        Genre("Comics", "comics-online"),
        Genre("Completed Manga", "completed-manga"),
        Genre("Drama", "drama-manga"),
        Genre("Ecchi", "ecchi-manga"),
        Genre("Fantasy", "fantasy-manga"),
        Genre("Gender Bender", "gender-bender-manga"),
        Genre("Hardcore Yaoi", "hardcore-yaoi"),
        Genre("Harem", "harem-manga"),
        Genre("Hipercool", "hipercool"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror-manga"),
        Genre("Incest", "incest-manga"),
        Genre("Josei", "josei"),
        Genre("Lolicon", "lolicon-manga"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa-manga"),
        Genre("Manhwa Hentai Manga", "manhwahentai"),
        Genre("Martial Arts", "martial-arts-manga"),
        Genre("Mature", "mature-manga"),
        Genre("Mystery", "mystery"),
        Genre("One shot", "one-shot"),
        Genre("Psychological", "psychological-manga"),
        Genre("Rape", "rape-manga"),
        Genre("Reincarnation", "reincarnation-manga"),
        Genre("Reverse Harem", "reverse-harem"),
        Genre("Romance", "romance-manga"),
        Genre("School Life", "read-school-life-manga"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen-manga"),
        Genre("Shotacon", "shotacon"),
        Genre("Shoujo", "shoujo-manga"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen-manga"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut-manga"),
        Genre("Soft Yaoi", "soft-yaoi"),
        Genre("Soft Yuri", "soft-yuri"),
        Genre("Sports", "sports-manga"),
        Genre("Supernatural", "supernatural"),
        Genre("Tragedy", "tragedy"),
        Genre("Webtoon", "webtoons"),
        Genre("Yaoi", "yaoi-manga"),
        Genre("Yuri", "yuri-manga")
    )
}

class NeoxScanlator : Madara("Neox Scanlator", "https://neoxscan.com/newsite", "pt-BR", SimpleDateFormat("dd 'de' MMM 'de' yyyy", Locale("pt", "BR"))) {
    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Referer", baseUrl)
        .add("Origin", baseUrl)

    // Only status and order by filter work.
    override fun getFilterList(): FilterList = FilterList(super.getFilterList().slice(3..4))

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.87 Safari/537.36"
    }
}

class MangaLord : Madara("Manga Lord", "https://mangalord.com", "en")

class PornComix : Madara("PornComix", "https://www.porncomixonline.net", "en")

class MangaRead : Madara("Manga Read", "https://mangaread.co", "en", SimpleDateFormat("yyyy-MM-dd", Locale.US))

class UnknownScans : Madara("Unknown Scans", "https://unknoscans.com", "en")

class Manga68 : Madara("Manga68", "https://manga68.com", "en") {
    override val pageListParseSelector = "div.page-break, div.text-left p"
    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListParseSelector).mapIndexed { index, element ->
            Page(index, "", element.select("img").first()?.let {
                it.absUrl(/*if (it.hasAttr("data-lazy-src")) "data-lazy-src" else */if (it.hasAttr("data-src")) "data-src" else "src")
            })
        }.filter { it.imageUrl!!.startsWith("http") }
    }
}

class ManhuaBox : Madara("ManhuaBox", "https://manhuabox.net", "en") {
    override val pageListParseSelector = "div.page-break, div.text-left p img"
}

class RaiderScans : Madara("Raider Scans", "https://raiderscans.com", "en")

class PojokManga : Madara("Pojok Manga", "https://pojokmanga.com", "id", SimpleDateFormat("MMM dd, yyyy", Locale.US))

class TopManhua : Madara("Top Manhua", "https://topmanhua.com", "en", SimpleDateFormat("MM/dd/yy", Locale.US)) {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", baseUrl)
}

class ManyToonClub : Madara("ManyToonClub", "https://manytoon.club", "ko")

class ManhuaUS : Madara("ManhuaUS", "https://manhuaus.com", "en") {
    override val pageListParseSelector = "li.blocks-gallery-item"
}

class MangaWT : Madara("MangaWT", "https://mangawt.com", "tr")

class DecadenceScans : Madara("Decadence Scans", "https://reader.decadencescans.com", "en")

class MangaRockTeam : Madara("Manga Rock Team", "https://mangarockteam.com", "en")

class MixedManga : Madara("Mixed Manga", "https://mixedmanga.com", "en") {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", baseUrl)
}

class ManhuasWorld : Madara("Manhuas World", "https://manhuasworld.com", "en")

class ArazNovel : Madara("ArazNovel", "https://www.araznovel.com", "tr", SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())) {
    override fun formBuilder(page: Int, popular: Boolean): FormBody.Builder = super.formBuilder(page, popular)
        .add("vars[meta_query][0][0][value]", "manga")
    override fun getGenreList() = listOf(
        Genre("Aksiyon", "action"),
        Genre("Macera", "adventure"),
        Genre("Cartoon", "cartoon"),
        Genre("Comic", "comic"),
        Genre("Komedi", "comedy"),
        Genre("Yemek", "cooking"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Dram", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantastik", "fantasy"),
        Genre("Harem", "harem"),
        Genre("Tarihi", "historical"),
        Genre("Korku", "horror"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Olgun", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Yetişkin", "adult"),
        Genre("Gizem", "mystery"),
        Genre("One Shot", "one-shot"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Dedektif", "detective"),
        Genre("Karanlık", "smut"),
        Genre("Romantizm", "romance"),
        Genre("Okul Yaşamı", "school-life"),
        Genre("Yaşamdan Kesit", "slice-of-life"),
        Genre("Spor", "sports"),
        Genre("Doğa Üstü", "supernatural"),
        Genre("Trajedi", "tragedy"),
        Genre("Webtoon ", "webtoon"),
        Genre("Dövüş Sanatları ", "martial-arts"),
        Genre("Bilim Kurgu", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Soft Yaoi", "soft-yaoi"),
        Genre("Soft Yuri", "soft-yuri"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri")
		    )
    override fun chapterListParse(response: Response): List<SChapter> {
        return getXhrChapters(response.asJsoup().select("div#manga-chapters-holder").attr("data-id")).let { document ->
            document.select("li.parent").let { elements ->
                if (!elements.isNullOrEmpty()) {
                    elements.reversed()
                        .map { volumeElement -> volumeElement.select(chapterListSelector()).map { chapterFromElement(it) } }
                        .flatten()
                } else {
                    document.select(chapterListSelector()).map { chapterFromElement(it) }
                }
            }
        }
    }
}

class MangaByte : Madara("Manga Byte", "https://mangabyte.com", "en")

class ManhwaRaw : Madara("Manhwa Raw", "https://manhwaraw.com", "ko")

class GuncelManga : Madara("GuncelManga", "https://guncelmanga.com", "tr")

class WeScans : Madara("WeScans", "https://wescans.xyz", "en") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manhua/manga/?m_orderby=views", headers)
    override fun popularMangaNextPageSelector(): String? = null
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manhua/manga/?m_orderby=latest", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/manhua/?s=$query&post_type=wp-manga")
    override fun searchMangaNextPageSelector(): String? = null
    override fun getFilterList(): FilterList = FilterList()
}

class ArangScans : Madara("Arang Scans", "https://www.arangscans.xyz", "en")

class MangaHentai : Madara("Manga Hentai", "https://mangahentai.me", "en")

class MangaPhoenix : Madara("Manga Phoenix", "https://mangaphoenix.com", "tr")

class FirstKissManhua : Madara("1st Kiss Manhua", "https://1stkissmanhua.com", "en", SimpleDateFormat("d MMM yyyy", Locale.US)) {
    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headersBuilder().add("Referer", "https://1stkissmanga.com").build())
}

class HeroManhua : Madara("Hero Manhua", "https://heromanhua.com", "en")

class MartialScans : Madara("Martial Scans", "https://martialscans.com", "en") {
    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        with(element) {
            select(popularMangaUrlSelector).last()?.let {
                manga.setUrlWithoutDomain(it.attr("href"))
                manga.title = it.ownText()
            }

            select("img").last()?.let {
                manga.thumbnail_url = imageFromElement(it)
            }
        }

        return manga
    }
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
}

class MangaYosh : Madara("MangaYosh", "https://mangayosh.xyz", "id", SimpleDateFormat("dd MMM yyyy", Locale.US))

class Reisubs : Madara("Reisubs", "https://www.reisubs.xyz", "en")

class MangaReadOrg : Madara("MangaRead.org", "https://www.mangaread.org", "en", SimpleDateFormat("dd.MM.yyy", Locale.US))

class TurkceManga : Madara("Türkçe Manga", "https://turkcemanga.com", "tr")
