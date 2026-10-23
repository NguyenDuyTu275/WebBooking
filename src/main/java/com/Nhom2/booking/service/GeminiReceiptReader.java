package com.Nhom2.booking.service;

import com.Nhom2.booking.dto.ScannedReceipt;
import com.Nhom2.booking.entity.AppConfig;
import com.Nhom2.booking.exception.BusinessException;
import com.Nhom2.booking.exception.ServiceUnavailableException;
import com.Nhom2.booking.repository.AppConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Gửi ảnh hóa đơn cho Gemini và nhận lại các trường đã đọc dưới dạng JSON. */
@Service
public class GeminiReceiptReader {

    private static final Logger log = LoggerFactory.getLogger(GeminiReceiptReader.class);
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    static final String API_KEY_CONFIG = "GEMINI_API_KEY";

    static final String PROMPT = """
            You read a photo of a hotel payment receipt or invoice (Vietnamese or English) and return JSON.
            - isReceipt: false if the photo is not a payment receipt or invoice.
            - bookingCode: the booking reference exactly as printed, e.g. "#37", "DH 37", "BK000037", "Mã đơn: 37"; null if none.
            - receiptNumber: the receipt or invoice number if printed; null if none.
            - totalAmount: the total amount paid in VND as a plain number, without separators or currency.
            - paymentDate: the payment date as yyyy-MM-dd; null if not printed.
            - customerName, hotelName: as printed; null if not printed.
            - paymentMethod: CASH, TRANSFER, CARD or OTHER.
            - description: one short line saying what was paid for (room, nights).
            Never guess a value that is not printed on the receipt.
            """;

    private final RestTemplate restTemplate;
    private final AppConfigRepository configRepository;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiReceiptReader(
            RestTemplate restTemplate,
            AppConfigRepository configRepository,
            ObjectMapper objectMapper,
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.model:gemini-flash-latest}") String model) {
        this.restTemplate = restTemplate;
        this.configRepository = configRepository;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    public ScannedReceipt read(byte[] image, String mimeType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", resolveApiKey());

        String response;
        try {
            response = restTemplate.postForObject(
                    String.format(ENDPOINT, model), new HttpEntity<>(requestBody(image, mimeType), headers), String.class);
        } catch (RestClientException e) {
            log.error("Gemini receipt request failed", e);
            throw new ServiceUnavailableException("error.receipt.aiUnavailable", e);
        }
        return parseResponse(response);
    }

    /** Khóa API lấy từ biến môi trường GEMINI_API_KEY, nếu trống thì từ bảng app_config (giống khóa OpenAI). */
    private String resolveApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        return configRepository.findById(API_KEY_CONFIG)
                .map(AppConfig::getConfigValue)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .orElseThrow(() -> new ServiceUnavailableException("error.receipt.aiNotConfigured", null));
    }

    Map<String, Object> requestBody(byte[] image, String mimeType) {
        Map<String, Object> inlineData = Map.of(
                "mimeType", mimeType,
                "data", Base64.getEncoder().encodeToString(image));
        Map<String, Object> content = Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", PROMPT), Map.of("inlineData", inlineData)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(content));
        body.put("generationConfig", Map.of(
                "responseMimeType", "application/json",
                "responseSchema", responseSchema()));
        return body;
    }

    private static Map<String, Object> responseSchema() {
        Map<String, Object> text = Map.of("type", "STRING", "nullable", true);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("isReceipt", Map.of("type", "BOOLEAN"));
        properties.put("bookingCode", text);
        properties.put("receiptNumber", text);
        properties.put("totalAmount", Map.of("type", "NUMBER", "nullable", true));
        properties.put("paymentDate", text);
        properties.put("customerName", text);
        properties.put("hotelName", text);
        properties.put("paymentMethod", Map.of(
                "type", "STRING", "nullable", true, "enum", List.of("CASH", "TRANSFER", "CARD", "OTHER")));
        properties.put("description", text);
        return Map.of("type", "OBJECT", "properties", properties, "required", List.of("isReceipt"));
    }

    ScannedReceipt parseResponse(String response) {
        try {
            JsonNode text = objectMapper.readTree(response == null ? "" : response)
                    .path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (!text.isTextual() || text.asText().isBlank()) {
                throw new BusinessException("error.receipt.unreadable");
            }
            return objectMapper.readValue(text.asText(), ScannedReceipt.class);
        } catch (JsonProcessingException e) {
            log.warn("Gemini returned a receipt that is not valid JSON", e);
            throw new BusinessException("error.receipt.unreadable");
        }
    }
}
