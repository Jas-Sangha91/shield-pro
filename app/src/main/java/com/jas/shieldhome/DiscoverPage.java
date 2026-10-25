package com.jas.shieldhome;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jas.shieldhome.Tmdb.Item;
import com.jas.shieldhome.Tmdb.Row;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.jas.shieldhome.Tmdb.TMDB_IMG;
import static com.jas.shieldhome.Tmdb.ageRating;
import static com.jas.shieldhome.Tmdb.dateOffset;
import static com.jas.shieldhome.Tmdb.hasToken;
import static com.jas.shieldhome.Tmdb.meta;
import static com.jas.shieldhome.Tmdb.moodForToday;
import static com.jas.shieldhome.Tmdb.parseItem;
import static com.jas.shieldhome.Tmdb.pickTrailer;
import static com.jas.shieldhome.Tmdb.tmdbGet;
import static com.jas.shieldhome.Ui.MATCH;
import static com.jas.shieldhome.Ui.NEUTRAL_GLOW;
import static com.jas.shieldhome.Ui.POSTER_H;
import static com.jas.shieldhome.Ui.POSTER_W;
import static com.jas.shieldhome.Ui.SEP;
import static com.jas.shieldhome.Ui.SOFT_TEXT;
import static com.jas.shieldhome.Ui.SPOT_BG;
import static com.jas.shieldhome.Ui.WRAP;
import static com.jas.shieldhome.Ui.dominantColor;
import static com.jas.shieldhome.Ui.str;

/** The Discover page: Tonight's pick, Browse by genre, My list, the rows, and the info card. */
final class DiscoverPage {

    private static final String TAG = "SanghaHome";
    private static final long REFRESH_MS = 3 * 60 * 60 * 1000L;
    private static final long SPOT_EVERY_MS = 8000L;
    private static final int ROW_MAX = 12;
    private static final int SPOT_COUNT = 5;
    private static final String YOUTUBE_APP = "com.google.android.youtube.tv";

    private interface RowMaker {
        Row make() throws Exception;
    }

    private final MainActivity a;
    private final BrowsePage browse;

    // Data
    private List<Row> rows = null;
    private List<Item> spotItems = new ArrayList<>();
    private long loadedAt = 0;
    private long triedAt = 0;
    private boolean loading = false;
    private boolean error = false;
    private String lastError = "";
    private final Map<String, String> trailerCache = new HashMap<>();

    // Tonight's pick
    private ImageView spotBackdrop;
    private TextView spotTitle, spotMeta, spotDesc, spotTrailer, spotList, spotSurprise;
    private final List<View> spotDots = new ArrayList<>();
    private int spotIndex = 0;

    // My list row
    private LinearLayout myListSection;
    private final List<View> myListTiles = new ArrayList<>();

    private final Runnable spotTick = new Runnable() {
        @Override
        public void run() {
            if (a.page == MainActivity.PAGE_DISCOVER && spotTitle != null && !spotlightFocused() && a.hasWindowFocus()) {
                showSpot(spotIndex + 1, true);
            }
            a.handler.postDelayed(this, SPOT_EVERY_MS);
        }
    };

    DiscoverPage(MainActivity a) {
        this.a = a;
        this.browse = new BrowsePage(a, this);
    }

    private int dp(int v) {
        return a.dp(v);
    }

    private void style(TextView tv, int sp, boolean bold, int color) {
        a.style(tv, sp, bold, color);
    }

    private void loadImage(String url, ImageView into) {
        a.images.load(url, into);
    }

    // ---------- Page lifecycle ----------

    /** Called before the page is redrawn, so old views aren't updated by mistake. */
    void clearViews() {
        stopTicker();
        spotTitle = null;
        spotBackdrop = null;
        spotDots.clear();
        myListSection = null;
        myListTiles.clear();
    }

    void startTicker() {
        a.handler.removeCallbacks(spotTick);
        if (spotTitle != null) a.handler.postDelayed(spotTick, SPOT_EVERY_MS);
    }

    void stopTicker() {
        a.handler.removeCallbacks(spotTick);
    }

    void render() {
        if (!hasToken()) {
            message("Discover needs a TMDB key", "Add the TMDB_TOKEN secret on GitHub, then build again.");
            return;
        }

        long now = System.currentTimeMillis();
        boolean stale = rows == null || now - loadedAt > REFRESH_MS;
        if (stale && !loading && now - triedAt > 30 * 1000L) load();

        if (rows == null) {
            if (error && !loading) {
                message("Couldn't load Discover",
                        "Check the internet connection, then open Discover again in a moment."
                                + (lastError.isEmpty() ? "" : "\n\nDetails: " + lastError));
            } else {
                message("Finding something good to watch\u2026", "");
            }
            return;
        }

        if (!spotItems.isEmpty()) buildSpotlight();

        buildGenreRow();

        myListSection = new LinearLayout(a);
        myListSection.setOrientation(LinearLayout.VERTICAL);
        myListSection.setClipChildren(false);
        myListSection.setClipToPadding(false);
        a.content.addView(myListSection, new LinearLayout.LayoutParams(MATCH, WRAP));
        refreshMyListRow();

        for (Row r : rows) {
            if (r.items.isEmpty()) continue;
            a.addHeader(r.title);
            posterRow(a.content, r.items, null);
        }

        TextView credit = new TextView(a);
        style(credit, 12, false, 0x80FFFFFF);
        credit.setText("This product uses the TMDB API but is not endorsed or certified by TMDB. Streaming data from JustWatch.");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(WRAP, WRAP);
        clp.topMargin = dp(28);
        clp.leftMargin = dp(12);
        a.content.addView(credit, clp);
    }

    private void message(String head, String body) {
        TextView t = new TextView(a);
        style(t, 26, true, 0xFFFFFFFF);
        t.setText(head);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        tlp.topMargin = dp(40);
        tlp.leftMargin = dp(12);
        a.content.addView(t, tlp);
        if (!body.isEmpty()) {
            TextView b = new TextView(a);
            style(b, 16, false, SOFT_TEXT);
            b.setText(body);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(WRAP, WRAP);
            blp.topMargin = dp(10);
            blp.leftMargin = dp(12);
            a.content.addView(b, blp);
        }
    }

    // ---------- Browse by genre ----------

    private void buildGenreRow() {
        a.addHeader("Browse by genre");
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(4), dp(12), dp(4));
        row.setClipChildren(false);
        row.setClipToPadding(false);
        for (int i = 0; i < BrowsePage.GENRES.length; i++) {
            final int gi = i;
            TextView p = a.pillButton(BrowsePage.GENRES[i].name, () -> browse.open(gi), null);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.rightMargin = dp(10);
            row.addView(p, lp);
        }
        TextView all = a.pillButton("All filters\u2026", () -> browse.open(BrowsePage.KEEP), null);
        row.addView(all, new LinearLayout.LayoutParams(WRAP, WRAP));

        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.addView(row, new FrameLayout.LayoutParams(WRAP, WRAP));
        a.content.addView(hs, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    // ---------- Loading ----------

    private void load() {
        loading = true;
        error = false;
        triedAt = System.currentTimeMillis();
        new Thread(() -> {
            final List<Item> picks = new ArrayList<>();
            final List<Row> found = new ArrayList<>();
            final Set<String> seen = new HashSet<>();
            final List<String> errors = new ArrayList<>();

            // Trending (and Tonight's pick), one page at a time so one bad page doesn't stop the rest
            Row trendRow = new Row();
            trendRow.title = "Trending this week";
            for (int pageNo = 1; pageNo <= 2; pageNo++) {
                try {
                    JSONArray trending = new JSONObject(tmdbGet("/trending/all/week?language=en-US&page=" + pageNo))
                            .getJSONArray("results");
                    for (int i = 0; i < trending.length(); i++) {
                        JSONObject raw = trending.getJSONObject(i);
                        if (!Languages.keep(raw)) continue;
                        Item it = parseItem(raw, null);
                        if (it == null || seen.contains(it.key())) continue;
                        Languages.remember(it, raw);
                        if (picks.size() < SPOT_COUNT && !it.backdrop.isEmpty() && !it.overview.isEmpty()) {
                            picks.add(it);
                            seen.add(it.key());
                        } else if (trendRow.items.size() < ROW_MAX) {
                            trendRow.items.add(it);
                            seen.add(it.key());
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Discover: trending page " + pageNo + " failed", e);
                    errors.add(describe(e));
                }
            }
            found.add(trendRow);

            tryRow(found, errors, () -> hindiPunjabiRow(seen));

            tryRow(found, errors, () -> fetchRow("New and highly rated",
                    "/discover/movie?language=en-US&sort_by=popularity.desc&vote_average.gte=7&vote_count.gte=300"
                            + "&primary_release_date.gte=" + dateOffset(-15) + "&primary_release_date.lte=" + dateOffset(0),
                    "movie", seen, null));

            tryRow(found, errors, () -> fetchRow("Binge-worthy series",
                    "/discover/tv?language=en-US&sort_by=popularity.desc&vote_average.gte=8&vote_count.gte=500",
                    "tv", seen, null));

            final String[] mood = moodForToday();
            tryRow(found, errors, () -> fetchRow("Tonight's mood: " + mood[1],
                    "/discover/movie?language=en-US&sort_by=popularity.desc&vote_average.gte=7&vote_count.gte=500"
                            + "&with_genres=" + mood[0],
                    "movie", seen, null));

            tryRow(found, errors, () -> fetchRow("Coming soon", "/movie/upcoming?language=en-US",
                    "movie", seen, dateOffset(0)));

            // Only call it a failure if nothing at all loaded
            boolean anything = !picks.isEmpty();
            for (Row r : found) if (!r.items.isEmpty()) anything = true;
            final boolean success = anything;
            final String firstError = errors.isEmpty() ? "" : errors.get(0);

            if (success) {
                for (Item it : picks) a.images.prefetch(TMDB_IMG + "w780" + it.backdrop);
            }
            a.runOnUiThread(() -> {
                loading = false;
                if (success) {
                    spotItems = picks;
                    rows = found;
                    loadedAt = System.currentTimeMillis();
                    spotIndex = 0;
                    lastError = "";
                } else {
                    error = true;
                    lastError = firstError;
                }
                if (a.page == MainActivity.PAGE_DISCOVER) a.render(null, false);
            });
        }).start();
    }

    /** Loads one row; if it fails, logs why and carries on with the others. */
    private void tryRow(List<Row> found, List<String> errors, RowMaker maker) {
        try {
            Row r = maker.make();
            if (r != null) found.add(r);
        } catch (Exception e) {
            Log.w(TAG, "Discover: a row failed", e);
            errors.add(describe(e));
        }
    }

    private static String describe(Exception e) {
        String msg = e.getMessage();
        String s = e.getClass().getSimpleName() + (msg == null || msg.isEmpty() ? "" : ": " + msg);
        return s.length() > 160 ? s.substring(0, 160) + "\u2026" : s;
    }

    /** Fetches up to two pages (only if a second exists), keeping your languages or big hits. */
    private Row fetchRow(String title, String path, String type, Set<String> seen, String minDate) throws Exception {
        Row row = new Row();
        row.title = title;
        int totalPages = 1;
        for (int pageNo = 1; pageNo <= 2 && pageNo <= totalPages && row.items.size() < ROW_MAX; pageNo++) {
            JSONObject o = new JSONObject(tmdbGet(path + "&page=" + pageNo));
            totalPages = o.optInt("total_pages", 1);
            JSONArray res = o.optJSONArray("results");
            if (res == null) break;
            for (int i = 0; i < res.length() && row.items.size() < ROW_MAX; i++) {
                JSONObject raw = res.getJSONObject(i);
                if (!Languages.keep(raw)) continue;
                Item it = parseItem(raw, type);
                if (it == null || seen.contains(it.key())) continue;
                if (minDate != null && (it.date.isEmpty() || it.date.compareTo(minDate) < 0)) continue;
                Languages.remember(it, raw);
                row.items.add(it);
                seen.add(it.key());
            }
        }
        return row;
    }

    /** Popular Hindi and Punjabi films, mixed two Hindi to one Punjabi. */
    private Row hindiPunjabiRow(Set<String> seen) throws Exception {
        String today = dateOffset(0);
        List<Item> hi = fetchList("/discover/movie?language=en-US&sort_by=popularity.desc&with_original_language=hi"
                + "&vote_count.gte=20&primary_release_date.lte=" + today, "movie", seen);
        List<Item> pa = new ArrayList<>();
        try {
            pa = fetchList("/discover/movie?language=en-US&sort_by=popularity.desc&with_original_language=pa"
                    + "&vote_count.gte=3&primary_release_date.lte=" + today, "movie", seen);
        } catch (Exception e) {
            Log.w(TAG, "Discover: Punjabi films failed", e);
        }
        Row row = new Row();
        row.title = "Hindi and Punjabi";
        int h = 0, p = 0;
        while (row.items.size() < ROW_MAX && (h < hi.size() || p < pa.size())) {
            for (int k = 0; k < 2 && h < hi.size() && row.items.size() < ROW_MAX; k++) addUnseen(row, hi.get(h++), seen);
            if (p < pa.size() && row.items.size() < ROW_MAX) addUnseen(row, pa.get(p++), seen);
        }
        return row;
    }

    private void addUnseen(Row row, Item it, Set<String> seen) {
        if (seen.add(it.key())) row.items.add(it);
    }

    private List<Item> fetchList(String path, String type, Set<String> seen) throws Exception {
        List<Item> out = new ArrayList<>();
        JSONArray res = new JSONObject(tmdbGet(path)).getJSONArray("results");
        for (int i = 0; i < res.length(); i++) {
            JSONObject raw = res.getJSONObject(i);
            Item it = parseItem(raw, type);
            if (it == null || it.poster.isEmpty() || seen.contains(it.key())) continue;
            Languages.remember(it, raw);
            out.add(it);
        }
        return out;
    }

    // ---------- Tonight's pick ----------

    private void buildSpotlight() {
        FrameLayout box = new FrameLayout(a);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(SPOT_BG);
        box.setBackground(bg);
        box.setClipToOutline(true);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(MATCH, dp(240));
        blp.setMargins(dp(12), dp(6), dp(12), dp(6));
        a.content.addView(box, blp);

        spotBackdrop = new ImageView(a);
        spotBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        box.addView(spotBackdrop, new FrameLayout.LayoutParams(dp(560), MATCH, Gravity.END));

        View shadeLeft = new View(a);
        shadeLeft.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{SPOT_BG, 0xB30E1118, 0x000E1118}));
        box.addView(shadeLeft, new FrameLayout.LayoutParams(dp(560), MATCH, Gravity.END));

        View shadeBottom = new View(a);
        shadeBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x000E1118, SPOT_BG}));
        box.addView(shadeBottom, new FrameLayout.LayoutParams(MATCH, dp(70), Gravity.BOTTOM));

        LinearLayout text = new LinearLayout(a);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(26), dp(20), dp(26), dp(20));
        text.setClipChildren(false);
        text.setClipToPadding(false);

        TextView label = new TextView(a);
        style(label, 13, true, 0xFFC9A8F0);
        label.setText("Tonight's pick");
        text.addView(label, new LinearLayout.LayoutParams(WRAP, WRAP));

        spotTitle = new TextView(a);
        style(spotTitle, 30, true, 0xFFFFFFFF);
        spotTitle.setSingleLine(true);
        spotTitle.setEllipsize(TextUtils.TruncateAt.END);
        spotTitle.setPadding(0, dp(2), 0, 0);
        text.addView(spotTitle, new LinearLayout.LayoutParams(dp(470), WRAP));

        spotMeta = new TextView(a);
        style(spotMeta, 15, false, SOFT_TEXT);
        spotMeta.setSingleLine(true);
        spotMeta.setPadding(0, dp(2), 0, 0);
        text.addView(spotMeta, new LinearLayout.LayoutParams(dp(470), WRAP));

        spotDesc = new TextView(a);
        style(spotDesc, 15, false, 0xFFE6E8EE);
        spotDesc.setMaxLines(2);
        spotDesc.setEllipsize(TextUtils.TruncateAt.END);
        spotDesc.setLineSpacing(0, 1.15f);
        spotDesc.setPadding(0, dp(8), 0, 0);
        text.addView(spotDesc, new LinearLayout.LayoutParams(dp(430), WRAP));

        text.addView(new View(a), new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        spotTrailer = a.pillButton("\u25B6  Trailer", () -> playTrailerFor(currentSpot()), this::onSpotFocus);
        spotList = a.pillButton("+  My list", () -> {
            toggleMyList(currentSpot());
            updateSpotListButton();
        }, this::onSpotFocus);
        spotSurprise = a.pillButton("Surprise me", this::surpriseMe, this::onSpotFocus);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(WRAP, WRAP);
        bl.rightMargin = dp(10);
        buttons.addView(spotTrailer, bl);
        LinearLayout.LayoutParams bl2 = new LinearLayout.LayoutParams(WRAP, WRAP);
        bl2.rightMargin = dp(10);
        buttons.addView(spotList, bl2);
        buttons.addView(spotSurprise, new LinearLayout.LayoutParams(WRAP, WRAP));
        text.addView(buttons, new LinearLayout.LayoutParams(WRAP, WRAP));

        box.addView(text, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout dots = new LinearLayout(a);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        spotDots.clear();
        for (int i = 0; i < spotItems.size(); i++) {
            View dv = new View(a);
            GradientDrawable dbg = new GradientDrawable();
            dbg.setCornerRadius(dp(2));
            dbg.setColor(0x59FFFFFF);
            dv.setBackground(dbg);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(6), dp(4));
            dlp.leftMargin = dp(5);
            dots.addView(dv, dlp);
            spotDots.add(dv);
        }
        FrameLayout.LayoutParams dotsLp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.END);
        dotsLp.setMargins(0, 0, dp(24), dp(22));
        box.addView(dots, dotsLp);

        showSpot(spotIndex, false);
        startTicker();
    }

    private Item currentSpot() {
        if (spotItems.isEmpty()) return null;
        return spotItems.get(Math.max(0, Math.min(spotIndex, spotItems.size() - 1)));
    }

    private boolean spotlightFocused() {
        return (spotTrailer != null && spotTrailer.isFocused())
                || (spotList != null && spotList.isFocused())
                || (spotSurprise != null && spotSurprise.isFocused());
    }

    private void showSpot(int i, boolean animate) {
        if (spotItems.isEmpty() || spotTitle == null) return;
        int n = spotItems.size();
        spotIndex = ((i % n) + n) % n;
        final Item it = spotItems.get(spotIndex);
        spotTitle.setText(it.title);
        String lang = Languages.foreignName(it);
        spotMeta.setText(meta(it) + (lang == null ? "" : SEP + lang));
        spotDesc.setText(it.overview);
        updateSpotListButton();

        for (int k = 0; k < spotDots.size(); k++) {
            View dv = spotDots.get(k);
            boolean active = k == spotIndex;
            ((GradientDrawable) dv.getBackground()).setColor(active ? 0xFFFFFFFF : 0x59FFFFFF);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dv.getLayoutParams();
            lp.width = dp(active ? 18 : 6);
            dv.setLayoutParams(lp);
        }

        final String url = TMDB_IMG + "w780" + it.backdrop;
        final ImageView target = spotBackdrop;
        if (animate && target != null) {
            target.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                loadImage(url, target);
                target.animate().alpha(1f).setDuration(450).start();
            }).start();
        } else if (target != null) {
            loadImage(url, target);
        }
        if (spotlightFocused()) onSpotFocus();
    }

    private void onSpotFocus() {
        Item it = currentSpot();
        if (it == null) return;
        a.titleView.setText(it.title);
        a.metaView.setText(meta(it));
        Drawable dr = spotBackdrop != null ? spotBackdrop.getDrawable() : null;
        a.setGlow(dr != null ? dominantColor(dr, NEUTRAL_GLOW) : NEUTRAL_GLOW);
    }

    private void updateSpotListButton() {
        Item it = currentSpot();
        if (spotList == null || it == null) return;
        spotList.setText(isInMyList(it) ? "\u2713  In My list" : "+  My list");
    }

    private void surpriseMe() {
        Toast.makeText(a, "Finding something good\u2026", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Item pick = null;
            try {
                Random r = new Random();
                String type = r.nextBoolean() ? "movie" : "tv";
                String base = "/discover/" + type + "?language=en-US&sort_by=popularity.desc"
                        + "&vote_average.gte=7.5&vote_count.gte=1000&page=";
                JSONArray res = new JSONObject(tmdbGet(base + (1 + r.nextInt(10)))).getJSONArray("results");
                if (res.length() == 0) res = new JSONObject(tmdbGet(base + 1)).getJSONArray("results");
                List<Item> options = new ArrayList<>();
                for (int i = 0; i < res.length(); i++) {
                    JSONObject raw = res.getJSONObject(i);
                    if (!Languages.keep(raw)) continue;
                    Item it = parseItem(raw, type);
                    if (it == null) continue;
                    Languages.remember(it, raw);
                    options.add(it);
                }
                if (!options.isEmpty()) pick = options.get(r.nextInt(options.size()));
            } catch (Exception e) {
                Log.w(TAG, "Surprise me failed", e);
            }
            final Item chosen = pick;
            a.runOnUiThread(() -> {
                if (chosen != null) openInfo(chosen);
                else Toast.makeText(a, "Couldn't find a pick right now. Try again in a moment.",
                        Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    // ---------- My list ----------

    private List<Item> loadMyList() {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(a.prefs.getString("mylist", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Item it = new Item();
                it.id = o.optInt("id", 0);
                it.type = str(o, "type");
                it.title = str(o, "title");
                it.poster = str(o, "poster");
                it.backdrop = str(o, "backdrop");
                it.overview = str(o, "overview");
                it.date = str(o, "date");
                it.rating = o.optDouble("rating", 0);
                it.year = str(o, "year");
                if (it.id != 0 && !it.poster.isEmpty()) out.add(it);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void saveMyList(List<Item> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Item it : list) {
                JSONObject o = new JSONObject();
                o.put("id", it.id);
                o.put("type", it.type);
                o.put("title", it.title);
                o.put("poster", it.poster);
                o.put("backdrop", it.backdrop);
                o.put("overview", it.overview);
                o.put("date", it.date);
                o.put("rating", it.rating);
                o.put("year", it.year);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        a.prefs.edit().putString("mylist", arr.toString()).apply();
    }

    private boolean isInMyList(Item it) {
        if (it == null) return false;
        for (Item m : loadMyList()) if (m.key().equals(it.key())) return true;
        return false;
    }

    private boolean toggleMyList(Item it) {
        if (it == null) return false;
        List<Item> list = loadMyList();
        boolean removed = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).key().equals(it.key())) {
                list.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) list.add(0, it);
        saveMyList(list);
        refreshMyListRow();
        Toast.makeText(a, removed ? "Removed from My list" : "Added to My list", Toast.LENGTH_SHORT).show();
        return !removed;
    }

    private void refreshMyListRow() {
        if (myListSection == null) return;
        for (View v : myListTiles) a.unregister(v);
        myListTiles.clear();
        myListSection.removeAllViews();
        List<Item> list = loadMyList();
        if (list.isEmpty()) return;
        a.addHeaderTo(myListSection, "My list");
        posterRow(myListSection, list, myListTiles);
    }

    // ---------- Posters ----------

    private void posterRow(LinearLayout parent, List<Item> items, List<View> track) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(4), dp(12), dp(4));
        row.setClipChildren(false);
        row.setClipToPadding(false);
        for (final Item it : items) {
            View p = a.register(buildPoster(it, POSTER_W, POSTER_H, true, () -> openInfo(it)));
            Languages.tag(a, p, it);
            if (track != null) track.add(p);
            row.addView(p, a.size(POSTER_W, POSTER_H, 8));
        }
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.addView(row, new FrameLayout.LayoutParams(WRAP, WRAP));
        parent.addView(hs, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    View buildPoster(final Item it, int w, int h, final boolean updateHeader, final Runnable onClick) {
        FrameLayout t = new FrameLayout(a);
        t.setFocusable(true);
        t.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(0xFF2B3140);
        t.setBackground(bg);
        t.setClipToOutline(true);

        final ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        t.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
        loadImage(TMDB_IMG + "w185" + it.poster, iv);

        final GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(10));
        ring.setStroke(dp(3), 0xFFFFFFFF);
        ring.setColor(0x00000000);

        t.setOnFocusChangeListener((v, f) -> {
            v.animate().scaleX(f ? 1.1f : 1f).scaleY(f ? 1.1f : 1f)
                    .translationZ(f ? dp(10) : 0).setDuration(150).start();
            ((FrameLayout) v).setForeground(f ? ring : null);
            if (f && updateHeader) {
                a.titleView.setText(it.title);
                a.metaView.setText(meta(it));
                Drawable dr = iv.getDrawable();
                a.setGlow(dr != null ? dominantColor(dr, NEUTRAL_GLOW) : NEUTRAL_GLOW);
                // The background follows the highlighted poster
                a.wallpaper.follow(it);
            }
        });
        t.setOnClickListener(v -> onClick.run());
        t.setOnLongClickListener(v -> {
            showPosterMenu(it);
            return true;
        });
        return t;
    }

    private void showPosterMenu(final Item it) {
        final boolean inList = isInMyList(it);
        String[] opts = {"More info", "\u25B6  Trailer",
                inList ? "Remove from My list" : "+  Add to My list", "Cancel"};
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(it.title)
                .setItems(opts, (d, which) -> {
                    if (which == 0) {
                        openInfo(it);
                    } else if (which == 1) {
                        playTrailerFor(it);
                    } else if (which == 2) {
                        toggleMyList(it);
                        updateSpotListButton();
                    }
                })
                .show();
    }

    // ---------- Info card ----------

    void openInfo(final Item it) {
        if (it == null) return;
        final Dialog dialog = new Dialog(a, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);

        FrameLayout root = new FrameLayout(a);
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setCornerRadius(dp(18));
        rootBg.setColor(0xFF0F131C);
        root.setBackground(rootBg);
        root.setClipToOutline(true);

        ImageView backdrop = new ImageView(a);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(backdrop, new FrameLayout.LayoutParams(MATCH, MATCH));
        if (!it.backdrop.isEmpty()) loadImage(TMDB_IMG + "w780" + it.backdrop, backdrop);

        View shade = new View(a);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF20B0E14, 0xD90B0E14, 0xA60B0E14}));
        root.addView(shade, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout outer = new LinearLayout(a);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(26), dp(24), dp(26), dp(16));
        outer.setClipChildren(false);
        outer.setClipToPadding(false);
        root.addView(outer, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setClipChildren(false);
        outer.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        ImageView poster = new ImageView(a);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setCornerRadius(dp(10));
        pbg.setColor(0xFF2B3140);
        poster.setBackground(pbg);
        poster.setClipToOutline(true);
        loadImage(TMDB_IMG + "w342" + it.poster, poster);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(140), dp(210));
        plp.rightMargin = dp(24);
        top.addView(poster, plp);

        LinearLayout info = new LinearLayout(a);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setClipChildren(false);
        info.setMinimumHeight(dp(210));
        top.addView(info, new LinearLayout.LayoutParams(0, WRAP, 1f));

        TextView title = new TextView(a);
        style(title, 26, true, 0xFFFFFFFF);
        title.setText(it.title);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));

        final TextView metaLine = new TextView(a);
        style(metaLine, 15, false, SOFT_TEXT);
        String knownLang = Languages.foreignName(it);
        metaLine.setText(meta(it) + (knownLang == null ? "" : SEP + knownLang));
        metaLine.setPadding(0, dp(4), 0, 0);
        info.addView(metaLine, new LinearLayout.LayoutParams(MATCH, WRAP));

        final TextView genres = new TextView(a);
        style(genres, 14, false, SOFT_TEXT);
        genres.setPadding(0, dp(2), 0, 0);
        info.addView(genres, new LinearLayout.LayoutParams(MATCH, WRAP));

        final TextView overview = new TextView(a);
        style(overview, 15, false, 0xFFFFFFFF);
        overview.setText(it.overview.isEmpty() ? "Loading details\u2026" : it.overview);
        overview.setMaxLines(3);
        overview.setEllipsize(TextUtils.TruncateAt.END);
        overview.setLineSpacing(0, 1.15f);
        overview.setPadding(0, dp(10), 0, 0);
        info.addView(overview, new LinearLayout.LayoutParams(MATCH, WRAP));

        final TextView cast = new TextView(a);
        style(cast, 13, false, SOFT_TEXT);
        cast.setPadding(0, dp(8), 0, 0);
        cast.setSingleLine(true);
        cast.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(cast, new LinearLayout.LayoutParams(MATCH, WRAP));

        info.addView(new View(a), new LinearLayout.LayoutParams(1, dp(12)));

        LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        final String[] trailerKey = new String[1];
        final TextView trailerBtn = a.pillButton("Finding trailer\u2026", () -> {
            if (trailerKey[0] != null) {
                dialog.dismiss();
                playTrailer(trailerKey[0]);
            }
        }, null);
        final TextView[] listBtnHolder = new TextView[1];
        listBtnHolder[0] = a.pillButton(isInMyList(it) ? "\u2713  In My list" : "+  My list", () -> {
            boolean nowIn = toggleMyList(it);
            listBtnHolder[0].setText(nowIn ? "\u2713  In My list" : "+  My list");
            updateSpotListButton();
        }, null);
        TextView closeBtn = a.pillButton("Close", dialog::dismiss, null);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(WRAP, WRAP);
        bl.rightMargin = dp(10);
        buttons.addView(trailerBtn, bl);
        LinearLayout.LayoutParams bl2 = new LinearLayout.LayoutParams(WRAP, WRAP);
        bl2.rightMargin = dp(10);
        buttons.addView(listBtnHolder[0], bl2);
        buttons.addView(closeBtn, new LinearLayout.LayoutParams(WRAP, WRAP));
        info.addView(buttons, new LinearLayout.LayoutParams(WRAP, WRAP));

        final WhereToWatch watch = new WhereToWatch(a, dialog);
        outer.addView(watch.view(), new LinearLayout.LayoutParams(MATCH, WRAP));

        final TextView moreHeader = new TextView(a);
        style(moreHeader, 15, true, 0xA0FFFFFF);
        moreHeader.setText("More like this");
        moreHeader.setVisibility(View.GONE);
        moreHeader.setPadding(0, dp(12), 0, dp(2));
        outer.addView(moreHeader, new LinearLayout.LayoutParams(WRAP, WRAP));

        final LinearLayout moreBand = new LinearLayout(a);
        moreBand.setOrientation(LinearLayout.HORIZONTAL);
        moreBand.setPadding(0, dp(4), dp(8), dp(4));
        moreBand.setClipChildren(false);
        moreBand.setClipToPadding(false);
        HorizontalScrollView moreScroll = new HorizontalScrollView(a);
        moreScroll.setHorizontalScrollBarEnabled(false);
        moreScroll.setClipChildren(false);
        moreScroll.setClipToPadding(false);
        moreScroll.addView(moreBand, new FrameLayout.LayoutParams(WRAP, WRAP));
        outer.addView(moreScroll, new LinearLayout.LayoutParams(MATCH, WRAP));

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
        }
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(dp(800), dp(510));
        trailerBtn.requestFocus();

        new Thread(() -> {
            try {
                String extra = it.type.equals("movie") ? "release_dates" : "content_ratings";
                JSONObject d = new JSONObject(tmdbGet("/" + it.type + "/" + it.id
                        + "?language=en-US&append_to_response=videos,credits,recommendations,watch/providers," + extra));

                List<String> g = new ArrayList<>();
                JSONArray ga = d.optJSONArray("genres");
                if (ga != null) for (int i = 0; i < ga.length() && i < 3; i++) g.add(str(ga.getJSONObject(i), "name"));

                String length = "";
                if (it.type.equals("movie")) {
                    int mins = d.optInt("runtime", 0);
                    if (mins > 0) length = (mins / 60) + "h " + (mins % 60) + "m";
                } else {
                    int seasons = d.optInt("number_of_seasons", 0);
                    if (seasons > 0) length = seasons + (seasons == 1 ? " season" : " seasons");
                }

                String ov = str(d, "overview");

                List<String> names = new ArrayList<>();
                JSONObject credits = d.optJSONObject("credits");
                JSONArray ca = credits != null ? credits.optJSONArray("cast") : null;
                if (ca != null) for (int i = 0; i < ca.length() && i < 3; i++) names.add(str(ca.getJSONObject(i), "name"));

                JSONObject videos = d.optJSONObject("videos");
                String key = pickTrailer(videos != null ? videos.optJSONArray("results") : null);

                String age = ageRating(d, it.type);
                String lang = Languages.foreignNameFor(str(d, "original_language"));

                final List<Item> recs = new ArrayList<>();
                JSONObject rec = d.optJSONObject("recommendations");
                JSONArray ra = rec != null ? rec.optJSONArray("results") : null;
                if (ra != null) {
                    for (int i = 0; i < ra.length() && recs.size() < 10; i++) {
                        JSONObject rj = ra.getJSONObject(i);
                        if (!Languages.keep(rj)) continue;
                        String rt = str(rj, "media_type");
                        Item ri = parseItem(rj, rt.isEmpty() ? it.type : rt);
                        if (ri == null) continue;
                        Languages.remember(ri, rj);
                        recs.add(ri);
                    }
                }

                final JSONObject providers = d.optJSONObject("watch/providers");
                final String genreText = TextUtils.join(", ", g)
                        + (length.isEmpty() ? "" : (g.isEmpty() ? "" : SEP) + length);
                final String overviewText = ov.isEmpty()
                        ? (it.overview.isEmpty() ? "No description available." : it.overview) : ov;
                final String castText = names.isEmpty() ? "" : "Starring " + TextUtils.join(", ", names);
                final String metaText = meta(it) + (age.isEmpty() ? "" : SEP + age) + (lang == null ? "" : SEP + lang);
                final String foundKey = key;
                if (key != null) trailerCache.put(it.key(), key);

                a.runOnUiThread(() -> {
                    metaLine.setText(metaText);
                    genres.setText(genreText);
                    overview.setText(overviewText);
                    cast.setText(castText);
                    trailerKey[0] = foundKey;
                    trailerBtn.setText(foundKey != null ? "\u25B6  Trailer" : "No trailer available");
                    watch.show(providers);
                    if (!recs.isEmpty()) {
                        moreHeader.setVisibility(View.VISIBLE);
                        for (final Item r : recs) {
                            View p = buildPoster(r, 56, 84, false, () -> {
                                dialog.dismiss();
                                openInfo(r);
                            });
                            moreBand.addView(p, a.size(56, 84, 6));
                        }
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "Info card failed", e);
                a.runOnUiThread(() -> {
                    overview.setText(it.overview.isEmpty()
                            ? "Couldn't load details. Check the internet connection." : it.overview);
                    trailerBtn.setText("No trailer available");
                    watch.failed();
                });
            }
        }).start();
    }

    // ---------- Trailers ----------

    private void playTrailerFor(final Item it) {
        if (it == null) return;
        String cached = trailerCache.get(it.key());
        if (cached != null) {
            playTrailer(cached);
            return;
        }
        new Thread(() -> {
            String key = null;
            try {
                JSONObject d = new JSONObject(tmdbGet("/" + it.type + "/" + it.id + "/videos?language=en-US"));
                key = pickTrailer(d.optJSONArray("results"));
            } catch (Exception ignored) {
            }
            final String k = key;
            a.runOnUiThread(() -> {
                if (k != null) {
                    trailerCache.put(it.key(), k);
                    playTrailer(k);
                } else {
                    Toast.makeText(a, "No trailer available for this one.", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void playTrailer(String key) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + key));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.setPackage(YOUTUBE_APP);
        try {
            a.startActivity(i);
        } catch (Exception e) {
            i.setPackage(null);
            a.startSafe(i);
        }
    }
}
