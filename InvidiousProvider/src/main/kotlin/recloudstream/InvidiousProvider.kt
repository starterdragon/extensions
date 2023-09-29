package recloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URLEncoder

class InvidiousProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://dopebox.to"
    override var name = "D[]pe B[]x" // name of provider
    override val supportedTypes = setOf(TvType.Others)

    override var lang = "en"

    // enable this when your provider has a main page
    override val hasMainPage = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val popular = tryParseJson<List<SearchEntry>>(
            app.get("$mainUrl/api/v1/popular?fields=videoId,title").text
        )
        val trending = tryParseJson<List<SearchEntry>>(
            app.get("$mainUrl/api/v1/trending?fields=videoId,title").text
        )
        return newHomePageResponse(
            listOf(
                HomePageList(
                    "Movie",
                    movie?.map { it.toSearchResponse(this) } ?: emptyList(),
                    true
                ),
                HomePageList(
                    "TV Show",
                    tv-show?.map { it.toSearchResponse(this) } ?: emptyList(),
                    true
                )
            ),
            false
        )
    }

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        val res = tryParseJson<List<SearchEntry>>(
            app.get("$mainUrl/search/${query.encodeUri()}").text
        )
        return res?.map { it.toSearchResponse(this) } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val videoId = Regex("watch\\?v=([a-zA-Z0-9_-]+)").find(url)?.groups?.get(1)?.value
        val res = tryParseJson<VideoEntry>(
            app.get("$mainUrl/search/").text
        )
        return res?.toLoadResponse(this)
    }

    private data class SearchEntry(
        val title: String,
        val videoId: String
    ) {
        fun toSearchResponse(provider: InvidiousProvider): SearchResponse {
            return provider.newMovieSearchResponse(
                title,
                "${provider.mainUrl}/movie/watch-${videoId}",
                TvType.Movie
            ) {
                this.posterUrl = "${provider.mainUrl}/vi/$videoId/mqdefault.jpg"
            }
        }
    }

    private data class VideoEntry(
        val title: String,
        val description: String,
        val videoId: String,
        val recommendedVideos: List<SearchEntry>,
        val author: String,
        val authorThumbnails: List<Thumbnail>
    ) {
        suspend fun toLoadResponse(provider: InvidiousProvider): LoadResponse {
            return provider.newMovieLoadResponse(
                title,
                "${provider.mainUrl}/watch?v=$videoId",
                TvType.Movie,
                "$videoId"
            ) {
                plot = description
                posterUrl = "${provider.mainUrl}/vi/$videoId/hqdefault.jpg"
                recommendations = recommendedVideos.map { it.toSearchResponse(provider) }
                actors = listOf(
                    ActorData(
                        Actor(author, authorThumbnails.get(authorThumbnails.size - 1)?.url ?: ""),
                        roleString = "Author"
                    )
                )
            }
        }
    }

    private data class Thumbnail(
        val url: String
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        loadExtractor(
            "https://youtube.com/watch?v=$data",
            subtitleCallback,
            callback
        )
        callback(
            ExtractorLink(
                "Invidious",
                "Invidious",
                "$mainUrl/api/manifest/dash/id/$data",
                "",
                Qualities.Unknown.value,
                false,
                mapOf(),
                null,
                true
            )
        )
        return true
    }

    companion object {
        fun String.encodeUri() = URLEncoder.encode(this, "utf8")
    }
}
