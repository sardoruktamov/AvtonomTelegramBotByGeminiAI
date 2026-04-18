package uz.newsbot.scheduler;

import org.springframework.context.ApplicationEvent;

public class ManualPipelineTriggerEvent extends ApplicationEvent {
    
    // So'rovni yuborgan adminning Telegram ID si
    private final long chatId;

    public ManualPipelineTriggerEvent(Object source, long chatId) {
        super(source);
        this.chatId = chatId;
    }

    public long getChatId() {
        return chatId;
    }
}
