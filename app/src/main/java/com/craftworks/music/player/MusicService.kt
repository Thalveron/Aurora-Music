package com.craftworks.music.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.ui.util.fastFilter
import androidx.core.math.MathUtils.clamp
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionError
import com.craftworks.music.MainActivity
import com.craftworks.music.R
import com.craftworks.music.data.model.toMediaItem
import com.craftworks.music.data.repository.AlbumRepository
import com.craftworks.music.data.repository.ArtistRepository
import com.craftworks.music.data.repository.LyricsRepository
import com.craftworks.music.data.repository.PlaylistRepository
import com.craftworks.music.data.repository.RadioRepository
import com.craftworks.music.data.repository.SongRepository
import com.craftworks.music.managers.NavidromeManager
import com.craftworks.music.managers.TranscodeManager
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.managers.settings.LocalDataSettingsManager
import com.craftworks.music.managers.settings.PlaybackSettingsManager
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.pow

/*
    Thanks to Yurowitz on StackOverflow for this! Used it as a template.
    https://stackoverflow.com/questions/76838126/can-i-define-a-medialibraryservice-without-an-app
*/

@UnstableApi
@AndroidEntryPoint
class ChoraMediaLibraryService : MediaLibraryService() {
    //region Vars
    lateinit var player: Player
    var session: MediaLibrarySession? = null

    private var scrobbleJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var _sleepTimerRemainingTime = MutableStateFlow(0)
    val sleepTimerRemainingTime: StateFlow<Int> = _sleepTimerRemainingTime.asStateFlow()

    @Inject lateinit var appearanceSettingsManager: AppearanceSettingsManager
    @Inject lateinit var playbackSettingsManager: PlaybackSettingsManager
    @Inject lateinit var transcodeManager: TranscodeManager

    @Inject lateinit var albumRepository: AlbumRepository
    @Inject lateinit var artistRepository: ArtistRepository
    @Inject lateinit var songRepository: SongRepository
    @Inject lateinit var radioRepository: RadioRepository
    @Inject lateinit var playlistRepository: PlaylistRepository
    @Inject lateinit var lyricsRepository: LyricsRepository

    companion object {
        private var instance: ChoraMediaLibraryService? = null

        fun getInstance(): ChoraMediaLibraryService? {
            return instance
        }
    }

    private val rootItem = MediaItem.Builder()
        .setMediaId("nodeROOT")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(false)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

    private val homeItem = MediaItem.Builder()
        .setMediaId("nodeHOME")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
                .setTitle("Home")
                .setExtras(Bundle().apply {
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                    )
                })
                .build()
        )
        .build()

    private val albumsItem = MediaItem.Builder()
        .setMediaId("nodeALBUMS")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                .setTitle("Albums")
                .build()
        )
        .build()

    private val artistsItem = MediaItem.Builder()
        .setMediaId("nodeARTISTS")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
                .setTitle("Artists")
                .build()
        )
        .build()

    private val radiosItem = MediaItem.Builder()
        .setMediaId("nodeRADIOS")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                .setTitle("Radios")
                .build()
        )
        .build()

    private val playlistsItem = MediaItem.Builder()
        .setMediaId("nodePLAYLISTS")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                .setTitle("Playlists")
                .build()
        )
        .build()

    private var rootHierarchy = mutableListOf<MediaItem>()

    private val serviceMainScope = CoroutineScope(Dispatchers.Main)
    private val serviceIOScope = CoroutineScope(Dispatchers.IO)

    var aHomeScreenItems = mutableListOf<MediaItem>()
    var aAlbumScreenItems = mutableListOf<MediaItem>()
    var aArtistsScreenItems = mutableListOf<MediaItem>()
    var aRadioScreenItems = mutableListOf<MediaItem>()
    var aPlaylistScreenItems = mutableListOf<MediaItem>()

    var aFolderSongs = mutableListOf<MediaItem>()

    //endregion

    private var lastDesktopSave=0L
    private var desktopStarting=false
    private var desktopSongs:List<MediaItem> = emptyList()
    private var desktopSongsRequest=""
    private val desktopPlayReceiver=object:android.content.BroadcastReceiver(){
        override fun onReceive(context:android.content.Context,intent:Intent){
            if(intent.action=="com.craftworks.music.tvcustom.DESKTOP_PRUNE"){
                val keepRemote=intent.getBooleanExtra("keep_remote",false)
                for(index in player.mediaItemCount-1 downTo 0)if(!com.craftworks.music.providers.local.LocalFileLibrary.available(applicationContext,player.getMediaItemAt(index),keepRemote))player.removeMediaItem(index)
                desktopSongs=emptyList();desktopSongsRequest=""
                if(player.mediaItemCount==0){player.stop();player.clearMediaItems();serviceMainScope.launch {LocalDataSettingsManager(applicationContext).setPlaybackResumption(emptyList(),0,0)}}else persistDesktopState()
                return
            }
            if(intent.action=="com.craftworks.music.tvcustom.DESKTOP_LIBRARY_REQUEST"){
                val request=intent.getStringExtra("request")?:return
                serviceMainScope.launch {
                    try{
                        val snapshot=songRepository.getAllSongs(false)
                        desktopSongs=snapshot
                        desktopSongsRequest=request
                        val pages=if(snapshot.isEmpty())listOf(emptyList())else snapshot.chunked(100)
                        pages.forEachIndexed { page,items ->
                            sendBroadcast(Intent("com.gathin.tvhome.DESKTOP_LIBRARY").setPackage("com.gathin.tvhome")
                                .putExtra("request",request).putExtra("total",snapshot.size).putExtra("offset",page*100)
                                .putExtra("ids",items.mapIndexed { index,_ -> (page*100+index).toString() }.toTypedArray())
                                .putExtra("titles",items.map { it.mediaMetadata.title?.toString()?.take(300)?:"未知歌曲" }.toTypedArray())
                                .putExtra("artists",items.map { it.mediaMetadata.artist?.toString()?.take(300)?:"" }.toTypedArray()),"com.gathin.tvhome.permission.DESKTOP_LYRICS")
                        }
                    }catch(e:Exception){sendBroadcast(Intent("com.gathin.tvhome.DESKTOP_LIBRARY").setPackage("com.gathin.tvhome").putExtra("request",request).putExtra("error",true),"com.gathin.tvhome.permission.DESKTOP_LYRICS")}
                }
                return
            }
            if(intent.action=="com.craftworks.music.tvcustom.DESKTOP_PLAY_INDEX"){
                val index=intent.getIntExtra("index",-1)
                if(intent.getStringExtra("request")!=desktopSongsRequest)return
                if(index !in desktopSongs.indices)return
                player.setMediaItems(desktopSongs,index,0);player.prepare();player.play();persistDesktopState()
                return
            }
            if(desktopStarting)return
            desktopStarting=true
            serviceMainScope.launch {
                try {
                    if(player.mediaItemCount==0){
                        val saved=LocalDataSettingsManager(applicationContext).playbackResumptionPlaylistWithStartPosition.first()
                        var available=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){saved.mediaItems.filter{com.craftworks.music.providers.local.LocalFileLibrary.available(applicationContext,it,true)}}
                        if(available.isEmpty()&&saved.mediaItems.any{it.localConfiguration?.uri?.scheme=="file"}){
                            Toast.makeText(applicationContext,"正在等候U盘就绪…",Toast.LENGTH_SHORT).show()
                            for(attempt in 0 until 10){kotlinx.coroutines.delay(1000);available=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){saved.mediaItems.filter{com.craftworks.music.providers.local.LocalFileLibrary.available(applicationContext,it,true)}};if(available.isNotEmpty())break}
                        }
                        if(available.isNotEmpty()){
                            val selected=saved.mediaItems.getOrNull(saved.startIndex)?.mediaId
                            val index=available.indexOfFirst{it.mediaId==selected}.takeIf{it>=0}?:0
                            player.setMediaItems(available,index,if(available[index].mediaId==selected)saved.startPositionMs.coerceAtLeast(0)else 0)
                        }else{
                            val songs=songRepository.getAllSongs(false)
                            if(songs.isNotEmpty())player.setMediaItems(songs)
                        }
                    }
                    if(player.mediaItemCount>0){
                        if(player.playbackState==Player.STATE_ENDED||(player.duration>0&&player.currentPosition>=player.duration-500))player.seekToDefaultPosition()
                        player.prepare();player.play()
                        persistDesktopState()
                    }else Toast.makeText(applicationContext,"暂无可播放歌曲，请检查U盘是否就绪，或重新扫描本地歌曲。",Toast.LENGTH_LONG).show()
                }catch(e:Exception){Toast.makeText(applicationContext,"音乐恢复失败，请稍后再试。",Toast.LENGTH_LONG).show()}
                finally{desktopStarting=false}
            }
        }
    }
    private fun persistDesktopState(){
        if(!::player.isInitialized||player.mediaItemCount==0)return
        val queue=List(player.mediaItemCount){player.getMediaItemAt(it)}
        val index=player.currentMediaItemIndex
        val position=player.currentPosition
        lastDesktopSave=android.os.SystemClock.elapsedRealtime()
        serviceIOScope.launch {
            LocalDataSettingsManager(applicationContext).setPlaybackResumption(queue,index,position)
        }
    }
    private var desktopLyricsJob:kotlinx.coroutines.Job?=null
    private val desktopHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val desktopTick = object : Runnable {
        override fun run() {
            if (::player.isInitialized) {
                if(player.mediaItemCount>0&&android.os.SystemClock.elapsedRealtime()-lastDesktopSave>=10000)persistDesktopState()
                val metadata=player.currentMediaItem?.mediaMetadata
                val lines=com.craftworks.music.data.repository.LyricsState.lyrics.value
                val timed=lines.filter { it.startMs>=0 }
                val index=timed.indexOfLast { it.startMs.toLong()<=player.currentPosition }
                val plain=if(timed.isEmpty())lines.flatMap { it.text }.flatMap { it.lines() }.filter { it.isNotBlank() } else emptyList()
                val line=if(index>=0)timed[index].text.joinToString("  ") else plain.firstOrNull().orEmpty()
                val next=if(index>=0)timed.getOrNull(index+1)?.text?.joinToString("  ").orEmpty() else plain.getOrNull(1).orEmpty()
                val window=Array(7){ offset->if(timed.isNotEmpty())timed.getOrNull(index+offset-3)?.text?.joinToString("  ").orEmpty().take(500) else plain.getOrNull(offset-3).orEmpty().take(500) }
                sendBroadcast(Intent("com.gathin.tvhome.DESKTOP_LYRICS")
                    .putExtra("window",window).putExtra("index",index).setPackage("com.gathin.tvhome")
                    .putExtra("third",if(index>=0)timed.getOrNull(index+2)?.text?.joinToString("  ").orEmpty().take(500) else plain.getOrNull(2).orEmpty().take(500))
                    .putExtra("line",line.take(500)).putExtra("next",next.take(500))
                    .putExtra("progress",if(index>=0)desktopLyricProgress(timed[index],timed.getOrNull(index+1)?.startMs,player.currentPosition,player.duration) else 0f)
                    .putExtra("song",metadata?.title?.toString().orEmpty())
                    .putExtra("source","com.craftworks.music.tvcustom"),"com.gathin.tvhome.permission.DESKTOP_LYRICS")
            }
            desktopHandler.postDelayed(this,200)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        instance = this
        desktopHandler.postDelayed(desktopTick,500)

        Log.d("AA", "onCreate Android Auto")

        if (session == null)
            initializePlayer()
        else
            Log.d("AA", "MediaSession already initialized, not recreating")
        val filter=android.content.IntentFilter("com.craftworks.music.tvcustom.DESKTOP_PLAY")
        filter.addAction("com.craftworks.music.tvcustom.DESKTOP_LIBRARY_REQUEST")
        filter.addAction("com.craftworks.music.tvcustom.DESKTOP_PLAY_INDEX")
        filter.addAction("com.craftworks.music.tvcustom.DESKTOP_PRUNE")
        if(android.os.Build.VERSION.SDK_INT>=33)registerReceiver(desktopPlayReceiver,filter,"com.gathin.tvhome.permission.DESKTOP_LYRICS",desktopHandler,android.content.Context.RECEIVER_EXPORTED)
        else registerReceiver(desktopPlayReceiver,filter,"com.gathin.tvhome.permission.DESKTOP_LYRICS",desktopHandler)
    }

    @OptIn(UnstableApi::class)
    fun initializePlayer() {
        serviceIOScope.launch {
            appearanceSettingsManager.bottomNavItemsFlow.collect { items ->
                val routeToItem = mapOf(
                    "home_screen" to homeItem,
                    "album_screen" to albumsItem,
                    "artists_screen" to artistsItem,
                    "radio_screen" to radiosItem,
                    "playlist_screen" to playlistsItem
                )

                rootHierarchy = items
                    .filter { it.enabled }
                    .mapNotNull { routeToItem[it.screenRoute] }
                    .toMutableList()

                session?.notifyChildrenChanged("nodeROOT", 0, null)
            }
        }

        val resolvingDataSourceFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this),
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    val uri = dataSpec.uri

                    if (uri.path?.contains("stream") == true) {
                        val bitrate = runBlocking { transcodeManager.currentBitrateFlow.first() }
                        if (bitrate == "No Transcoding")
                            return dataSpec

                        val format = runBlocking { transcodeManager.currentFormatFlow.first() }

                        val newUri = uri.buildUpon()
                            .appendQueryParameter("format", format)
                            .appendQueryParameter("maxBitRate", bitrate)
                            .build()

                        return dataSpec.withUri(newUri)
                    }
                    return dataSpec
                }
            }
        )

        player = ExoPlayer.Builder(this)
            .setSeekParameters(SeekParameters.EXACT)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingDataSourceFactory))
            .setWakeMode(
                if (NavidromeManager.checkActiveServers())
                    C.WAKE_MODE_NETWORK
                else
                    C.WAKE_MODE_LOCAL
            )
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build()

        player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = false

        var playerScrobbled = false

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                com.craftworks.music.data.repository.LyricsState.lyrics.value = emptyList()
                // Apply ReplayGain
                if (mediaItem?.mediaMetadata?.extras?.getFloat("replayGain") != null) {
                    player.volume = clamp(
                        (10f.pow(
                            ((mediaItem.mediaMetadata.extras?.getFloat("replayGain") ?: 0f) / 20f)
                        )), 0f, 1f
                    )
                    Log.d("REPLAY GAIN", "Setting ReplayGain to ${player.volume}")
                }

                playerScrobbled = false

                super.onMediaItemTransition(mediaItem, reason)

                desktopLyricsJob?.cancel()
                desktopLyricsJob=serviceMainScope.launch {
                    lyricsRepository.getLyrics(mediaItem?.mediaMetadata)
                    val mediaId = mediaItem?.mediaMetadata?.extras?.getString("navidromeID") ?: return@launch
                    songRepository.scrobbleSong(mediaId, false)
                }

                /*
                serviceMainScope.launch {
                    playbackSettingsManager.autoPlayFlow.collect {
                        if (it && player.currentMediaItemIndex == player.mediaItemCount - 1)
                            player.addMediaItems(songRepository.getSimilarSongs(mediaItem?.mediaMetadata?.extras?.getString("navidromeID")!!, 1))
                    }
                }
                */
            }

            override fun onPlayerError(error: PlaybackException) {
                error.printStackTrace()
                Log.e("PLAYER", error.stackTraceToString())

                Toast.makeText(
                    this@ChoraMediaLibraryService,
                    if(error.errorCode==PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) "歌曲文件暂不可用，请确认U盘已就绪；移动文件后请重新扫描。" else PlaybackException.getErrorCodeName(error.errorCode),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaLibrarySession.Builder(this, player, LibrarySessionCallback())
            .setId("AutoSession")
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        scrobbleJob = serviceMainScope.launch {
            while (isActive) {
                val duration = player.duration
                val mediaItem = player.currentMediaItem

                if (duration > 0 && !playerScrobbled) {
                    val currentPosition = player.currentPosition
                    val progress = (currentPosition * 100 / duration).toInt()
                    val scrobblePercentage = playbackSettingsManager.scrobblePercentFlow.first() * 10

                    if (progress >= scrobblePercentage) {
                        playerScrobbled = true
                        if (NavidromeManager.checkActiveServers() &&
                            mediaItem?.mediaMetadata?.extras?.getString("navidromeID")
                                ?.startsWith("Local") == false &&
                            mediaItem.mediaMetadata.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION
                        ) {
                            serviceIOScope.launch {
                                songRepository.scrobbleSong(mediaItem.mediaMetadata.extras?.getString("navidromeID") ?: "", true)
                            }
                        }
                    }
                }
                delay(1000)
            }
        }

        serviceMainScope.launch {
            transcodeManager.transcodingConfigChangesFlow
                .distinctUntilChanged()
                .collect {
                    if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                        if (player.currentTimeline.isEmpty.not()) {
                            updateTranscodingDuringPlayback()
                        }
                    }
                }
        }

        Log.d("AA", "Initialized MediaLibraryService.")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return session
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            serviceIOScope.launch {
                println("ONPOSTCONNTECT MUSIC SERVICE!")

                if (session.isAutoCompanionController(controller))
                    getHomeScreenItems()

                this@ChoraMediaLibraryService.session?.notifyChildrenChanged(
                    "nodeHOME",
                    aHomeScreenItems.size,
                    null
                )
            }
            super.onPostConnect(session, controller)
        }

        /*
        @OptIn(UnstableApi::class)
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaItemsWithStartPosition> {
            // We need to use URI from requestMetaData because of https://github.com/androidx/media/issues/282
            val updatedStartIndex =
                SongHelper.currentTracklist.indexOfFirst { it.mediaId == mediaItems[0].mediaId }

            val currentTracklist =
                if (updatedStartIndex != -1) {
                    SongHelper.currentTracklist
                } else {
                    SongHelper.currentTracklist = mediaItems.toMutableList()
                    mediaItems
                }

            val connectivityManager =
                this@ChoraMediaLibraryService.baseContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

            val networkCapabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

            val bitrate: String = runBlocking {
                when {
                    networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> {
                        Log.d("NetworkCheck", "Device is on Wi-Fi")
                        playbackSettingsManager.wifiTranscodingBitrateFlow.first()
                    }
                    networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                        Log.d("NetworkCheck", "Device is on Mobile Data")
                        playbackSettingsManager.mobileDataTranscodingBitrateFlow.first()
                    }
                    else -> {
                        Log.d("NetworkCheck", "Device is on another network type")
                        playbackSettingsManager.wifiTranscodingBitrateFlow.first()
                    }
                }
            }

            val bitrateOptions = if (bitrate != "No Transcoding" && bitrate.isNotEmpty()) {
                runBlocking {
                    "&maxBitRate=$bitrate&format=${playbackSettingsManager.transcodingFormatFlow.first()}"
                }
            } else {
                ""
            }

            val result = MediaItemsWithStartPosition(
                currentTracklist.map { mediaItem ->
                    MediaItem.Builder()
                        .setMediaId(mediaItem.mediaId)
                        .setMediaMetadata(mediaItem.mediaMetadata)
                        .setUri(mediaItem.mediaId + if (mediaItem.mediaMetadata.extras?.getString("navidromeID")?.startsWith("Local_") == false) bitrateOptions else "")
                        .build()
                },
                if (updatedStartIndex != -1) updatedStartIndex else startIndex,
                startPositionMs
            )

            return Futures.immediateFuture(result)
        }
        */

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            // Android Auto uses the legacy MediaController, so we need to do some very weird hacks to make it work nicely.

            // If only one item is requested, check if it belongs to a folder we've already loaded
            if (mediaItems.size == 1) {
                val requestedId = mediaItems[0].mediaId
                // Try to find the full item in the last browsed folder
                val fullItem = aFolderSongs.find { it.mediaId == requestedId }
                if (fullItem != null) {
                    val startIndex = aFolderSongs.indexOf(fullItem)
                    val folderQueue = aFolderSongs.subList(startIndex, aFolderSongs.size).map { item ->
                        item.buildUpon()
                            .setUri(item.mediaId)
                            .build()
                    }
                    return Futures.immediateFuture(folderQueue)
                }

                // Not found in the current folder
                val cachedItem = aPlaylistScreenItems.find { it.mediaId == requestedId }
                    ?: aRadioScreenItems.find { it.mediaId == requestedId }
                    ?: aAlbumScreenItems.find { it.mediaId == requestedId }
                    ?: aArtistsScreenItems.find { it.mediaId == requestedId }

                if (cachedItem != null) {
                    val enrichedItem = cachedItem.buildUpon()
                        .setUri(cachedItem.mediaId)
                        .build()
                    return Futures.immediateFuture(listOf(enrichedItem))
                }
            }

            val updatedMediaItems = mediaItems.map { item ->
                item.buildUpon()
                    .setUri(item.mediaId)
                    .build()
            }
            return Futures.immediateFuture(updatedMediaItems)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        @OptIn(UnstableApi::class)
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return Futures.immediateFuture(
                try {
                    LibraryResult.ofItemList(
                        when (parentId) {
                            "nodeROOT" -> rootHierarchy
                            "nodeHOME" -> getHomeScreenItems()
                            "nodeALBUMS" -> getAlbumScreenItems()
                            "nodeARTISTS" -> getArtistScreenItems()
                            "nodeRADIOS" -> getRadioItems()
                            "nodePLAYLISTS" -> getPlaylistItems()
                            else -> {
                                val mediaItem =
                                    aHomeScreenItems.find { it.mediaId == parentId }
                                        ?: aPlaylistScreenItems.find { it.mediaId == parentId }
                                        ?: aAlbumScreenItems.find { it.mediaId == parentId }
                                        ?: aArtistsScreenItems.find { it.mediaId == parentId }
                                getFolderItems(
                                    parentId,
                                    mediaItem?.mediaMetadata?.mediaType
                                        ?: MediaMetadata.MEDIA_TYPE_ALBUM
                                )
                            }
                        }, params)
                } catch (_: Exception) {
                    LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                }
            )
        }


        @OptIn(UnstableApi::class)
        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val mediaItem = aFolderSongs.find { it.mediaId == mediaId }
                ?: aPlaylistScreenItems.find { it.mediaId == mediaId }
                ?: aRadioScreenItems.find { it.mediaId == mediaId }
                ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))

            return Futures.immediateFuture(
                LibraryResult.ofItem(
                    mediaItem,
                    LibraryParams.Builder().build()
                )
            )
        }

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            session.notifyChildrenChanged(
                parentId,
                when (parentId) {
                    "nodeROOT" -> 2
                    "nodeHOME" -> aHomeScreenItems.size
                    "nodeALBUMS" -> aAlbumScreenItems.size
                    "nodeARTISTS" -> aArtistsScreenItems.size
                    "nodeRADIOS" -> aRadioScreenItems.size
                    "nodePLAYLISTS" -> aPlaylistScreenItems.size
                    else -> 0
                },
                params
            )

            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isToStream: Boolean
        ): ListenableFuture<MediaItemsWithStartPosition> {
            val settable=SettableFuture.create<MediaItemsWithStartPosition>()
            serviceMainScope.launch {
                try{
                    val saved=LocalDataSettingsManager(applicationContext).playbackResumptionPlaylistWithStartPosition.first()
                    var items=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){saved.mediaItems.filter{com.craftworks.music.providers.local.LocalFileLibrary.available(applicationContext,it,true)}}
                    if(items.isEmpty()&&saved.mediaItems.any{it.localConfiguration?.uri?.scheme=="file"})for(attempt in 0 until 10){kotlinx.coroutines.delay(1000);items=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){saved.mediaItems.filter{com.craftworks.music.providers.local.LocalFileLibrary.available(applicationContext,it,true)}};if(items.isNotEmpty())break}
                    val selected=saved.mediaItems.getOrNull(saved.startIndex)?.mediaId
                    val index=items.indexOfFirst{it.mediaId==selected}.takeIf{it>=0}?:0
                    settable.set(MediaItemsWithStartPosition(items,index,if(items.getOrNull(index)?.mediaId==selected)saved.startPositionMs.coerceAtLeast(0)else 0))
                }
                catch(e:Exception){settable.setException(e)}
            }
            return settable
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return Futures.immediateFuture(
                LibraryResult.ofItemList(
                    runBlocking {
                        songRepository.getSongs(query).toMutableList()
                    },
                    LibraryParams.Builder().build()
                )
            )
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            println("onSearch!!!")

            session.notifySearchResultChanged(
                browser,
                query,
                runBlocking {
                    songRepository.getSongs(query).size +
                            albumRepository.searchAlbum(query).size +
                            radioRepository.getRadios().map { it.toMediaItem() }.fastFilter {
                                it.mediaMetadata.station?.contains(
                                    query
                                ) ?: false
                            }.size +
                            playlistRepository.getPlaylists().fastFilter {
                                it.mediaMetadata.title?.contains(
                                    query
                                ) == true
                            }.size
                },
                LibraryParams.Builder().build()
            )

            return Futures.immediateFuture(LibraryResult.ofVoid())
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel() // Cancel any previously running timer

        if (minutes <= 0) {
            Log.d("SLEEPTIMER", "Sleep timer cancelled.")
            _sleepTimerRemainingTime.value = 0
            return
        }

        Log.d("SLEEPTIMER", "Sleep timer set for $minutes minutes.")

        sleepTimerJob = serviceMainScope.launch {
            var timeRemaining = minutes
            _sleepTimerRemainingTime.value = timeRemaining

            while (timeRemaining > 0) {
                delay(60 * 1000L)
                timeRemaining--
                _sleepTimerRemainingTime.value = timeRemaining
            }

            if (::player.isInitialized && player.isPlaying) {
                player.stop()
                Log.d("SLEEPTIMER", "Timer finished. Playback stopped.")
            }
        }
    }

    private fun updateTranscodingDuringPlayback() {
        val currentWindowIndex = player.currentMediaItemIndex
        val currentPlaybackPosition = player.currentPosition
        val wasPlaying = player.isPlaying

        val currentQueue = mutableListOf<MediaItem>()
        for (i in 0 until player.mediaItemCount) {
            currentQueue.add(player.getMediaItemAt(i))
        }

        player.setMediaItems(currentQueue, currentWindowIndex, currentPlaybackPosition)

        player.prepare()

        if (wasPlaying) {
            player.play()
        }
    }

    override fun onDestroy() {
        desktopHandler.removeCallbacks(desktopTick)
        unregisterReceiver(desktopPlayReceiver)
        saveState()
        session?.release()
        scrobbleJob?.cancel()
        sleepTimerJob?.cancel()
        instance = null
        super.onDestroy()
    }

    fun saveState() {
        if(!::player.isInitialized||player.mediaItemCount==0)return
        runBlocking {
            Log.d(
                "AA",
                "Saving state! Playlist: ${List(player.mediaItemCount) { i -> player.getMediaItemAt(i) }.map { it.mediaMetadata.title }}, current index: ${player.currentMediaItemIndex}, current position: ${player.currentPosition}"
            )

            LocalDataSettingsManager(applicationContext).setPlaybackResumption(
                List(player.mediaItemCount) { i ->
                    player.getMediaItemAt(i)
                },
                player.currentMediaItemIndex,
                player.currentPosition
            )
        }
    }

    //region getChildren
    private fun getHomeScreenItems(): MutableList<MediaItem> {
        println("GETTING ANDROID AUTO SCREEN ITEMS")
        runBlocking {
            if (aHomeScreenItems.isEmpty()) {
                val recentlyPlayedAlbums = async { albumRepository.getAlbums("recent", 6) }.await()
                val mostPlayedAlbums = async { albumRepository.getAlbums("frequent", 6) }.await()

                recentlyPlayedAlbums.forEach { album ->
                    aHomeScreenItems.add(
                        album.apply {
                            this.mediaMetadata.extras?.putString(
                                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                                this@ChoraMediaLibraryService.getString(R.string.recently_played)
                            )
                        }
                    )
                }

                mostPlayedAlbums.forEach { album ->
                    aHomeScreenItems.add(
                        album.apply {
                            this.mediaMetadata.extras?.putString(
                                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                                this@ChoraMediaLibraryService.getString(R.string.most_played)
                            )
                        }
                    )
                }
            }
        }
        return aHomeScreenItems
    }

    private fun getAlbumScreenItems() : MutableList<MediaItem> {
        println("GETTING ANDROID AUTO ALBUM SCREEN ITEMS")
        runBlocking {
            if (aAlbumScreenItems.isEmpty()) {
                while (true) {
                    val albums = async { albumRepository.getAlbums("alphabeticalByName", 250, aAlbumScreenItems.size) }.await()
                    aAlbumScreenItems.addAll(albums)
                    if (albums.isEmpty()) {
                        break
                    }
                }
            }
        }
        println("Got ALL albums. Should be 492, is ${aAlbumScreenItems.size}")
        return aAlbumScreenItems
    }

    private fun getArtistScreenItems() : MutableList<MediaItem> {
        println("GETTING ANDROID AUTO ARTIST SCREEN ITEMS")
        runBlocking {
            if (aArtistsScreenItems.isEmpty()) {
                val albums = async { artistRepository.getArtists() }.await()

                albums.forEach {
                    aArtistsScreenItems.add(
                        MediaItem.Builder()
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(it.name)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                                    .setArtworkUri(it.artistImageUrl?.toUri())
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .build()
                            )
                            .setMediaId(it.navidromeID)
                            .setUri(it.navidromeID)
                            .build()
                    )
                }
            }
        }
        return aArtistsScreenItems
    }

    private fun getRadioItems(): MutableList<MediaItem> {
        runBlocking {
            if (aRadioScreenItems.isEmpty()) {
                aRadioScreenItems.addAll(
                    radioRepository.getRadios().map { radio ->
                        Log.d("MediaItemTransition", radio.toString())
                        radio.toMediaItem()
                    }
                )
                Log.d("MediaItemTransition", "aRadioScreenItems: ${aRadioScreenItems.map { it.mediaMetadata }}")
            }
        }
        return aRadioScreenItems
    }

    private fun getPlaylistItems(): MutableList<MediaItem> {
        runBlocking {
            if (aPlaylistScreenItems.isEmpty()) {
                aPlaylistScreenItems.addAll(playlistRepository.getPlaylists())
            }
        }
        return aPlaylistScreenItems
    }

    private fun getFolderItems(parentId: String, type: Int): MutableList<MediaItem> {
        runBlocking {
            aFolderSongs.clear()
            when (type) {
                MediaMetadata.MEDIA_TYPE_ALBUM -> {
                    val albumSongs = async { albumRepository.getAlbum(parentId) }.await()
                    aFolderSongs.addAll(
                        albumSongs?.subList(1, albumSongs.size) ?: emptyList()
                    )
                }

                MediaMetadata.MEDIA_TYPE_PLAYLIST -> {
                    aFolderSongs.addAll(
                        playlistRepository.getPlaylistSongs(parentId)
                    )
                }

                MediaMetadata.MEDIA_TYPE_ARTIST -> {
                    aFolderSongs.addAll(
                        artistRepository.getArtistAlbums(parentId)
                    )
                }

                else -> aFolderSongs.clear()
            }
        }

        return aFolderSongs
    }
    //endregion
}
