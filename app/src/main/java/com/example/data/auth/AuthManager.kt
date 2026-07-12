package com.example.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AuthManager {
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _authState = MutableStateFlow<AuthState>(getInitialAuthState())
    val authState: StateFlow<AuthState> = _authState

    sealed class AuthState {
        object Unauthenticated : AuthState()
        data class Authenticated(val user: FirebaseUser) : AuthState()
        object Guest : AuthState()
    }

    private fun getInitialAuthState(): AuthState {
        val user = firebaseAuth.currentUser
        return if (user != null) {
            AuthState.Authenticated(user)
        } else {
            AuthState.Unauthenticated
        }
    }

    val currentUserId: String
        get() = when (val state = _authState.value) {
            is AuthState.Authenticated -> state.user.uid
            is AuthState.Guest -> "GUEST"
            else -> ""
        }

    fun isUserLoggedIn(): Boolean {
        return _authState.value is AuthState.Authenticated || _authState.value is AuthState.Guest
    }

    fun loginWithEmail(email: String, password: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (email.isEmpty() || password.isEmpty()) {
            onFailure("E-mail e senha são obrigatórios.")
            return
        }
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    _authState.value = AuthState.Authenticated(user)
                    onSuccess()
                } else {
                    onFailure("Erro inesperado ao realizar login.")
                }
            }
            .addOnFailureListener { exception ->
                onFailure(exception.localizedMessage ?: "Falha na autenticação.")
            }
    }

    fun signUpWithEmail(email: String, password: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (email.isEmpty() || password.isEmpty()) {
            onFailure("E-mail e senha são obrigatórios.")
            return
        }
        if (password.length < 6) {
            onFailure("A senha deve ter no mínimo 6 caracteres.")
            return
        }
        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    _authState.value = AuthState.Authenticated(user)
                    onSuccess()
                } else {
                    onFailure("Erro inesperado ao cadastrar.")
                }
            }
            .addOnFailureListener { exception ->
                onFailure(exception.localizedMessage ?: "Erro ao criar conta.")
            }
    }

    fun loginAsGuest() {
        _authState.value = AuthState.Guest
    }

    fun loginWithGoogle(idToken: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    _authState.value = AuthState.Authenticated(user)
                    onSuccess()
                } else {
                    onFailure("Erro inesperado ao realizar login com o Google.")
                }
            }
            .addOnFailureListener { exception ->
                onFailure(exception.localizedMessage ?: "Falha ao autenticar com o Google no Firebase.")
            }
    }

    fun logout(onComplete: () -> Unit) {
        firebaseAuth.signOut()
        _authState.value = AuthState.Unauthenticated
        onComplete()
    }
}
