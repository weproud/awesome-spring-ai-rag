package com.weproud.api.v1.chat.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class ChatStreamRequest(
    @field:NotBlank
    val query: String,
    @field:Positive
    val topK: Int? = null,
    val label: String? = null
)
