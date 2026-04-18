package uz.newsbot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"TELEGRAM_BOT_TOKEN=test-token",
		"TELEGRAM_BOT_USERNAME=test-bot",
		"TELEGRAM_ADMIN_ID=999888777",
		"TELEGRAM_CHANNEL_ID=-1000000000000",
		"GEMINI_API_KEY=test-key"
})
class NewsbotApplicationTests {

	@Test
	void contextLoads() {
	}

}
