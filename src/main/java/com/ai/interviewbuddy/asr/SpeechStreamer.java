package com.ai.interviewbuddy.asr;

import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpeechStreamer {
    private static final Logger log = LoggerFactory.getLogger(SpeechStreamer.class);
    private final SpeechConfig config;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    public SpeechStreamer(SpeechConfig config) {
        this.config = config;
    }

    public void stop() {
        stopRequested.set(true);
    }

    public void startStreaming(InputStream audioIn) throws Exception {
        // **LOAD CREDENTIALS FROM CLASSPATH** (not FileInputStream)
        try (InputStream credsStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(config.credentialPath)) {

            if (credsStream == null) {
                log.error("Could not load credentials from classpath: {}", config.credentialPath);
                return;
            }

            // Create SpeechClient with those credentials
            SpeechSettings settings = SpeechSettings.newBuilder()
                    .setCredentialsProvider(() -> GoogleCredentials.fromStream(credsStream)
                            .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform")))
                    .build();

            try (SpeechClient speechClient = SpeechClient.create(settings)) {

                ResponseObserver<StreamingRecognizeResponse> observer = new ResponseObserver<>() {
                    private String lastPartial = "";
                    private int lastWordCount = 0;

                    @Override
                    public void onStart(StreamController controller) {
                        log.info("ASR stream started");
                    }

                    @Override
                    public void onResponse(StreamingRecognizeResponse resp) {
                        for (var result : resp.getResultsList()) {
                            String raw = result.getAlternatives(0).getTranscript().trim();
                            String[] words = raw.isEmpty() ? new String[0] : raw.split("\\s+");
                            int wc = words.length;

                            if (!result.getIsFinal()) {
                                if (raw.startsWith(lastPartial) && wc > lastWordCount) {
                                    log.info("[PARTIAL] {}", raw);
                                    lastPartial = raw;
                                    lastWordCount = wc;
                                }
                            } else {
                                log.info("[FINAL]   {}", raw);
                                lastPartial = "";
                                lastWordCount = 0;
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        log.error("ASR error", t);
                    }

                    @Override
                    public void onComplete() {
                        log.info("ASR stream complete");
                    }
                };

                ClientStream<StreamingRecognizeRequest> clientStream = speechClient.streamingRecognizeCallable()
                        .splitCall(observer);

                RecognitionConfig recConfig = RecognitionConfig.newBuilder()
                        .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                        .setSampleRateHertz(config.sampleRateHertz)
                        .setLanguageCode(config.languageCode)
                        .build();
                StreamingRecognitionConfig streamConfig = StreamingRecognitionConfig.newBuilder()
                        .setConfig(recConfig)
                        .setInterimResults(true)
                        .build();
                clientStream.send(
                        StreamingRecognizeRequest.newBuilder()
                                .setStreamingConfig(streamConfig)
                                .build());

                int chunkBytes = config.sampleRateHertz * config.chunkMillis / 1000 * 2;
                byte[] buffer = new byte[chunkBytes];
                long endTime = System.currentTimeMillis() + config.durationSeconds * 1000L;

                log.info("Recording up to {}s (ENTER or Ctrl-C to stop)...", config.durationSeconds);
                while (System.currentTimeMillis() < endTime && !stopRequested.get()) {
                    int n = audioIn.read(buffer);
                    if (n > 0) {
                        clientStream.send(
                                StreamingRecognizeRequest.newBuilder()
                                        .setAudioContent(ByteString.copyFrom(buffer, 0, n))
                                        .build());
                    }
                }

                clientStream.closeSend();
                Thread.sleep(500);
            }
        }
    }
}
