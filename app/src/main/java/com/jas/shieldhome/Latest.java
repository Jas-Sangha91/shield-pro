package com.jas.shieldhome;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Where the Home wallpaper and the screensaver get their artwork: recent films and series only.
 * Each entry is {TMDB request, type}. The dates are worked out fresh every time the artwork reloads.
 */
final class Latest {

    // How far back counts as "latest"
    private static final int FILMS_DAYS = 180;          // films released in the last 6 months
    private static final int SERIES_DAYS = 60;          // series with an episode in the last 2 months
    private static final int HINDI_FILMS_DAYS = 365;
    private static final int HINDI_SERIES_DAYS = 120;
    private static final int PUNJABI_FILMS_DAYS = 540;

    // Leaves out talk shows, news, reality and soaps
    private static final String NOT_THESE = "&without_genres=10767,10763,10764,10766";

    private Latest() {
    }

    static String[][] sources() {
        String today = daysAgo(0);
        String films = "/discover/movie?language=en-US&sort_by=popularity.desc&vote_count.gte=50"
                + "&primary_release_date.gte=" + daysAgo(FILMS_DAYS)
                + "&primary_release_date.lte=" + today;
        String series = "/discover/tv?language=en-US&sort_by=popularity.desc&vote_count.gte=50" + NOT_THESE
                + "&air_date.gte=" + daysAgo(SERIES_DAYS)
                + "&air_date.lte=" + today;
        return new String[][]{
                {films + "&page=1", "movie"},
                {films + "&page=2", "movie"},
                {series + "&page=1", "tv"},
                {series + "&page=2", "tv"},
                {"/discover/movie?language=en-US&sort_by=popularity.desc&with_original_language=hi&vote_count.gte=5"
                        + "&primary_release_date.gte=" + daysAgo(HINDI_FILMS_DAYS)
                        + "&primary_release_date.lte=" + today, "movie"},
                {"/discover/tv?language=en-US&sort_by=popularity.desc&with_original_language=hi&vote_count.gte=3"
                        + NOT_THESE
                        + "&air_date.gte=" + daysAgo(HINDI_SERIES_DAYS)
                        + "&air_date.lte=" + today, "tv"},
                {"/discover/movie?language=en-US&sort_by=popularity.desc&with_original_language=pa&vote_count.gte=1"
                        + "&primary_release_date.gte=" + daysAgo(PUNJABI_FILMS_DAYS)
                        + "&primary_release_date.lte=" + today, "movie"}};
    }

    private static String daysAgo(int days) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -days);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }
}
