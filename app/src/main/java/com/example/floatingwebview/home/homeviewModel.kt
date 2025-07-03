package com.example.floatingwebview.home



import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val dao: VisitedPageDao) : ViewModel() {

//    var recentPages: StateFlow<List<VisitedPage>> = dao.getRecentUniquePages()
//        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentPages5: StateFlow<List<VisitedPage>> = dao.getRecentUniquePages5()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _recentPages = MutableStateFlow<List<VisitedPage>>(emptyList())
    val recentPages: StateFlow<List<VisitedPage>> = _recentPages.asStateFlow()

    init {
        observeRecentPages()
    }

    private fun observeRecentPages() {
        viewModelScope.launch {
            dao.getRecentUniquePages()
                .collect { pages ->
                    _recentPages.value = pages
                }
        }
    }
    // Now receives faviconUrl
    fun saveVisitedPage(url: String, title: String = "", faviconUrl: String = "") {
        val page = VisitedPage(url = url, title = title, faviconUrl = faviconUrl)
        viewModelScope.launch {
            dao.insert(page)
        }
    }

    fun deletePage(id: Int) {
        viewModelScope.launch {
            dao.deletePage(id)
            observeRecentPages()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            dao.clearAll()
        }
    }
}

class HomeViewModelFactory(private val dao: VisitedPageDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(dao) as T
    }
}

