package com.ai.interviewbuddy.views.mainview;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.UI;
// import com.vaadin.flow.component.tooltip.Tooltip;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Route("")
// @Push
@JsModule("./js/mic-stream.js")
@CssImport("./themes/interviewbuddy/interviewbuddy.css")
public class MainView extends VerticalLayout {

    private final ChatClient chatClient;

    private final Div transcriptDisplay = new Div();
    private final Div aiResponseDisplay = new Div();

    private String lastTranscribedText = "";

    private final ComboBox<String> interviewType = new ComboBox<>("Interview Type");
    private final ComboBox<String> roleType = new ComboBox<>("Target Role");
    private final ComboBox<String> responseFramework = new ComboBox<>("Response Framework");
    private final ComboBox<String> toneSelector = new ComboBox<>("Tone");
    private final ComboBox<String> responseFormat = new ComboBox<>("AI Response Format");
    private final TextField responseStyle = new TextField("Response Style");

    private final Button helpBtn = new Button("🆘 Ask AI for Help");
    private final Button micStart = new Button("🎙 Start Mic");
    private final Button micStop = new Button("🛑 Stop Mic");
    private final Button clear = new Button("🧼 Clear Transcript & AI");
    private final Button resetSetupBtn = new Button("🧹 Reset Setup");

    private final Details setupSection = new Details();


    private final Div micStatus = new Div();


    @Autowired
    public MainView(ChatClient chatClient) {
        this.chatClient = chatClient;

        interviewType.setItems("Coding", "System Design", "Behavioral", "Case Study");
        roleType.setItems("Backend Engineer", "Frontend Engineer", "Product Manager", "QA", "ML Engineer");
        responseFramework.setItems("SHER", "STAR", "None");
        toneSelector.setItems("Professional", "Empathetic", "Casual", "Neutral", "Educational");
        responseFormat.setItems("Bullet Points", "Descriptive Paragraph", "Step-by-Step", "Concise Summary");

        Tooltip.forComponent(responseFormat).setText("How should the AI answer?");
        Tooltip.forComponent(responseFramework).setText("STAR = Situation, Task, Action, Result");
        Tooltip.forComponent(toneSelector).setText("Choose a tone for the AI's response");

        VerticalLayout configForm = new VerticalLayout(interviewType, roleType, responseFramework, toneSelector,
                responseStyle, responseFormat);
        configForm.setPadding(false);
        configForm.setSpacing(false);

        setupSection.setSummaryText("🧠 Interview Setup");
        setupSection.setContent(configForm);
        setupSection.setOpened(true);

        add(new Text("🎓 Welcome to InterviewBuddy!"), setupSection);

        transcriptDisplay.setId("transcript");
        aiResponseDisplay.setId("ai-response");

        transcriptDisplay.setClassName("chat-box");
        aiResponseDisplay.setClassName("ai-box");

        micStart.addClickListener(e -> UI.getCurrent().getPage().executeJs("window.micStreamer.start();"));
        micStop.addClickListener(e -> UI.getCurrent().getPage().executeJs("window.micStreamer.stop();"));

        clear.addClickListener(e -> showConfirm("Clear All?", () -> {
            transcriptDisplay.setText("");
            aiResponseDisplay.setText("");
        }));

        resetSetupBtn.addClickListener(e -> showConfirm("Reset Setup?", () -> {
            UI.getCurrent().getPage().executeJs("""
                        localStorage.clear();
                        location.reload();
                    """);
        }));

        micStatus.setText("🔴 Mic: Inactive");
        micStatus.setId("micStatus");
        micStatus.getStyle()
                .set("font-weight", "bold")
                .set("color", "red")
                .set("margin-left", "10px")
                .set("margin-top", "8px");

        helpBtn.addClickListener(e -> {
            if (isSetupComplete() && !lastTranscribedText.isBlank()) {
                String prompt = """
                            You're helping someone in a %s interview for a %s role.
                            Response Style: %s
                            Preferred Framework: %s
                            Tone: %s
                            Format: Please answer in the form of a %s.

                            User just said:
                            %s
                        """.formatted(
                        interviewType.getValue(),
                        roleType.getValue(),
                        responseStyle.getValue(),
                        responseFramework.getValue(),
                        toneSelector.getValue(),
                        responseFormat.getValue(),
                        lastTranscribedText);

                String ai = chatClient.prompt().user(prompt).call().content();
                aiResponseDisplay.setText("🤖 " + ai);
            }
        });

        Button darkModeToggle = new Button("🌙 Toggle Dark Mode", e -> UI.getCurrent().getPage().executeJs("""
                    const html = document.querySelector('html');
                    const current = html.getAttribute('theme');
                    if (current === 'dark') {
                      html.removeAttribute('theme');
                      localStorage.setItem('theme', 'light');
                    } else {
                      html.setAttribute('theme', 'dark');
                      localStorage.setItem('theme', 'dark');
                    }
                """));

        HorizontalLayout controls = new HorizontalLayout(micStart, micStop, micStatus, clear, resetSetupBtn,
                darkModeToggle);
        controls.setAlignItems(Alignment.CENTER);

        add(controls, transcriptDisplay, helpBtn, aiResponseDisplay);

        interviewType.addValueChangeListener(e -> validateSetup());
        roleType.addValueChangeListener(e -> validateSetup());
        responseFramework.addValueChangeListener(e -> validateSetup());
        toneSelector.addValueChangeListener(e -> validateSetup());
        responseStyle.addValueChangeListener(e -> validateSetup());
        responseFormat.addValueChangeListener(e -> validateSetup());

        UI.getCurrent().getPage().executeJs("""
                    const load = k => localStorage.getItem(k);
                    Promise.all([
                      load("interviewType"), load("roleType"), load("responseFramework"),
                      load("toneSelector"), load("responseStyle"), load("responseFormat")
                    ]).then(v => $0.$server.restoreSetup(...v));
                """, getElement());

        validateSetup();
    }

    private boolean isSetupComplete() {
        return interviewType.getValue() != null &&
                roleType.getValue() != null &&
                responseFramework.getValue() != null &&
                toneSelector.getValue() != null &&
                responseFormat.getValue() != null &&
                !responseStyle.isEmpty();
    }

    private void validateSetup() {
        boolean ready = isSetupComplete();
        micStart.setEnabled(ready);
        micStop.setEnabled(ready);
        helpBtn.setEnabled(ready);
        clear.setEnabled(ready);
        resetSetupBtn.setEnabled(true);

        if (ready && setupSection.isOpened()) {
            setupSection.setOpened(false);
            Notification.show("🎉 Setup complete!", 3000, Notification.Position.TOP_CENTER);
        }
    }

    private void showConfirm(String title, Runnable onConfirm) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(title);
        dialog.setText("Are you sure?");
        dialog.setConfirmText("Yes");
        dialog.setCancelText("Cancel");
        dialog.addConfirmListener(e -> onConfirm.run());
        dialog.open();
    }

    public static java.util.function.Consumer<String> transcriptCallback = line -> {
        UI ui = UI.getCurrent();
        ui.access(() -> {
            ui.getPage().executeJs("""
                        const el = document.querySelector('#transcript');
                        if (el) {
                          el.innerHTML += "<div class='chat-bubble left'>[User]: " + $0 + "</div>";
                          el.scrollTop = el.scrollHeight;
                        }
                    """, line);
        });
    };

    @ClientCallable
    public void restoreSetup(String i, String r, String f, String t, String s, String fmt) {
        if (i != null)
            interviewType.setValue(i);
        if (r != null)
            roleType.setValue(r);
        if (f != null)
            responseFramework.setValue(f);
        if (t != null)
            toneSelector.setValue(t);
        if (s != null)
            responseStyle.setValue(s);
        if (fmt != null)
            responseFormat.setValue(fmt);
        validateSetup();
    }
}