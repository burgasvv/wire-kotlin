package org.burgas.dao

import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.io.readByteArray
import org.burgas.database.*
import org.burgas.dto.*
import org.burgas.encryption.EncryptionManager
import org.burgas.util.RegexUtil
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

interface Dao

interface Document

interface Uploader<in E : Dao> {
    fun upload(entity: E, partData: PartData)
}

interface Creator<in R : Request> {
    fun insert(request: R)
}

interface Editor<in R : Request> {
    fun update(request: R)
}

interface ResponseFactory<out S : Response, out F : Response> {

    fun toShortResponse(): S

    fun toFullResponse(): F
}

class IdentityEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, Creator<IdentityRequest>, Editor<IdentityRequest>,
    ResponseFactory<IdentityShortResponse, IdentityFullResponse> {

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

    override fun insert(request: IdentityRequest) {
        this.authority = request.authority ?: Authority.USER
        this.username = request.username!!
        this.password = BCrypt.hashpw(request.password!!, BCrypt.gensalt())
        val emailMatches = RegexUtil.EMAIL.matches(request.email!!)
        this.email = if (emailMatches)
            request.email else throw IllegalArgumentException("Email regex not matched")
        this.status = request.status ?: true
        this.firstname = request.firstname!!
        this.lastname = request.lastname!!
        this.patronymic = request.patronymic!!
    }

    override fun update(request: IdentityRequest) {
        this.authority = request.authority ?: this.authority
        this.username = request.username ?: this.username
        if (request.email != null) {
            val emailMatches = RegexUtil.EMAIL.matches(request.email)
            this.email = if (emailMatches)
                request.email else throw IllegalArgumentException("Email regex not matched")
        }
        this.firstname = request.firstname ?: this.firstname
        this.lastname = request.lastname ?: this.lastname
        this.patronymic = request.patronymic ?: this.patronymic
    }

    override fun toShortResponse(): IdentityShortResponse {
        return IdentityShortResponse(
            id = this.id.value,
            username = this.username,
            email = this.email,
            firstname = this.firstname,
            lastname = this.lastname,
            patronymic = this.patronymic,
            images = this.images.map { it.toImageResponse() }
        )
    }

    override fun toFullResponse(): IdentityFullResponse {
        return IdentityFullResponse(
            id = this.id.value,
            username = this.username,
            email = this.email,
            firstname = this.firstname,
            lastname = this.lastname,
            patronymic = this.patronymic,
            images = this.images.map { it.toImageResponse() },
            chats = this.chats.map { it.toShortResponse() },
            communities = this.communities.map { it.toShortResponse() }
        )
    }
}

class IdentityImageEntity(id: EntityID<UUID>) : UUIDEntity(id), Document, Uploader<IdentityEntity> {
    companion object : UUIDEntityClass<IdentityImageEntity>(IdentityImageTable)

    var name by IdentityImageTable.name
    var contentType by IdentityImageTable.contentType
    var preview by IdentityImageTable.preview
    var data by IdentityImageTable.data

    var identity by IdentityEntity referencedOn IdentityImageTable.identityId

    @OptIn(InternalAPI::class)
    override fun upload(entity: IdentityEntity, partData: PartData) {
        if (partData is PartData.FileItem) {
            if (partData.contentType!!.contentType.startsWith("image")) {
                this.name = partData.originalFileName!!
                this.contentType = "${partData.contentType!!.contentType}/${partData.contentType!!.contentSubtype}"
                this.preview = false
                this.data = ExposedBlob(partData.provider().readBuffer.readByteArray())
                this.identity = entity
            } else {
                throw IllegalArgumentException("Wrong file content type")
            }
        } else {
            throw IllegalArgumentException("Part data is not FileItem")
        }
    }

    fun toImageResponse(): ImageResponse {
        return ImageResponse(
            id = this.id.value,
            name = this.name,
            contentType = this.contentType,
            preview = this.preview
        )
    }
}

class ChatEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, Creator<ChatRequest>, Editor<ChatRequest>,
    ResponseFactory<ChatShortResponse, ChatFullResponse> {

    companion object : EntityClass<UUID, ChatEntity>(ChatTable)

    var name by ChatTable.name
    var description by ChatTable.description

    var admin by IdentityEntity optionalReferencedOn ChatTable.adminId
    val images by ChatImageEntity referrersOn ChatImageTable.chatId
    var identities by IdentityEntity via IdentityChatTable
    val messages by MessageEntity referrersOn MessageTable.chatId

    var createdAt by ChatTable.createdAt

    override fun insert(request: ChatRequest) {
        this.name = request.name!!
        this.description = request.description!!
        this.admin = IdentityEntity.findById(request.adminId!!)!!
        this.identities = SizedCollection(identities + admin!!)
        this.createdAt = LocalDateTime.now().toKotlinLocalDateTime()
    }

    override fun update(request: ChatRequest) {
        this.name = request.name ?: this.name
        this.description = request.description ?: this.description
        val applicant = IdentityEntity.findById(request.adminId ?: UUID(0, 0))
        if (applicant != null && this.identities.contains(applicant)) {
            this.admin = applicant
        }
    }

    override fun toShortResponse(): ChatShortResponse {
        return ChatShortResponse(
            id = this.id.value,
            name = this.name,
            description = this.description,
            admin = this.admin?.toShortResponse(),
            images = this.images.map { it.toImageResponse() },
            createdAt = this.createdAt.toJavaLocalDateTime()
                .format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm"))
        )
    }

    override fun toFullResponse(): ChatFullResponse {
        return ChatFullResponse(
            id = this.id.value,
            name = this.name,
            description = this.description,
            admin = this.admin?.toShortResponse(),
            images = this.images.map { it.toImageResponse() },
            identities = this.identities.map { it.toShortResponse() },
            messages = this.messages.map { it.toShortResponse() },
            createdAt = this.createdAt.toJavaLocalDateTime()
                .format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm"))
        )
    }
}

class ChatImageEntity(id: EntityID<UUID>) : UUIDEntity(id), Document, Uploader<ChatEntity> {
    companion object : UUIDEntityClass<ChatImageEntity>(ChatImageTable)

    var name by ChatImageTable.name
    var contentType by ChatImageTable.contentType
    var preview by ChatImageTable.preview
    var data by ChatImageTable.data

    var chat by ChatEntity referencedOn ChatImageTable.chatId

    @OptIn(InternalAPI::class)
    override fun upload(entity: ChatEntity, partData: PartData) {
        if (partData is PartData.FileItem) {
            if (partData.contentType!!.contentType.startsWith("image")) {
                this.name = partData.originalFileName!!
                this.contentType = "${partData.contentType!!.contentType}/${partData.contentType!!.contentSubtype}"
                this.preview = false
                this.data = ExposedBlob(partData.provider().readBuffer.readByteArray())
                this.chat = entity
            } else {
                throw IllegalArgumentException("Wrong file content type")
            }
        } else {
            throw IllegalArgumentException("Part data is not FileItem")
        }
    }

    fun toImageResponse(): ImageResponse {
        return ImageResponse(
            id = this.id.value,
            name = this.name,
            contentType = this.contentType,
            preview = this.preview
        )
    }
}

class MessageEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, Creator<MessageRequest>,
    ResponseFactory<MessageShortResponse, MessageFullResponse> {

    companion object : UUIDEntityClass<MessageEntity>(MessageTable)

    var chat by ChatEntity referencedOn MessageTable.chatId
    var sender by IdentityEntity optionalReferencedOn MessageTable.senderId

    var text by MessageTable.text
    val files by MessageFileEntity referrersOn MessageFileTable.messageId

    var createdAt by MessageTable.createdAt

    override fun insert(request: MessageRequest) {
        this.chat = ChatEntity.findById(request.chatId!!)!!
        this.sender = IdentityEntity.findById(request.senderId!!)!!
        this.text = EncryptionManager.encrypt(request.text!!)
        this.createdAt = LocalDateTime.now().toKotlinLocalDateTime()
    }

    override fun toShortResponse(): MessageShortResponse {
        return MessageShortResponse(
            id = this.id.value,
            sender = this.sender?.toShortResponse(),
            text = EncryptionManager.decrypt(this.text),
            files = this.files.map { it.toFileResponse() },
            createdAt = this.createdAt.toJavaLocalDateTime()
                .format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm"))
        )
    }

    override fun toFullResponse(): MessageFullResponse {
        return MessageFullResponse(
            id = this.id.value,
            chat = this.chat.toShortResponse(),
            sender = this.sender?.toShortResponse(),
            text = EncryptionManager.decrypt(this.text),
            files = this.files.map { it.toFileResponse() },
            createdAt = this.createdAt.toJavaLocalDateTime()
                .format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm"))
        )
    }
}

class MessageFileEntity(id: EntityID<UUID>) : UUIDEntity(id), Document, Uploader<MessageEntity> {
    companion object : UUIDEntityClass<MessageFileEntity>(MessageFileTable)

    var name by MessageFileTable.name
    var contentType by MessageFileTable.contentType
    var data by MessageFileTable.data

    var message by MessageEntity referencedOn MessageFileTable.messageId

    @OptIn(InternalAPI::class)
    override fun upload(entity: MessageEntity, partData: PartData) {
        if (partData is PartData.FileItem) {
            this.name = partData.originalFileName!!
            this.contentType = "${partData.contentType!!.contentType}/${partData.contentType!!.contentSubtype}"
            this.data = ExposedBlob(partData.provider().readBuffer.readByteArray())
            this.message = entity
        } else {
            throw IllegalArgumentException("Part data is not FileItem")
        }
    }

    fun toFileResponse(): FileResponse {
        return FileResponse(
            id = this.id.value,
            name = this.name,
            contentType = this.contentType
        )
    }
}

class CommunityEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, Creator<CommunityRequest>, Editor<CommunityRequest>,
    ResponseFactory<CommunityShortResponse, CommunityFullResponse> {

    companion object : UUIDEntityClass<CommunityEntity>(CommunityTable)

    var name by CommunityTable.name
    var description by CommunityTable.description

    var admin by IdentityEntity optionalReferencedOn CommunityTable.adminId
    val images by CommunityImageEntity referrersOn CommunityImageTable.communityId
    var identities by IdentityEntity via IdentityCommunityTable
    val publications by PublicationEntity referrersOn PublicationTable.communityId

    var createdAt by CommunityTable.createdAt

    override fun insert(request: CommunityRequest) {
        this.name = request.name!!
        this.description = request.description!!
        this.admin = IdentityEntity.findById(request.adminId!!)!!
        this.identities = SizedCollection(identities + admin!!)
        this.createdAt = LocalDateTime.now().toKotlinLocalDateTime()
    }

    override fun update(request: CommunityRequest) {
        this.name = request.name ?: this.name
        this.description = request.description ?: this.description
        val applicant = IdentityEntity.findById(request.adminId ?: UUID(0, 0))
        if (applicant != null && this.identities.contains(applicant)) this.admin = applicant
    }

    override fun toShortResponse(): CommunityShortResponse {
        return CommunityShortResponse(
            id = this.id.value,
            name = this.name,
            description = this.description,
            admin = this.admin?.toShortResponse(),
            images = this.images.map { it.toImageResponse() },
            createdAt = this.createdAt.toJavaLocalDateTime()
                .format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm"))
        )
    }

    override fun toFullResponse(): CommunityFullResponse {
        return CommunityFullResponse(
            id = this.id.value,
            name = this.name,
            description = this.description,
            admin = this.admin?.toShortResponse(),
            images = this.images.map { it.toImageResponse() },
            identities = this.identities.map { it.toShortResponse() },
            publications = this.publications.map { it.toShortResponse() },
            createdAt = this.createdAt.toJavaLocalDateTime()
                .format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm"))
        )
    }
}

class CommunityImageEntity(id: EntityID<UUID>) : UUIDEntity(id), Document, Uploader<CommunityEntity> {
    companion object : UUIDEntityClass<CommunityImageEntity>(CommunityImageTable)

    var name by CommunityImageTable.name
    var contentType by CommunityImageTable.contentType
    var preview by CommunityImageTable.preview
    var data by CommunityImageTable.data

    var community by CommunityEntity referencedOn CommunityImageTable.communityId

    @OptIn(InternalAPI::class)
    override fun upload(entity: CommunityEntity, partData: PartData) {
        if (partData is PartData.FileItem) {
            if (partData.contentType!!.contentType.startsWith("image")) {
                this.name = partData.originalFileName!!
                this.contentType = "${partData.contentType!!.contentType}/${partData.contentType!!.contentSubtype}"
                this.preview = false
                this.data = ExposedBlob(partData.provider().readBuffer.readByteArray())
                this.community = entity
            } else {
                throw IllegalArgumentException("Wrong file content type")
            }
        } else {
            throw IllegalArgumentException("Part data is not FileItem")
        }
    }

    fun toImageResponse(): ImageResponse {
        return ImageResponse(
            id = this.id.value,
            name = this.name,
            contentType = this.contentType,
            preview = this.preview
        )
    }
}

class PublicationEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, Creator<PublicationRequest>,
    ResponseFactory<PublicationShortResponse, PublicationFullResponse> {

    companion object : UUIDEntityClass<PublicationEntity>(PublicationTable)

    var community by CommunityEntity referencedOn PublicationTable.communityId
    var sender by IdentityEntity optionalReferencedOn PublicationTable.senderId

    var text by PublicationTable.text

    val images by PublicationImageEntity referrersOn PublicationImageTable.publicationId
    val files by PublicationFileEntity referrersOn PublicationFileTable.publicationId
    val comments by CommentEntity referrersOn CommentTable.publicationId

    var createdAt by PublicationTable.createdAt

    override fun insert(request: PublicationRequest) {
        this.community = CommunityEntity.findById(request.communityId!!)!!
        this.sender = IdentityEntity.findById(request.senderId!!)!!
        this.text = EncryptionManager.encrypt(request.text!!)
        this.createdAt = LocalDateTime.now().toKotlinLocalDateTime()
    }

    override fun toShortResponse(): PublicationShortResponse {
        return PublicationShortResponse(
            id = this.id.value,
            sender = this.sender?.toShortResponse(),
            text = EncryptionManager.decrypt(this.text),
            images = this.images.map { it.toImageResponse() },
            files = this.files.map { it.toFileResponse() },
            createdAt = this.createdAt.toJavaLocalDateTime()
                .format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm"))
        )
    }

    override fun toFullResponse(): PublicationFullResponse {
        return PublicationFullResponse(
            id = this.id.value,
            community = this.community.toShortResponse(),
            sender = this.sender?.toShortResponse(),
            text = EncryptionManager.decrypt(this.text),
            images = this.images.map { it.toImageResponse() },
            files = this.files.map { it.toFileResponse() },
            comments = this.comments.map { it.toShortResponse() },
            createdAt = this.createdAt.toJavaLocalDateTime()
                .format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm"))
        )
    }
}

class PublicationFileEntity(id: EntityID<UUID>) : UUIDEntity(id), Document, Uploader<PublicationEntity> {
    companion object : UUIDEntityClass<PublicationFileEntity>(PublicationFileTable)

    var name by PublicationFileTable.name
    var contentType by PublicationFileTable.contentType
    var data by PublicationFileTable.data

    var publication by PublicationEntity referencedOn PublicationFileTable.publicationId

    @OptIn(InternalAPI::class)
    override fun upload(entity: PublicationEntity, partData: PartData) {
        if (partData is PartData.FileItem) {
            this.name = partData.originalFileName!!
            this.contentType = "${partData.contentType!!.contentType}/${partData.contentType!!.contentSubtype}"
            this.data = ExposedBlob(partData.provider().readBuffer.readByteArray())
            this.publication = entity
        } else {
            throw IllegalArgumentException("Part data is not FileItem")
        }
    }

    fun toFileResponse(): FileResponse {
        return FileResponse(
            id = this.id.value,
            name = this.name,
            contentType = this.contentType
        )
    }
}

class PublicationImageEntity(id: EntityID<UUID>) : UUIDEntity(id), Document, Uploader<PublicationEntity> {
    companion object : UUIDEntityClass<PublicationImageEntity>(PublicationImageTable)

    var name by PublicationImageTable.name
    var contentType by PublicationImageTable.contentType
    var preview by PublicationImageTable.preview
    var data by PublicationImageTable.data

    var publication by PublicationEntity referencedOn PublicationImageTable.publicationId

    @OptIn(InternalAPI::class)
    override fun upload(entity: PublicationEntity, partData: PartData) {
        if (partData is PartData.FileItem) {
            if (partData.contentType!!.contentType.startsWith("image")) {
                this.name = partData.originalFileName!!
                this.contentType = "${partData.contentType!!.contentType}/${partData.contentType!!.contentSubtype}"
                this.preview = false
                this.data = ExposedBlob(partData.provider().readBuffer.readByteArray())
                this.publication = entity
            } else {
                throw IllegalArgumentException("Wrong file content type")
            }
        } else {
            throw IllegalArgumentException("Part data is not FileItem")
        }
    }

    fun toImageResponse(): ImageResponse {
        return ImageResponse(
            id = this.id.value,
            name = this.name,
            contentType = this.contentType,
            preview = this.preview
        )
    }
}

class CommentEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, Creator<CommentRequest>,
    ResponseFactory<CommentShortResponse, CommentFullResponse> {

    companion object : UUIDEntityClass<CommentEntity>(CommentTable)

    var publication by PublicationEntity referencedOn CommentTable.publicationId
    var sender by IdentityEntity optionalReferencedOn CommentTable.senderId

    var text by CommentTable.text

    val files by CommentFileEntity referrersOn CommentFileTable.commentId

    var createdAt by CommentTable.createdAt

    override fun insert(request: CommentRequest) {
        this.publication = PublicationEntity.findById(request.publicationId!!)!!
        this.sender = IdentityEntity.findById(request.senderId!!)!!
        this.text = EncryptionManager.encrypt(request.text!!)
        this.createdAt = LocalDateTime.now().toKotlinLocalDateTime()
    }

    override fun toShortResponse(): CommentShortResponse {
        return CommentShortResponse(
            id = this.id.value,
            sender = this.sender?.toShortResponse(),
            text = EncryptionManager.decrypt(this.text),
            files = this.files.map { it.toFileResponse() },
            createdAt = this.createdAt.toJavaLocalDateTime()
                .format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm"))
        )
    }

    override fun toFullResponse(): CommentFullResponse {
        return CommentFullResponse(
            id = this.id.value,
            publication = this.publication.toShortResponse(),
            sender = this.sender?.toShortResponse(),
            text = EncryptionManager.decrypt(this.text),
            files = this.files.map { it.toFileResponse() },
            createdAt = this.createdAt.toJavaLocalDateTime()
                .format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm"))
        )
    }
}

class CommentFileEntity(id: EntityID<UUID>) : UUIDEntity(id), Document, Uploader<CommentEntity> {
    companion object : UUIDEntityClass<CommentFileEntity>(CommentFileTable)

    var name by CommentFileTable.name
    var contentType by CommentFileTable.contentType
    var data by CommentFileTable.data

    var comment by CommentEntity referencedOn CommentFileTable.commentId

    @OptIn(InternalAPI::class)
    override fun upload(entity: CommentEntity, partData: PartData) {
        if (partData is PartData.FileItem) {
            this.name = partData.originalFileName!!
            this.contentType = "${partData.contentType!!.contentType}/${partData.contentType!!.contentSubtype}"
            this.data = ExposedBlob(partData.provider().readBuffer.readByteArray())
            this.comment = entity
        } else {
            throw IllegalArgumentException("Part data is not FileItem")
        }
    }

    fun toFileResponse(): FileResponse {
        return FileResponse(
            id = this.id.value,
            name = this.name,
            contentType = this.contentType
        )
    }
}