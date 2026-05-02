package crucible.lens.data.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnthropicImageSource(
    val type: String,           // no default — kotlinx.serialization skips fields with defaults (encodeDefaults=false)
    @SerialName("media_type") val mediaType: String,
    val data: String
)

@Serializable
data class AnthropicContentBlock(
    val type: String,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val source: AnthropicImageSource? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val text: String? = null
)

@Serializable
data class AnthropicMessage(
    val role: String,
    val content: List<AnthropicContentBlock>
)

@Serializable
data class AnthropicMessagesRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<AnthropicMessage>
)

@Serializable
data class AnthropicResponseContent(
    val type: String,
    val text: String? = null
)

@Serializable
data class AnthropicMessagesResponse(
    val content: List<AnthropicResponseContent>
)
