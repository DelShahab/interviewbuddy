package com.ai.interviewbuddy.asr;

import javax.sound.sampled.*;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsoleSpeechStreaming {
    private static final Logger log = LoggerFactory.getLogger(ConsoleSpeechStreaming.class);

    public static void main(String[] args) throws Exception {
        // Load config from system props (with defaults)
        SpeechConfig config = SpeechConfig.fromProperties();

        // Create the core streaming service
        SpeechStreamer streamer = new SpeechStreamer(config);

        // Shut down gracefully on Ctrl-C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("SIGINT received, stopping...");
            streamer.stop();
        }));

        // Also listen for ENTER key to stop early
        Thread enterListener = new Thread(() -> {
            try {
                System.in.read();
                log.info("ENTER pressed, stopping...");
                streamer.stop();
            } catch (Exception e) {
                log.error("Error in ENTER listener", e);
            }
        });
        enterListener.setDaemon(true);
        enterListener.start();

        // Open the microphone line
        AudioFormat fmt = new AudioFormat(
                config.sampleRateHertz, 16, 1, true /* signed */, false /* little-endian */
        );
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
        if (!AudioSystem.isLineSupported(info)) {
            log.error("Audio format not supported: {}", fmt);
            return;
        }
        try (TargetDataLine mic = (TargetDataLine) AudioSystem.getLine(info)) {
            mic.open(fmt);
            mic.start();
            // Wrap in AudioInputStream for easier reads
            try (InputStream audioStream = new AudioInputStream(mic)) {
                log.info("Speak now (listening)…");
                streamer.startStreaming(audioStream);
            }
        }
    }
}
