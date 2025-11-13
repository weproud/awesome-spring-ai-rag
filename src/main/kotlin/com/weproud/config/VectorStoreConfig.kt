package com.weproud.config

import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder
import java.nio.file.Files
import java.nio.file.Paths
import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.document.Document as L4JDocument
import dev.langchain4j.data.document.splitter.DocumentSplitters

@Configuration
class VectorStoreConfig {

    @Bean
    fun vectorStoreInitializer(vectorStore: VectorStore): ApplicationRunner = ApplicationRunner {
        val dir = Paths.get("src/main/resources/data")
        if (!Files.exists(dir) || !Files.isDirectory(dir)) return@ApplicationRunner

        val b = FilterExpressionBuilder()
        vectorStore.delete(b.ne("id", "__never__").build())

        val splitter = DocumentSplitters.recursive(1500, 100)
        val docs = mutableListOf<Document>()
        Files.list(dir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .forEach { file ->
                    val content = Files.readString(file)
                    val filename = file.fileName.toString()
                    val docType = filename.substringBeforeLast('.')
                    val md = Metadata().apply {
                        put("doc_type", docType)
                        put("file_name", filename)
                    }
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

        if (docs.isNotEmpty()) {
            vectorStore.add(docs)
        }
    }
}
