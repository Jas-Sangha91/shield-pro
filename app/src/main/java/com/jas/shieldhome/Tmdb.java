package com.jas.shieldhome;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import static com.jas.shieldhome.Ui.SEP;
import static com.jas.shieldhome.Ui.STAR;
import static com.jas.shieldhome.Ui.httpGet;
import static com.jas.shieldhome.Ui.str;

/** Everything that talks to TMDB (film and series data for Discover and Search). */
final class Tmdb {

    private Tmdb() {
    }

    static final String TMDB_API = "https://api.themoviedb.org/3";
    static final String TMDB_IMG = "https://image.tmdb.org/t/p/";

    /** A film or series. */
    static final class Item {
        int id;
        String type;
        String title;
        String poster;
        String backdrop;
        String overview;
        String date;
        double rating;
        String year;

        String key() {
            return type + ":" + id;
        }
    }

    /** A titled row of films and series on Discover. */
    static final class Row {
        String title;
        final List<Item> items = new ArrayList<>();
    }

    static boolean hasToken() {
        return BuildConfig.TMDB_TOKEN != null && !BuildConfig.TMDB_TOKEN.isEmpty();
    }

    static String tmdbGet(String path) throws Exception {
        return httpGet(TMDB_API + path, BuildConfig.TMDB_TOKEN);
    }

    static Item parseItem(JSONObject j, String forcedType) {
        String type = forcedType != null ? forcedType : str(j, "media_type");
        if (!type.equals("movie") && !type.equals("tv")) return null;
        String poster = str(j, "poster_path");
        if (poster.isEmpty()) return null;
        Item it = new Item();
        it.id = j.optInt("id", 0);
        if (it.id == 0) return null;
        it.type = type;
        it.title = type.equals("movie") ? str(j, "title") : str(j, "name");
        it.poster = poster;
        it.backdrop = str(j, "backdrop_path");
        it.overview = str(j, "overview");
        it.rating = j.optDouble("vote_average", 0);
        it.date = type.equals("movie") ? str(j, "release_date") : str(j, "first_air_date");
        it.year = it.date.length() >= 4 ? it.date.substring(0, 4) : "";
        return it;
    }

    static String dateOffset(int months) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, months);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    /** Genre for the "Tonight's mood" row, which changes each day. */
    static String[] moodForToday() {
        switch (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY: return new String[]{"35", "Comedy"};
            case Calendar.TUESDAY: return new String[]{"53", "Thriller"};
            case Calendar.WEDNESDAY: return new String[]{"878", "Sci-fi"};
            case Calendar.THURSDAY: return new String[]{"80", "Crime"};
            case Calendar.FRIDAY: return new String[]{"28", "Action"};
            case Calendar.SATURDAY: return new String[]{"10751", "Family"};
            default: return new String[]{"18", "Drama"};
        }
    }

    /** Picks a YouTube trailer (or teaser) from TMDB's list of videos. */
    static String pickTrailer(JSONArray va) {
        if (va == null) return null;
        try {
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < va.length(); i++) {
                    JSONObject v = va.getJSONObject(i);
                    if (!"YouTube".equals(str(v, "site"))) continue;
                    String vt = str(v, "type");
                    boolean good = pass == 0 ? "Trailer".equals(vt) : ("Teaser".equals(vt) || "Trailer".equals(vt));
                    if (good) {
                        String k = str(v, "key");
                        if (!k.isEmpty()) return k;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** UK age rating, falling back to the US one. */
    static String ageRating(JSONObject d, String type) {
        try {
            String us = "";
            if (type.equals("movie")) {
                JSONObject rd = d.optJSONObject("release_dates");
                JSONArray arr = rd != null ? rd.optJSONArray("results") : null;
                if (arr == null) return "";
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.getJSONObject(i);
                    String country = str(c, "iso_3166_1");
                    JSONArray dates = c.optJSONArray("release_dates");
                    if (dates == null) continue;
                    for (int k = 0; k < dates.length(); k++) {
                        String cert = str(dates.getJSONObject(k), "certification");
                        if (cert.isEmpty()) continue;
                        if ("GB".equals(country)) return cert;
                        if ("US".equals(country) && us.isEmpty()) us = cert;
                    }
                }
            } else {
                JSONObject cr = d.optJSONObject("content_ratings");
                JSONArray arr = cr != null ? cr.optJSONArray("results") : null;
                if (arr == null) return "";
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.getJSONObject(i);
                    String country = str(c, "iso_3166_1");
                    String rating = str(c, "rating");
                    if (rating.isEmpty()) continue;
                    if ("GB".equals(country)) return rating;
                    if ("US".equals(country) && us.isEmpty()) us = rating;
                }
            }
            return us;
        } catch (Exception e) {
            return "";
        }
    }

    /** "★ 7.8 · 2024 · Film" */
    static String meta(Item it) {
        List<String> parts = new ArrayList<>();
        if (it.rating > 0) parts.add(STAR + " " + String.format(Locale.UK, "%.1f", it.rating));
        if (!it.year.isEmpty()) parts.add(it.year);
        parts.add(it.type.equals("movie") ? "Film" : "Series");
        return TextUtils.join(SEP, parts);
    }
}
