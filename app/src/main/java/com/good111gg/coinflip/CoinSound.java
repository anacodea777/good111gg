package com.good111gg.coinflip;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;

/**
 * Synthesises the coin sounds at runtime (no audio files): a short "flick" when the toss
 * starts and a metallic "ping" on each impact.
 */
final class CoinSound {

    private static final int SAMPLE_RATE = 44100;

    private AudioTrack flick;
    private AudioTrack ping;
    private boolean enabled = true;

    CoinSound() {
        flick = build(synthFlick());
        ping = build(synthPing());
    }

    void setEnabled(boolean on) {
        enabled = on;
    }

    void playFlick() {
        play(flick, 0.8f);
    }

    void playPing(boolean loud) {
        play(ping, loud ? 1f : 0.35f);
    }

    void release() {
        if (flick != null) flick.release();
        if (ping != null) ping.release();
        flick = null;
        ping = null;
    }

    private void play(AudioTrack track, float volume) {
        if (!enabled || track == null || track.getState() != AudioTrack.STATE_INITIALIZED) return;
        try {
            if (track.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) track.stop();
            track.reloadStaticData();
            track.setVolume(volume);
            track.play();
        } catch (IllegalStateException ignored) {
            // Audio is a nicety; never crash because of it.
        }
    }

    private static AudioTrack build(short[] pcm) {
        try {
            AudioTrack t = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(pcm.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            t.write(pcm, 0, pcm.length);
            return t;
        } catch (Exception e) {
            return null;
        }
    }

    /** Inharmonic partials with fast decay — reads as a small metal disc hitting a table. */
    private static short[] synthPing() {
        int n = (int) (SAMPLE_RATE * 0.9);
        double[] freq = {2093, 3322, 4871, 6540, 8120};
        double[] amp = {1.0, 0.55, 0.35, 0.22, 0.12};
        double[] decay = {4.5, 6.5, 9.0, 12.0, 16.0};
        double[] buf = new double[n];
        Random rnd = new Random(7);
        double max = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double v = 0;
            for (int k = 0; k < freq.length; k++) {
                v += amp[k] * Math.sin(2 * Math.PI * freq[k] * t) * Math.exp(-decay[k] * t);
            }
            v += (rnd.nextDouble() * 2 - 1) * 0.6 * Math.exp(-t * 300); // click of the impact
            v *= Math.min(1.0, t / 0.0015);                               // tiny attack
            buf[i] = v;
            max = Math.max(max, Math.abs(v));
        }
        return toPcm(buf, max, 0.6);
    }

    private static short[] synthFlick() {
        int n = (int) (SAMPLE_RATE * 0.09);
        double[] buf = new double[n];
        Random rnd = new Random(3);
        double max = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double v = (rnd.nextDouble() * 2 - 1) * Math.exp(-t * 70) * 0.7
                    + Math.sin(2 * Math.PI * 1450 * t) * Math.exp(-t * 45) * 0.5;
            buf[i] = v;
            max = Math.max(max, Math.abs(v));
        }
        return toPcm(buf, max, 0.45);
    }

    private static short[] toPcm(double[] buf, double max, double gain) {
        short[] out = new short[buf.length];
        double k = max > 0 ? gain * 32767 / max : 0;
        for (int i = 0; i < buf.length; i++) out[i] = (short) Math.round(buf[i] * k);
        return out;
    }
}
