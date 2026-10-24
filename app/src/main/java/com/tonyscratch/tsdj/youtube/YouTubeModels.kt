package com.tonyscratch.tsdj.youtube

enum class YtView { Video, Audio }
enum class BrowseTab { Playlists, Liked, Search, Link }

data class YtItem(val videoId: String, val title: String, val channel: String)
data class YtPlaylist(val id: String, val title: String, val count: Int)
