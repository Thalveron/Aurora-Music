package com.craftworks.music.data.repository

import androidx.media3.common.MediaItem
import com.craftworks.music.data.datasource.local.LocalDataSource
import com.craftworks.music.data.datasource.navidrome.NavidromeDataSource
import com.craftworks.music.managers.LocalProviderManager
import com.craftworks.music.managers.NavidromeManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SongRepository @Inject constructor(
    private val localDataSource: LocalDataSource,
    private val navidromeDataSource: NavidromeDataSource
) {

    suspend fun getAllSongs(favoritesOnly:Boolean=false):List<MediaItem> = kotlinx.coroutines.supervisorScope {
        val sources=mutableListOf<Deferred<List<MediaItem>>>()
        if(LocalProviderManager.checkActiveFolders())sources.add(async(kotlinx.coroutines.Dispatchers.IO){localDataSource.getLocalSongs()})
        if(NavidromeManager.checkActiveServers())sources.add(async{ kotlinx.coroutines.withTimeoutOrNull(8000){
            if(favoritesOnly)navidromeDataSource.getNavidromeSongs(ignoreCachedResponse=true,favoritesOnly=true)
            else collectPages(key={item:MediaItem->item.mediaMetadata.extras?.getString("navidromeID") ?: item.mediaId}){size,offset->
                navidromeDataSource.getNavidromeSongs(songCount=size,songOffset=offset,ignoreCachedResponse=true)
            }
        }?:emptyList()})
        sources.map { try{it.await()}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){emptyList()} }.flatten().distinctBy { it.mediaMetadata.extras?.getString("navidromeID") ?: it.mediaId }
    }

    suspend fun getSongs(
        query: String? = "",
        songCount: Int = 100, 
        songOffset: Int = 0,
        ignoreCachedResponse: Boolean = false,
        favoritesOnly: Boolean = false,
    ): List<MediaItem> = kotlinx.coroutines.supervisorScope {
        val deferredSongs = mutableListOf<Deferred<List<MediaItem>>>()

        if (LocalProviderManager.checkActiveFolders())
            if (query.isNullOrEmpty() && songOffset == 0)
                deferredSongs.add(async(kotlinx.coroutines.Dispatchers.IO) { localDataSource.getLocalSongs() })

        if (NavidromeManager.checkActiveServers())
            deferredSongs.add(async {
                navidromeDataSource.getNavidromeSongs(query, songCount, songOffset, ignoreCachedResponse, favoritesOnly = favoritesOnly)
            })

        deferredSongs.awaitAll().flatten()
    }

    suspend fun getSong(songId: String, ignoreCachedResponse: Boolean = false): MediaItem? = kotlinx.coroutines.supervisorScope {
        if (songId.startsWith("Local_"))
            localDataSource.getLocalSong(songId)
        else
            navidromeDataSource.getNavidromeSong(songId, ignoreCachedResponse)
    }

    suspend fun getSimilarSongs(songId: String, count: Int) : List<MediaItem> = kotlinx.coroutines.supervisorScope {
        if (songId.startsWith("Local_"))
            emptyList()
        else
            navidromeDataSource.getNavidromeSimilarSong(songId, count)
    }

    suspend fun searchSongs(query: String, ignoreCachedResponse: Boolean = false): List<MediaItem> = kotlinx.coroutines.supervisorScope {
        val deferredSongs = mutableListOf<Deferred<List<MediaItem>>>()

        if (LocalProviderManager.checkActiveFolders())
            deferredSongs.add(async(kotlinx.coroutines.Dispatchers.IO) { localDataSource.searchLocalSongs(query) })

        if (NavidromeManager.checkActiveServers())
            deferredSongs.add(async { navidromeDataSource.getNavidromeSongs(query, ignoreCachedResponse = ignoreCachedResponse) })

        deferredSongs.awaitAll().flatten()
    }

    suspend fun scrobbleSong(songId: String, submission: Boolean) {
        if (songId.startsWith("Local_"))
            return

        navidromeDataSource.scrobbleSong(songId, submission)
    }
}
