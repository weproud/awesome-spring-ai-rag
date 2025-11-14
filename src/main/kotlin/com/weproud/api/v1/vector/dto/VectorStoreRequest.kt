package com.weproud.api.v1.vector.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SearchVectorStoreRequest(
    @field:NotBlank
    val query: String,
    @field:Min(1)
    val topK: Int? = null
)

data class DocumentRequest(
    @field:NotBlank
    val content: String,
    val metadata: Map<String, String>? = null
)

data class CreateVectorStoreRequest(
    @field:Size(min = 1)
    val documents: List<@Valid DocumentRequest>
)

data class IngestVectorStoreRequest(
    val deleteAll: Boolean? = false,
    val labels: List<String>? = null,
    @field:Min(1)
    val batchSize: Int? = null
)
