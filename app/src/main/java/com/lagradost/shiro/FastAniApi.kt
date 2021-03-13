package com.lagradost.shiro

import android.annotation.SuppressLint
import android.provider.Settings
import android.util.Base64.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.shiro.MainActivity.Companion.activity
import com.lagradost.shiro.MainActivity.Companion.md5
import khttp.structures.cookie.CookieJar
import org.jsoup.Jsoup
import java.lang.Exception
import java.net.URLEncoder
import kotlin.concurrent.thread

class FastAniApi {
    data class HomePageResponse(
        @JsonProperty("animeData") val animeData: AnimeData,
        @JsonProperty("homeSlidesData") val homeSlidesData: List<Card>,
        @JsonProperty("recentlyAddedData") val recentlyAddedData: List<Card>,
        @JsonProperty("trendingData") val trendingData: List<Card>,
        @JsonProperty("favorites") var favorites: List<BookmarkedTitle?>?,
        @JsonProperty("recentlySeen") var recentlySeen: List<LastEpisodeInfo?>?,
        @JsonProperty("schedule") var schedule: List<ScheduleItem?>?,
    )

    data class Token(
        @JsonProperty("headers") val headers: Map<String, String>,
        @JsonProperty("cookies") val cookies: CookieJar,
        @JsonProperty("token") val token: String,
    )

    data class Title(
        @JsonProperty("romaji") val romaji: String,
        @JsonProperty("english") val english: String,
        @JsonProperty("native") val native: String
    )

    data class ScheduleTitle(
        @JsonProperty("romaji") val romaji: String?,
        @JsonProperty("english") val english: String?,
        @JsonProperty("native") val native: String?
    )

    data class EndDate(
        @JsonProperty("year") val year: Int,
        @JsonProperty("month") val month: Int,
        @JsonProperty("day") val day: Int
    )

    data class FullEpisode(
        @JsonProperty("file") val file: String,
        @JsonProperty("title") val title: String?,
        @JsonProperty("thumb") val thumb: String?
    )

    data class Episode(@JsonProperty("file") val file: String)
    data class CoverImage(@JsonProperty("large") val large: String)
    data class Seasons(@JsonProperty("episodes") val episodes: List<FullEpisode>)
    data class CdnData(@JsonProperty("seasons") val seasons: List<Seasons>)
    data class Card(
        @JsonProperty("title") val title: Title,
        @JsonProperty("endDate") val endDate: EndDate,
        @JsonProperty("episodes") val episodes: Int,
        @JsonProperty("duration") val duration: Int,
        @JsonProperty("trailer") val trailer: String?,
        @JsonProperty("averageScore") val averageScore: Int,
        @JsonProperty("isAdult") val isAdult: Boolean,
        @JsonProperty("status") val status: String,
        @JsonProperty("coverImage") val coverImage: CoverImage,
        @JsonProperty("bannerImage") val bannerImage: String,
        @JsonProperty("anilistId") val anilistId: String,
        @JsonProperty("id") val id: String,
        @JsonProperty("description") val description: String,
        @JsonProperty("cdnData") val cdnData: CdnData,
        @JsonProperty("genres") val genres: List<String>,
    )

    data class ScheduleItem(
        @JsonProperty("episode") val episode: Int,
        @JsonProperty("timeUntilAiring") val timeUntilAiring: String,
        @JsonProperty("media") val media: ScheduleMediaItem
    )


    data class ScheduleMediaItem(
        @JsonProperty("averageScore") val averageScore: Int?,
        @JsonProperty("id") val id: Int,
        @JsonProperty("coverImage") val coverImage: CoverImage,
        @JsonProperty("title") val title: ScheduleTitle,
    )

    data class AnimeData(@JsonProperty("cards") val cards: List<Card>)
    data class SearchResponse(
        @JsonProperty("animeData") val animeData: AnimeData?,
        @JsonProperty("success") val success: Boolean
    )

    data class EpisodeResponse(
        @JsonProperty("anime") val anime: Card,
        @JsonProperty("nextEpisode") val nextEpisode: Int
    )

    data class Update(
        @JsonProperty("shouldUpdate") val shouldUpdate: Boolean,
        @JsonProperty("updateURL") val updateURL: String?,
        @JsonProperty("updateVersion") val updateVersion: String?
    )

    data class Donor(@JsonProperty("id") val id: String)

    data class ShiroSearchResponseShow(
        @JsonProperty("image") val image: String,
        @JsonProperty("_id") val _id: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("name") val name: String,
    )

    data class ShiroHomePageData(
        @JsonProperty("trending_animes") val trending_animes: List<AnimePageData>,
        @JsonProperty("ongoing_animes") val ongoing_animes: List<AnimePageData>,
        @JsonProperty("latest_animes") val latest_animes: List<AnimePageData>,
        @JsonProperty("latest_episodes") val latest_episodes: List<ShiroEpisodes>,

    )

    data class ShiroHomePage(
        @JsonProperty("status") val status: String,
        @JsonProperty("data") val data: ShiroHomePageData,
        @JsonProperty("random") var random: AnimePage?,
        @JsonProperty("favorites") var favorites: List<BookmarkedTitle?>?,
        @JsonProperty("recentlySeen") var recentlySeen: List<LastEpisodeInfo?>?
    )


    data class ShiroSearchResponse(
        @JsonProperty("data") val data: List<ShiroSearchResponseShow>,
        @JsonProperty("status") val status: String
    )

    data class ShiroFullSearchResponseCurrentPage(
        @JsonProperty("items") val items: List<ShiroSearchResponseShow>
    )

    data class ShiroFullSearchResponseNavItems(
        @JsonProperty("currentPage") val currentPage: ShiroFullSearchResponseCurrentPage
    )

    data class ShiroFullSearchResponseNav(
        @JsonProperty("nav") val nav: ShiroFullSearchResponseNavItems
    )

    data class ShiroFullSearchResponse(
        @JsonProperty("data") val data: ShiroFullSearchResponseNav,
        @JsonProperty("status") val status: String
    )

    data class ShiroVideo(
        @JsonProperty("video_id") val video_id: String,
        @JsonProperty("host") val host: String,
    )

    data class ShiroEpisodes(
        @JsonProperty("anime") val anime: AnimePageData?,
        @JsonProperty("anime_slug") val anime_slug: String,
        @JsonProperty("create") val create: String,
        @JsonProperty("dayOfTheWeek") val dayOfTheWeek: String,
        @JsonProperty("episode_number") val episode_number: Int,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("update") val update: String,
        @JsonProperty("_id") val _id: String,
        @JsonProperty("videos") val videos: List<ShiroVideo>
    )

    data class AnimePageData(
        @JsonProperty("banner") val banner: String?,
        @JsonProperty("canonicalTitle") val canonicalTitle: String?,
        @JsonProperty("episodeCount") val episodeCount: String,
        @JsonProperty("genres") val genres: List<String>?,
        @JsonProperty("image") val image: String,
        @JsonProperty("japanese") val japanese: String?,
        @JsonProperty("language") val language: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("synopsis") val synopsis: String,
        @JsonProperty("type") val type: String?,
        @JsonProperty("views") val views: Int?,
        @JsonProperty("year") val year: String?,
        @JsonProperty("_id") val _id: String,
        @JsonProperty("episodes") val episodes: List<ShiroEpisodes>?,
        @JsonProperty("status") val status: String?,
    )

    data class AnimePage(
        @JsonProperty("data") val data: AnimePageData,
        @JsonProperty("status") val status: String
    )

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:68.0) Gecko/20100101 Firefox/68.0"
        private val mapper = JsonMapper.builder().addModule(KotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

        // NULL IF ERROR
        private fun getToken(): Token? {
            try {
                val headers = mapOf("User-Agent" to USER_AGENT)
                val shiro = khttp.get("https://shiro.is", headers = headers)

                val jsMatch = Regex("""src="(/static/js/main.*?)"""").find(shiro.text)
                val (destructed) = jsMatch!!.destructured
                val jsLocation = "https://shiro.is$destructed"
                val js = khttp.get(jsLocation, headers = headers)
                val tokenMatch = Regex("""token:"(.*?)"""").find(js.text)
                val (token) = tokenMatch!!.destructured
                val tokenHeaders = mapOf(
                    "User-Agent" to USER_AGENT
                )
                return Token(
                    tokenHeaders,
                    shiro.cookies,
                    token
                )
            } catch (e: Exception) {
                println(e)
                return null
            }
        }

        fun getAppUpdate(): Update {
            try {
                val url = "https://cdn1.fastani.net/apk/"
                val response = khttp.get(url)
                val versionRegex = Regex("""href="(.*?((\d)\.(\d)\.(\d)).*\.apk)"""")
                val found =
                    versionRegex.findAll(response.text).sortedWith(compareBy {
                        it.groupValues[2]
                    }).toList().lastOrNull()
                val currentVersion = activity?.packageName?.let { activity?.packageManager?.getPackageInfo(it, 0) }
                //println(found.groupValues)
                //println(currentVersion?.versionName)

                val shouldUpdate =
                    if (found != null) currentVersion?.versionName?.compareTo(found.groupValues[2])!! < 0 else false
                return Update(shouldUpdate, url + found?.groupValues?.get(1), found?.groupValues?.get(2))

            } catch (e: Exception) {
                println(e)
                return Update(false, "", "")
            }
        }

        @SuppressLint("HardwareIds")
        // Developers please do not share an apk with donor mode enabled for all as fastani relies on donors to keep the site alive and ad-free.
        fun getDonorStatus(): String {
            val url = "https://raw.githubusercontent.com/Blatzar/donors/master/donors.json"
            try {
                val androidId: String =
                    Settings.Secure.getString(activity?.contentResolver, Settings.Secure.ANDROID_ID)
                // Change cache with this
                // , headers = mapOf("Cache-Control" to "max-age=60")
                val response = khttp.get(url).text
                val users = mapper.readValue<List<Donor>>(response)
                users.forEach {
                    try {
                        if (androidId.md5() == it.id || it.id == "all") {
                            return androidId.md5()
                        }
                    } catch (e: Exception) {
                        return@forEach
                    }
                }
                return ""
            } catch (e: Exception) {
                return ""
            }
        }


        fun getVideoLink(id: String): String? {
            return try {
                val res = khttp.get("https://ani.googledrive.stream/vidstreaming/vid-ad/$id").text
                val document = Jsoup.parse(res)
                val url = document.select("source").firstOrNull()?.attr("src")
                url
            } catch (e: Exception) {
                println("Failed to load video URL")
                null
            }
        }

        fun getRandomAnimePage(): AnimePage? {
            println("TOKEN ${currentToken?.token}")
            val url = "https://ani.api-web.site/anime/random/TV?token=${currentToken?.token}"
            val response = khttp.get(url)
            println(response.text)
            val mapped = response.let { mapper.readValue<AnimePage>(it.text) }
            return if (mapped.status == "Found")
                mapped
            else null
        }

        fun getAnimePage(show: ShiroSearchResponseShow): AnimePage? {
            val url = "https://ani.api-web.site/anime/slug/${show.slug}?token=${currentToken?.token}"
            return try {
                val response = khttp.get(url)
                val mapped = response.let { mapper.readValue<AnimePage>(it.text) }
                if (mapped.status == "Found")
                    mapped
                else null
            } catch (e: Exception) {
                null
            }
        }

        /*Overloaded function to get animepage for the bookmarked title*/
        fun getAnimePage(show: BookmarkedTitle): AnimePage? {
            val url = "https://ani.api-web.site/anime/slug/${show.slug}?token=${currentToken?.token}"
            return try {
                val response = khttp.get(url)
                val mapped = response.let { mapper.readValue<AnimePage>(it.text) }
                if (mapped.status == "Found")
                    mapped
                else null
            } catch (e: Exception) {
                null
            }
        }

        // TODO MAKE THIS ONCE FUNCTION
        fun getAnimePage(slug: String): AnimePage? {
            val url = "https://ani.api-web.site/anime/slug/${slug}?token=${currentToken?.token}"
            return try {
                val response = khttp.get(url)
                val mapped = response.let { mapper.readValue<AnimePage>(it.text) }
                if (mapped.status == "Found")
                    mapped
                else null
            } catch (e: Exception) {
                println(e.message)
                null
            }
        }

        //search via http get request, NOT INSTANT
        // ONLY PAGE 1
        fun quickSearch(query: String): List<ShiroSearchResponseShow>? {
            try {
                // Tags and years can be added
                val url = "https://ani.api-web.site/anime/auto-complete/${
                    URLEncoder.encode(
                        query,
                        "UTF-8"
                    )
                }?token=${currentToken?.token}".replace("+", "%20")
                println(url)
                // Security headers
                val headers = currentToken?.headers
                val response = headers?.let { khttp.get(url) }
                val mapped = response?.let { mapper.readValue<ShiroSearchResponse>(it.text) }
                return if (mapped?.status == "Found")
                    mapped.data
                else null
            } catch (e: Exception) {
                return null
            }
            //return response?.text?.let { mapper.readValue(it) }
        }

        fun search(query: String): List<ShiroSearchResponseShow>? {
            try {
                val url = "https://ani.api-web.site/advanced?search=${
                    URLEncoder.encode(
                        query,
                        "UTF-8"
                    )
                }&token=${currentToken?.token}".replace("+", "%20")
                val headers = currentToken?.headers
                val response = headers?.let { khttp.get(url) }
                println(response?.text)
                val mapped = response?.let { mapper.readValue<ShiroFullSearchResponse>(it.text) }
                return if (mapped?.status == "Found")
                    mapped.data.nav.currentPage.items
                else null
            } catch (e: Exception) {
                return null
            }
        }

        fun getFullUrl(url: String): String {
            return if (!url.startsWith("http")) {
                "https://ani-cdn.api-web.site/$url"
            } else url
        }

        /*val lastCards = hashMapOf<String, Card>()
        fun getCardById(id: String, canBeCached: Boolean = true): EpisodeResponse? {
            if (canBeCached && lastCards.containsKey(id)) {
                return EpisodeResponse(lastCards[id]!!, 0)
            }
            val url =
                "https://fastani.net/api/data/anime/$id" //?season=$season&episode=$episode" // SPECIFYING EPISODE AND SEASON WILL ONLY GIVE 1 EPISODE
            val response = currentToken?.headers?.let { khttp.get(url, headers = it, cookies = currentToken?.cookies) }
            val resp: EpisodeResponse? = response?.text?.let { mapper.readValue(it) }
            if (resp != null) {
                lastCards[id] = resp.anime
            }
            return resp
        }*/

        var cachedHome: ShiroHomePage? = null

        private fun getFav(): List<BookmarkedTitle?> {
            val keys = DataStore.getKeys(BOOKMARK_KEY)
            thread {
                keys.pmap {
                    DataStore.getKey<BookmarkedTitle>(it)
                }
            }

            return keys.map {
                DataStore.getKey<BookmarkedTitle>(it)
            }
        }

        private fun getLastWatch(): List<LastEpisodeInfo?> {
            val keys = DataStore.getKeys(VIEW_LST_KEY)
            println("KEYS: $keys")
            thread {
                keys.pmap {
                    DataStore.getKey<LastEpisodeInfo>(it)?.id
                }
            }
            return (DataStore.getKeys(VIEW_LST_KEY).map {
                DataStore.getKey<LastEpisodeInfo>(it)
            }).sortedBy { if (it == null) 0 else -(it.seenAt) }
        }

        private fun getFullFav() {

            /*
            fullBookmarks.clear()
            for (b in books) {
                if (b != null) {
                    fullBookmarks[b.anilistId] = b
                }
            }*/
        }

        fun requestHome(canBeCached: Boolean = true): HomePageResponse? {
            println("LOAD HOME $currentToken")
            if (currentToken == null) return null
            getHome(canBeCached)
            return null
        }

        private fun getSchedule(): List<ScheduleItem>? {
            val url = "https://fastani.net/api/schedule"
            val res = currentHeaders?.let {
                khttp.get(url, headers = it.toMap())
            }
            return if (res != null) {
                mapper.readValue(res.text)
            } else null
        }

        fun getHome(canBeCached: Boolean): ShiroHomePage? {
            var res: ShiroHomePage? = null
            if (canBeCached && cachedHome != null) {
                res = cachedHome
            } else {
                val url = "https://ani.api-web.site/latest?token=${currentToken!!.token}"
                val response = khttp.get(url)
                res = response.text.let { mapper.readValue(it) }

                if (res != null) {
                    res.random = getRandomAnimePage()
                }

                //res?.schedule = getSchedule()
            }
            // Anything below here shouldn't do network requests (network on main thread)
            // (card.removeButton.setOnClickListener {requestHome(true)})

            if (res == null) {
                hasThrownError = 0
                onHomeError.invoke(false)
                return null
            }
            res.favorites = getFav()
            res.recentlySeen = getLastWatch()
            cachedHome = res
            onHomeFetched.invoke(res)
            return res
        }

        var currentToken: Token? = null
        var currentHeaders: MutableMap<String, String>? = null
        var onHomeFetched = Event<ShiroHomePage?>()
        var onHomeError = Event<Boolean>() // TRUE IF FULL RELOAD OF TOKEN, FALSE IF JUST HOME
        var hasThrownError = -1

        fun init() {
            if (currentToken != null) return

            currentToken = getToken()
            if (currentToken != null) {
                currentHeaders = currentToken?.headers?.toMutableMap()
                currentHeaders?.set("Cookie", "")
                currentToken?.cookies?.forEach {
                    currentHeaders?.set("Cookie", it.key + "=" + it.value.substring(0, it.value.indexOf(';')) + ";")
                }
                requestHome()
            } else {
                println("TOKEN ERROR")
                hasThrownError = 1
                onHomeError.invoke(true)
            }
        }
    }
}
