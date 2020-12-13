package eu.kanade.tachiyomi.extension.zh.manhuadui

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Manhuadui : ParsedHttpSource() {

    override val name = "漫画堆"
    override val baseUrl = "https://www.manhuadai.com"
    override val lang = "zh"
    override val supportsLatest = true
    private val imageServer = arrayOf("https://mhcdn.manhuazj.com", "https://res.333dm.com", "https://res02.333dm.com")

    companion object {
        private const val DECRYPTION_KEY = "KA58ZAQ321oobbG8"
        private const val DECRYPTION_IV = "A1B2C3DEF1G321o8"
    }

    override val client: OkHttpClient = super.client.newBuilder()
        .followRedirects(true)
        .build()

    override fun popularMangaSelector() = "li.list-comic"
    override fun searchMangaSelector() = popularMangaSelector()
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun chapterListSelector() = "ul[id^=chapter-list] > li a"

    override fun searchMangaNextPageSelector() = "li.next"
    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()
    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/list_$page/", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/update/$page/", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query != "") {
            val url = HttpUrl.parse("$baseUrl/search/?keywords=$query")?.newBuilder()
            GET(url.toString(), headers)
        } else {
            val params = filters.map {
                if (it is UriPartFilter) {
                    it.toUriPart()
                } else ""
            }.filter { it != "" }.joinToString("-")
            val url = HttpUrl.parse("$baseUrl/list/$params/$page/")?.newBuilder()
            GET(url.toString(), headers)
        }
    }

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)
    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a.comic_img").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.select("img").attr("alt").trim()
            manga.thumbnail_url = if (it.select("img").attr("src").trim().indexOf("http") == -1)
                "https:${it.select("img").attr("src").trim()}"
            else it.select("img").attr("src").trim()
        }
        manga.author = element.select("span.comic_list_det > p").first()?.text()?.substring(3)
        return manga
    }

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val els = element.select("a.image-link")
        if (els.size == 0) {
            element.select("li.list-comic").first().let {
                manga.setUrlWithoutDomain(it.select("a").attr("href"))
                manga.title = it.select("span").attr("title").trim()
                manga.thumbnail_url = it.select("a > img").attr("src").trim()
                manga.author = it.select("span > p").first().text().split("：")[1].trim()
            }
        } else {
            element.select("a.image-link").first().let {
                manga.setUrlWithoutDomain(it.attr("href"))
                manga.title = it.attr("title").trim()
                manga.thumbnail_url = it.select("img").attr("src").trim()
            }
            manga.author = element.select("p.auth").text().trim()
        }
        return manga
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.attr("href"))
        chapter.name = element.select("span:first-child").text().trim()
        return chapter
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.description = document.select("p.comic_deCon_d").text().trim()
        manga.thumbnail_url = document.select("div.comic_i_img > img").attr("src")
        return manga
    }

    override fun chapterListRequest(manga: SManga) = GET(baseUrl.replace("www", "m") + manga.url)
    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).asReversed()
    }

    // ref: https://jueyue.iteye.com/blog/1830792
    private fun decryptAES(value: String): String? {
        return try {
            val secretKey = SecretKeySpec(DECRYPTION_KEY.toByteArray(), "AES")
            val ivParams = IvParameterSpec(DECRYPTION_IV.toByteArray())
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParams)

            val code = Base64.decode(value, Base64.NO_WRAP)

            String(cipher.doFinal(code))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private val chapterImagesRegex = Regex("""var chapterImages =\s*"(.*?)";""")
    private val imgPathRegex = Regex("""var chapterPath =\s*"(.*?)";""")
    private val imgCodeCleanupRegex = Regex("""[\[\]"\\]""")

    override fun pageListParse(document: Document): List<Page> {
        val html = document.html()
        val imgCodeStr = chapterImagesRegex.find(html)?.groups?.get(1)?.value ?: throw Exception("imgCodeStr not found")
        val imgCode = decryptAES(imgCodeStr)
            ?.replace(imgCodeCleanupRegex, "")
            ?.replace("%", "%25")
            ?: throw Exception("Decryption failed")
        val imgPath = imgPathRegex.find(document.html())?.groups?.get(1)?.value ?: throw Exception("imgPath not found")
        return imgCode.split(",").mapIndexed { i, imgStr ->
            if (imgStr.startsWith("http://images.dmzj.com")) {
                Page(i, "", "https://img01.eshanyao.com/showImage.php?url=$imgStr")
            } else {
                Page(i, "", if (imgStr.indexOf("http") == -1) "${imageServer[0]}/$imgPath$imgStr" else imgStr)
            }
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList(
        CategoryGroup(),
        RegionGroup(),
        GenreGroup(),
        ProgressGroup()
    )

    private class CategoryGroup : UriPartFilter(
        "按类型",
        arrayOf(
            Pair("全部", ""),
            Pair("儿童漫画", "ertong"),
            Pair("少年漫画", "shaonian"),
            Pair("少女漫画", "shaonv"),
            Pair("青年漫画", "qingnian")
        )
    )

    private class ProgressGroup : UriPartFilter(
        "按进度",
        arrayOf(
            Pair("全部", ""),
            Pair("已完结", "wanjie"),
            Pair("连载中", "lianzai")
        )
    )

    private class RegionGroup : UriPartFilter(
        "按地区",
        arrayOf(
            Pair("全部", ""),
            Pair("日本", "riben"),
            Pair("大陆", "dalu"),
            Pair("香港", "hongkong"),
            Pair("台湾", "taiwan"),
            Pair("欧美", "oumei"),
            Pair("韩国", "hanguo"),
            Pair("其他", "qita")
        )
    )

    private class GenreGroup : UriPartFilter(
        "按剧情",
        arrayOf(
            Pair("全部", ""),
            Pair("热血", "rexue"),
            Pair("冒险", "maoxian"),
            Pair("玄幻", "xuanhuan"),
            Pair("搞笑", "gaoxiao"),
            Pair("恋爱", "lianai"),
            Pair("宠物", "chongwu"),
            Pair("新作", "xinzuo")
        )
    )

    private open class UriPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
        defaultValue: Int = 0
    ) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue) {
        open fun toUriPart() = vals[state].second
    }
}
