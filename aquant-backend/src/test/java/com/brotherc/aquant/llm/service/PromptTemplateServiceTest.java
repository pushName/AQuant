package com.brotherc.aquant.llm.service;

import com.brotherc.aquant.llm.repository.AnalysisJobPromptSnapshotRepository;
import com.brotherc.aquant.llm.repository.PromptTemplateRepository;
import com.brotherc.aquant.llm.repository.PromptVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/** 提示词变量白名单测试，不需要连接数据库。 */
class PromptTemplateServiceTest {

    private PromptTemplateService createService() {
        return new PromptTemplateService(
                mock(PromptTemplateRepository.class),
                mock(PromptVersionRepository.class),
                mock(AnalysisJobPromptSnapshotRepository.class),
                new ObjectMapper());
    }

    @Test
    void shouldAcceptAllSupportedRuntimeVariables() {
        String content = "{{ticker}} {{date}} {{current_date}} {{reports}} {{messages}} {{tool_names}} {{debate_round}}";
        assertDoesNotThrow(() -> createService().validateContent(content, Arrays.asList(
                "ticker", "date", "current_date", "reports", "messages", "tool_names", "debate_round")));
    }

    @Test
    void shouldRejectUndeclaredOrUnsupportedRuntimeVariable() {
        assertThrows(IllegalArgumentException.class,
                () -> createService().validateContent("{{ticker}}", Arrays.asList("date")));
        assertThrows(IllegalArgumentException.class,
                () -> createService().validateContent("{{api_key}}", Arrays.asList("api_key")));
    }
}
