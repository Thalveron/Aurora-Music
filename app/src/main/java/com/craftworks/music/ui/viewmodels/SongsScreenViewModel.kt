package com.craftworks.music.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.craftworks.music.data.repository.SongRepository
import com.craftworks.music.managers.DataRefreshManager
import com.craftworks.music.managers.settings.LocalDataSettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SongsScreenViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val localDataSettingsManager: LocalDataSettingsManager
) : ViewModel() {

    private val _allSongs = MutableStateFlow<List<MediaItem>>(emptyList())
    val allSongs: StateFlow<List<MediaItem>> = _allSongs.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults: StateFlow<List<MediaItem>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly: StateFlow<Boolean> = _showFavoritesOnly.asStateFlow()

    private var loadJob:Job?=null
    private var fullyLoaded=false
    private val _librarySongCount=MutableStateFlow(-1)
    val librarySongCount:StateFlow<Int> = _librarySongCount.asStateFlow()

    init {
        viewModelScope.launch {
            localDataSettingsManager.showFavoriteOnly.collect { showFavorites ->
                _showFavoritesOnly.value = showFavorites
                getSongs()
            }
        }
        viewModelScope.launch {
            DataRefreshManager.dataSourceChangedEvent.collect {
                getSongs()
            }
        }
    }

    fun getSongs() {
        loadJob?.cancel()
        loadJob=viewModelScope.launch {
            _isLoading.value=true
            _allSongs.value=emptyList()
            _librarySongCount.value=0
            fullyLoaded=false
            try{
                val favorites=_showFavoritesOnly.value
                val snapshot=songRepository.getAllSongs(favorites)
                ensureActive()
                _allSongs.value=snapshot
                if(!favorites)_librarySongCount.value=snapshot.size
                fullyLoaded=true
            }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){_allSongs.value=emptyList();_librarySongCount.value=0}finally{
                if(isActive)_isLoading.value=false
            }
        }
    }

    fun getMoreSongs(size: Int){
        if (fullyLoaded || _isLoading.value || _showFavoritesOnly.value) return
        _isLoading.value = true
        viewModelScope.launch {
            _isLoading.value = true
            coroutineScope {
                val songOffset = _allSongs.value.size
                _allSongs.value += songRepository.getSongs(songCount = size, songOffset = songOffset)
            }
            _isLoading.value = false
        }
    }

    fun search(query: String){
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            coroutineScope {
                _searchResults.value = songRepository.searchSongs(query)
            }
            _isLoading.value = false
        }
    }
    fun setShowFavoritesOnly(showFavorites: Boolean) {
        viewModelScope.launch {
            localDataSettingsManager.saveShowFavoriteOnly(showFavorites)
        }
    }
}