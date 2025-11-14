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
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Service
class ChatService(
    private val chatClient: ChatClient,
    private val vectorStore: VectorStore
) {

    private fun extractQA(text: String): Pair<String, String>? {
        val lines = text.lines()
        val q = lines.firstOrNull { it.trim().startsWith("Q:") }?.substringAfter("Q:")?.trim().orEmpty()
        val a = lines.firstOrNull { it.trim().startsWith("A:") }?.substringAfter("A:")?.trim().orEmpty()
        return if (q.isNotEmpty() && a.isNotEmpty()) q to a else null
    }

    private fun tokenize(s: String): Set<String> =
        s.lowercase().replace(Regex("[^가-힣a-z0-9]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .toSet()

    private fun score(query: String, question: String): Int {
        val tq = tokenize(query)
        val tq2 = tokenize(question)
        return tq.intersect(tq2).size
    }

    fun stream(request: ChatStreamRequest): Flux<String> =
        Mono.fromCallable {
            val topK = request.topK ?: 3
            val searchBuilder = SearchRequest.builder()
                .query(request.query)
                .topK(topK)

            if (request.label != null) {
                val f = FilterExpressionBuilder().eq("doc_type", request.label).build()
                searchBuilder.filterExpression(f)
            }

            var docs = vectorStore.similaritySearch(searchBuilder.build())
            var context = run {
                if (docs.isEmpty()) "" else {
                    val best = docs.maxByOrNull { d ->
                        val qMeta = d.metadata["question"]?.toString()
                        val qText = qMeta ?: extractQA(d.text ?: "")?.first.orEmpty()
                        score(request.query, qText)
                    }
                    val qa = best?.text?.let { extractQA(it) }
                    qa?.second ?: best?.text.orEmpty()
                }
            }
            if (context.isBlank()) {
                val fallbackBuilder = SearchRequest.builder()
                    .query(request.query)
                    .topK(topK * 2)
                docs = vectorStore.similaritySearch(fallbackBuilder.build())
                context = if (docs.isEmpty()) "" else {
                    val best = docs.maxByOrNull { d ->
                        val qMeta = d.metadata["question"]?.toString()
                        val qText = qMeta ?: extractQA(d.text ?: "")?.first.orEmpty()
                        score(request.query, qText)
                    }
                    val qa = best?.text?.let { extractQA(it) }
                    qa?.second ?: best?.text.orEmpty()
                }
            }

            val systemTemplateRes = ClassPathResource("prompts/system.st")
            val userTemplateRes = ClassPathResource("prompts/user.st")

            val systemMessage: Message = SystemPromptTemplate(systemTemplateRes).createMessage()
            val userMessage: Message = PromptTemplate(userTemplateRes).createMessage(mapOf(
                "query" to request.query,
                "context" to context
            ))

            Prompt(listOf(systemMessage, userMessage))
        }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany { prompt ->
                chatClient.prompt(prompt)
                    .stream()
                    .content()
            }
}
