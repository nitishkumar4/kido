package dev.glidian.kido;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Lightweight local-event timeline. Reads only holidays.json. */
final class TimelineView extends FrameLayout {
	static final class Event {
		final LocalDate date;
		final String title;
		final String type;
		final String description;
		final String region;

		Event(LocalDate date, String title, String type, String description, String region) {
			this.date = date;
			this.title = title;
			this.type = type;
			this.description = description;
			this.region = region;
		}
	}

	private final TraceView frame;
	private final View accent;
	private final List<Event> events = new ArrayList<>();
	private final FrameLayout[] eventCards = new FrameLayout[5];
	private final TextView[] dates = new TextView[5];
	private final TextView[] titles = new TextView[5];
	private final TextView[] nodeCodes = new TextView[5];
	private final TextView detailDay;
	private final TextView detailMonth;
	private final TextView detailTitle;
	private final TextView detailMeta;
	private final TextView detailDescription;
	private final TextView detailType;
	private int selectedIndex;

	TimelineView(Context context) {
		super(context);
		setFocusable(true);
		setFocusableInTouchMode(true);
		setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);

		frame = new TraceView(context);
		place(this, frame, 30, 432, 1840, 608);

		accent = new View(context);
		accent.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
				new int[]{Kido.withAlpha(Kido.LIME, 0.55f), Kido.withAlpha(Kido.AMBER, 0.5f),
						Kido.withAlpha(Kido.RED, 0.55f)}));
		accent.setPivotX(0);
		accent.setScaleX(0f);
		place(this, accent, 30, 344, 1500, 8);

		loadEvents(context);
		addToday();
		sortEvents();
		selectedIndex = todayIndex();

		TextView heading = label("時線", Kido.mincho, 46, Kido.AMBER, 1.0f);
		place(this, heading, 120, 360, 110, 58);
		place(this, label("TIMELINE", Kido.condBold, 30, Kido.INK, 0.14f), 250, 384, 260, 34);
		place(this, label("PUBLIC HOLIDAYS // FESTIVALS // IMPORTANT DAYS", Kido.mono, 19, Kido.DIM, 0.08f),
				560, 390, 1050, 28);
		place(this, label("SEC-06", Kido.mono, 22, Kido.DIM, 0.05f), 1640, 390, 140, 28);

		// Chronology rail: five instrument panels, with the selected node treated as the active signal.
		place(this, label("MAGI // CHRONOLOGY ARRAY", Kido.mono, 16, Kido.DIM, 0.12f),
				116, 448, 520, 24);
		place(this, label("EVENT STREAM  /  LOCAL DATABASE  /  IN-MH", Kido.mono, 16, Kido.DIM, 0.08f),
				1120, 448, 680, 24);

		for (int i = 0; i < 5; i++) {
			FrameLayout card = new FrameLayout(context);
			card.setBackground(new SimCardDrawable(Kido.withAlpha(Kido.PANEL, 0.94f),
					Kido.withAlpha(Kido.LINE, 0.9f), Kido.px(22)));
			int x = 112 + i * 340;
			place(this, card, x, 472, 320, 178);
			eventCards[i] = card;

			View stripe = new View(context);
			stripe.setTag("stripe");
			place(card, stripe, 0, 0, 5, 178);

			nodeCodes[i] = label("NODE 00", Kido.mono, 15, Kido.DIM, 0.12f);
			place(card, nodeCodes[i], 14, 16, 180, 22);
			TextView state = label(i < 2 ? "ARCHIVE" : i == 2 ? "ACTIVE" : "INCOMING",
					Kido.mono, 13, Kido.DIM, 0.08f);
			state.setGravity(Gravity.END);
			place(card, state, 202, 18, 104, 20);

			dates[i] = label("", Kido.condBold, 30, Kido.CREAM, 0.04f);
			place(card, dates[i], 14, 54, 292, 38);

			titles[i] = label("", Kido.mono, 17, Kido.DIM, 0.015f);
			titles[i].setMaxLines(2);
			titles[i].setEllipsize(android.text.TextUtils.TruncateAt.END);
			place(card, titles[i], 14, 105, 274, 48);
		}

		// Selected-event dossier. Three distinct data zones, like a tactical console readout.
		FrameLayout dossier = new FrameLayout(context);
		dossier.setBackground(new TicketDrawable(Kido.withAlpha(Kido.PANEL, 0.98f), Kido.withAlpha(Kido.LINE, 0.95f), Kido.withAlpha(Kido.AMBER, 0.55f)));
		place(this, dossier, 112, 686, 1696, 246);

		place(dossier, label("BOARDING PASS // EVENT RECORD", Kido.mono, 15, Kido.DIM, 0.1f),
				28, 18, 270, 22);
		place(dossier, label("DATE", Kido.mono, 13, Kido.DIM, 0.12f),
				28, 48, 180, 18);
		detailDay = label("", Kido.condBold, 76, Kido.AMBER, 0.02f);
		place(dossier, detailDay, 28, 54, 230, 82);
		detailMonth = label("", Kido.mono, 19, Kido.CREAM, 0.08f);
		place(dossier, detailMonth, 30, 148, 250, 28);
		detailType = label("", Kido.mono, 15, Kido.RED, 0.1f);
		place(dossier, detailType, 30, 192, 260, 22);

		detailTitle = label("", Kido.condBold, 36, Kido.CREAM, 0.04f);
		detailTitle.setSingleLine(true);
		detailTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
		place(dossier, detailTitle, 348, 38, 890, 50);
		place(dossier, label("DESTINATION // EVENT", Kido.mono, 14, Kido.AMBER, 0.1f),
				350, 20, 780, 20);
		detailDescription = label("", Kido.mono, 19, Kido.INK, 0.015f);
		detailDescription.setMaxLines(3);
		detailDescription.setEllipsize(android.text.TextUtils.TruncateAt.END);
		place(dossier, detailDescription, 350, 132, 890, 82);

		place(dossier, label("FLIGHT DETAILS", Kido.mono, 15, Kido.DIM, 0.12f),
				1290, 20, 340, 22);
		detailMeta = label("", Kido.mono, 18, Kido.LIME, 0.04f);
		place(dossier, detailMeta, 1290, 68, 360, 86);
		place(dossier, label("KIDO AIR  /  IN-MH", Kido.mono, 12, Kido.DIM, 0.08f),
				1290, 190, 370, 18);

		TextView hints = label("◀ PREVIOUS EVENT                         NEXT EVENT ▶          HOME // TODAY",
				Kido.mono, 18, Kido.DIM, 0.07f);
		hints.setGravity(Gravity.CENTER);
		place(this, hints, 400, 966, 1120, 34);
		place(this, new RulerView(context, 440), 36, 446, 64, 580);
		render();
	}

	Event getSelectedEvent() {
		return events.get(selectedIndex);
	}

	void previousEvent() {
		slideTo(-1);
	}

	void nextEvent() {
		slideTo(1);
	}

	private void slideTo(int direction) {
		int nextIndex = selectedIndex + direction;
		if (nextIndex < 0 || nextIndex >= events.size()) return;
		selectedIndex = nextIndex;
		render();
	}

	void resetToToday() {
		selectedIndex = todayIndex();
		render();
	}

	void intro() {
		frame.trace(620);
		accent.setScaleX(0f);
		accent.animate().scaleX(1f).setDuration(560)
				.setInterpolator(new android.view.animation.PathInterpolator(0.77f, 0f, 0.18f, 1f))
				.start();
	}

	private void loadEvents(Context context) {
		try (InputStream input = context.getAssets().open("holidays.json");
				BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
			StringBuilder json = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) json.append(line);
			JSONArray array = new JSONArray(json.toString());
			for (int i = 0; i < array.length(); i++) {
				JSONObject item = array.optJSONObject(i);
				if (item == null) continue;
				LocalDate date = LocalDate.parse(item.optString("date"));
				events.add(new Event(date, item.optString("title"), item.optString("type"),
						item.optString("description"), item.optString("region", "IN-MH")));
			}
		} catch (Exception ignored) {
			events.clear();
		}
	}

	private void addToday() {
		LocalDate today = LocalDate.now();
		for (int i = 0; i < events.size(); i++) {
			Event event = events.get(i);
			if (event.date.equals(today)) {
				return;
			}
		}
		events.add(new Event(today, "TODAY", "TODAY", "No public holiday or festival is listed for today.",
				"LOCAL DATE"));
	}

	private void sortEvents() {
		Collections.sort(events, Comparator.comparing(event -> event.date));
	}

	private int todayIndex() {
		LocalDate today = LocalDate.now();
		for (int i = 0; i < events.size(); i++) {
			if (events.get(i).date.equals(today)) return i;
		}
		return 0;
	}

	private void render() {
		Event selected = getSelectedEvent();
		for (int slot = 0; slot < 5; slot++) {
			int index = selectedIndex + slot - 2;
			boolean center = slot == 2;
			FrameLayout card = eventCards[slot];
			if (index < 0 || index >= events.size()) {
				card.setVisibility(View.INVISIBLE);
				continue;
			}
			card.setVisibility(View.VISIBLE);
			card.setAlpha(1f);
			Event event = events.get(index);
			nodeCodes[slot].setText(String.format(Locale.ROOT, "NODE %02d", index + 1));
			dates[slot].setText(event.date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))
					.toUpperCase(Locale.ROOT));
			titles[slot].setText(event.title.toUpperCase(Locale.ROOT));

			int signal = center ? Kido.AMBER : Kido.DIM;
			card.setBackground(new SimCardDrawable(center ? Kido.withAlpha(Kido.ORANGE, 0.13f)
					: Kido.withAlpha(Kido.PANEL, 0.94f),
					center ? Kido.AMBER : Kido.withAlpha(Kido.LINE, 0.9f), Kido.px(22)));
			View stripe = card.findViewWithTag("stripe");
			if (stripe != null) stripe.setBackgroundColor(center ? Kido.RED : Kido.withAlpha(Kido.AMBER, 0.35f));
			nodeCodes[slot].setTextColor(signal);
			dates[slot].setTextColor(center ? Kido.AMBER : Kido.CREAM);
			titles[slot].setTextColor(center ? Kido.CREAM : Kido.DIM);
		}

		detailDay.setText(selected.date.format(DateTimeFormatter.ofPattern("dd", Locale.ENGLISH)));
		detailMonth.setText(selected.date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
				.toUpperCase(Locale.ROOT));
		detailType.setText("◆  " + selected.type.toUpperCase(Locale.ROOT));
		detailTitle.setText(selected.title.toUpperCase(Locale.ROOT));
		detailDescription.setText(selected.description);
		int week = selected.date.get(WeekFields.ISO.weekOfWeekBasedYear());
		detailMeta.setText(String.format(Locale.ROOT, "WEEK   %02d\nDAY    %03d\nAREA   %s",
				week, selected.date.getDayOfYear(), selected.region));
	}

	private static final class TicketDrawable extends android.graphics.drawable.Drawable {
		private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
		private final android.graphics.Path path = new android.graphics.Path();
		private final int fillColor;
		private final int strokeColor;
		private final int perforationColor;

		TicketDrawable(int fillColor, int strokeColor, int perforationColor) {
			this.fillColor = fillColor;
			this.strokeColor = strokeColor;
			this.perforationColor = perforationColor;
		}

		@Override
		public void draw(android.graphics.Canvas canvas) {
			float w = getBounds().width();
			float h = getBounds().height();
			float a = Kido.px(310);
			float b = Kido.px(1260);
			float r = Kido.px(18);
			path.reset();
			path.moveTo(0, 0);
			path.lineTo(a, 0);
			path.lineTo(a, h / 2f - r);
			path.quadTo(a - r, h / 2f, a, h / 2f + r);
			path.lineTo(a, h);
			path.lineTo(b, h);
			path.lineTo(b, h / 2f + r);
			path.quadTo(b + r, h / 2f, b, h / 2f - r);
			path.lineTo(b, 0);
			path.lineTo(w, 0);
			path.lineTo(w, h);
			path.lineTo(0, h);
			path.close();
			paint.setStyle(android.graphics.Paint.Style.FILL);
			paint.setColor(fillColor);
			canvas.drawPath(path, paint);
			paint.setStyle(android.graphics.Paint.Style.STROKE);
			paint.setStrokeWidth(Kido.px(2));
			paint.setColor(strokeColor);
			canvas.drawPath(path, paint);
			paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{Kido.px(5), Kido.px(6)}, 0));
			paint.setColor(perforationColor);
			canvas.drawLine(a, Kido.px(16), a, h - Kido.px(16), paint);
			canvas.drawLine(b, Kido.px(16), b, h - Kido.px(16), paint);
			paint.setPathEffect(null);
		}

		@Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
		@Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
		@Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
	}

	private static final class SimCardDrawable extends android.graphics.drawable.Drawable {
		private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
		private final android.graphics.Path path = new android.graphics.Path();
		private final int fillColor;
		private final int strokeColor;
		private final float cut;

		SimCardDrawable(int fillColor, int strokeColor, float cut) {
			this.fillColor = fillColor;
			this.strokeColor = strokeColor;
			this.cut = cut;
		}

		@Override
		public void draw(android.graphics.Canvas canvas) {
			android.graphics.Rect bounds = getBounds();
			float right = bounds.width();
			float bottom = bounds.height();
			path.reset();
			path.moveTo(0, 0);
			path.lineTo(right, 0);
			path.lineTo(right, bottom - cut);
			path.lineTo(right - cut, bottom);
			path.lineTo(0, bottom);
			path.close();
			paint.setStyle(android.graphics.Paint.Style.FILL);
			paint.setColor(fillColor);
			canvas.drawPath(path, paint);
			paint.setStyle(android.graphics.Paint.Style.STROKE);
			paint.setStrokeWidth(Kido.px(2));
			paint.setColor(strokeColor);
			canvas.drawPath(path, paint);
		}

		@Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
		@Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
		@Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
	}

	private TextView label(String value, android.graphics.Typeface face, int size, int color, float spacing) {
		TextView view = new TextView(getContext());
		view.setText(value);
		view.setTypeface(face);
		view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Kido.px(size));
		view.setTextColor(color);
		view.setIncludeFontPadding(false);
		if (spacing != 0f) view.setLetterSpacing(spacing);
		return view;
	}

	private GradientDrawable outline(int color, int stroke) {
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
}
