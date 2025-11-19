// language: java
package com.task.hwai.config;

public final class GenerationResult {
    private final String content;

    public GenerationResult(String content) {
        this.content = content;
    }

    public String content() {
        return content;
    }

    @Override
    public String toString() {
        return content;
    }
}
