package dev.glidian.kido;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
		final String category;
		final String day;
		final String description;
		final String region;

		Event(LocalDate date, String day, String title, String category, String description, String region) {
			this.date = date;
			this.day = day;
			this.title = title;
			this.category = category;
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
	private final GradientDrawable categoryBackground;
	private final GlowView categoryGlow;
	private final TextView[] states = new TextView[5];
	private final View[] monthMarkers = new View[12];
	private final GlowView[] monthGlows = new GlowView[12];
	private final TextView[] monthLabels = new TextView[12];
	private final int[] monthColors = {
			0xFFFF5B55, 0xFFFFA24A, 0xFFFFD34E,
			0xFF70D68A, 0xFF55BCEB, 0xFFB18AE8
	};
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

		LinearLayout title = new LinearLayout(context);
		title.setOrientation(LinearLayout.HORIZONTAL);
		title.setGravity(Gravity.BOTTOM);
		title.addView(label("時線", Kido.mincho, 46, Kido.AMBER, 0f));
		title.addView(gap(18));
		title.addView(label("EVENT SEQUENCE", Kido.condBold, 30, Kido.INK, 0.14f));
		title.addView(gap(14));
		title.addView(label("SEC-06", Kido.mono, 22, Kido.DIM, 0f));
		place(this, title, 120, 360, -1, 58);

		LocalDate today = LocalDate.now();
		int daysInYear = today.lengthOfYear();
		int daysRemaining = daysInYear - today.getDayOfYear();
		float yearProgress = today.getDayOfYear() / (float) daysInYear;
		place(this, label("ANNUAL CYCLE // " + today.getYear(), Kido.mono, 13, Kido.DIM, 0.1f),
				112, 943, 520, 18);
		TextView progressValue = label(String.format(Locale.ROOT, "%.1f%% ELAPSED  //  %02d DAYS REMAINING",
				yearProgress * 100f, daysRemaining), Kido.mono, 13, Kido.AMBER, 0.08f);
		progressValue.setGravity(Gravity.END);
		place(this, progressValue, 1160, 943, 648, 18);
		String[] months = {"JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
				"JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"};
		for (int i = 0; i < months.length; i++) {
			int left = 112 + i * 1696 / 12;
			int right = 112 + (i + 1) * 1696 / 12;
			monthGlows[i] = new GlowView(context, Kido.px(7), false);
			monthGlows[i].setVisibility(View.INVISIBLE);
			place(this, monthGlows[i], left - 2, 958, right - left + 4, 18);
			monthMarkers[i] = new View(context);
			place(this, monthMarkers[i], left + 5, 965, right - left - 10, 3);
			monthLabels[i] = label(months[i], Kido.mono, 12, Kido.DIM, 0.025f);
			monthLabels[i].setGravity(Gravity.CENTER);
			place(this, monthLabels[i], left, 973, right - left, 20);
		}

		// Chronology rail: five instrument panels, with the selected node treated as the active signal.
		place(this, label("MAGI // CHRONOLOGY ARRAY", Kido.mono, 16, Kido.DIM, 0.12f),
				116, 439, 520, 24);
		place(this, label("EVENT STREAM  /  LOCAL DATABASE", Kido.mono, 16, Kido.DIM, 0.08f),
				792, 439, 680, 24);

		for (int i = 0; i < 5; i++) {
			FrameLayout card = new FrameLayout(context);
			card.setBackground(new SimCardDrawable(Kido.withAlpha(Kido.PANEL, 0.94f),
					Kido.withAlpha(Kido.LINE, 0.9f), Kido.px(22)));
			int x = 112 + i * 340;
			place(this, card, x, 463, 320, 178);
			eventCards[i] = card;

			View stripe = new View(context);
			stripe.setTag("stripe");
			place(card, stripe, 0, 0, 5, 178);

			nodeCodes[i] = label("NODE 00", Kido.mono, 15, Kido.DIM, 0.12f);
			place(card, nodeCodes[i], 14, 16, 180, 22);
			states[i] = label(i < 2 ? "ARCHIVE" : i == 2 ? "ACTIVE" : "UPCOMING",
					Kido.mono, 13, Kido.LIME, 0.08f);
			states[i].setGravity(Gravity.END);
			place(card, states[i], 202, 18, 104, 20);

			dates[i] = label("", Kido.condBold, 30, Kido.CREAM, 0.04f);
			place(card, dates[i], 14, 54, 292, 38);

			titles[i] = label("", Kido.mono, 17, Kido.DIM, 0.015f);
			titles[i].setMaxLines(2);
			titles[i].setEllipsize(android.text.TextUtils.TruncateAt.END);
			place(card, titles[i], 14, 105, 274, 48);
		}

		// Selected-event dossier. Three distinct data zones, like a tactical console readout.
		FrameLayout dossier = new FrameLayout(context);
		dossier.setBackground(outline(Kido.withAlpha(Kido.PANEL, 0.98f), Kido.LINE));
		place(this, dossier, 112, 675, 1696, 246);

		View warning = new View(context);
		warning.setBackgroundColor(Kido.RED);
		place(dossier, warning, 0, 0, 7, 246);
		place(dossier, label("SELECTED NODE // 03", Kido.mono, 15, Kido.DIM, 0.1f),
				28, 20, 270, 22);
		detailDay = label("", Kido.condBold, 76, Kido.AMBER, 0.02f);
		place(dossier, detailDay, 28, 54, 230, 82);
		detailMonth = label("", Kido.mono, 19, Kido.CREAM, 0.08f);
		place(dossier, detailMonth, 30, 148, 250, 28);
		FrameLayout categoryBadge = new FrameLayout(context);
		categoryBackground = new GradientDrawable();
		categoryBackground.setColor(Kido.withAlpha(Kido.RED, 0.08f));
		categoryBackground.setStroke(Kido.px(1), Kido.withAlpha(Kido.RED, 0.8f));
		categoryBadge.setBackground(categoryBackground);
		categoryGlow = new GlowView(context, Kido.px(5), false);
		categoryGlow.setColor(Kido.withAlpha(Kido.RED, 0.3f));
		categoryGlow.setGlowing(true);
		place(categoryBadge, categoryGlow, 0, 0, 260, 30);
		detailType = label("", Kido.condBold, 16, Kido.RED, 0.08f);
		detailType.setGravity(Gravity.CENTER_VERTICAL);
		detailType.setPadding(Kido.px(8), 0, Kido.px(8), 0);
		place(categoryBadge, detailType, 0, 0, 260, 30);
		place(dossier, categoryBadge, 30, 186, 260, 30);

		View dividerA = new View(context);
		dividerA.setBackgroundColor(Kido.withAlpha(Kido.AMBER, 0.45f));
		place(dossier, dividerA, 310, 24, 2, 198);

		detailTitle = label("", Kido.condBold, 36, Kido.CREAM, 0.04f);
		detailTitle.setSingleLine(true);
		detailTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
		place(dossier, detailTitle, 348, 38, 890, 50);
		place(dossier, label("EVENT RECORD // VERIFIED LOCAL ENTRY", Kido.mono, 14, Kido.DIM, 0.09f),
				350, 98, 780, 20);
		detailDescription = label("", Kido.mono, 19, Kido.INK, 0.015f);
		detailDescription.setMaxLines(4);
		detailDescription.setEllipsize(android.text.TextUtils.TruncateAt.END);
		place(dossier, detailDescription, 350, 132, 890, 96);

		View dividerB = new View(context);
		dividerB.setBackgroundColor(Kido.withAlpha(Kido.AMBER, 0.45f));
		place(dossier, dividerB, 1260, 24, 2, 198);

		place(dossier, label("TEMPORAL DATA", Kido.mono, 15, Kido.DIM, 0.12f),
				1290, 26, 340, 22);
		detailMeta = label("", Kido.mono, 18, Kido.LIME, 0.04f);
		place(dossier, detailMeta, 1290, 68, 360, 86);
		place(dossier, label("NERV // PUBLIC INFORMATION TERMINAL", Kido.mono, 12, Kido.DIM, 0.08f),
				1290, 190, 370, 18);

		TextView back = label("◀  事象  ARCHIVE", Kido.condBold, 26, Kido.DIM, 0.14f);
		place(this, back, 122, 995, 400, 34);

		TextView controls = label("UP // PREVIOUS       DOWN // NEXT       OK // CURRENT",
				Kido.mono, 20, Kido.DIM, 0.08f);
		controls.setGravity(Gravity.CENTER);
		place(this, controls, 500, 1000, 1000, 34);
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
				events.add(new Event(date, item.optString("day", date.getDayOfWeek().name()),
						item.optString("event"), item.optString("category"),
						item.optString("description"), item.optString("region", "IN")));
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
		events.add(new Event(today, today.getDayOfWeek().name(), "TODAY", "DATE MARKER",
				"No public holiday or festival is listed for today.", "IN"));
	}

	private void sortEvents() {
		Collections.sort(events, Comparator.comparing(event -> event.date));
	}

	static int categoryColor(String category) {
		String value = category == null ? "" : category.toUpperCase(Locale.ROOT);
		if (value.contains("WAR") || value.contains("POLITIC")) return Kido.RED;
		if (value.contains("RELIGIOUS") || value.contains("FESTIVAL")
				|| value.contains("CULTURAL FESTIVAL") || value.contains("CULTURAL OBSERVANCE")) {
			return 0xFFB18AE8;
		}
		if (value.contains("HISTORICAL") || value.contains("COMMEMORATION")) return 0xFF55BCEB;
		if (value.contains("STATE") || value.contains("UT OBSERVANCE")
				|| value.contains("TERRITORY")) return 0xFF70D68A;
		if (value.contains("INTERNATIONAL") || value.contains("AWARENESS")) return 0xFF6B9DFF;
		if (value.contains("NATIONAL") || value.contains("PUBLIC HOLIDAY")) return Kido.AMBER;
		if (value.contains("DATE MARKER")) return Kido.DIM;
		return Kido.DIM;
	}

	private int brighten(int color) {
		int red = android.graphics.Color.red(color);
		int green = android.graphics.Color.green(color);
		int blue = android.graphics.Color.blue(color);
		return android.graphics.Color.rgb(red + (255 - red) / 3,
				green + (255 - green) / 3, blue + (255 - blue) / 3);
	}

	private void updateAnnualCycle(LocalDate today, LocalDate selectedDate) {
		int selectedMonth = selectedDate.getMonthValue() - 1;
		for (int i = 0; i < monthMarkers.length; i++) {
			boolean active = i == selectedMonth;
			int baseColor = monthColors[i / 2];
			int color = active ? brighten(baseColor) : Kido.withAlpha(baseColor,
					i < today.getMonthValue() - 1 ? 0.62f : 0.38f);
			monthMarkers[i].setBackgroundColor(color);
			monthGlows[i].setColor(color);
			monthGlows[i].setGlowing(active);
			monthGlows[i].setVisibility(active ? View.VISIBLE : View.INVISIBLE);
			FrameLayout.LayoutParams markerParams = (FrameLayout.LayoutParams) monthMarkers[i].getLayoutParams();
			markerParams.height = Kido.px(active ? 4 : 3);
			monthMarkers[i].setLayoutParams(markerParams);
			monthLabels[i].setTextColor(color);
			monthLabels[i].setTypeface(active ? Kido.condBold : Kido.mono);
			if (active) monthLabels[i].setShadowLayer(Kido.px(8), 0, 0, Kido.withAlpha(color, 0.55f));
			else monthLabels[i].setShadowLayer(0, 0, 0, 0);
		}
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
		LocalDate today = LocalDate.now();
		updateAnnualCycle(today, selected.date);
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
			states[slot].setText(center ? "ACTIVE" : index < selectedIndex ? "ARCHIVE" : "UPCOMING");
			states[slot].setTextColor(Kido.LIME);

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
		detailType.setText("◆  " + selected.category.toUpperCase(Locale.ROOT));
		int categoryColor = categoryColor(selected.category);
		detailType.setTextColor(categoryColor);
		categoryBackground.setColor(Kido.withAlpha(categoryColor, 0.08f));
		categoryBackground.setStroke(Kido.px(1), Kido.withAlpha(categoryColor, 0.8f));
		categoryGlow.setColor(Kido.withAlpha(categoryColor, 0.3f));
		detailTitle.setText(selected.title.toUpperCase(Locale.ROOT));
		detailDescription.setText(selected.description);
		int week = selected.date.get(WeekFields.ISO.weekOfWeekBasedYear());
		detailMeta.setText(String.format(Locale.ROOT, "WEEK   %02d\nDAY    %03d\nAREA   %s",
				week, selected.date.getDayOfYear(), selected.region));
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

	private View gap(int width) {
		View view = new View(getContext());
		view.setLayoutParams(new LinearLayout.LayoutParams(Kido.px(width), 1));
		return view;
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
