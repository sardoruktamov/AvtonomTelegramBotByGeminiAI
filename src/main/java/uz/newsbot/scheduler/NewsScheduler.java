package uz.newsbot.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.newsbot.entity.NewsArticleDto;
import uz.newsbot.service.GeminiService;
import uz.newsbot.service.MediaService;
import uz.newsbot.service.RssFetcherService;
import uz.newsbot.service.RssFetcherService.FeedConfig;
import uz.newsbot.service.TelegramPublisherService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Asosiy Orchestrator — News Pipeline Scheduler
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "rss") // YML dagi 'rss' bo'limiga to'g'ridan-to'g'ri ulanadi
public class NewsScheduler {

    private final RssFetcherService rssFetcherService;
    private final GeminiService geminiService;
    private final MediaService mediaService;
    private final TelegramPublisherService telegramPublisherService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    private final AtomicInteger languageCounter = new AtomicInteger(0);
    private final AtomicBoolean pipelineRunning = new AtomicBoolean(false);
    private static final String[] LANGUAGES = {"uz"};
    private static final String PIPELINE_BUSY_MESSAGE = "⏳ Pipeline allaqachon ishlamoqda. Joriy sikl tugagach qayta urinib ko'ring.";

    @org.springframework.beans.factory.annotation.Value("${scheduler.news.initial-delay-ms:10000}")
    private long initialDelayMs;

    @org.springframework.beans.factory.annotation.Value("${gemini.delay-ms:10000}")
    private long geminiDelayMs;

    // Spring Boot avtomatik ravishda application.yml dagi 'rss.feeds' ro'yxatini shu yerga joylaydi!
    @Setter
    private List<FeedItem> feeds = new ArrayList<>();

    // YML dagi har bir feed obyekti uchun struktura
    public static class FeedItem {
        private String url;
        private String name;
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Ilova tayyor. {} ms dan so'ng birinchi marta ishga tushadi...", initialDelayMs);
        Thread thread = new Thread(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(initialDelayMs);
                runNewsPipeline();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        thread.setName("news-initial-run");
        thread.setDaemon(true);
        thread.start();
    }

    @Scheduled(cron = "${scheduler.news.cron:0 0 * * * *}")
    public void runScheduledPipeline() {
        runNewsPipeline();
    }

    @org.springframework.scheduling.annotation.Async
    @EventListener(ManualPipelineTriggerEvent.class)
    public void handleManualTrigger(ManualPipelineTriggerEvent event) {
        log.info("Admin {} tomonidan qo'lda (manual) tekshiruv ishga tushirildi.", event.getChatId());
        String summary = runNewsPipeline();
        eventPublisher.publishEvent(new PipelineFinishedEvent(this, event.getChatId(), summary));
    }

    public String runNewsPipeline() {
        if (!pipelineRunning.compareAndSet(false, true)) {
            log.warn("Pipeline so'rovi rad etildi: oldingi sikl hali tugamagan.");
            return PIPELINE_BUSY_MESSAGE;
        }

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("📰 Yangilik pipeline ishga tushdi...");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        long startTime = System.currentTimeMillis();
        int successCount = 0;
        int failCount = 0;
        int skipCount = 0;

        try {
            // ─── QADAM 1: Dinamik RSS Feed'lardan yangiliklar yig'ish ───
            List<FeedConfig> feedConfigs = buildFeedConfigs();
            List<NewsArticleDto> freshArticles = rssFetcherService.fetchNewArticles(feedConfigs);

            if (freshArticles.isEmpty()) {
                log.info("ℹ️  Yangi maqola topilmadi. Keyingi siklgacha kutilmoqda.");
                return "ℹ️ Hech qanday yangi maqola topilmadi.";
            }

            log.info("📥 {} ta yangi maqola qayta ishlanmoqda...", freshArticles.size());

            for (NewsArticleDto article : freshArticles) {
                try {
                    boolean published = processSingleArticle(article);
                    if (published) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                } catch (Exception e) {
                    log.error("Maqola qayta ishlashda xato: '{}' — {}",
                            article.getOriginalTitle(), e.getMessage(), e);
                    failCount++;
                }
            }

        } catch (Exception e) {
            log.error("Pipeline umumiy xatosi: {}", e.getMessage(), e);
            return "❌ Xatolik yuz berdi: " + e.getMessage();
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("✅ Pipeline yakunlandi: {} ta muvaffaqiyatli, {} ta xato, {} ta o'tkazildi",
                    successCount, failCount, skipCount);
            log.info("⏱️  Jami vaqt: {} ms ({} soniya)", duration, duration / 1000);
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            pipelineRunning.set(false);
        }
        return String.format("✅ Qidiruv tugatildi.\n\n📊 Natija:\n✅ Muvaffaqiyatli: %d ta\n❌ Xatolik: %d ta\n⏱️ Sarflangan vaqt: %d soniya", successCount, failCount, (System.currentTimeMillis() - startTime) / 1000);
    }

    private boolean processSingleArticle(NewsArticleDto article) {
        int langIndex = languageCounter.getAndIncrement() % LANGUAGES.length;
        String targetLanguage = LANGUAGES[langIndex];

        log.debug("Maqola qayta ishlanmoqda [{}]: '{}'", targetLanguage.toUpperCase(), article.getOriginalTitle());

        // --- GEMINI RATE LIMIT HIMOAYSI ---
        try {
            log.info("Gemini limitiga tushmaslik uchun {} ms tanaffus kutilmoqda...", geminiDelayMs);
            TimeUnit.MILLISECONDS.sleep(geminiDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // ----------------------------------

        String formattedContent = geminiService.processArticle(
                article.getOriginalTitle(),
                article.getOriginalContent(),
                targetLanguage
        );

        if (formattedContent == null || formattedContent.trim().isEmpty()) {
            log.warn("Gemini xatosi sababli tarjima qilinmadi. Post o'tkazib yuborildi: {}", article.getOriginalTitle());
            return false;
        }

        if (formattedContent.contains("IGNORE_NEWS")) {
            log.info("Lokal/Keraksiz yangilik filtrlashdan o'tmadi, tashlab yuborildi: {}", article.getOriginalTitle());
            return false;
        }

        article.setFormattedContent(formattedContent);
        article.setTargetLanguage(targetLanguage);

        String finalImageUrl = mediaService.resolveImageUrl(
                article.getOriginalImageUrl(),
                article.getOriginalTitle()
        );
        article.setFinalImageUrl(finalImageUrl);

        return telegramPublisherService.publishAndSave(article);
    }

    /**
     * application.yml'dan o'qilgan ro'yxat asosida dinamik FeedConfig'larni yig'adi.
     */
    private List<FeedConfig> buildFeedConfigs() {
        return feeds.stream()
                .map(item -> new FeedConfig(item.getUrl(), item.getName()))
                .collect(Collectors.toList());
    }
}