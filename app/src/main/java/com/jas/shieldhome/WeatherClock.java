package com.jas.shieldhome;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.jas.shieldhome.Ui.DEG;
import static com.jas.shieldhome.Ui.DOT;
import static com.jas.shieldhome.Ui.MATCH;
import static com.jas.shieldhome.Ui.SOFT_TEXT;
import static com.jas.shieldhome.Ui.WRAP;
import static com.jas.shieldhome.Ui.httpGet;
import static com.jas.shieldhome.Ui.str;

/**
 * Weather (data by Open-Meteo.com) and the clock time zone (display only, the Shield's time is unchanged).
 * The Home line shows now, plus today's high and low. Press OK on it for the full forecast.
 */
final class WeatherClock {

    private static final long EVERY_MS = 30 * 60 * 1000L;
    private static final String GAP = "  " + DOT + "  ";
    private static final int RAIN_BLUE = 0xFF85B7EB;

    private final MainActivity a;
    private volatile long lastFetch = 0;
    private volatile JSONObject lastData = null;

    WeatherClock(MainActivity a) {
        this.a = a;
    }

    void refreshIfDue() {
        if (System.currentTimeMillis() - lastFetch >= EVERY_MS) fetch();
    }

    void showPlaceholder() {
        a.weatherView.setText("");
        if (a.prefs.contains("wx_lat")) {
            a.placeView.setText(a.prefs.getString("wx_name", ""));
        } else {
            a.placeView.setText("Set weather in Settings");
        }
    }

    // ---------- Weather location ----------

    void askWeatherPlace() {
        final EditText input = placeInput();
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Weather location")
                .setMessage("Weather data by Open-Meteo.com")
                .setView(wrap(input))
                .setPositiveButton("Search", (d, w) -> searchPlace(input.getText().toString().trim(), false))
                .setNeutralButton("Remove weather", (d, w) -> {
                    a.prefs.edit().remove("wx_lat").remove("wx_lon").remove("wx_name").apply();
                    lastFetch = 0;
                    lastData = null;
                    showPlaceholder();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- Clock time zone ----------

    void askClockZone() {
        final EditText input = placeInput();
        String current = a.prefs.getString("clock_name", null);
        String msg = "Showing: " + (a.prefs.contains("clock_tz") && current != null ? current : "Shield's time")
                + "\n\nThis changes the clock on this screen only. The Shield's own time stays the same.";

        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Clock time zone")
                .setMessage(msg)
                .setView(wrap(input))
                .setPositiveButton("Search", (d, w) -> searchPlace(input.getText().toString().trim(), true))
                .setNeutralButton("Use Shield's time", (d, w) -> {
                    a.prefs.edit().remove("clock_tz").remove("clock_name").apply();
                    applyClockZone();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Sets the clock to the chosen time zone. The city name isn't shown under the date. */
    void applyClockZone() {
        String tz = a.prefs.getString("clock_tz", null);
        if (a.timeClock != null) a.timeClock.setTimeZone(tz);
        if (a.dateClock != null) a.dateClock.setTimeZone(tz);
        if (a.clockPlaceView != null) a.clockPlaceView.setVisibility(View.GONE);
    }

    // ---------- Place search (shared by weather and clock) ----------

    private EditText placeInput() {
        EditText input = new EditText(a);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setSingleLine(true);
        input.setHint("Town or city");
        return input;
    }

    private FrameLayout wrap(EditText input) {
        FrameLayout box = new FrameLayout(a);
        box.setPadding(a.dp(24), a.dp(8), a.dp(24), 0);
        box.addView(input);
        return box;
    }

    private void searchPlace(final String q, final boolean forClock) {
        if (q.isEmpty()) return;
        new Thread(() -> {
            try {
                String url = "https://geocoding-api.open-meteo.com/v1/search?count=6&language=en&format=json&name="
                        + URLEncoder.encode(q, "UTF-8");
                JSONObject o = new JSONObject(httpGet(url, null));
                JSONArray res = o.optJSONArray("results");
                final List<String> names = new ArrayList<>();
                final List<String> shortNames = new ArrayList<>();
                final List<double[]> coords = new ArrayList<>();
                final List<String> zones = new ArrayList<>();
                if (res != null) {
                    for (int i = 0; i < res.length(); i++) {
                        JSONObject r = res.getJSONObject(i);
                        String name = str(r, "name");
                        String admin = str(r, "admin1");
                        String country = str(r, "country");
                        StringBuilder sb = new StringBuilder(name);
                        if (!admin.isEmpty()) sb.append(", ").append(admin);
                        if (!country.isEmpty()) sb.append(", ").append(country);
                        names.add(sb.toString());
                        shortNames.add(name);
                        coords.add(new double[]{r.getDouble("latitude"), r.getDouble("longitude")});
                        zones.add(str(r, "timezone"));
                    }
                }
                a.runOnUiThread(() -> {
                    if (names.isEmpty()) {
                        Toast.makeText(a, "No places found. Try another spelling.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("Which one?")
                            .setItems(names.toArray(new String[0]), (d, w) -> {
                                if (forClock) {
                                    String tz = zones.get(w);
                                    if (tz.isEmpty()) {
                                        Toast.makeText(a, "Couldn't find a time zone for that place.",
                                                Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                    a.prefs.edit()
                                            .putString("clock_tz", tz)
                                            .putString("clock_name", shortNames.get(w))
                                            .apply();
                                    applyClockZone();
                                } else {
                                    a.prefs.edit()
                                            .putFloat("wx_lat", (float) coords.get(w)[0])
                                            .putFloat("wx_lon", (float) coords.get(w)[1])
                                            .putString("wx_name", shortNames.get(w))
                                            .apply();
                                    lastData = null;
                                    showPlaceholder();
                                    fetch();
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
            } catch (Exception ex) {
                a.runOnUiThread(() -> Toast.makeText(a,
                        "Couldn't search right now. Check the internet connection.", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // ---------- Fetching the weather ----------

    void fetch() {
        if (!a.prefs.contains("wx_lat")) {
            showPlaceholder();
            return;
        }
        final float lat = a.prefs.getFloat("wx_lat", 0f);
        final float lon = a.prefs.getFloat("wx_lon", 0f);
        lastFetch = System.currentTimeMillis();
        new Thread(() -> {
            try {
                String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,apparent_temperature,weather_code,is_day"
                        + "&hourly=temperature_2m,weather_code,precipitation_probability,is_day"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
                        + "precipitation_probability_max,sunrise,sunset"
                        + "&timezone=auto&forecast_days=7";
                JSONObject o = new JSONObject(httpGet(url, null));
                JSONObject cur = o.getJSONObject("current");
                final long now = Math.round(cur.getDouble("temperature_2m"));
                final int code = cur.getInt("weather_code");
                final boolean day = cur.optInt("is_day", 1) == 1;

                String range = "";
                JSONObject daily = o.optJSONObject("daily");
                if (daily != null) {
                    JSONArray hi = daily.optJSONArray("temperature_2m_max");
                    JSONArray lo = daily.optJSONArray("temperature_2m_min");
                    if (hi != null && lo != null && hi.length() > 0 && lo.length() > 0) {
                        range = Math.round(hi.getDouble(0)) + DEG + " / " + Math.round(lo.getDouble(0)) + DEG;
                    }
                }
                final String todayRange = range;
                lastData = o;

                a.runOnUiThread(() -> {
                    a.weatherView.setText(symbol(code, day) + "  " + now + DEG);
                    String place = a.prefs.getString("wx_name", "");
                    a.placeView.setText(place + GAP + describe(code)
                            + (todayRange.isEmpty() ? "" : GAP + todayRange));
                });
            } catch (Exception ex) {
                lastFetch = 0; // try again on the next check
            }
        }).start();
    }

    // ---------- Forecast card ----------

    /** Press OK on the weather: the full forecast. */
    void openForecast() {
        if (!a.prefs.contains("wx_lat")) {
            askWeatherPlace();
            return;
        }
        JSONObject o = lastData;
        if (o == null) {
            Toast.makeText(a, "Getting the forecast\u2026", Toast.LENGTH_SHORT).show();
            fetch();
            return;
        }
        try {
            showForecast(o);
        } catch (Exception e) {
            Toast.makeText(a, "Couldn't show the forecast right now.", Toast.LENGTH_LONG).show();
        }
    }

    private void showForecast(JSONObject o) throws Exception {
        JSONObject cur = o.getJSONObject("current");
        JSONObject hourly = o.getJSONObject("hourly");
        JSONObject daily = o.getJSONObject("daily");

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(24), a.dp(4), a.dp(24), a.dp(8));

        // Now
        int code = cur.getInt("weather_code");
        boolean day = cur.optInt("is_day", 1) == 1;
        long temp = Math.round(cur.getDouble("temperature_2m"));
        long feels = Math.round(cur.optDouble("apparent_temperature", temp));
        text(box, symbol(code, day) + "  " + temp + DEG + "   " + describe(code), 22, true, 0xFFFFFFFF, 0);

        // Today's details
        String hi = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)) + DEG;
        String lo = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)) + DEG;
        int rain = daily.getJSONArray("precipitation_probability_max").optInt(0, 0);
        String sunrise = timeOf(daily.getJSONArray("sunrise").optString(0, ""));
        String sunset = timeOf(daily.getJSONArray("sunset").optString(0, ""));
        text(box, "Feels like " + feels + DEG + GAP + "High " + hi + GAP + "Low " + lo + GAP + "Rain " + rain + "%",
                14, false, SOFT_TEXT, 6);
        text(box, "Sunrise " + sunrise + GAP + "Sunset " + sunset, 14, false, SOFT_TEXT, 2);

        // Next 12 hours
        text(box, "Next 12 hours", 15, true, 0xA0FFFFFF, 16);
        JSONArray times = hourly.getJSONArray("time");
        JSONArray temps = hourly.getJSONArray("temperature_2m");
        JSONArray codes = hourly.getJSONArray("weather_code");
        JSONArray rains = hourly.optJSONArray("precipitation_probability");
        JSONArray days = hourly.optJSONArray("is_day");
        String nowTime = str(cur, "time");
        String nowHour = nowTime.length() >= 13 ? nowTime.substring(0, 13) : nowTime;
        int start = 0;
        for (int i = 0; i < times.length(); i++) {
            if (times.getString(i).compareTo(nowHour) >= 0) {
                start = i;
                break;
            }
        }
        LinearLayout hours = new LinearLayout(a);
        hours.setOrientation(LinearLayout.HORIZONTAL);
        hours.setPadding(0, a.dp(6), 0, 0);
        for (int i = start; i < Math.min(start + 12, times.length()); i++) {
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            String label = i == start ? "Now" : timeOf(times.getString(i));
            boolean isDay = days == null || days.optInt(i, 1) == 1;
            int r = rains != null ? rains.optInt(i, 0) : 0;
            text(col, label, 12, false, SOFT_TEXT, 0).setGravity(Gravity.CENTER);
            text(col, symbol(codes.optInt(i, 0), isDay), 20, false, 0xFFFFFFFF, 2).setGravity(Gravity.CENTER);
            text(col, Math.round(temps.optDouble(i, 0)) + DEG, 15, true, 0xFFFFFFFF, 2).setGravity(Gravity.CENTER);
            text(col, r >= 20 ? r + "%" : " ", 12, false, RAIN_BLUE, 1).setGravity(Gravity.CENTER);
            hours.addView(col, new LinearLayout.LayoutParams(a.dp(56), WRAP));
        }
        box.addView(hours, new LinearLayout.LayoutParams(WRAP, WRAP));

        // Next 7 days
        text(box, "Next 7 days", 15, true, 0xA0FFFFFF, 16);
        JSONArray dTimes = daily.getJSONArray("time");
        JSONArray dCodes = daily.getJSONArray("weather_code");
        JSONArray dHi = daily.getJSONArray("temperature_2m_max");
        JSONArray dLo = daily.getJSONArray("temperature_2m_min");
        JSONArray dRain = daily.optJSONArray("precipitation_probability_max");
        SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.UK);
        SimpleDateFormat out = new SimpleDateFormat("EEE d", Locale.UK);
        for (int i = 0; i < dTimes.length(); i++) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, a.dp(4), 0, 0);

            String label;
            if (i == 0) label = "Today";
            else if (i == 1) label = "Tomorrow";
            else {
                Date d = in.parse(dTimes.getString(i));
                label = d != null ? out.format(d) : dTimes.getString(i);
            }
            int dc = dCodes.optInt(i, 0);
            int dr = dRain != null ? dRain.optInt(i, 0) : 0;

            row.addView(make(label, 15, false, 0xFFFFFFFF), new LinearLayout.LayoutParams(a.dp(110), WRAP));
            row.addView(make(symbol(dc, true), 18, false, 0xFFFFFFFF), new LinearLayout.LayoutParams(a.dp(40), WRAP));
            row.addView(make(describe(dc), 14, false, SOFT_TEXT), new LinearLayout.LayoutParams(a.dp(170), WRAP));
            row.addView(make(Math.round(dHi.optDouble(i, 0)) + DEG + " / " + Math.round(dLo.optDouble(i, 0)) + DEG,
                    15, true, 0xFFFFFFFF), new LinearLayout.LayoutParams(a.dp(110), WRAP));
            row.addView(make(dr >= 20 ? "Rain " + dr + "%" : "", 13, false, RAIN_BLUE),
                    new LinearLayout.LayoutParams(WRAP, WRAP));
            box.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
        }

        text(box, "Weather data by Open-Meteo.com", 12, false, 0x80FFFFFF, 16);

        ScrollView sv = new ScrollView(a);
        sv.setFocusable(true);
        sv.addView(box);

        AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(a.prefs.getString("wx_name", "Weather"))
                .setView(sv)
                .setPositiveButton("Close", null)
                .create();
        dlg.show();
        if (dlg.getWindow() != null) dlg.getWindow().setLayout(a.dp(760), WRAP);
    }

    private TextView make(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(a);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setSingleLine(true);
        t.setText(s);
        return t;
    }

    private TextView text(LinearLayout parent, String s, int sp, boolean bold, int color, int topDp) {
        TextView t = make(s, sp, bold, color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.topMargin = a.dp(topDp);
        parent.addView(t, lp);
        return t;
    }

    /** "2026-10-24T19:00" becomes "19:00". */
    private static String timeOf(String iso) {
        return iso != null && iso.length() >= 16 ? iso.substring(11, 16) : "";
    }

    // ---------- Icons and descriptions ----------

    private static String symbol(int c, boolean day) {
        if (c == 0) return day ? "\u2600\uFE0F" : "\uD83C\uDF19";               // sun / moon
        if (c == 1) return day ? "\uD83C\uDF24\uFE0F" : "\uD83C\uDF19";         // sun behind small cloud / moon
        if (c == 2) return day ? "\u26C5" : "\u2601\uFE0F";                     // sun behind cloud / cloud
        if (c == 3) return "\u2601\uFE0F";                                      // cloud
        if (c == 45 || c == 48) return "\uD83C\uDF2B\uFE0F";                    // fog
        if (c >= 51 && c <= 57) return "\uD83C\uDF26\uFE0F";                    // drizzle
        if ((c >= 61 && c <= 67) || (c >= 80 && c <= 82)) return "\uD83C\uDF27\uFE0F"; // rain
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return "\uD83C\uDF28\uFE0F";   // snow
        if (c >= 95) return "\u26C8\uFE0F";                                     // thunder
        return "\uD83C\uDF21\uFE0F";
    }

    private static String describe(int c) {
        if (c == 0) return "Clear";
        if (c == 1) return "Mostly clear";
        if (c == 2) return "Partly cloudy";
        if (c == 3) return "Cloudy";
        if (c == 45 || c == 48) return "Fog";
        if (c >= 51 && c <= 57) return "Drizzle";
        if (c >= 61 && c <= 67) return "Rain";
        if (c >= 71 && c <= 77) return "Snow";
        if (c >= 80 && c <= 82) return "Showers";
        if (c == 85 || c == 86) return "Snow showers";
        if (c >= 95) return "Thunderstorms";
        return "";
    }
}
