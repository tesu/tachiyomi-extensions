package eu.kanade.tachiyomi.extension.all.mangaplus

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.protobuf.ProtoNumber

@Serializer(forClass = MangaPlusResponse::class)
object MangaPlusSerializer

@Serializable
data class MangaPlusResponse(
    @ProtoNumber(1) val success: SuccessResult? = null,
    @ProtoNumber(2) val error: ErrorResult? = null
)

@Serializable
data class ErrorResult(
    @ProtoNumber(1) val action: Action,
    @ProtoNumber(2) val englishPopup: Popup,
    @ProtoNumber(3) val spanishPopup: Popup
)

enum class Action { DEFAULT, UNAUTHORIZED, MAINTAINENCE, GEOIP_BLOCKING }

@Serializable
data class Popup(
    @ProtoNumber(1) val subject: String,
    @ProtoNumber(2) val body: String
)

@Serializable
data class SuccessResult(
    @ProtoNumber(1) val isFeaturedUpdated: Boolean? = false,
    @ProtoNumber(5) val allTitlesView: AllTitlesView? = null,
    @ProtoNumber(6) val titleRankingView: TitleRankingView? = null,
    @ProtoNumber(8) val titleDetailView: TitleDetailView? = null,
    @ProtoNumber(10) val mangaViewer: MangaViewer? = null,
    @ProtoNumber(11) val webHomeView: WebHomeView? = null
)

@Serializable
data class TitleRankingView(@ProtoNumber(1) val titles: List<Title> = emptyList())

@Serializable
data class AllTitlesView(@ProtoNumber(1) val titles: List<Title> = emptyList())

@Serializable
data class WebHomeView(@ProtoNumber(2) val groups: List<UpdatedTitleGroup> = emptyList())

@Serializable
data class TitleDetailView(
    @ProtoNumber(1) val title: Title,
    @ProtoNumber(2) val titleImageUrl: String,
    @ProtoNumber(3) val overview: String,
    @ProtoNumber(4) val backgroundImageUrl: String,
    @ProtoNumber(5) val nextTimeStamp: Int = 0,
    @ProtoNumber(6) val updateTiming: UpdateTiming? = UpdateTiming.DAY,
    @ProtoNumber(7) val viewingPeriodDescription: String = "",
    @ProtoNumber(8) val nonAppearanceInfo: String = "",
    @ProtoNumber(9) val firstChapterList: List<Chapter> = emptyList(),
    @ProtoNumber(10) val lastChapterList: List<Chapter> = emptyList(),
    @ProtoNumber(14) val isSimulReleased: Boolean = true,
    @ProtoNumber(17) val chaptersDescending: Boolean = true
)

enum class UpdateTiming { NOT_REGULARLY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY, DAY }

@Serializable
data class MangaViewer(@ProtoNumber(1) val pages: List<MangaPlusPage> = emptyList())

@Serializable
data class Title(
    @ProtoNumber(1) val titleId: Int,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val author: String,
    @ProtoNumber(4) val portraitImageUrl: String,
    @ProtoNumber(5) val landscapeImageUrl: String,
    @ProtoNumber(6) val viewCount: Int = 0,
    @ProtoNumber(7) val language: Language? = Language.ENGLISH
)

@Serializable
enum class Language(val id: Int) {
    @ProtoNumber(0)
    @SerializedName("0")
    ENGLISH(0),

    @ProtoNumber(1)
    @SerializedName("1")
    SPANISH(1)
}

@Serializable
data class UpdatedTitleGroup(
    @ProtoNumber(1) val groupName: String,
    @ProtoNumber(2) val titles: List<UpdatedTitle> = emptyList()
)

@Serializable
data class UpdatedTitle(
    @ProtoNumber(1) val title: Title? = null
)

@Serializable
data class Chapter(
    @ProtoNumber(1) val titleId: Int,
    @ProtoNumber(2) val chapterId: Int,
    @ProtoNumber(3) val name: String,
    @ProtoNumber(4) val subTitle: String? = null,
    @ProtoNumber(6) val startTimeStamp: Int,
    @ProtoNumber(7) val endTimeStamp: Int
)

@Serializable
data class MangaPlusPage(@ProtoNumber(1) val page: MangaPage? = null)

@Serializable
data class MangaPage(
    @ProtoNumber(1) val imageUrl: String,
    @ProtoNumber(2) val width: Int,
    @ProtoNumber(3) val height: Int,
    @ProtoNumber(5) val encryptionKey: String? = null
)

// Used for the deserialization on older devices.
const val DECODE_SCRIPT: String =
    """
    var protobuf = module.exports;

    var Root = protobuf.Root;
    var Type = protobuf.Type;
    var Field = protobuf.Field;
    var Enum = protobuf.Enum;
    var OneOf = protobuf.OneOf;

    var Response = new Type("Response")
        .add(
            new OneOf("data")
                .add(new Field("success", 1, "SuccessResult"))
                .add(new Field("error", 2, "ErrorResult"))
        );

    var ErrorResult = new Type("ErrorResult")
        .add(new Field("action", 1, "Action"))
        .add(new Field("englishPopup", 2, "Popup"))
        .add(new Field("spanishPopup", 3, "Popup"));

    var Action = new Enum("Action")
        .add("DEFAULT", 0)
        .add("UNAUTHORIZED", 1)
        .add("MAINTAINENCE", 2)
        .add("GEOIP_BLOCKING", 3);

    var Popup = new Type("Popup")
        .add(new Field("subject", 1, "string"))
        .add(new Field("body", 2, "string"));

    var SuccessResult = new Type("SuccessResult")
        .add(new Field("isFeaturedUpdated", 1, "bool"))
        .add(
              new OneOf("data")
                  .add(new Field("allTitlesView", 5, "AllTitlesView"))
                  .add(new Field("titleRankingView", 6, "TitleRankingView"))
                  .add(new Field("titleDetailView", 8, "TitleDetailView"))
                  .add(new Field("mangaViewer", 10, "MangaViewer"))
                  .add(new Field("webHomeView", 11, "WebHomeView"))
          );

    var TitleRankingView = new Type("TitleRankingView")
        .add(new Field("titles", 1, "Title", "repeated"));

    var AllTitlesView = new Type("AllTitlesView")
        .add(new Field("titles", 1, "Title", "repeated"));

    var WebHomeView = new Type("WebHomeView")
        .add(new Field("groups", 2, "UpdatedTitleGroup", "repeated"));

    var TitleDetailView = new Type("TitleDetailView")
        .add(new Field("title", 1, "Title"))
        .add(new Field("titleImageUrl", 2, "string"))
        .add(new Field("overview", 3, "string"))
        .add(new Field("backgroundImageUrl", 4, "string"))
        .add(new Field("nextTimeStamp", 5, "uint32"))
        .add(new Field("updateTiming", 6, "UpdateTiming"))
        .add(new Field("viewingPeriodDescription", 7, "string"))
        .add(new Field("nonAppearanceInfo", 8, "string", {"default": ""}))
        .add(new Field("firstChapterList", 9, "Chapter", "repeated"))
        .add(new Field("lastChapterList", 10, "Chapter", "repeated"))
        .add(new Field("isSimulReleased", 14, "bool"))
        .add(new Field("chaptersDescending", 17, "bool"));

    var UpdateTiming = new Enum("UpdateTiming")
        .add("NOT_REGULARLY", 0)
        .add("MONDAY", 1)
        .add("TUESDAY", 2)
        .add("WEDNESDAY", 3)
        .add("THURSDAY", 4)
        .add("FRIDAY", 5)
        .add("SATURDAY", 6)
        .add("SUNDAY", 7)
        .add("DAY", 8);

    var MangaViewer = new Type("MangaViewer")
        .add(new Field("pages", 1, "Page", "repeated"));

    var Title = new Type("Title")
        .add(new Field("titleId", 1, "uint32"))
        .add(new Field("name", 2, "string"))
        .add(new Field("author", 3, "string"))
        .add(new Field("portraitImageUrl", 4, "string"))
        .add(new Field("landscapeImageUrl", 5, "string"))
        .add(new Field("viewCount", 6, "uint32", {"default": 0}))
        .add(new Field("language", 7, "Language", {"default": 0}));

    var Language = new Enum("Language")
        .add("ENGLISH", 0)
        .add("SPANISH", 1);

    var UpdatedTitleGroup = new Type("UpdatedTitleGroup")
        .add(new Field("groupName", 1, "string"))
        .add(new Field("titles", 2, "UpdatedTitle", "repeated"));

    var UpdatedTitle = new Type("UpdatedTitle")
        .add(new Field("title", 1, "Title"))
        .add(new Field("chapterId", 2, "uint32"))
        .add(new Field("chapterName", 3, "string"))
        .add(new Field("chapterSubtitle", 4, "string"));

    var Chapter = new Type("Chapter")
        .add(new Field("titleId", 1, "uint32"))
        .add(new Field("chapterId", 2, "uint32"))
        .add(new Field("name", 3, "string"))
        .add(new Field("subTitle", 4, "string", "optional"))
        .add(new Field("startTimeStamp", 6, "uint32"))
        .add(new Field("endTimeStamp", 7, "uint32"));

    var Page = new Type("Page")
        .add(new Field("page", 1, "MangaPage"));

    var MangaPage = new Type("MangaPage")
        .add(new Field("imageUrl", 1, "string"))
        .add(new Field("width", 2, "uint32"))
        .add(new Field("height", 3, "uint32"))
        .add(new Field("encryptionKey", 5, "string", "optional"));

    var root = new Root()
        .define("mangaplus")
        .add(Response)
        .add(ErrorResult)
        .add(Action)
        .add(Popup)
        .add(SuccessResult)
        .add(TitleRankingView)
        .add(AllTitlesView)
        .add(WebHomeView)
        .add(TitleDetailView)
        .add(UpdateTiming)
        .add(MangaViewer)
        .add(Title)
        .add(Language)
        .add(UpdatedTitleGroup)
        .add(UpdatedTitle)
        .add(Chapter)
        .add(Page)
        .add(MangaPage);

    function decode(arr) {
        var Response = root.lookupType("Response");
        var message = Response.decode(arr);
        return Response.toObject(message, {defaults: true});
    }

    (function () {
        return JSON.stringify(decode(BYTE_ARR)).replace(/\,\{\}/g, "");
    })();
    """
