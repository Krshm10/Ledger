package com.expense.tracker.service;

import com.expense.tracker.model.Expense;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.List;
import java.util.Map;

@Service
public class AiInsightService {

    @Value("${groq.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public String getInsights(List<Expense> expenses) {

        StringBuilder summary = new StringBuilder();
        summary.append(
                "Analyze these expenses and give spending patterns, overspent category, and 2 savings tips:\\n\\n");

        for (Expense e : expenses) {
            summary.append(e.getCategory())
                    .append(" - Rs.").append(e.getAmount())
                    .append(" on ").append(e.getDate())
                    .append(" (").append(e.getDescription()).append(")\\n");
        }

        String requestBody = """
                {
                 "model": "llama-3.3-70b-versatile",
                  "messages": [
                    {
                      "role": "user",
                      "content": "%s"
                    }
                  ]
                }
                """.formatted(summary.toString());

        String url = "https://api.groq.com/openai/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            var choices = (List<Map>) response.getBody().get("choices");
            var message = (Map) choices.get(0).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            return "AI insights unavailable: " + e.getMessage();
        }
    }
}