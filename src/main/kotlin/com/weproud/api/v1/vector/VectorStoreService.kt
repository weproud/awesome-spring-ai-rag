package com.weproud.api.v1.vector

import com.weproud.api.v1.vector.dto.CreateVectorStoreRequest
import com.weproud.api.v1.vector.dto.SearchVectorStoreHit
import com.weproud.api.v1.vector.dto.SearchVectorStoreRequest
import com.weproud.api.v1.vector.dto.SearchVectorStoreResponse
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

@Service
class VectorStoreService(
    private val vectorStore: VectorStore
) {

    fun addDocuments(request: CreateVectorStoreRequest): Int {
        val docs = request.documents.map { Document(it.content, it.metadata ?: emptyMap()) }
        vectorStore.add(docs)
        return docs.size
    }

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
