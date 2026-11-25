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

	private final List<Event> events = new ArrayList<>();
	private final TextView[] dates = new TextView[5];
	private final TextView[] titles = new TextView[5];
	private final View[] markers = new View[5];
	private final TextView detailDate;
	private final TextView detailTitle;
	private final TextView detailMeta;
	private final TextView detailDescription;
	private int selectedIndex;

	TimelineView(Context context) {
		super(context);
		setFocusable(true);
		setFocusableInTouchMode(true);
		setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);

		loadEvents(context);
		addToday();
		sortEvents();
		selectedIndex = todayIndex();

		TextView heading = label("時線", Kido.mincho, 46, Kido.AMBER);
		place(this, heading, 120, 360, 110, 58);
		place(this, label("TIMELINE", Kido.condBold, 30, Kido.INK, 0.14f), 250, 384, 260, 34);
		place(this, label("PUBLIC HOLIDAYS // FESTIVALS // IMPORTANT DAYS", Kido.mono, 19, Kido.DIM, 0.08f),
				560, 390, 1050, 28);

		View track = new View(context);
		track.setBackgroundColor(Kido.LINE);
		place(this, track, 160, 508, 1600, 3);

		for (int i = 0; i < 5; i++) {
			FrameLayout point = new FrameLayout(context);
			int x = 36 + i * 370;
			place(this, point, x, 440, 360, 190);

			markers[i] = new View(context);
			place(point, markers[i], 164, 64, 28, 28);
			dates[i] = label("", Kido.mono, 20, Kido.DIM, 0.05f);
			dates[i].setGravity(Gravity.CENTER);
			place(point, dates[i], 10, 108, 340, 28);
			titles[i] = label("", Kido.condBold, 22, Kido.CREAM, 0.04f);
			titles[i].setGravity(Gravity.CENTER);
			titles[i].setSingleLine(true);
			titles[i].setEllipsize(android.text.TextUtils.TruncateAt.END);
			place(point, titles[i], 8, 142, 344, 30);
		}

		FrameLayout detail = new FrameLayout(context);
		detail.setBackground(outline(Kido.PANEL, Kido.LINE));
		place(this, detail, 120, 666, 1680, 250);
		detailDate = label("", Kido.condBold, 42, Kido.AMBER, 0.06f);
		place(detail, detailDate, 34, 30, 500, 54);
		detailMeta = label("", Kido.mono, 20, Kido.DIM, 0.06f);
		place(detail, detailMeta, 36, 100, 470, 34);
		detailTitle = label("", Kido.condBold, 34, Kido.CREAM, 0.04f);
		detailTitle.setSingleLine(true);
		detailTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
		place(detail, detailTitle, 570, 34, 1050, 48);
		detailDescription = label("", Kido.mono, 21, Kido.INK, 0.02f);
		detailDescription.setMaxLines(3);
		place(detail, detailDescription, 572, 100, 1040, 110);

		TextView hints = label("◀ PREVIOUS EVENT                         NEXT EVENT ▶          HOME // TODAY",
				Kido.mono, 20, Kido.DIM, 0.07f);
		hints.setGravity(Gravity.CENTER);
		place(this, hints, 400, 966, 1120, 34);
		render();
	}

	Event getSelectedEvent() {
		return events.get(selectedIndex);
	}

	void previousEvent() {
		if (selectedIndex > 0) {
			selectedIndex--;
			render();
		}
	}

	void nextEvent() {
		if (selectedIndex < events.size() - 1) {
			selectedIndex++;
			render();
		}
	}

	void resetToToday() {
		selectedIndex = todayIndex();
		render();
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
			if (index < 0 || index >= events.size()) {
				dates[slot].setText("");
				titles[slot].setText("");
				markers[slot].setBackgroundColor(0x00000000);
				continue;
			}
			Event event = events.get(index);
			dates[slot].setText(event.date.format(DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH))
					.toUpperCase(Locale.ROOT));
			titles[slot].setText(event.title.toUpperCase(Locale.ROOT));
			int color = center ? Kido.LIME : Kido.DIM;
			dates[slot].setTextColor(color);
			titles[slot].setTextColor(center ? Kido.CREAM : Kido.DIM);
			GradientDrawable marker = new GradientDrawable();
			marker.setShape(GradientDrawable.OVAL);
			marker.setColor(center ? Kido.LIME : Kido.withAlpha(Kido.INK, 0.55f));
			if (center) marker.setStroke(Kido.px(3), Kido.LIME);
			markers[slot].setBackground(marker);
			FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) markers[slot].getLayoutParams();
			params.width = Kido.px(center ? 34 : 22);
			params.height = Kido.px(center ? 34 : 22);
			params.leftMargin = Kido.px(center ? 161 : 167);
			params.topMargin = Kido.px(center ? 61 : 67);
			markers[slot].setLayoutParams(params);
		}

		detailDate.setText(selected.date.format(DateTimeFormatter.ofPattern("EEEE // dd MMMM yyyy", Locale.ENGLISH))
				.toUpperCase(Locale.ROOT));
		int week = selected.date.get(WeekFields.ISO.weekOfWeekBasedYear());
		detailMeta.setText(String.format(Locale.ROOT, "WEEK %02d  //  DAY %03d  //  %s",
				week, selected.date.getDayOfYear(), selected.region));
		detailTitle.setText(selected.title.toUpperCase(Locale.ROOT));
		detailDescription.setText(selected.type.toUpperCase(Locale.ROOT) + "  //  " + selected.description);
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
