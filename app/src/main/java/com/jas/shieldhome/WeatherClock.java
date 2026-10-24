package com.jas.shieldhome;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import static com.jas.shieldhome.Ui.DEG;
import static com.jas.shieldhome.Ui.DOT;
import static com.jas.shieldhome.Ui.httpGet;
import static com.jas.shieldhome.Ui.str;

/** Weather (data by Open-Meteo.com) and the clock time zone (display only, the Shield's time is unchanged). */
final class WeatherClock {

    private static final long EVERY_MS = 30 * 60 * 1000L;

    private final MainActivity a;
    private volatile long lastFetch = 0;

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
                        + "&current=temperature_2m,weather_code"
                        + "&timezone=auto&forecast_days=1";
                JSONObject o = new JSONObject(httpGet(url, null));
                JSONObject cur = o.getJSONObject("current");
                final long now = Math.round(cur.getDouble("temperature_2m"));
                final int code = cur.getInt("weather_code");
                a.runOnUiThread(() -> {
                    a.weatherView.setText(symbol(code) + "  " + now + DEG);
                    a.placeView.setText(a.prefs.getString("wx_name", "") + "  " + DOT + "  " + describe(code));
                });
            } catch (Exception ex) {
                lastFetch = 0; // try again on the next check
            }
        }).start();
    }

    private static String symbol(int c) {
        if (c == 0) return "\u2600\uFE0F";
        if (c == 1) return "\uD83C\uDF24\uFE0F";
        if (c == 2) return "\u26C5";
        if (c == 3) return "\u2601\uFE0F";
        if (c == 45 || c == 48) return "\uD83C\uDF2B\uFE0F";
        if (c >= 51 && c <= 57) return "\uD83C\uDF26\uFE0F";
        if ((c >= 61 && c <= 67) || (c >= 80 && c <= 82)) return "\uD83C\uDF27\uFE0F";
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return "\uD83C\uDF28\uFE0F";
        if (c >= 95) return "\u26C8\uFE0F";
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
