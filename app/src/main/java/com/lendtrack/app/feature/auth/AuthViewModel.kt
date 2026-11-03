package com.lendtrack.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed class AuthUiState {
    data object Loading                            : AuthUiState()
    data object Unauthenticated                    : AuthUiState()
    data class  Authenticated(val user: FirebaseUser) : AuthUiState()
    data class  Error(val message: String)         : AuthUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor() : ViewModel() {

    private val auth: FirebaseAuth? by lazy {
        runCatching { FirebaseAuth.getInstance() }.getOrNull()
    }

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        // Resolve immediately — no async check needed; currentUser is cached
        val user = auth?.currentUser
        _state.value = if (user != null) AuthUiState.Authenticated(user)
                       else AuthUiState.Unauthenticated
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank())    { _state.value = AuthUiState.Error("Email is required."); return }
        if (password.isBlank()) { _state.value = AuthUiState.Error("Password is required."); return }

        val firebaseAuth = auth ?: run {
            _state.value = AuthUiState.Error("Firebase Auth is unavailable.")
            return
        }

        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val result = firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
                val user   = result.user ?: throw Exception("Sign-in failed — no user returned.")
                _state.value = AuthUiState.Authenticated(user)
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(friendlyError(e.message))
            }
        }
    }

    fun signOut() {
        auth?.signOut()
        _state.value = AuthUiState.Unauthenticated
    }

    fun clearError() {
        if (_state.value is AuthUiState.Error) _state.value = AuthUiState.Unauthenticated
    }

    private fun friendlyError(msg: String?): String = when {
        msg == null                                   -> "Sign-in failed."
        msg.contains("INVALID_LOGIN_CREDENTIALS")     -> "Incorrect email or password."
        msg.contains("password")                      -> "Incorrect password."
        msg.contains("no user record")                -> "No account found with this email."
        msg.contains("network")                       -> "Network error. Check your connection."
        msg.contains("too-many-requests")             -> "Too many attempts. Try again later."
        else                                          -> "Sign-in failed. Please try again."
    }
}
