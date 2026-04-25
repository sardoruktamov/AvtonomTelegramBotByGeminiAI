# Telegram News Bot — loyiha xulosasi (o‘zbek)

Bu fayl loyihani boshqa kompyuter yoki papkaga ko‘chirganda tez eslash uchun yozilgan.

## Maqsad

RSS manbalardan yangiliklarni avtomatik yig‘ish, Google Gemini bilan qisqa post (sarlavha + matn) tayyorlash, rasmni aniqlash yoki generatsiya qilish, natijani **Telegram kanal**ga chiqarish va **PostgreSQL**da nashr qilingan URL bo‘yicha dublikatlarni oldini olish.

## Asosiy vazifalar

1. **RSS** — `RssFetcherService`: sozlangan feedlardan maqolalar, XXE himoyasi bilan XML o‘qish.
2. **AI** — `GeminiService`: post matni (Telegram HTML: qalin sarlavha + oddiy matn).
3. **Rasm** — `MediaService`: manba rasmi yoki Pollinations orqali zaxira.
4. **Nashr** — `TelegramPublisherService` + `NewsBot`: kanalga photo/matn, keyin `PublishedArticle` yozish.
5. **Reja** — `NewsScheduler`: cron va startupdan keyin birinchi ishga tushish; **bitta vaqtda bitta pipeline** (parallel bloklash).
6. **Admin bot** — `NewsBot`: faqat `bot.admin_id` (Telegram **user id**) mos keladigan foydalanuvchi uchun `/start`, **ReplyKeyboard** (`📢 Elon yaratish`, `🔄 Yangiliklarni hozir olish`), qo‘lda pipeline trigger.

## Texnik stack

- Java 17, Spring Boot 4, Spring Data JPA, PostgreSQL  
- `telegrambots-spring-boot-starter`, Google GenAI SDK, ROME (RSS)  
- `TelegramPollingEnsurer`: Spring Boot 4 da starter ba’zan `TelegramBotsApi` bermay qolishi mumkin — long polling aniq `registerBot` bilan yoqiladi.

## Muhim konfiguratsiya (`application.yml` + muhit)

- `bot.token`, `bot.username` (**@siz**, masalan `avtonomnew_bot`), `bot.admin_id`, `bot.channel.id`  
- `gemini.api.key`, scheduler va publisher kechikishlari (`scheduler.news.*`, `gemini.delay-ms`, `publisher.delay-between-posts-ms`)  
- Maxfiy qiymatlarni ixtiyoriy ravishda muhit o‘zgaruvchilari orqali berish tavsiya etiladi.

## Foydalanuvchi uchun eslatmalar

- Kanalga post chiqishi **kiruvchi polling**dan mustaqil; `/start` ishlamasa — webhook / polling / `TelegramPollingEnsurer` loglarini tekshiring.  
- Bot chat **kanal emas** — `@avtonomnew_bot` kabi bot username bilan ochiladi.  
- `bot.admin_id` shaxsiy chatdagi `chatId` bilan bir xil bo‘lishi kerak (odatda sizning Telegram user id).

---
*Oxirgi yangilanish: loyiha to‘liq ishlagan holat bo‘yicha saqlangan.*
