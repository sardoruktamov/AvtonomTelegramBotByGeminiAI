package uz.newsbot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import uz.newsbot.bot.NewsBot;

/**
 * Ba'zi Spring Boot versiyalarida {@code telegrambots-spring-boot-starter} pollingni
 * to'liq ro'yxatdan o'tkazmasligi mumkin — {@code /start} va ReplyKeyboard kelmay qoladi.
 * Bu komponent ishga tushgandan keyin botni aniq {@code registerBot} qiladi.
 */
@Slf4j
@Component
@Order(Integer.MAX_VALUE)
@RequiredArgsConstructor
public class TelegramPollingEnsurer implements ApplicationRunner {

    private final NewsBot newsBot;
    private final ObjectProvider<TelegramBotsApi> telegramBotsApiProvider;

    @Override
    public void run(ApplicationArguments args) {
        try {
            TelegramBotsApi api = telegramBotsApiProvider.getIfAvailable();
            if (api == null) {
                log.warn("TelegramBotsApi bean topilmadi — yangi TelegramBotsApi yaratilmoqda");
                api = new TelegramBotsApi(DefaultBotSession.class);
            }
            api.registerBot(newsBot);
            log.info("Telegram long polling tasdiqlandi: bot @{}", newsBot.getBotUsername());
        } catch (TelegramApiException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("is already registered") || msg.contains("already registered")) {
                log.info("Telegram bot allaqachon ro'yxatdan o'tgan (starter ishlagan): {}", msg);
            } else {
                log.error("Telegram botni ro'yxatdan o'tkazib bo'lmadi: {}", msg, e);
            }
        }
    }
}
