package com.ai.interviewbuddy.ws;

import com.ai.interviewbuddy.service.PushService;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class AudioWebSocketHandler extends BinaryWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(AudioWebSocketHandler.class);

    private volatile boolean googleAsrReady = false;
    private volatile boolean hasReceivedFirstAudio = false;
    private SpeechClient speechClient;
    private ClientStream<StreamingRecognizeRequest> stream;

    @Autowired
    private PushService pushService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connection established");
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
            int payloadSize = message.getPayload().remaining();
            if (payloadSize == 0) {
                log.warn("Skipping empty payload");
                return;
            }
            log.info("Audio chunk received: {} bytes", payloadSize);
            if (!hasReceivedFirstAudio) {
                hasReceivedFirstAudio = true;
                log.info("First audio chunk received. Starting ASR...");
                startGoogleAsrStream();
            }
            if (googleAsrReady && stream != null) {
                byte[] audioBytes = toByteArray(message.getPayload());
                stream.send(StreamingRecognizeRequest.newBuilder()
                        .setAudioContent(ByteString.copyFrom(audioBytes))
                        .build());
            } else {
                log.warn("Skipped sending audio, ASR not ready");
            }
        } catch (Exception e) {
            log.error("Exception in handleBinaryMessage", e);
        }
    }

    private byte[] toByteArray(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private void startGoogleAsrStream() {
        try (InputStream credentialsStream = getClass().getClassLoader()
                .getResourceAsStream("credentials/interview-credentials.json")) {
            if (credentialsStream == null) {
                throw new RuntimeException("Credential file not found");
            }
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream)
                    .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
            SpeechSettings settings = SpeechSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();
            speechClient = SpeechClient.create(settings);

            ResponseObserver<StreamingRecognizeResponse> responseObserver = responseObserver();
            stream = speechClient.streamingRecognizeCallable().splitCall(responseObserver);

            RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(44100) // match browser mic rate
                    .setLanguageCode("en-US")
                    .build();
            StreamingRecognitionConfig streamConfig = StreamingRecognitionConfig.newBuilder()
                    .setConfig(recognitionConfig)
                    .setInterimResults(true)
                    .setSingleUtterance(false)
                    .build();
            stream.send(StreamingRecognizeRequest.newBuilder()
                    .setStreamingConfig(streamConfig)
                    .build());

            googleAsrReady = true;
            log.info("Google ASR stream initialized successfully");
        } catch (Exception e) {
            log.error("Failed to start SpeechClient", e);
            stream = null;
            googleAsrReady = false;
        }
    }

    private ResponseObserver<StreamingRecognizeResponse> responseObserver() {
        return new ResponseObserver<>() {
            @Override
            public void onStart(StreamController controller) {
                log.info("ASR gRPC stream started");
            }

            @Override
            public void onResponse(StreamingRecognizeResponse response) {
                response.getResultsList().forEach(result -> {
                    boolean isFinal = result.getIsFinal();
                    String transcript = result.getAlternativesCount() > 0
                            ? result.getAlternatives(0).getTranscript()
                            : "";
                    if (!transcript.isBlank()) {
                        log.debug("Transcription result ({}) : {}", isFinal ? "FINAL" : "PARTIAL", transcript);
                    }
                    if (isFinal) {
                        pushService.pushTranscript(transcript);
                    }
                });
            }

            @Override
            public void onError(Throwable t) {
                log.error("Google ASR stream error", t);
                googleAsrReady = false;
                hasReceivedFirstAudio = false;
                shutdownAsr();
            }

            @Override
            public void onComplete() {
                log.info("ASR stream completed");
            }
        };
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket closed: {}", status);
        shutdownAsr();
    }

    private void shutdownAsr() {
        if (speechClient != null) {
            try {
                speechClient.shutdown();
                if (!speechClient.awaitTermination(1, TimeUnit.SECONDS)) {
                    speechClient.shutdownNow();
                }
                log.info("SpeechClient shut down");
            } catch (Exception e) {
                log.warn("Error shutting down SpeechClient", e);
            }
        }
        stream = null;
        googleAsrReady = false;
        hasReceivedFirstAudio = false;
    }
}
