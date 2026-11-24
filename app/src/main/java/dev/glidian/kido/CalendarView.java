package dev.glidian.kido;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Read-only current-month calendar and locally bundled historical events. */
final class CalendarView extends FrameLayout {
	private int eventTagColor = Kido.RED;
	private final TraceView frame;
	private final View accent;
	private FrameLayout monthPanel;
	private YearMonth displayedMonth = YearMonth.now();
	private OnMonthChangedListener monthChangedListener;

	interface OnMonthChangedListener {
		void onMonthChanged(YearMonth month);
	}

	CalendarView(Context context) {
		super(context);
		setFocusable(true);
		setFocusableInTouchMode(true);
		setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);

		frame = new TraceView(context);
		place(frame, 30, 432, 1840, 608);

		accent = new View(context);
		accent.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
				new int[]{Kido.withAlpha(Kido.LIME, 0.55f), Kido.withAlpha(Kido.AMBER, 0.5f),
						Kido.withAlpha(Kido.RED, 0.55f)}));
		accent.setPivotX(0);
		accent.setScaleX(0f);
		place(accent, 30, 344, 1500, 8);

		LinearLayout title = new LinearLayout(context);
		title.setOrientation(LinearLayout.HORIZONTAL);
		title.setGravity(Gravity.BOTTOM);
		title.addView(label("事象", Kido.mincho, 46, Kido.AMBER));
		title.addView(gap(18));
		title.addView(label("EVENT ARCHIVE", Kido.condBold, 30, Kido.INK, 0.14f));
		title.addView(gap(14));
		title.addView(label("SEC-05", Kido.mono, 22, Kido.DIM));
		place(title, 120, 360, -1, 58);

		renderMonth();
		buildEvents(context);
		buildHints();
		place(new RulerView(context, 440), 36, 446, 64, 580);
	}

	private void buildHints() {
		TextView system = label("◀  系統  SYSTEM", Kido.condBold, 26, Kido.DIM, 0.14f);
		place(system, 122, 966, 400, 34);

		TextView monthControls = label("UP // PREVIOUS       DOWN // NEXT       OK // CURRENT",
				Kido.mono, 20, Kido.DIM, 0.08f);
		monthControls.setGravity(Gravity.CENTER);
		place(monthControls, 500, 966, 1000, 34);
	}

	private void renderMonth() {
		if (monthPanel != null) removeView(monthPanel);
		buildMonth(getContext());
		if (monthChangedListener != null) monthChangedListener.onMonthChanged(displayedMonth);
	}

	void setOnMonthChangedListener(OnMonthChangedListener listener) {
		monthChangedListener = listener;
	}

	YearMonth getDisplayedMonth() {
		return displayedMonth;
	}

	void previousMonth() {
		displayedMonth = displayedMonth.minusMonths(1);
		renderMonth();
	}

	void nextMonth() {
		displayedMonth = displayedMonth.plusMonths(1);
		renderMonth();
	}

	void resetMonth() {
		YearMonth current = YearMonth.now();
		if (!displayedMonth.equals(current)) {
			displayedMonth = current;
			renderMonth();
		}
	}

	private void buildMonth(Context context) {
		YearMonth month = displayedMonth;
		LocalDate today = LocalDate.now();

		monthPanel = panel();
		place(monthPanel, 110, 452, 770, 500);
		place(monthPanel, label(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
				.toUpperCase(Locale.ROOT), Kido.condBold, 30, Kido.INK, 0.12f),
				28, 22, 460, 42);

		TextView todayTag = label("TODAY", Kido.mono, 18, Kido.LIME, 0.1f);
		todayTag.setGravity(Gravity.CENTER);
		todayTag.setVisibility(month.equals(YearMonth.from(today)) ? View.VISIBLE : View.INVISIBLE);
		todayTag.setBackground(outline(Kido.withAlpha(Kido.LIME, 0.10f), Kido.LIME));
		place(monthPanel, todayTag, 630, 22, 104, 34);

		String[] weekdays = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
		int cellWidth = 100;
		for (int i = 0; i < weekdays.length; i++) {
			TextView day = label(weekdays[i], Kido.mono, 17, Kido.DIM, 0.06f);
			day.setGravity(Gravity.CENTER);
			place(monthPanel, day, 28 + i * cellWidth, 86, cellWidth, 28);
		}

		int offset = month.atDay(1).getDayOfWeek().getValue() % 7;
		for (int day = 1; day <= month.lengthOfMonth(); day++) {
			int slot = offset + day - 1;
			int column = slot % 7;
			int row = slot / 7;
			boolean isToday = month.equals(YearMonth.from(today)) && day == today.getDayOfMonth();
			TextView cell = label(String.format(Locale.ROOT, "%02d", day),
					Kido.mono, 24, isToday ? Kido.VOID : Kido.CREAM, 0.02f);
			cell.setGravity(Gravity.CENTER);
			if (isToday) {
				cell.setBackground(outline(Kido.LIME, Kido.LIME));
				cell.setShadowLayer(Kido.px(12), 0, 0, Kido.withAlpha(Kido.LIME, 0.6f));
			} else {
				cell.setBackground(outline(Kido.withAlpha(Kido.PANEL, 0.35f),
						Kido.withAlpha(Kido.LINE, 0.65f)));
			}
			place(monthPanel, cell, 28 + column * cellWidth, 124 + row * 54, 88, 46);
		}

		TextView note = label("MONTH CYCLE  //  READ ONLY", Kido.mono, 16, Kido.DIM, 0.06f);
		place(monthPanel, note, 28, 462, -1, 24);
	}

	void setEventTagColor(int color) {
		eventTagColor = color;
	}

	private void buildEvents(Context context) {
		LocalDate today = LocalDate.now();
		FrameLayout panel = panel();
		place(panel, 910, 452, 900, 500);

		place(panel, label("本日", Kido.mincho, 30, Kido.AMBER), 24, 14, 80, 42);
		place(panel, label("WHAT HAPPENED ON THIS DAY", Kido.condBold, 26, Kido.INK, 0.12f),
				104, 22, 640, 34);
		String date = today.format(DateTimeFormatter.ofPattern("EEEE // dd MMMM yyyy", Locale.ENGLISH))
				.toUpperCase(Locale.ROOT);
		place(panel, label(date, Kido.mono, 17, Kido.DIM, 0.05f), 26, 66, 820, 26);
		View divider = new View(context);
		divider.setBackgroundColor(Kido.LINE);
		place(panel, divider, 24, 102, 850, 2);

		LinearLayout rows = new LinearLayout(context);
		rows.setOrientation(LinearLayout.VERTICAL);
		ScrollView scroll = new ScrollView(context);
		scroll.setVerticalScrollBarEnabled(false);
		scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
		scroll.addView(rows);
		place(panel, scroll, 20, 116, 860, 360);

		JSONArray events = readEvents(context, today);
		if (events == null || events.length() == 0) {
			TextView empty = label("NO LOCAL EVENTS FOR THIS DATE.", Kido.mono, 20, Kido.DIM, 0.06f);
			empty.setGravity(Gravity.CENTER_VERTICAL);
			rows.addView(empty, new LinearLayout.LayoutParams(-1, Kido.px(90)));
			return;
		}

		for (int i = 0; i < events.length(); i++) {
			JSONObject event = events.optJSONObject(i);
			if (event == null) continue;
			LinearLayout row = new LinearLayout(context);
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.setPadding(Kido.px(8), Kido.px(6), Kido.px(12), Kido.px(6));

			TextView year = label(String.valueOf(event.optInt("year")), Kido.condBold, 34,
					Kido.AMBER, 0.04f);
			year.setGravity(Gravity.CENTER);
			row.addView(year, new LinearLayout.LayoutParams(Kido.px(112), -1));

			TextView tag = label(event.optString("tag", "FACT"), Kido.mono, 15, Kido.VOID, 0.04f);
			tag.setGravity(Gravity.CENTER);
			tag.setBackground(outline(eventTagColor, eventTagColor));
			LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(Kido.px(120), Kido.px(32));
			tagParams.rightMargin = Kido.px(14);
			row.addView(tag, tagParams);

			TextView description = label(event.optString("title", "Historical event"),
					Kido.mono, 20, Kido.CREAM, 0.02f);
			description.setGravity(Gravity.CENTER_VERTICAL);
			row.addView(description, new LinearLayout.LayoutParams(0, -1, 1f));
			rows.addView(row, new LinearLayout.LayoutParams(-1, Kido.px(88)));

			if (i < events.length() - 1) {
				View line = new View(context);
				line.setBackgroundColor(Kido.withAlpha(Kido.LINE, 0.7f));
				rows.addView(line, new LinearLayout.LayoutParams(-1, Kido.px(1)));
			}
		}
	}

	private JSONArray readEvents(Context context, LocalDate date) {
		String key = date.format(DateTimeFormatter.ofPattern("MM-dd", Locale.ROOT));
		try (InputStream input = context.getAssets().open("calendar_events.json");
				BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
			StringBuilder json = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) json.append(line);
			return new JSONObject(json.toString()).optJSONArray(key);
		} catch (Exception ignored) {
			return null;
		}
	}

	void intro() {
		frame.trace(620);
		accent.setScaleX(0f);
		accent.animate().scaleX(1f).setDuration(560)
				.setInterpolator(new android.view.animation.PathInterpolator(0.77f, 0f, 0.18f, 1f))
				.start();
	}

	private FrameLayout panel() {
		FrameLayout view = new FrameLayout(getContext());
		view.setBackground(outline(Kido.PANEL, Kido.LINE));
		return view;
	}

	private GradientDrawable outline(int color, int stroke) {
		GradientDrawable drawable = new GradientDrawable();
		drawable.setColor(color);
		drawable.setStroke(Kido.px(2), stroke);
		return drawable;
	}

	private TextView label(String value, android.graphics.Typeface face, int size, int color) {
		return label(value, face, size, color, 0f);
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

	private View gap(int width) {
		View view = new View(getContext());
		view.setLayoutParams(new LinearLayout.LayoutParams(Kido.px(width), 1));
		return view;
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
