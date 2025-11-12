package com.weproud.api.v1.chat

import com.weproud.api.v1.chat.dto.ChatStreamRequest
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.ai.chat.prompt.SystemPromptTemplate
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux

@Service
class ChatService(
    private val chatClient: ChatClient,
    private val vectorStore: VectorStore
) {

    fun stream(request: ChatStreamRequest): Flux<String> {
        val topK = request.topK ?: 5
        val searchBuilder = SearchRequest.builder()
            .query(request.query)
            .topK(topK)

        if (request.label != null) {
            val f = FilterExpressionBuilder().eq("label", request.label).build()
            searchBuilder.filterExpression(f)
        }

        val docs = vectorStore.similaritySearch(searchBuilder.build())
        val context = docs.joinToString(separator = "\n\n") { it.text ?: "" }

        val systemTemplateRes = ClassPathResource("prompts/system.st")
        val userTemplateRes = ClassPathResource("prompts/user.st")

        val systemMessage: Message = SystemPromptTemplate(systemTemplateRes).createMessage()
        val userMessage: Message = PromptTemplate(userTemplateRes).createMessage(mapOf(
            "query" to request.query,
            "context" to context
        ))

        val prompt = Prompt(listOf(systemMessage, userMessage))

        return chatClient.prompt(prompt)
            .stream()
            .content()
    }
}
