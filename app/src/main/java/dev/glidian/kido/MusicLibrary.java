package dev.glidian.kido;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

/** Reads the device's indexed local music. Call load() off the UI thread. */
final class MusicLibrary {
    private static final String[] PROJECTION = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
    };

    private MusicLibrary() {
    }

    static final class Track {
        final Uri uri;
        final String title;
        final String artist;
        final long duration;

        Track(Uri uri, String title, String artist, long duration) {
            this.uri = uri;
            this.title = title;
            this.artist = artist;
            this.duration = duration;
        }
    }

    static List<Track> load(Context context) {
        List<Track> tracks = new ArrayList<>();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String sortOrder = MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";

        try (Cursor cursor = context.getContentResolver().query(
                collection, PROJECTION, null, null, sortOrder)) {
            if (cursor == null) return tracks;

            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                Uri uri = ContentUris.withAppendedId(collection, id);
                String title = cursor.getString(titleColumn);
                String artist = cursor.getString(artistColumn);
                long duration = cursor.getLong(durationColumn);

                tracks.add(new Track(uri, title, artist, duration));
            }
        }

        return tracks;
    }
}
