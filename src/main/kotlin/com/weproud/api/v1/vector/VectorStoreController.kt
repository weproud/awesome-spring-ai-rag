package com.weproud.api.v1.vector

import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestBody
import jakarta.validation.Valid
import reactor.core.publisher.Mono
import com.weproud.api.v1.vector.dto.CreateVectorStoreResponse
import com.weproud.api.v1.vector.dto.IngestVectorStoreRequest
import com.weproud.api.v1.vector.dto.SearchVectorStoreRequest
import com.weproud.api.v1.vector.dto.SearchVectorStoreResponse

@Validated
@RestController
@RequestMapping("/api/v1/vectors")
class VectorStoreController(
    private val vectorStoreService: VectorStoreService
) {

    @PostMapping
    fun add(@RequestBody(required = false) req: IngestVectorStoreRequest?): Mono<CreateVectorStoreResponse> =
        vectorStoreService.ingestFromClasspathData(req)
            .map { CreateVectorStoreResponse(it) }

    @PostMapping("/search")
    fun search(@Valid @RequestBody req: SearchVectorStoreRequest): SearchVectorStoreResponse {
        return vectorStoreService.search(req)
    }
}
