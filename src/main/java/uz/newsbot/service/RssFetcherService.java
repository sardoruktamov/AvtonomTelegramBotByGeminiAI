package uz.newsbot.service;

import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uz.newsbot.NewsbotApplication;
import uz.newsbot.entity.NewsArticleDto;
import uz.newsbot.repository.PublishedArticleRepository;

import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 1-QADAM: Ma'lumot Yig'ish Servisi
 *
 * ROME kutubxonasi yordamida RSS/Atom feed'lardan yangiliklarni o'qiydi.
 * Har bir feed uchun:
 *   - Sarlavha
 *   - Tavsif/matn
 *   - Rasm URL'si (enclosure yoki media:content yoki matn ichidagi img tag'dan)
 *   - Manba URL'si
 * olinadi.
 *
 * Anti-duplicate: bazada mavjud URL'lar o'tkazib yuboriladi.
 */

@Service
@RequiredArgsConstructor
public class RssFetcherService {

    private static final Logger log = LoggerFactory.getLogger(RssFetcherService.class);
    private final PublishedArticleRepository publishedArticleRepository;

    @Value("${rss.fetch.timeout-seconds:15}")
    private int timeoutSeconds;

    @Value("${rss.fetch.max-entries-per-feed:5}")
    private int maxEntriesPerFeed;

    /**
     * Barcha sozlangan RSS feed'lardan yangi maqolalarni tortib oladi.
     *
     * @param feedConfigs feed URL'lari va nomlari ro'yxati (application.yml'dan keladi)
     * @return yangi (bazada yo'q) maqolalar ro'yxati
     */
    public List<NewsArticleDto> fetchNewArticles(List<FeedConfig> feedConfigs) {
        List<NewsArticleDto> newArticles = new ArrayList<>();

        for (FeedConfig feedConfig : feedConfigs) {
            try {
                log.info("Feed o'qilmoqda: {} → {}", feedConfig.name(), feedConfig.url());
                List<NewsArticleDto> articles = fetchFromFeed(feedConfig);
                log.info("Feed '{}': {} ta yangi maqola topildi", feedConfig.name(), articles.size());
                newArticles.addAll(articles);
            } catch (Exception e) {
                // Bitta feed xato bo'lsa, boshqalari davom etaveradi
                log.error("Feed o'qishda xato [{}]: {}", feedConfig.name(), e.getMessage(), e);
            }
        }

        log.info("Jami {} ta yangi maqola yig'ildi (barcha feed'lardan)", newArticles.size());
        return newArticles;
    }

    /**
     * Bitta RSS feed'dan maqolalarni o'qiydi.
     */
    private List<NewsArticleDto> fetchFromFeed(FeedConfig feedConfig) throws Exception {
        List<NewsArticleDto> result = new ArrayList<>();

        URL feedUrl = new URL(feedConfig.url());
        URLConnection connection = feedUrl.openConnection();
        connection.setConnectTimeout(timeoutSeconds * 1000);
        connection.setReadTimeout(timeoutSeconds * 1000);
        // Ba'zi saytlar bot so'rovlarini bloklaydi, shuning uchun User-Agent qo'shamiz
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (compatible; NewsBot/1.0; +https://t.me/newsbot)");

        SyndFeedInput input = new SyndFeedInput();

        // XXE hujumlarini kamaytirish uchun DOCTYPE'ni bloklaymiz.
        input.setAllowDoctypes(false);

        SyndFeed feed;

        // XMLReader orqali o'qish
        try (XmlReader reader = new XmlReader(connection)) {
            feed = input.build(reader);
        }

        List<SyndEntry> entries = feed.getEntries();
        int count = 0;

        for (SyndEntry entry : entries) {
            if (count >= maxEntriesPerFeed) break;

            String link = cleanUrl(entry.getLink());
            if (link == null || link.isBlank()) {
                log.warn("URL topilmadi, o'tkazib yuborildi: {}", entry.getTitle());
                continue;
            }

            // Anti-duplicate tekshiruvi
            if (publishedArticleRepository.existsBySourceUrl(link)) {
                log.debug("Allaqachon post qilingan, o'tkazildi: {}", link);
                continue;
            }

            String title = cleanText(entry.getTitle());
            String content = extractContent(entry);
            String imageUrl = extractImageUrl(entry);

            if (title == null || title.isBlank()) {
                log.warn("Sarlavhasiz maqola o'tkazildi: {}", link);
                continue;
            }

            NewsArticleDto dto = NewsArticleDto.builder()
                    .originalTitle(title)
                    .originalContent(content)
                    .sourceUrl(link)
                    .feedName(feedConfig.name())
                    .originalImageUrl(imageUrl)
                    .build();

            result.add(dto);
            count++;
            log.debug("Yangi maqola: [{}] {}", feedConfig.name(), title);
        }

        return result;
    }

    /**
     * SyndEntry'dan matn kontentini ajratib oladi.
     * Birinchi navbatda to'liq kontent, bo'lmasa tavsif ishlatiladi.
     */
    private String extractContent(SyndEntry entry) {
        // To'liq kontent tekshiruvi
        if (entry.getContents() != null && !entry.getContents().isEmpty()) {
            String content = entry.getContents().get(0).getValue();
            if (content != null && !content.isBlank()) {
                return stripHtml(content);
            }
        }

        // Tavsif tekshiruvi
        if (entry.getDescription() != null) {
            String desc = entry.getDescription().getValue();
            if (desc != null && !desc.isBlank()) {
                return stripHtml(desc);
            }
        }

        // Hech narsa topilmasa sarlavhani qaytarish
        return entry.getTitle();
    }

    /**
     * RSS entry'dan rasm URL'sini topishga harakat qiladi.
     * Tekshiruv tartibi: enclosure → media:content → matn ichidagi <img>
     */
    private String extractImageUrl(SyndEntry entry) {
        // 1. Enclosure (ko'p RSS feed'lar rasm shu yerda beradi)
        if (entry.getEnclosures() != null && !entry.getEnclosures().isEmpty()) {
            for (SyndEnclosure enclosure : entry.getEnclosures()) {
                if (enclosure.getType() != null &&
                        enclosure.getType().startsWith("image/")) {
                    return enclosure.getUrl();
                }
            }
        }

        // 2. Foreign markup (media:content, media:thumbnail)
        if (entry.getForeignMarkup() != null) {
            for (var element : entry.getForeignMarkup()) {
                String name = element.getName();
                if ("thumbnail".equals(name) || "content".equals(name)) {
                    String url = element.getAttributeValue("url");
                    if (url != null && !url.isBlank()) {
                        return url;
                    }
                }
            }
        }

        // 3. HTML kontent ichidagi <img> tag
        String rawContent = null;
        if (entry.getContents() != null && !entry.getContents().isEmpty()) {
            rawContent = entry.getContents().get(0).getValue();
        } else if (entry.getDescription() != null) {
            rawContent = entry.getDescription().getValue();
        }

        if (rawContent != null) {
            return extractImgFromHtml(rawContent);
        }

        return null;
    }

    /**
     * HTML matndan birinchi <img> ning src attribute'sini ajratib oladi.
     */
    private String extractImgFromHtml(String html) {
        Pattern pattern = Pattern.compile(
                "<img[^>]+src=[\"']([^\"']+)[\"']",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * HTML teglarini matndan tozalaydi.
     */
    private String stripHtml(String html) {
        if (html == null) return null;
        // HTML entities ham tozalanadi
        return html
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Matnni tozalaydi va bo'sh joylarni kamaytiradi.
     */
    private String cleanText(String text) {
        if (text == null) return null;
        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * URL'dan ortiqcha parametrlarni olib tashlaydi (tracking params).
     */
    private String cleanUrl(String url) {
        if (url == null) return null;
        // UTM va tracking parametrlarni olib tashlaymiz
        return url.split("[?#]")[0].trim();
    }

    /**
     * Feed konfiguratsiyasi uchun record (Java 17+).
     */
    public record FeedConfig(String url, String name) {}
}