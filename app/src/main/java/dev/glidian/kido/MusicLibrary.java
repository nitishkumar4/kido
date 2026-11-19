package dev.glidian.kido;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reads indexed audio files, including audio stored in Downloads. Call load() off the UI thread. */
final class MusicLibrary {
    private static final String TAG = "KidoMusic";
    private static final String[] PROJECTION = {
            MediaStore.Files.FileColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.TITLE,
            MediaStore.Audio.AudioColumns.ARTIST,
            MediaStore.Audio.AudioColumns.DURATION
    };

    private MusicLibrary() {
    }

    static final class Track {
        final Uri uri;
        final String title;
        final String artist;
        final long duration;
        final String format;

        Track(Uri uri, String title, String artist, long duration, String format) {
            this.uri = uri;
            this.title = title;
            this.artist = artist;
            this.duration = duration;
            this.format = format;
        }
    }

    static List<Track> load(Context context) {
        List<Track> tracks = new ArrayList<>();
        Uri collection = MediaStore.Files.getContentUri("external");
        String selection = "(" + MediaStore.Files.FileColumns.MEDIA_TYPE + "=?"
                + " OR " + MediaStore.MediaColumns.MIME_TYPE + " LIKE ?)";
        String[] selectionArgs = {
                String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO),
                "audio/%"
        };
        String sortOrder = MediaStore.MediaColumns.TITLE + " COLLATE NOCASE ASC";

        try (Cursor cursor = context.getContentResolver().query(
                collection, PROJECTION, selection, selectionArgs, sortOrder)) {
            if (cursor == null) return tracks;

            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.TITLE);
            int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DURATION);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                String title = cursor.getString(titleColumn);
                if (title == null || title.trim().isEmpty()) {
                    title = cursor.getString(nameColumn);
                    if (title != null) {
                        int extension = title.lastIndexOf('.');
                        if (extension > 0) title = title.substring(0, extension);
                    }
                }
                String artist = cursor.getString(artistColumn);
                long duration = cursor.getLong(durationColumn);
                String displayName = cursor.getString(nameColumn);
                String mimeType = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.MediaColumns.MIME_TYPE));
                String format = audioFormat(displayName, mimeType);
                tracks.add(new Track(ContentUris.withAppendedId(collection, id),
                        title == null ? "Unknown track" : title, artist, duration, format));
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to query indexed audio files", e);
        }

        return tracks;
    }
}
