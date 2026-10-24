package com.tonyscratch.tsdj.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class YouTubeApiException(val code: Int, message: String) : Exception(message)

/**
 * Reads your YouTube account with the official YouTube Data API (read-only).
 * Needs a Google sign-in token with the youtube.readonly permission.
 */
object YouTubeApi {
    private const val BASE = "https://www.googleapis.com/youtube/v3/"

    suspend fun myPlaylists(token: String): List<YtPlaylist> {
        val json = get(token, "playlists?part=snippet,contentDetails&mine=true&maxResults=50")
        val items = json.optJSONArray("items") ?: return emptyList()
        return List(items.length()) { i ->
            val o = items.getJSONObject(i)
            YtPlaylist(
                id = o.getString("id"),
                title = o.getJSONObject("snippet").optString("title", "Playlist"),
                count = o.optJSONObject("contentDetails")?.optInt("itemCount", 0) ?: 0,
            )
        }
    }

    suspend fun playlistItems(token: String, playlistId: String): List<YtItem> {
        val json = get(token, "playlistItems?part=snippet&maxResults=50&playlistId=${enc(playlistId)}")
        val items = json.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<YtItem>()
        for (i in 0 until items.length()) {
            val s = items.getJSONObject(i).getJSONObject("snippet")
            val id = s.optJSONObject("resourceId")?.optString("videoId").orEmpty()
            val title = s.optString("title")
            if (id.isEmpty() || title == "Deleted video" || title == "Private video") continue
            val channel = s.optString("videoOwnerChannelTitle").ifEmpty { s.optString("channelTitle") }
            out += YtItem(id, title, cleanChannel(channel))
        }
        return out
    }

    suspend fun likedVideos(token: String): List<YtItem> {
        val json = get(token, "videos?part=snippet&myRating=like&maxResults=50")
        val items = json.optJSONArray("items") ?: return emptyList()
        return List(items.length()) { i ->
            val o = items.getJSONObject(i)
            val s = o.getJSONObject("snippet")
            YtItem(o.getString("id"), s.optString("title"), cleanChannel(s.optString("channelTitle")))
        }
    }

    suspend fun search(token: String, query: String): List<YtItem> {
        val json = get(token, "search?part=snippet&type=video&maxResults=25&q=${enc(query)}")
        val items = json.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<YtItem>()
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            val id = o.optJSONObject("id")?.optString("videoId").orEmpty()
            if (id.isEmpty()) continue
            val s = o.getJSONObject("snippet")
            out += YtItem(id, unescape(s.optString("title")), cleanChannel(s.optString("channelTitle")))
        }
        return out
    }

    /**
     * Title and artist for a video, without signing in (YouTube's public oEmbed service).
     * Returns null if it can't be reached.
     */
    suspend fun titleFor(videoId: String): YtItem? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://www.youtube.com/oembed?format=json&url=" + enc("https://www.youtube.com/watch?v=$videoId"))
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                if (conn.responseCode !in 200..299) return@withContext null
                val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                YtItem(videoId, o.optString("title", "YouTube video"), cleanChannel(o.optString("author_name", "YouTube")))
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Finds the 11-character video ID in a YouTube or YouTube Music link (or accepts a bare ID). */
    fun videoIdFrom(text: String): String? {
        val t = text.trim()
        if (Regex("^[A-Za-z0-9_-]{11}$").matches(t)) return t
        val patterns = listOf(
            Regex("[?&]v=([A-Za-z0-9_-]{11})"),
            Regex("youtu\\.be/([A-Za-z0-9_-]{11})"),
            Regex("/(?:shorts|embed|live)/([A-Za-z0-9_-]{11})"),
        )
        for (p in patterns) p.find(t)?.let { return it.groupValues[1] }
        return null
    }

    private fun cleanChannel(c: String): String = c.removeSuffix(" - Topic")

    private fun unescape(s: String): String =
        s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun get(token: String, path: String): JSONObject = withContext(Dispatchers.IO) {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = try {
                    JSONObject(body).getJSONObject("error").optString("message", "YouTube error $code")
                } catch (_: Exception) {
                    "YouTube error $code"
                }
                throw YouTubeApiException(code, msg)
            }
            JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }
}
