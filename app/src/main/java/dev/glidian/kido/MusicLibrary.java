package dev.glidian.kido;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.InputStream;

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

    private static final String[] ARTWORK_PROJECTION = {
            MediaStore.Files.FileColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.TITLE,
            MediaStore.Audio.AudioColumns.ARTIST,
            MediaStore.Audio.AudioColumns.DURATION,
            MediaStore.Audio.AudioColumns.ALBUM_ID
    };

    private MusicLibrary() {
    }

    static final class Track {
        final Uri uri;
        final String title;
        final String artist;
        final long duration;
        final String format;
        final long albumId;

        Track(Uri uri, String title, String artist, long duration, String format, long albumId) {
            this.uri = uri;
            this.title = title;
            this.artist = artist;
            this.duration = duration;
            this.format = format;
            this.albumId = albumId;
        }
    }

    static List<Track> load(Context context) {
        List<Track> tracks = query(context, ARTWORK_PROJECTION, true);
        return tracks != null ? tracks : query(context, PROJECTION, false);
    }

    private static List<Track> query(Context context, String[] projection, boolean hasAlbumId) {
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
            int albumColumn = hasAlbumId
                    ? cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM_ID) : -1;

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
                long albumId = albumColumn >= 0 ? cursor.getLong(albumColumn) : -1;
                String displayName = cursor.getString(nameColumn);
                String mimeType = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.MediaColumns.MIME_TYPE));
                String format = audioFormat(displayName, mimeType);
                tracks.add(new Track(ContentUris.withAppendedId(collection, id),
                        title == null ? "Unknown track" : title, artist, duration, format, albumId));
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to query indexed audio files", e);
        }

        return tracks;
    }


    /** Samples a tiny album-art bitmap and releases it immediately; no disk or memory cache. */
    static int albumColor(Context context, long albumId, int fallback) {
        if (albumId < 0) return fallback;
        Uri artwork = Uri.parse("content://media/external/audio/albumart/" + albumId);
        Bitmap bitmap = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = context.getContentResolver().openInputStream(artwork)) {
                if (input == null) return fallback;
                BitmapFactory.decodeStream(input, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return fallback;
            int sample = 1;
            while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 32) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream input = context.getContentResolver().openInputStream(artwork)) {
                if (input == null) return fallback;
                bitmap = BitmapFactory.decodeStream(input, null, options);
            }
            if (bitmap == null) return fallback;

            int[] counts = new int[4096];
            int width = bitmap.getWidth(), height = bitmap.getHeight();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = bitmap.getPixel(x, y);
                    int red = (pixel >> 20) & 0x0F;
                    int green = (pixel >> 12) & 0x0F;
                    int blue = (pixel >> 4) & 0x0F;
                    counts[(red << 8) | (green << 4) | blue]++;
                }
            }
            int dominant = 0;
            for (int i = 1; i < counts.length; i++) {
                if (counts[i] > counts[dominant]) dominant = i;
            }
            return 0xFF000000 | (((dominant >> 8) & 0x0F) * 17 << 16)
                    | (((dominant >> 4) & 0x0F) * 17 << 8) | ((dominant & 0x0F) * 17);
        } catch (Exception e) {
            return fallback;
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private static String audioFormat(String displayName, String mimeType) {
        if (displayName != null) {
            int extension = displayName.lastIndexOf('.');
            if (extension >= 0 && extension < displayName.length() - 1) {
                return displayName.substring(extension + 1).toUpperCase(Locale.ROOT);
            }
        }
        if (mimeType != null && mimeType.startsWith("audio/")) {
            return mimeType.substring("audio/".length()).toUpperCase(Locale.ROOT);
        }
        return "AUDIO";
    }

}
