package com.Nhom2.booking.service;

import com.Nhom2.booking.dto.ScannedReceipt;
import com.Nhom2.booking.entity.AppConfig;
import com.Nhom2.booking.exception.BusinessException;
import com.Nhom2.booking.exception.ServiceUnavailableException;
import com.Nhom2.booking.repository.AppConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiReceiptReaderTest {

    private static final String RESPONSE = """
            {"candidates":[{"content":{"parts":[{"text":
            "{\\"isReceipt\\":true,\\"bookingCode\\":\\"BK000037\\",\\"totalAmount\\":3000000,\\"paymentDate\\":\\"2026-10-23\\",\\"paymentMethod\\":\\"CASH\\",\\"extra\\":1}"
            }]}}]}
            """;

    private RestTemplate restTemplate;
    private AppConfigRepository configRepository;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        configRepository = mock(AppConfigRepository.class);
    }

    private GeminiReceiptReader reader(String apiKey) {
        return new GeminiReceiptReader(restTemplate, configRepository, new ObjectMapper(), apiKey, "gemini-flash-latest");
    }

    @Test
    void readsTheJsonGeminiReturnsAndIgnoresUnknownFields() {
        ScannedReceipt receipt = reader("key").parseResponse(RESPONSE);

        assertThat(receipt.receipt()).isTrue();
        assertThat(receipt.bookingCode()).isEqualTo("BK000037");
        assertThat(receipt.totalAmount()).isEqualTo(3_000_000d);
        assertThat(receipt.paymentDate()).isEqualTo("2026-10-23");
        assertThat(receipt.paymentMethod()).isEqualTo("CASH");
        assertThat(receipt.receiptNumber()).isNull();
    }

    @Test
    void anAnswerWithoutTextOrValidJsonIsUnreadable() {
        GeminiReceiptReader reader = reader("key");

        assertThatThrownBy(() -> reader.parseResponse("{\"candidates\":[]}"))
                .isInstanceOf(BusinessException.class).hasMessage("error.receipt.unreadable");
        assertThatThrownBy(() -> reader.parseResponse("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"not json\"}]}}]}"))
                .isInstanceOf(BusinessException.class).hasMessage("error.receipt.unreadable");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsTheImageInlineWithTheKeyInAHeaderAndAJsonSchema() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class))).thenReturn(RESPONSE);

        reader("secret-key").read(new byte[]{1, 2, 3}, "image/jpeg");

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity<Map<String, Object>>> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(url.capture(), entity.capture(), eq(String.class));

        assertThat(url.getValue()).endsWith("/models/gemini-flash-latest:generateContent").doesNotContain("secret-key");
        assertThat(entity.getValue().getHeaders().getFirst("x-goog-api-key")).isEqualTo("secret-key");
        Map<String, Object> content = ((List<Map<String, Object>>) entity.getValue().getBody().get("contents")).get(0);
        Map<String, Object> image = ((List<Map<String, Object>>) content.get("parts")).get(1);
        assertThat((Map<String, Object>) image.get("inlineData"))
                .containsEntry("mimeType", "image/jpeg")
                .containsEntry("data", "AQID");
        assertThat((Map<String, Object>) entity.getValue().getBody().get("generationConfig"))
                .containsEntry("responseMimeType", "application/json")
                .containsKey("responseSchema");
    }

    @Test
    void fallsBackToTheKeyStoredInAppConfig() {
        AppConfig stored = new AppConfig();
        stored.setConfigKey("GEMINI_API_KEY");
        stored.setConfigValue("db-key");
        when(configRepository.findById("GEMINI_API_KEY")).thenReturn(Optional.of(stored));
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class))).thenReturn(RESPONSE);

        assertThat(reader("").read(new byte[]{1}, "image/png").receipt()).isTrue();
    }

    @Test
    void missingKeyOrUnreachableGeminiIsReportedAsUnavailable() {
        when(configRepository.findById("GEMINI_API_KEY")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> reader("").read(new byte[]{1}, "image/png"))
                .isInstanceOf(ServiceUnavailableException.class).hasMessage("error.receipt.aiNotConfigured");

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("timeout"));
        assertThatThrownBy(() -> reader("key").read(new byte[]{1}, "image/png"))
                .isInstanceOf(ServiceUnavailableException.class).hasMessage("error.receipt.aiUnavailable");
    }
}
