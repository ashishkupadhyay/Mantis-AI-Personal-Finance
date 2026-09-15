package io.github.ashishkupadhyay.mantis.core.domain.message

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A transient message for the user, shown by the app shell as a snackbar (doc 05 §9). [action] is optional and
 * runs in the shell's scope when tapped — "Undo" after a delete (FR-TXN-2), for instance.
 */
data class UserMessage(
    val text: String,
    val actionLabel: String? = null,
    val action: (suspend () -> Unit)? = null,
    val long: Boolean = false,
)

/**
 * Process-wide message bus: ViewModels post, the shell collects. Messages posted while nothing collects are
 * dropped rather than replayed, so a stale "Deleted" never appears after a restart.
 */
@Singleton
class UserMessages @Inject constructor() {
    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = BUFFER)
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()

    fun show(message: UserMessage) {
        _messages.tryEmit(message)
    }

    fun show(text: String) = show(UserMessage(text))

    private companion object {
        const val BUFFER = 8
    }
}
