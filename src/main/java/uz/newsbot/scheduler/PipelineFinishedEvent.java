package uz.newsbot.scheduler;

import org.springframework.context.ApplicationEvent;

public class PipelineFinishedEvent extends ApplicationEvent {
    
    private final long adminChatId;
    private final String summaryMessage;

    public PipelineFinishedEvent(Object source, long adminChatId, String summaryMessage) {
        super(source);
        this.adminChatId = adminChatId;
        this.summaryMessage = summaryMessage;
    }

    public long getAdminChatId() {
        return adminChatId;
    }

    public String getSummaryMessage() {
        return summaryMessage;
    }
}
