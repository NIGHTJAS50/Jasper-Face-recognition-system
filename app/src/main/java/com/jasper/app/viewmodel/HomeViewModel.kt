package com.jasper.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jasper.app.data.repository.FaceRepository
import com.jasper.app.ui.state.HomeUiState
import com.jasper.app.ui.state.UserWithStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: FaceRepository) : ViewModel() {

    val uiState = combine(
        repository.allUsers,
        repository.userStats
    ) { users, stats ->
        val statsMap = stats.associateBy { it.userId }
        val usersWithStats = users.map { user ->
            UserWithStats(
                user = user,
                recognitionCount = statsMap[user.id]?.recognitionCount ?: 0,
                lastSeenAt = statsMap[user.id]?.lastSeenAt
            )
        }
        HomeUiState.Ready(usersWithStats) as HomeUiState
    }
    .catch { e -> emit(HomeUiState.Error(e.message ?: "Failed to load users")) }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState.Loading
    )

    fun deleteUser(userId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteUser(userId)
        }
    }

    fun renameUser(userId: Int, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.renameUser(userId, newName.trim())
        }
    }

    companion object {
        fun factory(repository: FaceRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(repository) as T
        }
    }
}
