package uz.newsbot.entity;

import lombok.*;

/**
 * Servislar orasida yangilik ma'lumotlarini o'tkazish uchun ishlatiladigan DTO.
 * Bu klass database bilan bog'liq emas — faqat RAM'da ishlaydi.
 *
 * Pipeline: RssFetcherService → GeminiService → MediaService → TelegramPublisherService
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsArticleDto {

    // ===== RSS manbasidan olinadigan ma'lumotlar =====

    /** Yangilik sarlavhasi (original til) */
    private String originalTitle;

    /** Yangilik to'liq matni yoki tavsifi (original til) */
    private String originalContent;

    /** Asl yangilik manzili (duplicate tekshiruvi uchun ham ishlatiladi) */
    private String sourceUrl;

    /** RSS feed nomi (BBC, CNN, Daryo...) */
    private String feedName;

    /** RSS ichidagi rasm URL'si (bo'lmasligi mumkin) */
    private String originalImageUrl;

    // ===== Gemini API qayta ishlashidan keyin to'ldiriladigan maydonlar =====

    /**
     * Gemini tomonidan tayyorlangan formatlangan matn.
     * Bu yerda navbatga qarab O'zbek yoki Rus tili bo'ladi.
     */
    private String formattedContent;

    /**
     * Post qilinayotgan til: "uz" yoki "ru"
     */
    private String targetLanguage;

    // ===== Media xizmatidan keyin to'ldiriladigan maydon =====

    /**
     * Yakuniy rasm URL'si.
     * Agar originalImageUrl mavjud bo'lsa — o'sha, aks holda Pollinations.ai URL'si.
     */
    private String finalImageUrl;
}