package uz.newsbot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Telegram News Bot — asosiy kirish nuqtasi.
 *
 * Ilovaning umumiy vazifasi:
 *  1. RSS feed'lardan yangiliklarni yig'adi
 *  2. Gemini API orqali tahlil qilib, o'zbek/rus tilida matn tayyorlaydi
 *  3. Rasm URL'sini belgilaydi (original yoki Pollinations.ai)
 *  4. Telegram kanalga post qiladi va SQLite'ga yozadi
 */

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class NewsbotApplication {

	private static final Logger log = LoggerFactory.getLogger(NewsbotApplication.class);
	public static void main(String[] args) {
		log.info("╔══════════════════════════════════════════════╗");
		log.info("║     Telegram News Bot ishga tushmoqda...     ║");
		log.info("╚══════════════════════════════════════════════╝");
		SpringApplication.run(NewsbotApplication.class, args);
	}

}
