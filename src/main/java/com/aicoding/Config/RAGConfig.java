package com.aicoding.Config;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RAGConfig {

    @Bean
    public DocumentSplitter documentSplitter() {
        return DocumentSplitters.recursive(500, 50);
    }
}
