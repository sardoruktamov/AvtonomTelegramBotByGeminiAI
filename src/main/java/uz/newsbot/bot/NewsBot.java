package uz.newsbot.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import java.util.Optional;

/**
 * Telegram Bot asosiy klassi.
 *
 * TelegramLongPollingBot'dan meros oladi — bu bot odatdagi polling usulida ishlaydi.
 * Loyihaning asosiy maqsadi chiquvchi postlar (kanalga yozish),
 * kiruvchi xabarlar (onUpdateReceived) hozircha e'tiborsiz qoldiriladi.
 *
 * Bot registratsiyasi:
 *  1. @BotFather da yangi bot yarating
 *  2. Token va username'ni environment variable'larga qo'shing
 *  3. Botni kanalingizga admin sifatida qo'shing (post qilish ruxsati bilan)
 */

@Component
public class NewsBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(NewsBot.class);
    private static final String ACTION_FETCH_NEWS = "🔄 Yangiliklarni hozir olish";
    private static final String ACTION_CREATE_ANNOUNCEMENT = "📢 Elon yaratish";
    private final String botUsername;
    private final String channelId;
    private final Long adminId;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public NewsBot(
            @Value("${bot.token}") String botToken,
            @Value("${bot.username}") String botUsername,
            @Value("${bot.channel.id}") String channelId,
            @Value("${bot.admin_id}") Long adminId,
            org.springframework.context.ApplicationEventPublisher eventPublisher
    ) {
        super(botToken);
        this.botUsername = botUsername;
        this.channelId = channelId;
        this.adminId = adminId;
        this.eventPublisher = eventPublisher;
        log.info("NewsBot ishga tushdi: @{}, kanal: {}", botUsername, channelId);
        log.info("Admin tekshiruvi: bot.admin_id (kutilayotgan Telegram user-id) = {}", adminId);
    }

    /**
     * Webhook yoqilgan bo'lsa, getUpdates (long polling) bo'sh qoladi.
     * Ishga tushganda webhook'ni o'chirib, polling ishlashini ta'minlaymiz.
     */
    @PostConstruct
    public void ensurePollingMode() {
        try {
            Boolean ok = execute(DeleteWebhook.builder().dropPendingUpdates(false).build());
            log.info("Telegram deleteWebhook (polling rejimi): muvaffaqiyatli={}", Boolean.TRUE.equals(ok));
        } catch (TelegramApiException e) {
            log.warn("deleteWebhook chaqirishda xato: {} — kiruvchi xabarlar kelmasligi mumkin (webhook tekshiring)", e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    /**
     * Kiruvchi xabarlarni qayta ishlash.
     * Bu loyihada asosan chiquvchi postlar ishlatiladigan bo'lsa ham,
     * basic /start komandasi uchun handler qoldirganmiz.
     */
    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) {
            log.debug("Update {}: message yo'q (masalan callback/sticker) — o'tkazildi", update.getUpdateId());
            return;
        }

        Message message = update.getMessage();
        if (!message.hasText()) {
            log.info("Bot xabar: chatId={}, matn emas (rasm/stiker va h.k.)", message.getChatId());
            return;
        }

        String text = message.getText();
        long chatId = message.getChatId();
        log.info("Bot xabar qabul qilindi: chatId={}, text={}", chatId, text);

        if (adminId == null || !adminId.equals(chatId)) {
            log.warn("Ruxsatsiz foydalanuvchi botga ma'lumot jo'natdi: chatId={}", chatId);
            sendTextMessage(String.valueOf(chatId), "Kechirasiz, tizimga kirishga ruxsat yo'q.\n\nSizning ID raqamingiz: " + chatId + "\nAdmin kutayotgan ID: " + adminId);
            return;
        }

        if (isStartCommand(text)) {
            sendAdminMenu(chatId);
        } else if (isPipelineAction(text)) {
            sendTextMessage(String.valueOf(chatId), "⏳ E'lon yaratish boshlandi... Yangiliklar qayta qidirilib, publish qilinadi. Yakunda sizga natija yuboraman.");
            eventPublisher.publishEvent(new uz.newsbot.scheduler.ManualPipelineTriggerEvent(this, chatId));
        } else {
            log.info("Noma'lum matn (admin): chatId={}, text={} — yordam yuborildi", chatId, text);
            sendTextMessage(String.valueOf(chatId),
                    "Buyruq tanilmadi.\n\n/start — menyu\nYoki tugmadagi matnni aynan yuboring: " + ACTION_CREATE_ANNOUNCEMENT);
        }
    }

    /** Ba'zi Telegram mijozlari /start o'rniga /start@BotUser yuboradi. */
    private static boolean isStartCommand(String text) {
        if (text == null) {
            return false;
        }
        return "/start".equals(text) || text.startsWith("/start@");
    }

    /** Tugma matni yoki emoji'siz qo'lda terilgan variant. */
    private static boolean isPipelineAction(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        if (ACTION_FETCH_NEWS.equals(t) || ACTION_CREATE_ANNOUNCEMENT.equals(t)) {
            return true;
        }
        String lower = t.toLowerCase();
        return "elon yaratish".equals(lower)
                || "yangiliklarni hozir olish".equals(lower);
    }

    private void sendAdminMenu(long chatId) {
        try {
            org.telegram.telegrambots.meta.api.methods.send.SendMessage sendMessage = org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text("⚡️ Asosiy menyu. Kerakli harakatni tanlang:")
                    .build();

            org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup keyboardMarkup = new org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup();
            keyboardMarkup.setResizeKeyboard(true);

            java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow> keyboard = new java.util.ArrayList<>();
            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow row = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow();
            row.add(ACTION_CREATE_ANNOUNCEMENT);
            row.add(ACTION_FETCH_NEWS);
            keyboard.add(row);
            
            keyboardMarkup.setKeyboard(keyboard);
            sendMessage.setReplyMarkup(keyboardMarkup);

            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Menyu yuborishda xato: {}", e.getMessage());
        }
    }

    @org.springframework.context.event.EventListener(uz.newsbot.scheduler.PipelineFinishedEvent.class)
    public void onPipelineFinished(uz.newsbot.scheduler.PipelineFinishedEvent event) {
        sendTextMessage(String.valueOf(event.getAdminChatId()), event.getSummaryMessage());
    }

    /**
     * Kanalga rasm va matn bilan post yuboradi (asosiy metod).
     *
     * @param caption     HTML formatdagi post matni
     * @param imageUrl    rasm URL'si
     * @return yuborilgan Message obyekti (message ID uchun)
     */
    public Optional<Message> sendPhotoToChannel(String caption, String imageUrl) {
        try {
            SendPhoto sendPhoto = SendPhoto.builder()
                    .chatId(channelId)
                    .photo(new InputFile(imageUrl))
                    .caption(caption)
                    .parseMode("HTML")  // <b>, <i>, <a> teglarni qo'llab-quvvatlash
                    .build();

            Message sentMessage = execute(sendPhoto);
            log.info("Kanal'ga photo post yuborildi. Message ID: {}", sentMessage.getMessageId());
            return Optional.of(sentMessage);

        } catch (TelegramApiException e) {
            log.error("Photo yuborishda Telegram API xatosi: {}", e.getMessage());

            // Rasm yuborib bo'lmasa, faqat matn yuborishga urinamiz
            log.info("Fallback: faqat matn yuborilmoqda...");
            return sendTextToChannel(caption);
        }
    }

    /**
     * Kanalga faqat matn post yuboradi (fallback metod).
     *
     * @param text HTML formatdagi matn
     * @return yuborilgan Message (yoki empty agar xato bo'lsa)
     */
    public Optional<Message> sendTextToChannel(String text) {
        try {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(channelId)
                    .text(text)
                    .parseMode("HTML")
                    .disableWebPagePreview(false) // Preview ko'rsatish
                    .build();

            Message sentMessage = execute(sendMessage);
            log.info("Kanal'ga text post yuborildi. Message ID: {}", sentMessage.getMessageId());
            return Optional.of(sentMessage);

        } catch (TelegramApiException e) {
            log.error("Text yuborishda Telegram API xatosi: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Istalgan chat'ga oddiy matn xabar yuboradi (utility metod).
     */
    public void sendTextMessage(String chatId, String text) {
        try {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .build();
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Text xabar yuborishda xato [chatId={}]: {}", chatId, e.getMessage());
        }
    }
}