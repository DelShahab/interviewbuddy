package com.ai.interviewbuddy.service;

import com.ai.interviewbuddy.views.mainview.MainView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final AtomicReference<UI> activeUi = new AtomicReference<>();

    public void register(UI ui) {
        log.info("✅ UI registered for live push: {}", ui);
        activeUi.set(ui);
    }

    public static void sendTranscript(String text) {
        UI.getCurrent().access(() -> {
            MainView.transcriptCallback.accept(text);
        });
    }

    public void pushTranscript(String line) {
        UI ui = activeUi.get();
        if (ui != null) {
            log.info("📡 Pushing transcript to UI: {}", line);
            ui.access((Command) () -> {
                ui.getPage().executeJs(
                        "const el = document.querySelector('#transcript');" +
                                "if (el) el.textContent += '\\n📡 ' + $0;",
                        line);
            });
        } else {
            log.warn("⚠️ No active UI registered. Skipping transcript: {}", line);
        }
    }
}
