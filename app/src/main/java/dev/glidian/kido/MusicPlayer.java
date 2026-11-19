package dev.glidian.kido;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;

/** Small platform-only music player. No service or third-party playback dependency. */
final class MusicPlayer {
	private final Context context;
	private final AudioManager audio;
	private final MediaSession session;
	private MediaPlayer player;
	private Uri currentUri;
	private boolean prepared;
	private boolean focusHeld;

	private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
		if (change < 0) pauseForFocusLoss();
	};

	MusicPlayer(Context context) {
		this.context = context.getApplicationContext();
		audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		session = new MediaSession(this.context, "KidoMusic");
		session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
				| MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
		session.setCallback(new MediaSession.Callback() {
			@Override
			public void onPlay() {
				if (player == null || !requestFocus()) return;
				if (prepared && !player.isPlaying()) player.start();
				updateState();
			}

			@Override
			public void onPause() {
				pause();
			}

			@Override
			public void onStop() {
				stop();
			}
		});
	}

	boolean toggle(Uri uri, String title, String artist, long duration) {
		if (player != null && uri.equals(currentUri) && prepared && player.isPlaying()) {
			pause();
			return true;
		}
		return play(uri, title, artist, duration);
	}

	boolean play(Uri uri, String title, String artist, long duration) {
		if (player != null && uri.equals(currentUri)) {
			if (prepared && !player.isPlaying()) {
				if (!requestFocus()) return false;
				player.start();
			}
			updateState();
			return true;
		}

		stop();
		if (!requestFocus()) return false;

		currentUri = uri;
		session.setMetadata(new MediaMetadata.Builder()
				.putString(MediaMetadata.METADATA_KEY_TITLE, title)
				.putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
				.putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
				.build());
		player = new MediaPlayer();
		try {
			player.setAudioStreamType(AudioManager.STREAM_MUSIC);
			player.setDataSource(context, uri);
			player.setOnPreparedListener(mp -> {
				prepared = true;
				if (focusHeld) mp.start();
				session.setActive(true);
				updateState();
			});
			player.setOnCompletionListener(mp -> stop());
			player.setOnErrorListener((mp, what, extra) -> {
				stop();
				return true;
			});
			player.prepareAsync();
			updateState();
			return true;
		} catch (Exception e) {
			stop();
			return false;
		}
	}

	private boolean requestFocus() {
		int result = audio.requestAudioFocus(focusListener,
				AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
		focusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
		return focusHeld;
	}

	private void pauseForFocusLoss() {
		pause();
		focusHeld = false;
	}

	private void pause() {
		if (player != null && prepared && player.isPlaying()) player.pause();
		updateState();
	}

	private void updateState() {
		if (!session.isActive()) return;
		boolean playing = player != null && prepared && player.isPlaying();
		int state = playing ? PlaybackState.STATE_PLAYING
				: prepared ? PlaybackState.STATE_PAUSED : PlaybackState.STATE_BUFFERING;
		long position = player != null && prepared ? player.getCurrentPosition() : 0;
		PlaybackState playbackState = new PlaybackState.Builder()
				.setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
						| PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP)
				.setState(state, position, playing ? 1f : 0f)
				.build();
		session.setPlaybackState(playbackState);
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
		session.setPlaybackState(new PlaybackState.Builder()
				.setState(PlaybackState.STATE_STOPPED, 0, 0f)
				.build());
		session.setActive(false);
		session.setMetadata(null);
	}

	void release() {
		stop();
		session.release();
	}
}
