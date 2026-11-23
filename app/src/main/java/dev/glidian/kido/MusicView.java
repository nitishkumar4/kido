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
	private static final int[] TRACK_COLORS = {
		0xFFFF8C00, 0xFFFF3D9A, 0xFFB44CFF, 0xFF397BFF, 0xFF00D9FF,
		0xFF00E6A0, 0xFFA5E635, 0xFFF2E635, 0xFFFF4D4D, 0xFFFFFFFF
	};

	static String formatKanji(String format) {
		if ("MP3".equalsIgnoreCase(format)) return "圧縮";
		if ("FLAC".equalsIgnoreCase(format)) return "無損";
		if ("AAC".equalsIgnoreCase(format)) return "先進";
		return "音声";
	}

	static String fullFormat(String format) {
		if ("MP3".equalsIgnoreCase(format)) return "MPEG AUDIO LAYER III";
		if ("FLAC".equalsIgnoreCase(format)) return "LOSSLESS AUDIO";
		if ("AAC".equalsIgnoreCase(format)) return "ADVANCED AUDIO CODING";
		return format == null || format.trim().isEmpty() ? "AUDIO" : format.toUpperCase(Locale.ROOT);
	}

	static int colorForDuration(long duration) {
		int minute = (int) Math.max(0, duration / 60000L);
		return TRACK_COLORS[Math.min(Math.max(0, minute - 1), TRACK_COLORS.length - 1)];
	}

	private final PathInterpolator fluid = new PathInterpolator(0.2f, 0f, 0f, 1f);
	private final PathInterpolator snap = new PathInterpolator(0.77f, 0f, 0.18f, 1f);
	private final TraceView frame;
	private final View accent;
	private final List<TextView> names = new ArrayList<>();
	private final List<HexView> tiles = new ArrayList<>();
	private final List<Integer> tileRows = new ArrayList<>();
	private final List<String> trackTitles = new ArrayList<>();
	private ScrollView namesScroll;
	private int topRow;
	private boolean rowsMoved;

	MusicView(Context context, List<MusicLibrary.Track> tracks, Consumer<MusicLibrary.Track> onSelect,
			Consumer<MusicLibrary.Track> onFocus) {
		super(context);
		setFocusable(true);
		setFocusableInTouchMode(true);

		frame = new TraceView(context);
		place(frame, 30, 432, 1840, 608);

		accent = new View(context);
		accent.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
				new int[]{Kido.withAlpha(Kido.LIME, 0.55f), Kido.withAlpha(Kido.AMBER, 0.5f),
						Kido.withAlpha(Kido.RED, 0.55f)}));
		accent.setPivotX(0);
		accent.setScaleX(0f);
		place(accent, 30, 344, 1500, 8);

		LinearLayout sectionTitle = new LinearLayout(context);
		sectionTitle.setOrientation(LinearLayout.HORIZONTAL);
		sectionTitle.setGravity(Gravity.BOTTOM);
		sectionTitle.addView(label("音楽", Kido.mincho, 46, Kido.AMBER));
		sectionTitle.addView(gap(18));
		sectionTitle.addView(label("AUDIO ARCHIVE", Kido.condBold, 30, Kido.INK, 0.14f));
		sectionTitle.addView(gap(14));
		sectionTitle.addView(label("SEC-04", Kido.mono, 22, Kido.DIM));
		place(sectionTitle, 120, 360, -1, 58);

		TextView trackCount = label("AUDIO NODES  //  "
				+ String.format(Locale.ROOT, "%02d", tracks.size()), Kido.mono, 22, Kido.DIM, 0.06f);
		trackCount.setGravity(Gravity.END);
		place(trackCount, 1300, 380, 500, 30);


		FrameLayout listPanel = new FrameLayout(context);
		listPanel.setBackground(background(Kido.PANEL, Kido.LINE));
		place(listPanel, 1400, 452, 430, 500);

		place(listPanel, label("曲目", Kido.mincho, 28, Kido.AMBER), 20, 12, 76, 42);
		place(listPanel, label("SIGNAL INDEX", Kido.condBold, 26, Kido.INK, 0.12f), 94, 18, 210, 32);
		TextView count = label(String.format(Locale.ROOT, "%02d", tracks.size()),
				Kido.mono, 22, Kido.DIM);
		count.setGravity(Gravity.END);
		place(listPanel, count, 372, 20, 40, 28);
		View listDivider = new View(context);
		listDivider.setBackgroundColor(Kido.LINE);
		place(listPanel, listDivider, 18, 62, 394, 2);

		LinearLayout list = new LinearLayout(context);
		list.setOrientation(LinearLayout.VERTICAL);
		namesScroll = new ScrollView(context);
		namesScroll.setVerticalScrollBarEnabled(false);
		namesScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
		namesScroll.addView(list);
		place(listPanel, namesScroll, 12, 76, 406, 412);

		place(new RulerView(context, 440), 36, 452, 64, 500);

		FrameLayout grid = new FrameLayout(context);
		grid.setClipChildren(false);
		place(grid, 110, 452, 1270, 500);

		GlowView focusGlow = new GlowView(context, Kido.px(60), true);
		focusGlow.setColor(Kido.withAlpha(Kido.ORANGE, 0.8f));
		focusGlow.setAlpha(0f);
		place(grid, focusGlow, 0, 0, 352, 352);
		unclip(focusGlow);

		int index = 0;
		int rowIndex = 0;
		while (index < tracks.size()) {
			int perRow = rowIndex % 2 == 0 ? 5 : 4;
			for (int column = 0; column < perRow && index < tracks.size(); column++, index++) {
				final int trackIndex = index;
				MusicLibrary.Track track = tracks.get(index);
				Kido.Look look = new Kido.Look(track.format, "音楽", "LOCAL AUDIO",
						colorForDuration(track.duration), 0);
				HexView tile = new HexView(context, look,
						String.format(Locale.ROOT, "%02d", index + 1));
				float x = 40 + column * 246 + (rowIndex % 2 == 1 ? 123 : 0);
				place(grid, tile, Math.round(x), rowIndex * HEX_PITCH, 232, 266);

				TextView name = label("", Kido.mono, 22, Kido.DIM);
				name.setGravity(Gravity.CENTER_VERTICAL);
				name.setSingleLine(true);
				name.setEllipsize(TextUtils.TruncateAt.END);
				name.setTag(trackIndex);
				list.addView(name, new LinearLayout.LayoutParams(-1, Kido.px(38)));
				names.add(name);
				trackTitles.add(track.title);

				tile.setOnFocusChangeListener((v, focused) -> {
					if (focused) {
						onFocus.accept(track);
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

		TextView mainHint = label("▲ 制御系統 SYSTEM", Kido.condBold, 26, Kido.DIM, 0.14f);
		mainHint.setGravity(Gravity.START);
		place(mainHint, 110, 966, 420, 34);
		TextView controlsHint = label("OK // PLAY    ARROWS // BROWSE", Kido.mono, 20, Kido.DIM, 0.08f);
		controlsHint.setGravity(Gravity.CENTER);
		place(controlsHint, 560, 970, 800, 30);

		if (tracks.isEmpty()) {
			TextView empty = label("Open MusicView after adding audio to local storage.",
					Kido.mono, 20, Kido.DIM);
			place(empty, 150, 452, 900, 50);
		} else {
			listPanel.setFocusable(false);
			scrollRows(0, false);
		}

	}

	void intro() {
		frame.trace(620);
		accent.setScaleX(0f);
		accent.animate().scaleX(1f).setDuration(560)
				.setInterpolator(snap).start();
	}

	@Override
	public View focusSearch(View focused, int direction) {
		int index = tiles.indexOf(focused);
		if (index >= 0) {
			if (direction == View.FOCUS_LEFT && index > 0) return tiles.get(index - 1);
			if (direction == View.FOCUS_RIGHT && index + 1 < tiles.size()) return tiles.get(index + 1);
		}
		View next = super.focusSearch(focused, direction);
		if (index >= 0 && (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT)
				&& !tiles.contains(next)) return focused;
		return next;
	}

	boolean isTopRowFocused(View focused) {
		if (tiles.isEmpty()) return focused == this;
		int index = tiles.indexOf(focused);
		return index >= 0 && tileRows.get(index) == 0;
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
		float pad = Kido.px(60);
		float x = params.leftMargin - pad;
		float y = params.topMargin - Kido.px(topRow * HEX_PITCH) - Kido.px(43);
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
			names.get(i).setText((active ? "▶ " : "  ")
					+ "T." + String.format(Locale.ROOT, "%02d ", i + 1)
					+ trackTitles.get(i).toUpperCase(Locale.ROOT));
			names.get(i).setTextColor(active ? Kido.INK : Kido.DIM);
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

	private View gap(int width) {
		View view = new View(getContext());
		view.setLayoutParams(new LinearLayout.LayoutParams(Kido.px(width), 1));
		return view;
	}

	private TextView label(String value, android.graphics.Typeface typeface, int size, int color) {
		return label(value, typeface, size, color, 0f);
	}

	private TextView label(String value, android.graphics.Typeface typeface, int size, int color, float spacing) {
		TextView view = new TextView(getContext());
		view.setText(value);
		view.setTypeface(typeface);
		view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Kido.px(size));
		view.setTextColor(color);
		view.setIncludeFontPadding(false);
		if (spacing != 0f) view.setLetterSpacing(spacing);
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
