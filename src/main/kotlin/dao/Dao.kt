package org.burgas.dao

import org.burgas.database.ChatImageTable
import org.burgas.database.ChatTable
import org.burgas.database.CommentFileTable
import org.burgas.database.CommentTable
import org.burgas.database.CommunityImageTable
import org.burgas.database.CommunityTable
import org.burgas.database.IdentityChatTable
import org.burgas.database.IdentityCommunityTable
import org.burgas.database.IdentityImageTable
import org.burgas.database.IdentityTable
import org.burgas.database.MessageFileTable
import org.burgas.database.MessageTable
import org.burgas.database.PublicationFileTable
import org.burgas.database.PublicationImageTable
import org.burgas.database.PublicationTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class IdentityEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<IdentityEntity>(IdentityTable)

    var authority by IdentityTable.authority
    var username by IdentityTable.username
    var password by IdentityTable.password
    var email by IdentityTable.email
    var status by IdentityTable.status
    var firstname by IdentityTable.firstname
    var lastname by IdentityTable.lastname
    var patronymic by IdentityTable.patronymic

    val images by IdentityImageEntity referrersOn IdentityImageTable.identityId
    var chats by ChatEntity via IdentityChatTable
    var communities by CommunityEntity via IdentityCommunityTable
}

class IdentityImageEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<IdentityImageEntity>(IdentityImageTable)

    var name by IdentityImageTable.name
    var contentType by IdentityImageTable.contentType
    var preview by IdentityImageTable.preview
    var data by IdentityImageTable.data

    var identity by IdentityEntity referencedOn IdentityImageTable.identityId
}

class ChatEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ChatEntity>(ChatTable)

    var name by ChatTable.name
    var description by ChatTable.description

    var admin by IdentityEntity optionalReferencedOn ChatTable.adminId
    val images by ChatImageEntity referrersOn ChatImageTable.chatId
    var identities by IdentityEntity via IdentityChatTable
    val messages by MessageEntity referrersOn MessageTable.chatId

    var createdAt by ChatTable.createdAt
}

class ChatImageEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ChatImageEntity>(ChatImageTable)

    var name by ChatImageTable.name
    var contentType by ChatImageTable.contentType
    var preview by ChatImageTable.preview

    var chat by ChatEntity referencedOn ChatImageTable.chatId
}

class MessageEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<MessageEntity>(MessageTable)

    var chat by ChatEntity referencedOn MessageTable.chatId
    var sender by IdentityEntity optionalReferencedOn MessageTable.senderId

    var text by MessageTable.text
    val files by MessageFileEntity referrersOn MessageFileTable.messageId

    var createdAt by MessageTable.createdAt
}

class MessageFileEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<MessageFileEntity>(MessageFileTable)

    var name by MessageFileTable.name
    var contentType by MessageFileTable.contentType
    var data by MessageFileTable.data

    var message by MessageEntity referencedOn MessageFileTable.messageId
}

class CommunityEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CommunityEntity>(CommunityTable)

    var name by CommunityTable.name
    var description by CommunityTable.description

    var admin by IdentityEntity optionalReferencedOn CommunityTable.adminId
    val images by CommunityImageEntity referrersOn CommunityImageTable.communityId
    var identities by IdentityEntity via IdentityCommunityTable
    val publications by PublicationEntity referrersOn PublicationTable.communityId

    var createdAt by CommunityTable.createdAt
}

class CommunityImageEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CommunityImageEntity>(CommunityImageTable)

    var name by CommunityImageTable.name
    var contentType by CommunityImageTable.contentType
    var preview by CommunityImageTable.preview

    var community by CommunityEntity referencedOn CommunityImageTable.communityId
}

class PublicationEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PublicationEntity>(PublicationTable)

    var community by CommunityEntity referencedOn PublicationTable.communityId
    var sender by IdentityEntity optionalReferencedOn PublicationTable.senderId

    var text by PublicationTable.text

    val images by PublicationImageEntity referrersOn PublicationImageTable.publicationId
    val files by PublicationFileEntity referrersOn PublicationFileTable.publicationId
    val comments by CommentEntity referrersOn CommentTable.publicationId

    var createdAt by PublicationTable.createdAt
}

class PublicationFileEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PublicationFileEntity>(PublicationFileTable)

    var name by PublicationFileTable.name
    var contentType by PublicationFileTable.contentType
    var data by PublicationFileTable.data

    val publication by PublicationEntity referencedOn PublicationFileTable.publicationId
}

class PublicationImageEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PublicationImageEntity>(PublicationImageTable)

    var name by PublicationImageTable.name
    var contentType by PublicationImageTable.contentType
    var preview by PublicationImageTable.preview
    var data by PublicationImageTable.data

    var publication by PublicationEntity referencedOn PublicationImageTable.publicationId
}

class CommentEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CommentEntity>(CommentTable)

    var publication by PublicationEntity referencedOn CommentTable.publicationId
    var sender by IdentityEntity optionalReferencedOn CommentTable.senderId

    var text by CommentTable.text

    val files by CommentFileEntity referrersOn CommentFileTable.commentId

    var createdAt by CommentTable.createdAt
}

class CommentFileEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CommentFileEntity>(CommentFileTable)

    var name by CommentFileTable.name
    var contentType by CommentFileTable.contentType
    var data by CommentFileTable.data

    var comment by CommentEntity referencedOn CommentFileTable.commentId
}