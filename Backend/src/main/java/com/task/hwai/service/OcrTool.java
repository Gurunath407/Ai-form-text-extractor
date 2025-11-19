package com.task.hwai.service;

import java.nio.file.Path;
import java.util.List;

public interface OcrTool {
    List<PageResult> extract(Path path) throws Exception;

    // PageResult and Token are defined as nested classes below for convenience:
    public static class PageResult {
        private final int page;
        private final java.util.List<Token> tokens;
        private final java.util.List<java.util.Map<String,Object>> rawBlocks;

        public PageResult(int page, java.util.List<Token> tokens, java.util.List<java.util.Map<String,Object>> rawBlocks) {
            this.page = page;
            this.tokens = tokens;
            this.rawBlocks = rawBlocks;
        }

        public int getPage() { return page; }
        public java.util.List<Token> getTokens() { return tokens; }
        public java.util.List<java.util.Map<String,Object>> getRawBlocks() { return rawBlocks; }
    }

    public static class Token {
        private final String text;
        private final float confidence;

        public Token(String text, float confidence) {
            this.text = text;
            this.confidence = confidence;
        }

        public String getText() { return text; }
        public float getConfidence() { return confidence; }
    }
}
