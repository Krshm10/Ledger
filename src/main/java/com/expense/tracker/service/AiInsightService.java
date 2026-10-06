package com.expense.tracker.service;

import com.expense.tracker.model.Expense;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class AiInsightService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiInsightService.class);

    @Value("${groq.api.key}")
    private String apiKey;
    @Value("${groq.model:openai/gpt-oss-120b}")
    private String model;

    private final RestTemplate restTemplate = buildTemplate();

    private static RestTemplate buildTemplate() {
        var f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(java.time.Duration.ofSeconds(5));
        f.setReadTimeout(java.time.Duration.ofSeconds(40));
        return new RestTemplate(f);
    }

    public String getInsights(List<Expense> expenses) {

        // Pre-compute all numbers in Java so the model never does the maths
        Map<String, BigDecimal> byCategory = expenses.stream().collect(Collectors.groupingBy(
                Expense::getCategory,
                Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)));
        BigDecimal total = byCategory.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        int n = expenses.size();
        BigDecimal avg = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);

        Expense largest = expenses.stream().max(Comparator.comparing(Expense::getAmount)).get();
        var from = expenses.stream().map(Expense::getDate).min(Comparator.naturalOrder()).get();
        var to = expenses.stream().map(Expense::getDate).max(Comparator.naturalOrder()).get();

        StringBuilder data = new StringBuilder();
        data.append("Period covered: ").append(from).append(" to ").append(to).append("\n");
        data.append("Total spent: Rs.").append(total).append(" in ").append(n)
                .append(" transactions (average Rs.").append(avg).append(" each)\n");
        data.append("Largest single expense: Rs.").append(largest.getAmount()).append(" on ")
                .append(largest.getCategory()).append(" (").append(largest.getDate()).append(")\n\n");

        data.append("Category breakdown (highest first):\n");
        byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(en -> {
                    long count = expenses.stream().filter(e -> e.getCategory().equals(en.getKey())).count();
                    BigDecimal pct = en.getValue().multiply(BigDecimal.valueOf(100))
                            .divide(total, 1, RoundingMode.HALF_UP);
                    data.append("- ").append(en.getKey()).append(": Rs.").append(en.getValue())
                            .append(" (").append(pct).append("% of total, ").append(count)
                            .append(count == 1 ? " transaction)\n" : " transactions)\n");
                });

        Map<String, BigDecimal> byMonth = new TreeMap<>();
        for (Expense e : expenses)
            byMonth.merge(YearMonth.from(e.getDate()).toString(), e.getAmount(), BigDecimal::add);
        data.append("\nMonth by month: ");
        byMonth.forEach((m, amt) -> data.append(m).append(" Rs.").append(amt).append("; "));

        data.append("\n\nAll transactions:\n");
        for (Expense e : expenses) {
            data.append("- ").append(e.getDate()).append(" | ").append(e.getCategory())
                    .append(" | Rs.").append(e.getAmount());
            if (e.getDescription() != null && !e.getDescription().isBlank())
                data.append(" | ").append(e.getDescription());
            data.append("\n");
        }

        String system = """
                You are a practical personal finance analyst writing for one person in India.
                Amounts are in rupees. Use ONLY the numbers given; never invent transactions or figures.

                Write 250 to 320 words in plain text with exactly these four parts. Start each part with its
                bold label, then continue in normal sentences:

                **Where your money went:** Walk through the main categories with their rupee amounts and
                share of the total. Mention how many transactions each had and what that says (one big
                purchase vs. many small ones).

                **What stands out:** Point out the 2 or 3 most notable things. Compare the largest expense
                with the average transaction, say whether a spend looks one-off or routine, and if the data
                spans more than one month, compare the months. Be specific about dates and amounts.

                **Where to be careful:** Name the biggest risk to this person's budget and explain why,
                using the numbers.

                **What to do next:** Give 3 numbered recommendations (1. 2. 3., each on its own line).
                Each must include a concrete target in rupees or a percentage worked out from this data,
                a realistic action, and roughly how much it could save. Tailor them to the actual
                categories and descriptions shown.

                Rules: no tables, no markdown headings, no horizontal lines, no emojis. Do not use phrases
                like "consider a budgeting app" or "track your expenses". If there are fewer than 5
                transactions, say once that the picture is limited, then still give specific advice.
                """;

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", data.toString())));

        String url = "https://api.groq.com/openai/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            var choices = (List<Map>) response.getBody().get("choices");
            var message = (Map) choices.get(0).get("message");
            return (String) message.get("content");
        }catch(

    org.springframework.web.client.HttpStatusCodeException ex)
    {
        log.error("Groq API error {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
        return "AI insights are temporarily unavailable. Please try again.";
    }catch(
    Exception ex)
    {
        log.error("AI insights failed", ex);
        return "AI insights are temporarily unavailable. Please try again.";
    }
    }
}