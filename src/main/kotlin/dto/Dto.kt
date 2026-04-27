package org.burgas.dto

import kotlinx.serialization.Serializable
import org.burgas.database.Authority
import org.burgas.serialization.UUIDSerializer
import java.util.*

interface Request {
    val id: UUID?
}

interface Response {
    val id: UUID?
}

@Serializable
data class CsrfToken(@Serializable(with = UUIDSerializer::class)  val value: UUID)

@Serializable
data class ExceptionResponse(
    val status: String,
    val code: Int,
    val message: String
)

@Serializable
data class DocumentRequest(
    val documentIds: List<@Serializable(with = UUIDSerializer::class) UUID>
)

@Serializable
data class GroupRequest(
    @Serializable(with = UUIDSerializer::class)
    val groupId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val applicantId: UUID
)

@Serializable
data class ImageResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val contentType: String? = null,
    val preview: Boolean? = null
) : Response

@Serializable
data class FileResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val contentType: String? = null
) : Response

@Serializable
data class IdentityRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val authority: Authority? = null,
    val username: String? = null,
    val password: String? = null,
    val email: String? = null,
    val status: Boolean? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null
) : Request

@Serializable
data class IdentityShortResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val username: String? = null,
    val email: String? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val images: List<ImageResponse>? = null
) : Response

@Serializable
data class IdentityFullResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val username: String? = null,
    val email: String? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val images: List<ImageResponse>? = null,
    val chats: List<ChatShortResponse>? = null,
    val communities: List<CommunityShortResponse>? = null
) : Response

@Serializable
data class ChatRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val adminId: UUID? = null
) : Request

@Serializable
data class ChatShortResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val admin: IdentityShortResponse? = null,
    val images: List<ImageResponse>? = null,
    val createdAt: String? = null
) : Response

@Serializable
data class ChatFullResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val admin: IdentityShortResponse? = null,
    val images: List<ImageResponse>? = null,
    val identities: List<IdentityShortResponse>? = null,
    val messages: List<MessageShortResponse>? = null,
    val createdAt: String? = null
) : Response

@Serializable
data class MessageRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val chatId: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val senderId: UUID? = null,
    val text: String? = null
) : Request

@Serializable
data class MessageShortResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val sender: IdentityShortResponse? = null,
    val text: String? = null,
    val files: List<FileResponse>? = null,
    val createdAt: String? = null
) : Response

@Serializable
data class MessageFullResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val chat: ChatShortResponse? = null,
    val sender: IdentityShortResponse? = null,
    val text: String? = null,
    val files: List<FileResponse>? = null,
    val createdAt: String? = null
) : Response

@Serializable
data class CommunityRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val adminId: UUID? = null
) : Request

@Serializable
data class CommunityShortResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val admin: IdentityShortResponse? = null,
    val images: List<ImageResponse>? = null,
    val createdAt: String? = null
) : Response

@Serializable
data class CommunityFullResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val admin: IdentityShortResponse? = null,
    val images: List<ImageResponse>? = null,
    val identities: List<IdentityShortResponse>? = null,
    val publications: List<PublicationShortResponse>? = null,
    val createdAt: String? = null
) : Response

@Serializable
data class PublicationRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val communityId: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val senderId: UUID? = null,
    val text: String? = null
) : Request

@Serializable
data class PublicationShortResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val sender: IdentityShortResponse? = null,
    val text: String? = null,
    val images: List<ImageResponse>? = null,
    val files: List<FileResponse>? = null,
    val createdAt: String? = null
) : Response

@Serializable
data class PublicationFullResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val community: CommunityShortResponse? = null,
    val sender: IdentityShortResponse? = null,
    val text: String? = null,
    val images: List<ImageResponse>? = null,
    val files: List<FileResponse>? = null,
    val comments: List<CommentShortResponse>? = null,
    val createdAt: String? = null
) : Response

@Serializable
data class CommentRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val publicationId: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val senderId: UUID? = null,
    val text: String? = null
) : Request

@Serializable
data class CommentShortResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val sender: IdentityShortResponse? = null,
    val text: String? = null,
    val files: List<FileResponse>? = null,
    val createdAt: String? = null
) : Response

@Serializable
data class CommentFullResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val publication: PublicationShortResponse? = null,
    val sender: IdentityShortResponse? = null,
    val text: String? = null,
    val files: List<FileResponse>? = null,
    val createdAt: String? = null
) : Response