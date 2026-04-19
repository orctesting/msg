package org.messenger.app.shared.domain.repository

import org.messenger.app.shared.data.model.ContactDto
import org.messenger.app.shared.data.remote.ApiService

class ContactsRepository(private val api: ApiService) {

    suspend fun getContacts(): List<ContactDto> = api.getContacts()

    suspend fun createContact(phone: String, displayName: String): ContactDto =
        api.createContact(phone, displayName)

    suspend fun updateContact(
        contactId: String,
        displayName: String? = null,
        phone: String? = null,
    ): ContactDto = api.updateContact(contactId, displayName, phone)

    suspend fun deleteContact(contactId: String) = api.deleteContact(contactId)

    suspend fun dismissPeer(peerUserId: String) = api.dismissPeer(peerUserId)
}