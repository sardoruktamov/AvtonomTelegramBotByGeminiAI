package uz.newsbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.newsbot.entity.PublishedArticle;

/**
 * SQLite bilan ishlash uchun Spring Data JPA repository.
 *
 * Asosiy vazifalar:
 *  - URL'ning bazada borligini tekshirish (anti-duplicate)
 *  - Muvaffaqiyatli post qilingan maqolani saqlash
 */
@Repository
public interface PublishedArticleRepository extends JpaRepository<PublishedArticle, Long> {

    /**
     * Berilgan URL avval post qilinganmi yoki yo'qligini tekshiradi.
     * Bu loyihaning eng ko'p chaqiriladigan metodidir.
     *
     * @param sourceUrl tekshiriladigan yangilik URL'si
     * @return true — agar bazada mavjud bo'lsa (qayta post qilmaslik kerak)
     */
    boolean existsBySourceUrl(String sourceUrl);
}