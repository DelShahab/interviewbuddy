package com.ai.interviewbuddy.ws;

import com.ai.interviewbuddy.service.PushService;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

// @Component
public class AudioWebSocketHandler extends BinaryWebSocketHandler {

    private volatile boolean googleAsrReady = false;
    private volatile boolean hasReceivedFirstAudio = false;
    private SpeechClient speechClient;
    private ClientStream<StreamingRecognizeRequest> stream;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("🔌 WebSocket connection established");
        // ASR not started until first audio is received
    }

    @Override
    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
            int payloadSize = message.getPayload().remaining();
            if (payloadSize == 0) {
                System.out.println("⚠️ Skipping empty payload");
                return;
            }

            System.out.println("📤 Audio chunk received: " + payloadSize + " bytes");

            if (!hasReceivedFirstAudio) {
                hasReceivedFirstAudio = true;
                System.out.println("🎧 First audio chunk received. Starting ASR...");
                startGoogleAsrStream();
            }

            if (googleAsrReady && stream != null) {
                byte[] audioBytes = toByteArray(message.getPayload());
                stream.send(StreamingRecognizeRequest.newBuilder()
                        .setAudioContent(ByteString.copyFrom(audioBytes))
                        .build());
            } else {
                System.out.println("⚠️ Skipped sending audio, ASR not ready");
            }

        } catch (Exception e) {
            System.err.println("❌ Exception in handleBinaryMessage: " + e.getMessage());
        }
    }

    private byte[] toByteArray(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private void startGoogleAsrStream() {
        try {
            // Load credentials from resources
            InputStream credentialsStream = getClass().getClassLoader()
                    .getResourceAsStream("credentials/interview-credentials.json");
            if (credentialsStream == null) {
                throw new RuntimeException("❌ Credential file not found in resources.");
            }

            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(credentialsStream)
                    .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));

            SpeechSettings settings = SpeechSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();

            speechClient = SpeechClient.create(settings);

            ResponseObserver<StreamingRecognizeResponse> responseObserver = responseObserver(); // extract below

            stream = speechClient.streamingRecognizeCallable().splitCall(responseObserver);

            RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(16000)
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
            System.out.println("🎤 Google ASR stream initialized successfully");

        } catch (Exception e) {
            System.err.println("❌ Failed to start SpeechClient: " + e.getMessage());
            stream = null;
        }
    }

    private ResponseObserver<StreamingRecognizeResponse> responseObserver() {
        return new ResponseObserver<>() {
            @Override
            public void onStart(StreamController controller) {
                System.out.println("✅ ASR gRPC stream started");
            }

            @Override
            public void onResponse(StreamingRecognizeResponse response) {
                response.getResultsList().forEach(result -> {
                    boolean isFinal = result.getIsFinal();
                    String transcript = result.getAlternativesCount() > 0
                            ? result.getAlternatives(0).getTranscript()
                            : "[NO ALTERNATIVE]";

                    if (!transcript.isBlank()) {
                        System.out.println(
                                "📝 Transcription result (" + (isFinal ? "FINAL" : "PARTIAL") + "): " + transcript);
                    }

                    if (isFinal) {
                        PushService.sendTranscript(transcript);
                    }
                });
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("❌ Google ASR stream error: " + t.getMessage());

                // Optional retry logic
                if (t.getMessage().contains("CANCELLED") || t.getMessage().contains("UNAVAILABLE")) {
                    System.out.println("🔁 Retrying ASR after failure...");
                    googleAsrReady = false;
                    hasReceivedFirstAudio = false;
                }

                shutdownAsr();
            }

            @Override
            public void onComplete() {
                System.out.println("✅ ASR stream completed");
            }
        };
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        System.out.println("🔌 WebSocket closed: " + status);
        shutdownAsr();
    }

    private void shutdownAsr() {
        if (speechClient != null) {
            try {
                speechClient.shutdownNow();
                System.out.println("🛑 SpeechClient shut down");
            } catch (Exception e) {
                System.err.println("⚠️ Error shutting down SpeechClient: " + e.getMessage());
            }
        }
        stream = null;
        googleAsrReady = false;
        hasReceivedFirstAudio = false;
    }

}
