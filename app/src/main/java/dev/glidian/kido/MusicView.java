package dev.glidian.kido;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Lightweight local music browser. The Activity owns permission and playback. */
final class MusicView extends FrameLayout {
    private final List<TextView> names = new ArrayList<>();
    private final TextView selectedTitle;
    private final List<HexView> tiles = new ArrayList<>();

    MusicView(Context context, List<MusicLibrary.Track> tracks, Consumer<MusicLibrary.Track> onSelect) {
        super(context);
        setFocusableInTouchMode(true);
        setBackgroundColor(0xF5040404);

        TextView heading = label("音楽   MUSIC LIBRARY", Kido.condBold, 34, Kido.AMBER);
        place(heading, 110, 70, 900, 54);

        selectedTitle = label(tracks.isEmpty() ? "NO LOCAL MUSIC FOUND" : tracks.get(0).title,
                Kido.mincho, 30, Kido.CREAM);
        selectedTitle.setSingleLine(true);
        place(selectedTitle, 1130, 150, 650, 54);

        TextView count = label(String.format(Locale.ROOT, "%02d TRACKS", tracks.size()),
                Kido.mono, 20, Kido.DIM);
        place(count, 1130, 210, 650, 34);

        FrameLayout listPanel = new FrameLayout(context);
        listPanel.setBackground(background(Kido.PANEL, Kido.LINE));
        place(listPanel, 1120, 270, 660, 650);

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView namesScroll = new ScrollView(context);
        namesScroll.setVerticalScrollBarEnabled(false);
        namesScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        namesScroll.addView(list);
        listPanel.addView(namesScroll, new FrameLayout.LayoutParams(-1, -1));

        ScrollView gridScroll = new ScrollView(context);
        gridScroll.setVerticalScrollBarEnabled(false);
        gridScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        gridScroll.addView(grid);
        place(gridScroll, 110, 270, 930, 650);

        for (int start = 0; start < tracks.size(); start += 4) {
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            for (int i = start; i < Math.min(start + 4, tracks.size()); i++) {
                final int index = i;
                MusicLibrary.Track track = tracks.get(i);
                Kido.Look look = new Kido.Look("MP3", "音楽", "LOCAL AUDIO", Kido.GREEN, 0);
                HexView tile = new HexView(context, look, String.format(Locale.ROOT, "%02d", i + 1));
                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(Kido.px(205), Kido.px(235));
                tileParams.setMargins(0, 0, Kido.px(18), Kido.px(12));
                row.addView(tile, tileParams);
                TextView name = label(track.title, Kido.mono, 18, Kido.DIM);
                name.setSingleLine(true);
                name.setTag(index);
                name.setPadding(Kido.px(12), Kido.px(14), Kido.px(12), Kido.px(14));
                list.addView(name);
                names.add(name);
                tile.setOnFocusChangeListener((v, focused) -> {
                    if (focused) {
                        selectedTitle.setText(track.title);
                        highlight(index);
                    }
                });
                tile.setFocusable(true);
                tile.setClickable(true);
                // DPAD focus only previews the title; OK/Enter activates the tile and starts playback.
                tile.setOnClickListener(v -> onSelect.accept(track));
                tiles.add(tile);
                if (i == 0) tile.setId(View.generateViewId());
            }
            grid.addView(row);
        }

        place(label("BACK  //  MAIN", Kido.mono, 18, Kido.DIM), 110, 970, 500, 34);
        if (tracks.isEmpty()) {
            TextView empty = label("Open MusicView after adding audio to local storage.",
                    Kido.mono, 20, Kido.DIM);
            place(empty, 110, 300, 900, 50);
        } else {
            gridScroll.setFocusable(false);
            listPanel.setFocusable(false);
            // The first tile is the entry point; subsequent tiles follow in reading order.
            gridScroll.setNextFocusDownId(View.NO_ID);
            gridScroll.post(() -> {
                View first = grid.getChildAt(0);
                if (first instanceof LinearLayout && ((LinearLayout) first).getChildCount() > 0) {
                    ((LinearLayout) first).getChildAt(0).requestFocus();
                }
            });
        }
    }

    void focusFirstTrack() {
        if (!tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    private void highlight(int selected) {
        for (int i = 0; i < names.size(); i++) {
            names.get(i).setTextColor(i == selected ? Kido.INK : Kido.DIM);
            names.get(i).setBackgroundColor(i == selected ? Kido.withAlpha(Kido.GREEN, 0.18f) : Color.TRANSPARENT);
        }
    }

    private TextView label(String value, android.graphics.Typeface typeface, int size, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTypeface(typeface);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable background(int color, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setStroke(Kido.px(2), stroke);
        return drawable;
    }

    private void place(View view, int x, int y, int width, int height) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                width < 0 ? width : Kido.px(width), height < 0 ? height : Kido.px(height));
        params.leftMargin = Kido.px(x);
        params.topMargin = Kido.px(y);
        addView(view, params);
    }
}
