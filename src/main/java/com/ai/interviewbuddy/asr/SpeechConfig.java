package com.ai.interviewbuddy.asr;

public class SpeechConfig {
    public final int sampleRateHertz;
    public final int chunkMillis;
    public final int durationSeconds;
    public final String languageCode;
    public final String credentialPath;

    private SpeechConfig(int sampleRateHertz, int chunkMillis, int durationSeconds,
            String languageCode, String credentialPath) {
        this.sampleRateHertz = sampleRateHertz;
        this.chunkMillis = chunkMillis;
        this.durationSeconds = durationSeconds;
        this.languageCode = languageCode;
        this.credentialPath = credentialPath;
    }

    /**
     * Load from system properties or use defaults:
     * -DsampleRate=16000 -DchunkMillis=200 -Dduration=60
     * -DlanguageCode=en-US -Dcredentials=credentials/interview-credentials.json
     */
    public static SpeechConfig fromProperties() {
        int sr = Integer.parseInt(System.getProperty("sampleRate", "16000"));
        int cm = Integer.parseInt(System.getProperty("chunkMillis", "200"));
        int dur = Integer.parseInt(System.getProperty("duration", "60"));
        String lang = System.getProperty("languageCode", "en-US");
        String cred = System.getProperty("credentials",
                "credentials/interview-credentials.json");
        return new SpeechConfig(sr, cm, dur, lang, cred);
    }
}
