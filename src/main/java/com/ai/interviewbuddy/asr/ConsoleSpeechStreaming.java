package com.ai.interviewbuddy.asr;

import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;

import javax.sound.sampled.*;
import java.io.InputStream;
import java.util.List;

public class ConsoleSpeechStreaming {

    public static void main(String[] args) throws Exception {
        // 1) Load credentials
        InputStream credsStream = ConsoleSpeechStreaming.class
                .getClassLoader()
                .getResourceAsStream("credentials/interview-credentials.json");
        if (credsStream == null) {
            System.err.println("ERROR: credentials/interview-credentials.json not found");
            return;
        }
        GoogleCredentials creds = GoogleCredentials.fromStream(credsStream)
                .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));

        // 2) SpeechClient (global endpoint)
        SpeechSettings settings = SpeechSettings.newBuilder()
                .setCredentialsProvider(() -> creds)
                .build();
        try (SpeechClient speechClient = SpeechClient.create(settings)) {

            // 3) Observer with word-count guard
            ResponseObserver<StreamingRecognizeResponse> observer = new ResponseObserver<>() {
                private String lastPartial = "";
                private int lastWordCount = 0;

                @Override
                public void onStart(StreamController controller) {
                    System.out.println("[ASR] Stream started");
                }

                @Override
                public void onResponse(StreamingRecognizeResponse resp) {
                    for (StreamingRecognitionResult result : resp.getResultsList()) {
                        String raw = result.getAlternatives(0).getTranscript().trim();
                        String[] words = raw.isEmpty() ? new String[0] : raw.split("\\s+");
                        int wordCount = words.length;

                        if (!result.getIsFinal()) {
                            // must start with old text and add at least one word
                            if (raw.startsWith(lastPartial) && wordCount > lastWordCount) {
                                System.out.println("[PARTIAL] " + raw);
                                lastPartial = raw;
                                lastWordCount = wordCount;
                            }
                        } else {
                            // final: always print, then reset
                            System.out.println("[FINAL]   " + raw);
                            lastPartial = "";
                            lastWordCount = 0;
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    System.err.println("[ASR] Error: " + t);
                }

                @Override
                public void onComplete() {
                    System.out.println("[ASR] Stream complete");
                }
            };
            ClientStream<StreamingRecognizeRequest> clientStream = speechClient.streamingRecognizeCallable()
                    .splitCall(observer);

            // 4) Initial config: 16 kHz, LINEAR16, interim enabled
            RecognitionConfig recConfig = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setLanguageCode("en-US")
                    .setSampleRateHertz(16000)
                    .build();
            StreamingRecognitionConfig streamConfig = StreamingRecognitionConfig.newBuilder()
                    .setConfig(recConfig)
                    .setInterimResults(true)
                    .build();
            clientStream.send(
                    StreamingRecognizeRequest.newBuilder()
                            .setStreamingConfig(streamConfig)
                            .build());

            // 5) Mic at 16 kHz, 16-bit, mono, little-endian
            AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("ERROR: Line not supported: " + format);
                return;
            }
            TargetDataLine mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(format);
            mic.start();
            AudioInputStream audio = new AudioInputStream(mic);

            // 6) Stream 200 ms chunks = 16 000 Hz × 0.2 s × 2 bytes = 6 400 bytes
            byte[] buffer = new byte[6400];
            long endTime = System.currentTimeMillis() + 60_000;

            System.out.println("Speak now (streaming)…");
            while (System.currentTimeMillis() < endTime) {
                int bytesRead = audio.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    clientStream.send(
                            StreamingRecognizeRequest.newBuilder()
                                    .setAudioContent(ByteString.copyFrom(buffer, 0, bytesRead))
                                    .build());
                }
            }

            // 7) Tear down
            clientStream.closeSend();
            mic.stop();
            mic.close();
            Thread.sleep(500); // let final pieces flush
        }
    }
}
