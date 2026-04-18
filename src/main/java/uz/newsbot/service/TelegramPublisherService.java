package uz.newsbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.newsbot.bot.NewsBot;
import uz.newsbot.entity.NewsArticleDto;
import uz.newsbot.entity.PublishedArticle;
import uz.newsbot.repository.PublishedArticleRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 4-QADAM: Nashr qilish va Saqlash Servisi
 *
 * Vazifasi:
 * 1. Tayyor bo'lgan yangilikni Telegram kanalga yuboradi
 * 2. Muvaffaqiyatli yuborilgandan keyin URL'ni bazaga (PostgreSQL) yozadi
 * 3. Xato bo'lsa, bazaga yozilmaydi (keyingi sikl urinib ko'radi)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramPublisherService {

    private final NewsBot newsBot;
    private final PublishedArticleRepository publishedArticleRepository;

    // Telegram'da ketma-ket postlar orasidagi kutish vaqti (ms) — 429 Too Many Requests xatosini oldini olish uchun.
    @Value("${publisher.delay-between-posts-ms:7000}")
    private long delayBetweenPostsMs;

    /**
     * Yangilikni Telegram kanalga publish qiladi va bazaga saqlaydi.
     *
     * @param dto formatlangan yangilik ma'lumotlari (content, image, language to'ldirilgan)
     * @return true — muvaffaqiyatli publish bo'lsa, false — xato bo'lsa
     */
    @Transactional
    public boolean publishAndSave(NewsArticleDto dto) {
        log.info("Publish qilinmoqda: [{}] '{}'", dto.getTargetLanguage(), dto.getOriginalTitle());

        try {
            // Telegram kanalga yuborish
            Optional<Message> sentMessageOpt = newsBot.sendPhotoToChannel(
                    dto.getFormattedContent(),
                    dto.getFinalImageUrl()
            );

            if (sentMessageOpt.isEmpty()) {
                log.error("Telegram'ga yuborib bo'lmadi: '{}'", dto.getOriginalTitle());
                return false;
            }

            Message sentMessage = sentMessageOpt.get();

            // PostgreSQL bazasiga yozish
            PublishedArticle record = PublishedArticle.builder()
                    .sourceUrl(dto.getSourceUrl())
                    .title(dto.getOriginalTitle())
                    .feedName(dto.getFeedName())
                    .language(dto.getTargetLanguage())
                    .publishedAt(LocalDateTime.now())
                    .telegramMessageId(sentMessage.getMessageId())
                    .build();

            publishedArticleRepository.save(record);

            log.info("✅ Muvaffaqiyatli publish: [{}] '{}' (TG ID: {})",
                    dto.getTargetLanguage().toUpperCase(),
                    dto.getOriginalTitle(),
                    sentMessage.getMessageId()
            );

            // Telegram rate limit (Ban) uchun 7 soniya kutish
            log.info("Spam-filtrdan himoya: Keyingi postgacha {} ms kutilmoqda...", delayBetweenPostsMs);
            TimeUnit.MILLISECONDS.sleep(delayBetweenPostsMs);

            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Publish jarayonida kutish to'xtatildi (interrupt signal): {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Publish qilishda kutilmagan xato ['{}']: {}",
                    dto.getOriginalTitle(), e.getMessage(), e);
            return false;
        }
    }
}