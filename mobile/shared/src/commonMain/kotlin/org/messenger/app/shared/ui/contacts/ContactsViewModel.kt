package org.messenger.app.shared.ui.contacts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.messenger.app.shared.data.model.ContactDto
import org.messenger.app.shared.domain.repository.ContactsRepository

data class ContactsUiState(
    val contacts: List<ContactDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class ContactsViewModel(
    private val repository: ContactsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(ContactsUiState())
    val state: StateFlow<ContactsUiState> = _state.asStateFlow()

    init {
        loadContacts()
    }

    fun loadContacts() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val contacts = repository.getContacts()
                _state.update { it.copy(contacts = contacts, isLoading = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }

    fun addContact(phone: String, displayName: String, onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            try {
                val contact = repository.createContact(phone.trim(), displayName.trim())
                _state.update {
                    it.copy(
                        contacts = (it.contacts + contact).sortedBy { c -> c.displayName.lowercase() }
                    )
                }
                onDone(true)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка создания контакта") }
                onDone(false)
            }
        }
    }

    fun updateContact(
        contactId: String,
        displayName: String?,
        phone: String?,
        onDone: (Boolean) -> Unit = {},
    ) {
        scope.launch {
            try {
                val updated = repository.updateContact(contactId, displayName, phone)
                _state.update { s ->
                    s.copy(
                        contacts = s.contacts.map { if (it.id == updated.id) updated else it }
                            .sortedBy { c -> c.displayName.lowercase() }
                    )
                }
                onDone(true)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка обновления") }
                onDone(false)
            }
        }
    }

    fun deleteContact(contactId: String) {
        scope.launch {
            try {
                repository.deleteContact(contactId)
                _state.update { s ->
                    s.copy(contacts = s.contacts.filterNot { it.id == contactId })
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Ошибка удаления") }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}