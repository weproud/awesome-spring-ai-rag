package com.weproud.api.v1.vector

import com.weproud.api.v1.vector.dto.CreateVectorStoreRequest
import com.weproud.api.v1.vector.dto.IngestVectorStoreRequest
import com.weproud.api.v1.vector.dto.SearchVectorStoreHit
import com.weproud.api.v1.vector.dto.SearchVectorStoreRequest
import com.weproud.api.v1.vector.dto.SearchVectorStoreResponse
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.document.Document as L4JDocument
import dev.langchain4j.data.document.splitter.DocumentSplitters
import io.github.oshai.kotlinlogging.KotlinLogging

@Service
class VectorStoreService(
    private val vectorStore: VectorStore
) {
    private val log = KotlinLogging.logger {}

    fun addDocuments(request: CreateVectorStoreRequest): Mono<Int> =
        Mono.fromCallable {
            val docs = request.documents.map { Document(it.content, it.metadata ?: emptyMap()) }
            vectorStore.add(docs)
            docs.size
        }.subscribeOn(Schedulers.boundedElastic())

    fun ingestFromClasspathData(options: IngestVectorStoreRequest?): Mono<Int> =
        Mono.fromCallable {
            log.info { "VectorStore 수동 적재 시작: classpath:data/*" }
            val opts = options ?: IngestVectorStoreRequest()
            val labels = opts.labels?.toSet()

            if (opts.deleteAll == true) {
                val b = FilterExpressionBuilder()
                vectorStore.delete(b.ne("id", "__never__").build())
                log.info { "기존 벡터 삭제 완료(전체)" }
            } else if (!labels.isNullOrEmpty()) {
                labels.forEach { lab ->
                    val b = FilterExpressionBuilder()
                    vectorStore.delete(b.eq("doc_type", lab).build())
                }
                log.info { "기존 벡터 삭제 완료(라벨 기준): ${labels.size}" }
            }

            val splitter = DocumentSplitters.recursive(500, 50)
            val docs = mutableListOf<Document>()
            val resolver = PathMatchingResourcePatternResolver()
            val resources = resolver.getResources("classpath:data/*")
            if (resources.isEmpty()) {
                log.info { "적재 스킵: classpath:data에 리소스 없음" }
                return@fromCallable 0
            }
            resources.forEach { res ->
                val filename = res.filename ?: return@forEach
                val content = res.inputStream.bufferedReader().use { it.readText() }
                val docType = filename.substringBeforeLast('.')
                if (!labels.isNullOrEmpty() && !labels.contains(docType)) return@forEach
                val md = Metadata().apply {
                    put("doc_type", docType)
                    put("file_name", filename)
                }
                val qaPairs = mutableListOf<Pair<String, String>>()
                val lines = content.lines()
                var i = 0
                while (i < lines.size) {
                    val qLine = lines[i].trim()
                    if (qLine.startsWith("Q:")) {
                        val q = qLine.removePrefix("Q:").trim()
                        var a = ""
                        var j = i + 1
                        while (j < lines.size) {
                            val l = lines[j].trim()
                            if (l.startsWith("A:")) {
                                a = l.removePrefix("A:").trim()
                                j++
                                break
                            }
                            j++
                        }
                        if (q.isNotEmpty() && a.isNotEmpty()) qaPairs.add(q to a)
                        i = j
                    } else {
                        i++
                    }
                }
                if (qaPairs.isNotEmpty()) {
                    qaPairs.forEachIndexed { idx, (q, a) ->
                        val text = "Q: $q\nA: $a"
                        val metaAny = mutableMapOf<String, Any>(
                            "doc_type" to docType,
                            "file_name" to filename,
                            "id" to "$filename#qa$idx",
                            "question" to q
                        )
                        docs.add(Document(text, metaAny))
                    }
                } else {
                    val document = L4JDocument.from(content, md)
                    val segments = splitter.split(document)
                    segments.forEachIndexed { idx, seg ->
                        val meta = seg.metadata().toMap().mapValues { it.value.toString() }.toMutableMap()
                        meta["id"] = "$filename#$idx"
                        val metaAny = mutableMapOf<String, Any>()
                        metaAny.putAll(meta)
                        docs.add(Document(seg.text(), metaAny))
                    }
                }
            }

            if (docs.isEmpty()) return@fromCallable 0
            val batch = opts.batchSize?.coerceAtLeast(1) ?: 64
            var inserted = 0
            try {
                docs.chunked(batch).forEach { chunk ->
                    vectorStore.add(chunk)
                    inserted += chunk.size
                }
                log.info { "VectorStore 수동 적재 완료: $inserted (배치: $batch)" }
                inserted
            } catch (e: Exception) {
                log.error(e) { "수동 적재 중 임베딩 실패" }
                0
            }
        }.subscribeOn(Schedulers.boundedElastic())

    fun search(request: SearchVectorStoreRequest): SearchVectorStoreResponse {
        val topK = request.topK ?: 5
        val results = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(request.query)
                .topK(topK)
                .build()
        )
        val hits = results.map { doc ->
            SearchVectorStoreHit(
                content = doc.text ?: "",
                metadata = doc.metadata
            )
        }
        return SearchVectorStoreResponse(hits = hits, count = hits.size)
    }
}
