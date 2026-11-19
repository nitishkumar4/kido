package dev.glidian.kido;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;

/** Small platform-only music player. No service or third-party playback dependency. */
final class MusicPlayer {
    private final Context context;
    private final AudioManager audio;
    private MediaPlayer player;
    private boolean prepared;
    private boolean focusHeld;

    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) pauseForFocusLoss();
    };

    MusicPlayer(Context context) {
        this.context = context.getApplicationContext();
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    boolean play(Uri uri) {
        if (player != null && uri.equals(currentUri)) {
            if (prepared && !player.isPlaying()) {
                if (!requestFocus()) return false;
                player.start();
            }
            return true;
        }
        stop();
        if (!requestFocus()) return false;
        currentUri = uri;
        player = new MediaPlayer();
        try {
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setDataSource(context, uri);
            player.setOnPreparedListener(mp -> {
                prepared = true;
                mp.start();
            });
            player.setOnCompletionListener(mp -> stop());
            player.setOnErrorListener((mp, what, extra) -> {
                stop();
                return true;
            });
            player.prepareAsync();
            return true;
        } catch (Exception e) {
            stop();
            return false;
        }
    }

    private Uri currentUri;

    private boolean requestFocus() {
        int result = audio.requestAudioFocus(focusListener,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        focusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return focusHeld;
    }

    private void pauseForFocusLoss() {
        if (player != null && prepared && player.isPlaying()) player.pause();
        focusHeld = false;
    }

    void stop() {
        if (player != null) {
            player.setOnPreparedListener(null);
            player.setOnCompletionListener(null);
            player.setOnErrorListener(null);
            player.release();
            player = null;
        }
        prepared = false;
        currentUri = null;
        if (focusHeld) audio.abandonAudioFocus(focusListener);
        focusHeld = false;
    }

    void release() {
        stop();
    }
}
