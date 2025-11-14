package com.weproud.api.v1.chat

import com.weproud.api.v1.chat.dto.ChatStreamRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Validated
@RestController
@RequestMapping("/api/v1/chat")
class ChatController(
    private val chatService: ChatService
) {

    @PostMapping(
        produces = [MediaType.TEXT_PLAIN_VALUE]
    )
    fun text(@Valid @RequestBody req: ChatStreamRequest): Mono<String> =
        chatService.stream(req).collectList().map { it.joinToString(separator = "") }

    @PostMapping(
        "/stream",
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE]
    )
    fun stream(@Valid @RequestBody req: ChatStreamRequest): Flux<String> =
        chatService.stream(req)
}
