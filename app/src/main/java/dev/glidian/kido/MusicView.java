package dev.glidian.kido;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.text.TextUtils;
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
	private static final int HEX_PITCH = 214;
	private final PathInterpolator fluid = new PathInterpolator(0.2f, 0f, 0f, 1f);
	private final List<TextView> names = new ArrayList<>();
	private final List<HexView> tiles = new ArrayList<>();
	private final List<Integer> tileRows = new ArrayList<>();
	private final List<String> trackTitles = new ArrayList<>();
	private final TextView selectedTitle;
	private final TextView selectedArtist;
	private final TextView selectedMeta;
	private ScrollView namesScroll;
	private int topRow;
	private boolean rowsMoved;

	MusicView(Context context, List<MusicLibrary.Track> tracks, Consumer<MusicLibrary.Track> onSelect) {
		super(context);
		setFocusableInTouchMode(true);
		setBackgroundColor(0xF5040404);

		View hexGrid = new View(context);
		hexGrid.setBackground(Backdrop.hexGrid(getResources(), Kido.withAlpha(Kido.AMBER, 0.07f)));
		addView(hexGrid, new FrameLayout.LayoutParams(-1, -1));

		TraceView frame = new TraceView(context);
		place(frame, 30, 240, 1840, 700);

		View accent = new View(context);
		accent.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
				new int[]{Kido.withAlpha(Kido.LIME, 0.55f), Kido.withAlpha(Kido.AMBER, 0.5f),
						Kido.withAlpha(Kido.RED, 0.55f)}));
		accent.setPivotX(0);
		accent.setScaleX(0f);
		place(accent, 30, 190, 1500, 8);

		TextView heading = label("音楽   MUSIC LIBRARY", Kido.condBold, 34, Kido.AMBER);
		place(heading, 110, 70, 900, 54);

		place(label("NOW SELECTED", Kido.mono, 18, Kido.DIM), 1430, 132, 370, 28);
		selectedTitle = label(tracks.isEmpty() ? "NO LOCAL MUSIC FOUND" : tracks.get(0).title,
				Kido.mincho, 30, Kido.CREAM);
		selectedTitle.setSingleLine(true);
		selectedTitle.setEllipsize(TextUtils.TruncateAt.END);
		place(selectedTitle, 1430, 164, 370, 46);

		selectedArtist = label(tracks.isEmpty() ? "" : artistName(tracks.get(0)),
				Kido.condBold, 20, Kido.AMBER);
		selectedArtist.setSingleLine(true);
		selectedArtist.setEllipsize(TextUtils.TruncateAt.END);
		place(selectedArtist, 1430, 210, 370, 30);

		selectedMeta = label(tracks.isEmpty() ? "" : trackMeta(tracks.get(0)),
				Kido.mono, 17, Kido.DIM);
		place(selectedMeta, 1430, 242, 370, 24);

		FrameLayout listPanel = new FrameLayout(context);
		listPanel.setBackground(background(Kido.PANEL, Kido.LINE));
		place(listPanel, 1430, 270, 370, 650);

		place(listPanel, label("曲目", Kido.mincho, 34, Kido.AMBER), 20, 10, 76, 48);
		place(listPanel, label("TRACK LIST", Kido.condBold, 22, Kido.INK), 104, 22, 170, 30);
		TextView count = label(String.format(Locale.ROOT, "%02d", tracks.size()),
				Kido.mono, 18, Kido.DIM);
		count.setGravity(Gravity.END);
		place(listPanel, count, 300, 24, 48, 26);
		View listDivider = new View(context);
		listDivider.setBackgroundColor(Kido.LINE);
		place(listPanel, listDivider, 18, 66, 334, 2);

		LinearLayout list = new LinearLayout(context);
		list.setOrientation(LinearLayout.VERTICAL);
		namesScroll = new ScrollView(context);
		namesScroll.setVerticalScrollBarEnabled(false);
		namesScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
		namesScroll.addView(list);
		place(listPanel, namesScroll, 12, 78, 346, 556);

		FrameLayout grid = new FrameLayout(context);
		grid.setClipChildren(false);
		place(grid, 110, 270, 1270, 650);

		GlowView focusGlow = new GlowView(context, Kido.px(44), true);
		focusGlow.setColor(Kido.withAlpha(Kido.ORANGE, 0.7f));
		focusGlow.setAlpha(0f);
		place(grid, focusGlow, 0, 0, 312, 312);
		unclip(focusGlow);

		int index = 0;
		int rowIndex = 0;
		while (index < tracks.size()) {
			int perRow = rowIndex % 2 == 0 ? 5 : 4;
			for (int column = 0; column < perRow && index < tracks.size(); column++, index++) {
				final int trackIndex = index;
				MusicLibrary.Track track = tracks.get(index);
				Kido.Look look = new Kido.Look(track.format, "音楽", "LOCAL AUDIO", Kido.GREEN, 0);
				HexView tile = new HexView(context, look,
						String.format(Locale.ROOT, "%02d", index + 1));
				float x = 40 + column * 246 + (rowIndex % 2 == 1 ? 123 : 0);
				place(grid, tile, Math.round(x), rowIndex * HEX_PITCH, 232, 266);

				TextView name = label("", Kido.mono, 20, Kido.DIM);
				name.setSingleLine(true);
				name.setEllipsize(TextUtils.TruncateAt.END);
				name.setGravity(Gravity.CENTER_VERTICAL);
				name.setTag(trackIndex);
				name.setPadding(Kido.px(14), Kido.px(8), Kido.px(12), Kido.px(8));
				list.addView(name, new LinearLayout.LayoutParams(-1, Kido.px(54)));
				names.add(name);
				trackTitles.add(track.title);

				tile.setOnFocusChangeListener((v, focused) -> {
					if (focused) {
						selectedTitle.setText(track.title);
						selectedArtist.setText(artistName(track));
						selectedMeta.setText(trackMeta(track));
						highlight(trackIndex);
						scrollListTo(name);
						scrollRows(tileRows.get(trackIndex), true);
						moveFocusGlow(focusGlow, tile, true);
					}
				});
				tile.setOnClickListener(v -> onSelect.accept(track));
				tiles.add(tile);
				tileRows.add(rowIndex);
			}
			rowIndex++;
		}

		for (int i = 0; i < tiles.size(); i++) {
			HexView tile = tiles.get(i);
			tile.setId(View.generateViewId());
			int previous = i > 0 ? tiles.get(i - 1).getId() : tile.getId();
			int next = i + 1 < tiles.size() ? tiles.get(i + 1).getId() : tile.getId();
			tile.setNextFocusLeftId(previous);
			tile.setNextFocusRightId(next);
		}

		place(label("BACK  //  MAIN", Kido.mono, 18, Kido.DIM), 110, 970, 500, 34);
		if (tracks.isEmpty()) {
			TextView empty = label("Open MusicView after adding audio to local storage.",
					Kido.mono, 20, Kido.DIM);
			place(empty, 110, 300, 900, 50);
		} else {
			listPanel.setFocusable(false);
			scrollRows(0, false);
			grid.post(() -> tiles.get(0).requestFocus());
		}

		View scanlines = new View(context);
		scanlines.setBackground(Backdrop.scanlines(getResources(), 0x30000000));
		scanlines.setFocusable(false);
		addView(scanlines, new FrameLayout.LayoutParams(-1, -1));

		post(() -> {
			frame.trace(620);
			accent.animate().scaleX(1f).setDuration(560).start();
		});
	}

	@Override
	public View focusSearch(View focused, int direction) {
		int index = tiles.indexOf(focused);
		if (index >= 0) {
			if (direction == View.FOCUS_LEFT && index > 0) return tiles.get(index - 1);
			if (direction == View.FOCUS_RIGHT && index + 1 < tiles.size()) return tiles.get(index + 1);
		}
		View next = super.focusSearch(focused, direction);
		if (index >= 0 && !tiles.contains(next)) return focused;
		return next;
	}

	void focusFirstTrack() {
		if (!tiles.isEmpty()) tiles.get(0).requestFocus();
	}

	private void scrollRows(int row, boolean animate) {
		if (tiles.isEmpty()) return;
		int lastRow = tileRows.get(tileRows.size() - 1);
		int nextTop = Math.max(0, Math.min(row - 1, Math.max(0, lastRow - 1)));
		rowsMoved = nextTop != topRow;
		topRow = nextTop;
		float shift = -Kido.px(topRow * HEX_PITCH);
		for (int i = 0; i < tiles.size(); i++) {
			HexView tile = tiles.get(i);
			int tileRow = tileRows.get(i);
			float alpha = tileRow >= topRow && tileRow <= topRow + 1 ? 1f : 0f;
			if (animate) {
				tile.animate().translationY(shift).alpha(alpha).setDuration(340).start();
			} else {
				tile.setTranslationY(shift);
				tile.setAlpha(alpha);
			}
		}
	}

	private void moveFocusGlow(GlowView glow, HexView tile, boolean animate) {
		FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) tile.getLayoutParams();
		float pad = Kido.px(40);
		float x = params.leftMargin - pad;
		float y = params.topMargin - Kido.px(topRow * HEX_PITCH) - Kido.px(23);
		if (!animate || glow.getAlpha() < 0.05f) {
			glow.animate().cancel();
			glow.setTranslationX(x);
			glow.setTranslationY(y);
			glow.animate().alpha(1f).setDuration(220).start();
			return;
		}
		glow.animate().translationX(x).translationY(y)
				.setDuration(rowsMoved ? 340 : 240).setInterpolator(fluid).start();
	}

	private static void unclip(View view) {
		for (android.view.ViewParent parent = view.getParent(); parent instanceof ViewGroup;
				parent = parent.getParent()) {
			((ViewGroup) parent).setClipChildren(false);
		}
	}

	private void highlight(int selected) {
		for (int i = 0; i < names.size(); i++) {
			boolean active = i == selected;
			names.get(i).setText((active ? "▶  " : "   ")
					+ String.format(Locale.ROOT, "%02d  ", i + 1) + trackTitles.get(i));
			names.get(i).setTextColor(active ? Kido.INK : Kido.DIM);
			names.get(i).setBackgroundColor(active
					? Kido.withAlpha(Kido.GREEN, 0.16f) : Color.TRANSPARENT);
		}
	}

	private void scrollListTo(TextView row) {
		if (namesScroll == null) return;
		namesScroll.post(() -> {
			int top = namesScroll.getScrollY();
			int bottom = top + namesScroll.getHeight();
			if (row.getTop() < top || row.getBottom() > bottom) {
				namesScroll.smoothScrollTo(0,
						Math.max(0, row.getTop() - namesScroll.getHeight() / 2 + row.getHeight() / 2));
			}
		});
	}

	private static String artistName(MusicLibrary.Track track) {
		return track.artist == null || track.artist.trim().isEmpty()
				|| track.artist.equals("<unknown>") ? "UNKNOWN ARTIST"
				: track.artist.toUpperCase(Locale.ROOT);
	}

	private static String trackMeta(MusicLibrary.Track track) {
		long seconds = Math.max(0, track.duration / 1000);
		String duration = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
		return track.format + "  //  " + duration;
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

	private void place(ViewGroup parent, View view, int x, int y, int width, int height) {
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
				width < 0 ? width : Kido.px(width), height < 0 ? height : Kido.px(height));
		params.leftMargin = Kido.px(x);
		params.topMargin = Kido.px(y);
		parent.addView(view, params);
	}

	private void place(View view, int x, int y, int width, int height) {
		place(this, view, x, y, width, height);
	}
}
