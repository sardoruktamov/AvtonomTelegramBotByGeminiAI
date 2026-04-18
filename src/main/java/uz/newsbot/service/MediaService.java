package uz.newsbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

/**
 * 3-QADAM: Media va Rasmlar Boshqarish Servisi
 *
 * Ish mantiqi:
 *   1. Agar RSS'dan rasm URL'si kelgan bo'lsa → o'sha rasm ishlatiladi
 *   2. Agar rasm yo'q bo'lsa → Pollinations.ai orqali bepul rasm URL'si yaratiladi
 *
 * Pollinations.ai — API key talab qilmaydigan, bepul AI rasm generatsiya servisi.
 * URL strukturasi: https://image.pollinations.ai/prompt/{encoded_prompt}?width=1024&height=576
 */
@Slf4j
@Service
public class MediaService {

    @Value("${pollinations.base-url:https://image.pollinations.ai/prompt/}")
    private String pollinationsBaseUrl;

    @Value("${pollinations.image-width:1024}")
    private int imageWidth;

    @Value("${pollinations.image-height:576}")
    private int imageHeight;

    /**
     * Yangilik uchun eng mos rasm URL'sini qaytaradi.
     *
     * @param originalImageUrl RSS'dan kelgan rasm URL'si (null bo'lishi mumkin)
     * @param articleTitle     Yangilik sarlavhasi (Pollinations prompt uchun)
     * @return ishlatilishi mumkin bo'lgan rasm URL'si (hech qachon null emas)
     */
    public String resolveImageUrl(String originalImageUrl, String articleTitle) {
        // 1-bosqich: Original rasm bor va ishlayaptimi?
        if (originalImageUrl != null && !originalImageUrl.isBlank()) {
            if (isImageAccessible(originalImageUrl)) {
                log.debug("Original rasm ishlatilmoqda: {}", originalImageUrl);
                return originalImageUrl;
            } else {
                log.warn("Original rasm ishlamaydi, Pollinations ishlatiladi: {}", originalImageUrl);
            }
        }

        // 2-bosqich: Pollinations.ai orqali rasm URL'si yaratish
        String pollinationsUrl = buildPollinationsUrl(articleTitle);
        log.info("Pollinations rasm URL'si yaratildi: {}", pollinationsUrl);
        return pollinationsUrl;
    }

    /**
     * Pollinations.ai uchun URL yaratadi.
     *
     * Prompt qoidalari:
     *  - Sarlavhani ingliz tiliga emas, asl holatida yuboramiz (Pollinations ko'p til qo'llaydi)
     *  - "news, photorealistic, professional" qo'shimcha sozlamalar qo'shamiz
     *  - URL encode qilinadi
     *
     * @param title yangilik sarlavhasi
     * @return to'liq Pollinations URL'si
     */
    private String buildPollinationsUrl(String title) {
        // Sarlavhadan sifatli prompt yasash
        String cleanTitle = cleanForPrompt(title);
        String prompt = String.format(
                "%s, breaking news, photorealistic, professional journalism photography, " +
                        "high quality, 4k, editorial style",
                cleanTitle
        );

        String encodedPrompt;
        try {
            encodedPrompt = URLEncoder.encode(prompt, StandardCharsets.UTF_8)
                    .replace("+", "%20"); // Bo'shliqlarni + emas %20 bilan almashtirish
        } catch (Exception e) {
            log.error("Prompt encode qilishda xato: {}", e.getMessage());
            encodedPrompt = "breaking+news+professional+photography";
        }

        return String.format("%s%s?width=%d&height=%d&nologo=true&seed=%d",
                pollinationsBaseUrl,
                encodedPrompt,
                imageWidth,
                imageHeight,
                Math.abs(title.hashCode()) // Har xil yangilik uchun har xil rasm
        );
    }

    /**
     * Rasm URL'sining haqiqatan mavjudligini tekshiradi (HTTP HEAD so'rov).
     * Timeout: 5 soniya — ishlamasa, tez o'tib ketadi.
     */
    private boolean isImageAccessible(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; NewsBot/1.0)");
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            boolean accessible = responseCode >= 200 && responseCode < 400;
            if (!accessible) {
                log.debug("Rasm URL HTTP {}: {}", responseCode, imageUrl);
            }
            return accessible;

        } catch (Exception e) {
            log.debug("Rasm URL tekshirib bo'lmadi ({}): {}", e.getMessage(), imageUrl);
            return false;
        }
    }

    /**
     * Sarlavhani Pollinations uchun prompt'ga moslashtiradi.
     * Maxsus belgilar, ortiqcha uzunlik va keraksiz so'zlarni olib tashlaydi.
     */
    private String cleanForPrompt(String title) {
        if (title == null || title.isBlank()) {
            return "world news";
        }

        // 1. Avval barcha tozalash ishlarini qilib, yangi o'zgaruvchiga olamiz
        String cleanedTitle = title
                .replaceAll("['\"«»]", "") // Tirnoqlar
                .replaceAll("[\\[\\](){}]", "") // Qavslar
                .replaceAll("[-–—]", " ")       // Chiziqlar
                .replaceAll("\\s+", " ")         // Ko'p bo'shliqlar
                .trim();

        // 2. Endi kesish uchun original title'ni emas, cleanedTitle uzunligini ishlatamiz
        return cleanedTitle.substring(0, Math.min(cleanedTitle.length(), 100));
    }
}