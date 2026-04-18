package uz.newsbot.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GeminiService {
    @Value("${gemini.api.key}")
    private String apiKey;

    public String processArticle(String title, String content, String lang) {
        try {
            Client client = Client.builder().apiKey(apiKey).build();

            String safeTitle = title == null ? "" : title.trim();
            String safeContent = content == null ? "" : content.trim();
            String targetLanguage = (lang == null || lang.isBlank()) ? "o'zbek" : lang;
            String prompt = "Sen professional tarjimon va tajribali jurnalistsan.\n" +
                    "Quyidagi yangilikni o'qib chiq:\n" +
                    "Sarlavha: " + safeTitle + "\n" +
                    "Matn: " + safeContent + "\n\n" +
                    "Vazifa:\n" +
                    "1. Birinchi qatorda faqat sarlavhani yoz.\n" +
                    "2. Keyingi qatorda bo'sh qator qoldir.\n" +
                    "3. So'ng 3-4 gapli qisqa xulosa yoz.\n" +
                    "4. \"Sarlavha:\", \"Xulosa:\" kabi bo'lim nomlari yozilmasin.\n" +
                    "5. Faqat " + targetLanguage + " tilida yoz, ortiqcha texnik belgi qo'shma.";

            GenerateContentResponse response = client.models.generateContent(
                    "gemini-2.5-flash",
                    prompt,
                    null
            );

            String aiSummary = response.text() == null ? "" : response.text().trim();
            return buildTelegramPost(aiSummary, safeTitle);

        } catch (Exception e) {
            log.error("Gemini AI bilan ishlashda xato yuz berdi: {}", e.getMessage(), e);
            return null;
        }
    }

    private String buildTelegramPost(String aiText, String fallbackTitle) {
        String normalized = aiText == null ? "" : aiText.replace("\r", "").trim();
        String[] parts = normalized.split("\n+", 2);

        String extractedTitle = parts.length > 0 ? stripUnsafeMarkup(parts[0]) : "";
        String body = parts.length > 1 ? stripUnsafeMarkup(parts[1]) : "";

        String finalTitle = extractedTitle.isBlank() ? fallbackTitle : extractedTitle;
        String safeTitle = escapeHtml(finalTitle);
        String safeBody = escapeHtml(body).trim();

        StringBuilder post = new StringBuilder();
        post.append("📰 <b>").append(safeTitle).append("</b>\n\n");
        if (!safeBody.isBlank()) {
            post.append(safeBody).append("\n\n");
        }
        post.append("👉 @notinchdunyo ⚡️");
        return post.toString();
    }

    private String stripUnsafeMarkup(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("<[^>]*>", "").trim();
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}

