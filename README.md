# 📰 Telegram News Bot

Dunyo yangiliklarini avtomatik ravishda yig'ib, Gemini AI orqali tahlil qilib, Telegram kanalga post qiladigan Spring Boot ilovasi.

## 🏗️ Arxitektura

```
RSS Feeds → RssFetcherService
                ↓
         GeminiService (Tahlil + Post tayyorlash: uz)
                ↓
          MediaService (Rasm: original yoki Pollinations.ai)
                ↓
   TelegramPublisherService → Telegram Kanal + PostgreSQL
```

## 🚀 Ishga Tushirish

### 1. Talablar
- Java 17+
- Maven 3.8+
- Telegram Bot token (@BotFather)
- Google Gemini API key (https://aistudio.google.com)

### 2. Bot va Kanal Sozlash
1. Telegram'da @BotFather ga `/newbot` yuboring
2. Bot nomi va username'ini kiriting
3. Token'ni olib qo'ying
4. Kanalingizni yarating va botni **admin** qilib qo'shing (post qilish ruxsati)

### 3. Environment Variables Sozlash

Quyidagi o'zgaruvchilarni tizimda sozlang:

```bash
export TELEGRAM_BOT_TOKEN="your_token_here"
export TELEGRAM_BOT_USERNAME="your_bot_username"
export TELEGRAM_ADMIN_ID="442569765"
export TELEGRAM_CHANNEL_ID="-1001234567890"
export GEMINI_API_KEY="your_gemini_key"
```

### 4. Build va Run

```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/telegram-news-bot-1.0.0.jar

# Yoki Maven bilan to'g'ridan-to'g'ri
mvn spring-boot:run
```

### 5. Docker bilan Ishlatish (ixtiyoriy)

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/telegram-news-bot-1.0.0.jar app.jar
RUN mkdir -p /app/data
VOLUME /app/data
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
docker build -t news-bot .
docker run -d \
  -e TELEGRAM_BOT_TOKEN=xxx \
  -e TELEGRAM_BOT_USERNAME=xxx \
  -e TELEGRAM_CHANNEL_ID=@xxx \
  -e GEMINI_API_KEY=xxx \
  -v ./data:/app/data \
  news-bot
```

## 📁 Loyiha Strukturasi

```
src/main/java/uz/newsbot/
├── NewsAggregatorApplication.java   ← Entry point
├── bot/
│   └── NewsBot.java                 ← Telegram bot (sendPhoto/sendMessage)
├── config/
│   └── WebClientConfig.java         ← HTTP client sozlamalari
├── entity/
│   ├── PublishedArticle.java        ← JPA Entity (PostgreSQL)
│   └── NewsArticleDto.java          ← Pipeline DTO
├── repository/
│   └── PublishedArticleRepository.java ← Spring Data JPA
├── scheduler/
│   └── NewsScheduler.java           ← Har 1 soatda ishga tushuvchi orchestrator
└── service/
    ├── RssFetcherService.java       ← ROME orqali RSS o'qish
    ├── GeminiService.java           ← Gemini API (tahlil + tarjima)
    ├── MediaService.java            ← Rasm URL'sini aniqlash
    └── TelegramPublisherService.java ← Publish + PostgreSQL saqlash
```

## ⚙️ Sozlash

`application.yml` da quyidagilarni o'zgartirishingiz mumkin:

| Sozlama | Default | Tavsif |
|---------|---------|--------|
| `bot.admin_id` | `TELEGRAM_ADMIN_ID` yoki default | Faqat shu Telegram user-id bot menyusini ko‘radi |
| `scheduler.news.cron` | `0 0 * * * *` | Har soatda |
| `scheduler.news.initial-delay-ms` | `10000` | Startup'dan keyin birinchi ishga tushish |
| `rss.fetch.max-entries-per-feed` | `5` | Feed boshiga max yangilik |
| `publisher.delay-between-posts-ms` | `7000` | Telegram postlar orasidagi kutish |
| `gemini.delay-ms` | `10000` | Gemini so'rovlari oralig'idagi kutish |
| `pollinations.image-width` | `1024` | Generatsiya qilinadigan rasm eni |

## 🔧 Yangi RSS Manbasi Qo'shish

`application.yml` da `rss.feeds` bo'limiga qo'shing:
```yaml
rss:
  feeds:
    - url: https://yangi-manba.com/rss
      name: Yangi Manba
```

`NewsScheduler` feedlar ro'yxatini avtomatik ravishda `application.yml` dan o'qiydi.

## 📊 Loglash

Barcha loglар konsol'da ko'rinadi. Log darajasini o'zgartirish:
```yaml
logging:
  level:
    uz.newsbot: DEBUG  # DEBUG, INFO, WARN, ERROR
```
