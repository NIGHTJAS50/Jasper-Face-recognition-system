package com.jasper.app.ui.state

import com.jasper.app.data.db.UserEntity

data class UserWithStats(
    val user: UserEntity,
    val recognitionCount: Int = 0,
    val lastSeenAt: Long? = null
)

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Ready(val users: List<UserWithStats>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
