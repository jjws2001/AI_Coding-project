package com.aicoding.ai.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeSymbolExtractorTest {

    @Test
    void extractsJvmTypesAndFunctions() {
        assertThat(CodeSymbolExtractor.extract("UserService.java", """
                public class UserService {
                    public User validateAccessToken(String token) { return null; }
                }
                """))
                .contains("UserService", "validateAccessToken");

        assertThat(CodeSymbolExtractor.extract("Parser.kt", """
                object Parser {
                    suspend fun parseDocument(input: String) = input
                }
                """))
                .contains("Parser", "parseDocument");
    }

    @Test
    void extractsPythonGoAndJavaScriptFunctions() {
        assertThat(CodeSymbolExtractor.extract("parser.py", """
                async def parse_document(source):
                    return source
                """))
                .contains("parse_document");

        assertThat(CodeSymbolExtractor.extract("handler.go", """
                func (h *Handler) HandleRequest(ctx Context) error { return nil }
                """))
                .contains("HandleRequest");

        assertThat(CodeSymbolExtractor.extract("client.ts", """
                export async function fetchProject(id: number) { return id; }
                const normalizePath = (path: string) => path;
                """))
                .contains("fetchProject", "normalizePath");
    }
}
