package uz.newsbot.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Published bo'lgan yangiliklar rekordini saqlaydi.
 * Maqsad: bir xil yangilikni ikki marta post qilishdan saqlash (anti-duplicate).
 *
 * SQLite'da saqlanadi, hajmi juda kichik bo'ladi chunki
 * faqat URL va metadata saqlanadi, to'liq matn emas.
 */
@Entity
@Table(
        name = "published_articles",
        indexes = {
                @Index(name = "idx_source_url", columnList = "sourceUrl", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PublishedArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Asl yangilik manbaining URL'si — duplicate tekshiruvi shu orqali amalga oshadi.
     * UNIQUE constraint qo'yilgan.
     */
    @Column(name = "source_url", nullable = false, unique = true, length = 1000)
    private String sourceUrl;

    /**
     * Yangilik sarlavhasi — monitoring va debug uchun.
     */
    @Column(name = "title", length = 500)
    private String title;

    /**
     * Qaysi feed manbasidan olingani (BBC, CNN, Daryo...).
     */
    @Column(name = "feed_name", length = 100)
    private String feedName;

    /**
     * Telegram kanalga post qilingan vaqt.
     */
    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    /**
     * Telegram'da qaysi tilda post qilindi (uz yoki ru — navbatma-navbat).
     */
    @Column(name = "language", length = 5)
    private String language;

    /**
     * Telegram message ID — agar keyinchalik o'chirish/tahrirlash kerak bo'lsa.
     */
    @Column(name = "telegram_message_id")
    private Integer telegramMessageId;

    @PrePersist
    protected void onCreate() {
        if (publishedAt == null) {
            publishedAt = LocalDateTime.now();
        }
    }
}