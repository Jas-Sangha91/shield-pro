package com.jas.shieldhome;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jas.shieldhome.Tmdb.Item;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.jas.shieldhome.Tmdb.TMDB_IMG;
import static com.jas.shieldhome.Tmdb.dateOffset;
import static com.jas.shieldhome.Tmdb.meta;
import static com.jas.shieldhome.Tmdb.parseItem;
import static com.jas.shieldhome.Tmdb.tmdbGet;
import static com.jas.shieldhome.Ui.MATCH;
import static com.jas.shieldhome.Ui.SEP;
import static com.jas.shieldhome.Ui.SOFT_TEXT;
import static com.jas.shieldhome.Ui.WRAP;
import static com.jas.shieldhome.Ui.str;

/**
 * Browse: every film and series on TMDB (worldwide, any service), filtered by genre, subgenre,
 * sort, decade and length. Also shows a person's films and series (opened from Search).
 * Follows the language rules in Languages. Remembers the last filters.
 */
final class BrowsePage {

    /** Pass to open() to keep the last genre. */
    static final int KEEP = -2;

    private static final int COLS = 7;
    private static final int PW = 108;
    private static final int PH = 162;
    private static final int BATCH = 21;
    private static final int PAGES_PER_BATCH = 4;
    private static final int GIVE_UP_PAGE = 20;
    private static final int PERSON_MAX = 120;
    private static final int BG = 0xFF0A0D14;

    static final class Genre {
        final String name;
        final int movie;        // TMDB film genre
        final int tv;           // TMDB series genre (0 = series have no such genre)
        final String tvKeyword; // used for series when tv == 0
        final String[][] subs;  // {label, TMDB keyword}

        Genre(String name, int movie, int tv, String tvKeyword, String[][] subs) {
            this.name = name;
            this.movie = movie;
            this.tv = tv;
            this.tvKeyword = tvKeyword;
            this.subs = subs;
        }
    }

    private static final String[][] HORROR_SUBS = {
            {"Slasher", "slasher"},
            {"Supernatural", "supernatural"},
            {"Found footage", "found footage"},
            {"Zombie", "zombie"},
            {"Ghost", "ghost"},
            {"Haunted house", "haunted house"},
            {"Vampire", "vampire"},
            {"Creature feature", "creature"},
            {"Body horror", "body horror"},
            {"Psychological", "psychological horror"}};

    private static final String[][] COMEDY_SUBS = {
            {"Romantic comedy", "romantic comedy"},
            {"Dark comedy", "dark comedy"},
            {"Spoof", "spoof"},
            {"Stand-up", "stand-up comedy"}};

    static final Genre[] GENRES = {
            new Genre("Horror", 27, 0, "horror", HORROR_SUBS),
            new Genre("Comedy", 35, 35, null, COMEDY_SUBS),
            new Genre("Action", 28, 10759, null, null),
            new Genre("Thriller", 53, 9648, null, null),
            new Genre("Sci-fi", 878, 10765, null, null),
            new Genre("Drama", 18, 18, null, null),
            new Genre("Crime", 80, 80, null, null),
            new Genre("Mystery", 9648, 9648, null, null),
            new Genre("Romance", 10749, 0, "romance", null),
            new Genre("Fantasy", 14, 10765, null, null),
            new Genre("Animation", 16, 16, null, null),
            new Genre("Family", 10751, 10751, null, null),
            new Genre("Documentary", 99, 99, null, null),
            new Genre("War", 10752, 10768, null, null),
            new Genre("Western", 37, 37, null, null)};

    private static final String[] TYPES = {"Films", "Series"};
    private static final String[] SORTS = {"Popular", "Top rated", "Newest", "Hidden gems"};
    private static final String[] DECADES = {"Any decade", "2020s", "2010s", "2000s", "1990s", "1980s", "Older"};
    private static final String[] LENGTHS = {"Any length", "Under 100 min", "About 2 hours", "Long, over 2h 20m"};

    private static final String[] PERSON_SHOW = {"Films and series", "Films", "Series"};
    private static final String[] PERSON_SORT = {"Best known", "Newest"};

    private interface Pick {
        void pick(int which);
    }

    private final MainActivity a;
    private final DiscoverPage discover;

    // Filters (remembered)
    private int type, genre, sub, sort, decade, length;

    // Person mode (opened from Search)
    private int personId = 0;
    private String personName = "";
    private String personDept = "";
    private int personShow = 0;
    private int personSort = 0;
    private List<Item> personAll = null;
    private final Map<String, Integer> personVotes = new HashMap<>();

    // Screen
    private Dialog dialog;
    private ImageView backdrop;
    private TextView infoTitle, infoMeta, status;
    private LinearLayout pillsRow, grid, rowView;
    private int inRow = 0, count = 0;

    // Loading
    private int generation = 0;
    private boolean loading = false, done = false;
    private int nextPage = 1;
    private final Set<String> seen = new HashSet<>();

    // Background that follows the highlighted poster
    private Item pendingBg = null;
    private String bgKey = null;
    private final Runnable bgRun = new Runnable() {
        @Override
        public void run() {
            final Item it = pendingBg;
            if (it == null || dialog == null || it.backdrop == null || it.backdrop.isEmpty()) return;
            if (it.key().equals(bgKey)) return;
            a.images.fetchInto(TMDB_IMG + "w1280" + it.backdrop, b -> {
                if (b == null || dialog == null || pendingBg != it) return;
                bgKey = it.key();
                backdrop.animate().cancel();
                backdrop.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                    backdrop.setImageBitmap(b);
                    backdrop.animate().alpha(1f).setDuration(500).start();
                }).start();
            });
        }
    };

    BrowsePage(MainActivity a, DiscoverPage discover) {
        this.a = a;
        this.discover = discover;
    }

    private int dp(int v) {
        return a.dp(v);
    }

    // ---------- Helpers for Search ----------

    static int genreIndex(String name) {
        for (int i = 0; i < GENRES.length; i++) if (GENRES[i].name.equals(name)) return i;
        return -1;
    }

    static int subIndex(int g, String label) {
        if (g < 0 || g >= GENRES.length || GENRES[g].subs == null || label == null) return -1;
        String[][] subs = GENRES[g].subs;
        for (int i = 0; i < subs.length; i++) if (subs[i][0].equals(label)) return i;
        return -1;
    }

    /** The last Films/Series choice (0 = films, 1 = series). */
    static int savedType(MainActivity a) {
        return clamp(a.prefs.getInt("browse_type", 0), TYPES.length);
    }

    // ---------- Opening ----------

    /** Opens Browse on a genre (index into GENRES), -1 for any genre, or KEEP for the last one. */
    void open(int genreIndex) {
        personId = 0;
        loadFilters();
        if (genreIndex != KEEP && genreIndex != genre) {
            genre = genreIndex;
            sub = -1;
            saveFilters();
        }
        start();
    }

    /** Opens Browse with a genre, subgenre (-1 = any) and type (0 films, 1 series, -1 = last used). */
    void openWith(int genreIndex, int subIndex, int typeIndex) {
        personId = 0;
        loadFilters();
        if (typeIndex == 0 || typeIndex == 1) type = typeIndex;
        if (genreIndex >= -1 && genreIndex < GENRES.length) genre = genreIndex;
        boolean subOk = genre >= 0 && GENRES[genre].subs != null && subIndex >= 0 && subIndex < GENRES[genre].subs.length;
        sub = subOk ? subIndex : -1;
        saveFilters();
        start();
    }

    /** Opens a person's films and series. dept is TMDB's "known for" department (Acting, Directing...). */
    void openPerson(int id, String name, String dept) {
        personId = id;
        personName = name == null ? "" : name;
        personDept = dept == null ? "" : dept;
        personShow = 0;
        personSort = 0;
        personAll = null;
        personVotes.clear();
        start();
    }

    private void start() {
        pendingBg = null;
        bgKey = null;
        build();
        reload();
    }

    private void loadFilters() {
        type = clamp(a.prefs.getInt("browse_type", 0), TYPES.length);
        genre = a.prefs.getInt("browse_genre", 0);
        if (genre < -1 || genre >= GENRES.length) genre = 0;
        sub = a.prefs.getInt("browse_sub", -1);
        if (genre < 0 || GENRES[genre].subs == null || sub >= GENRES[genre].subs.length) sub = -1;
        sort = clamp(a.prefs.getInt("browse_sort", 0), SORTS.length);
        decade = clamp(a.prefs.getInt("browse_decade", 0), DECADES.length);
        length = clamp(a.prefs.getInt("browse_len", 0), LENGTHS.length);
    }

    private void saveFilters() {
        a.prefs.edit()
                .putInt("browse_type", type)
                .putInt("browse_genre", genre)
                .putInt("browse_sub", sub)
                .putInt("browse_sort", sort)
                .putInt("browse_decade", decade)
                .putInt("browse_len", length)
                .apply();
    }

    private static int clamp(int v, int size) {
        return v < 0 || v >= size ? 0 : v;
    }

    // ---------- Screen ----------

    private void build() {
        dialog = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(BG);

        int artH = Math.round(a.getResources().getDisplayMetrics().heightPixels * 0.62f);
        FrameLayout art = new FrameLayout(a);
        root.addView(art, new FrameLayout.LayoutParams(MATCH, artH, Gravity.TOP));

        backdrop = new ImageView(a);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0f);
        art.addView(backdrop, new FrameLayout.LayoutParams(MATCH, MATCH));

        View dim = new View(a);
        dim.setBackgroundColor(0x4D0A0D14);
        art.addView(dim, new FrameLayout.LayoutParams(MATCH, MATCH));

        View left = new View(a);
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF20A0D14, 0xB30A0D14, 0x330A0D14, 0x000A0D14}));
        art.addView(left, new FrameLayout.LayoutParams(MATCH, MATCH));

        View bottom = new View(a);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x000A0D14, 0xB30A0D14, BG}));
        art.addView(bottom, new FrameLayout.LayoutParams(MATCH, Math.round(artH * 0.6f), Gravity.BOTTOM));

        LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        root.addView(page, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(40), dp(26), dp(40), dp(4));
        header.setClipChildren(false);
        header.setClipToPadding(false);

        TextView label = new TextView(a);
        a.style(label, 13, true, 0xFFC9A8F0);
        label.setText(personId != 0 ? "People" : "Browse");
        header.addView(label, new LinearLayout.LayoutParams(WRAP, WRAP));

        infoTitle = new TextView(a);
        a.style(infoTitle, 26, true, 0xFFFFFFFF);
        infoTitle.setSingleLine(true);
        infoTitle.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(infoTitle, new LinearLayout.LayoutParams(dp(620), WRAP));

        infoMeta = new TextView(a);
        a.style(infoMeta, 15, false, SOFT_TEXT);
        infoMeta.setSingleLine(true);
        infoMeta.setEllipsize(TextUtils.TruncateAt.END);
        infoMeta.setPadding(0, dp(2), 0, 0);
        header.addView(infoMeta, new LinearLayout.LayoutParams(dp(620), WRAP));

        pillsRow = new LinearLayout(a);
        pillsRow.setOrientation(LinearLayout.HORIZONTAL);
        pillsRow.setClipChildren(false);
        pillsRow.setPadding(0, dp(4), dp(8), dp(4));
        HorizontalScrollView pillsScroll = new HorizontalScrollView(a);
        pillsScroll.setHorizontalScrollBarEnabled(false);
        pillsScroll.setClipChildren(false);
        pillsScroll.setClipToPadding(false);
        pillsScroll.addView(pillsRow, new FrameLayout.LayoutParams(WRAP, WRAP));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(MATCH, WRAP);
        plp.topMargin = dp(12);
        header.addView(pillsScroll, plp);

        page.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));

        ScrollView scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(4), 0, dp(30));

        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(33), 0, dp(33), 0);
        body.setClipChildren(false);
        body.setClipToPadding(false);

        status = new TextView(a);
        a.style(status, 17, false, SOFT_TEXT);
        status.setPadding(dp(7), dp(16), 0, 0);
        body.addView(status, new LinearLayout.LayoutParams(WRAP, WRAP));

        grid = new LinearLayout(a);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        body.addView(grid, new LinearLayout.LayoutParams(MATCH, WRAP));

        scroll.addView(body, new FrameLayout.LayoutParams(MATCH, WRAP));
        page.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> {
            generation++;
            a.handler.removeCallbacks(bgRun);
            dialog = null;
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
        buildPills(null);
    }

    // ---------- Filter buttons ----------

    private void buildPills(String focusKey) {
        pillsRow.removeAllViews();

        if (personId != 0) {
            addPill("pshow", PERSON_SHOW[personShow], () -> choose("Show", PERSON_SHOW, personShow, w -> {
                personShow = w;
                personChanged("pshow");
            }));
            addPill("psort", PERSON_SORT[personSort], () -> choose("Sort", PERSON_SORT, personSort, w -> {
                personSort = w;
                personChanged("psort");
            }));
        } else {
            addPill("type", TYPES[type], () -> choose("Films or series", TYPES, type, w -> {
                type = w;
                changed("type");
            }));

            addPill("genre", genre < 0 ? "Any genre" : GENRES[genre].name,
                    () -> choose("Genre", genreOptions(), genre + 1, w -> {
                        genre = w - 1;
                        sub = -1;
                        changed("genre");
                    }));

            final Genre g = genre >= 0 ? GENRES[genre] : null;
            if (g != null && g.subs != null) {
                addPill("sub", sub < 0 ? "Any subgenre" : g.subs[sub][0],
                        () -> choose(g.name + " subgenre", subOptions(g), sub + 1, w -> {
                            sub = w - 1;
                            changed("sub");
                        }));
            }

            addPill("sort", SORTS[sort], () -> choose("Sort", SORTS, sort, w -> {
                sort = w;
                changed("sort");
            }));

            addPill("decade", DECADES[decade], () -> choose("Decade", DECADES, decade, w -> {
                decade = w;
                changed("decade");
            }));

            if (type == 0) {
                addPill("length", LENGTHS[length], () -> choose("Length", LENGTHS, length, w -> {
                    length = w;
                    changed("length");
                }));
            }
        }

        View focus = pillsRow.getChildCount() > 0 ? pillsRow.getChildAt(0) : null;
        if (focusKey != null) {
            for (int i = 0; i < pillsRow.getChildCount(); i++) {
                View c = pillsRow.getChildAt(i);
                if (focusKey.equals(c.getTag())) focus = c;
            }
        }
        if (focus != null) focus.requestFocus();
    }

    private void addPill(String key, String text, Runnable onClick) {
        TextView p = a.pillButton(text + "  \u25BE", onClick, null);
        p.setTag(key);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.rightMargin = dp(10);
        pillsRow.addView(p, lp);
    }

    private String[] genreOptions() {
        String[] out = new String[GENRES.length + 1];
        out[0] = "Any genre";
        for (int i = 0; i < GENRES.length; i++) out[i + 1] = GENRES[i].name;
        return out;
    }

    private String[] subOptions(Genre g) {
        String[] out = new String[g.subs.length + 1];
        out[0] = "Any subgenre";
        for (int i = 0; i < g.subs.length; i++) out[i + 1] = g.subs[i][0];
        return out;
    }

    private void choose(String title, String[] options, final int current, final Pick pick) {
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setSingleChoiceItems(options, current, (d, which) -> {
                    d.dismiss();
                    if (which != current) pick.pick(which);
                })
                .show();
    }

    private void changed(String key) {
        saveFilters();
        buildPills(key);
        reload();
    }

    private void personChanged(String key) {
        buildPills(key);
        reload();
    }

    private String summary() {
        String what = type == 0 ? "films" : "series";
        String g = genre >= 0 ? GENRES[genre].name : "All";
        String s = "";
        if (genre >= 0 && GENRES[genre].subs != null && sub >= 0) s = SEP + GENRES[genre].subs[sub][0];
        return g + " " + what + s;
    }

    private static String deptLabel(String dept) {
        if (dept == null || dept.isEmpty() || dept.equals("Acting")) return "Actor";
        if (dept.equals("Directing")) return "Director";
        if (dept.equals("Writing")) return "Writer";
        if (dept.equals("Production")) return "Producer";
        return dept;
    }

    // ---------- Results ----------

    private void reload() {
        generation++;
        loading = false;
        done = false;
        nextPage = 1;
        seen.clear();
        count = 0;
        inRow = 0;
        rowView = null;
        grid.removeAllViews();
        status.setVisibility(View.VISIBLE);
        status.setText("Loading\u2026");

        if (personId != 0) {
            done = true;
            infoTitle.setText(personName);
            infoMeta.setText(deptLabel(personDept));
            if (personAll == null) fetchPerson();
            else showPerson();
            return;
        }

        infoTitle.setText(summary());
        infoMeta.setText(SORTS[sort] + (decade > 0 ? SEP + DECADES[decade] : "")
                + (type == 0 && length > 0 ? SEP + LENGTHS[length] : ""));
        loadMore();
    }

    private void loadMore() {
        if (loading || done || dialog == null) return;
        loading = true;
        final int gen = generation;
        final int start = nextPage;
        final int fType = type, fGenre = genre, fSub = sub, fSort = sort, fDecade = decade, fLength = length;

        new Thread(() -> {
            final List<Item> found = new ArrayList<>();
            int pageNo = start;
            int total = 1;
            boolean failed = false;
            boolean missing = false;
            try {
                String base = buildPath(fType, fGenre, fSub, fSort, fDecade, fLength);
                if (base == null) {
                    missing = true;
                } else {
                    String kind = fType == 1 ? "tv" : "movie";
                    for (int n = 0; n < PAGES_PER_BATCH && found.size() < BATCH; n++) {
                        JSONObject o = new JSONObject(tmdbGet(base + "&page=" + pageNo));
                        total = Math.min(o.optInt("total_pages", 0), 500);
                        JSONArray res = o.optJSONArray("results");
                        if (res != null) {
                            for (int i = 0; i < res.length(); i++) {
                                JSONObject raw = res.getJSONObject(i);
                                if (!Languages.keep(raw)) continue;
                                Item it = parseItem(raw, kind);
                                if (it == null || it.poster == null || it.poster.isEmpty()) continue;
                                Languages.remember(it, raw);
                                found.add(it);
                            }
                        }
                        pageNo++;
                        if (pageNo > total) break;
                    }
                }
            } catch (Exception e) {
                failed = true;
            }

            final int next = pageNo;
            final int totalPages = total;
            final boolean fail = failed;
            final boolean noKeyword = missing;
            a.runOnUiThread(() -> {
                if (gen != generation) return;
                loading = false;
                nextPage = next;
                done = fail || noKeyword || next > totalPages || (count == 0 && found.isEmpty() && next > GIVE_UP_PAGE);
                for (Item it : found) if (seen.add(it.key())) addPoster(it);

                if (count == 0) {
                    status.setVisibility(View.VISIBLE);
                    if (fail) status.setText("Couldn't load. Check the internet connection, then try again.");
                    else if (noKeyword) status.setText("Couldn't find that subgenre on TMDB right now.");
                    else if (done) status.setText("Nothing found. Try fewer filters.");
                    else {
                        status.setText("Still looking\u2026");
                        loadMore();
                    }
                } else {
                    status.setVisibility(View.GONE);
                }
            });
        }).start();
    }

    /** The TMDB Discover request for the chosen filters, or null if a keyword couldn't be found. */
    private String buildPath(int fType, int fGenre, int fSub, int fSort, int fDecade, int fLength) throws Exception {
        boolean tv = fType == 1;
        String dateField = tv ? "first_air_date" : "primary_release_date";
        StringBuilder q = new StringBuilder("/discover/").append(tv ? "tv" : "movie")
                .append("?language=en-US&include_adult=false");

        List<String> keywords = new ArrayList<>();
        if (fGenre >= 0) {
            Genre g = GENRES[fGenre];
            if (tv && g.tv == 0) {
                String id = keywordId(g.tvKeyword);
                if (id == null) return null;
                keywords.add(id);
            } else {
                q.append("&with_genres=").append(tv ? g.tv : g.movie);
            }
            if (g.subs != null && fSub >= 0 && fSub < g.subs.length) {
                String id = keywordId(g.subs[fSub][1]);
                if (id == null) return null;
                keywords.add(id);
            }
        }
        if (!keywords.isEmpty()) q.append("&with_keywords=").append(TextUtils.join(",", keywords));

        String gte = null, lte = null;
        switch (fDecade) {
            case 1: gte = "2020-01-01"; break;
            case 2: gte = "2010-01-01"; lte = "2019-12-31"; break;
            case 3: gte = "2000-01-01"; lte = "2009-12-31"; break;
            case 4: gte = "1990-01-01"; lte = "1999-12-31"; break;
            case 5: gte = "1980-01-01"; lte = "1989-12-31"; break;
            case 6: lte = "1979-12-31"; break;
            default: break;
        }
        String today = dateOffset(0);
        if (lte == null || lte.compareTo(today) > 0) lte = today;
        if (gte != null) q.append("&").append(dateField).append(".gte=").append(gte);
        q.append("&").append(dateField).append(".lte=").append(lte);

        switch (fSort) {
            case 1:
                q.append("&sort_by=vote_average.desc&vote_count.gte=").append(tv ? 200 : 300);
                break;
            case 2:
                q.append("&sort_by=").append(dateField).append(".desc&vote_count.gte=").append(tv ? 3 : 10);
                break;
            case 3:
                q.append("&sort_by=vote_average.desc&vote_average.gte=7&vote_count.gte=").append(tv ? 40 : 80)
                        .append("&vote_count.lte=").append(tv ? 800 : 1500);
                break;
            default:
                q.append("&sort_by=popularity.desc&vote_count.gte=").append(tv ? 20 : 50);
        }

        if (!tv) {
            switch (fLength) {
                case 1: q.append("&with_runtime.lte=99"); break;
                case 2: q.append("&with_runtime.gte=100&with_runtime.lte=140"); break;
                case 3: q.append("&with_runtime.gte=141"); break;
                default: break;
            }
        }
        return q.toString();
    }

    /** Looks up a TMDB keyword ID by name (remembered after the first lookup). */
    private String keywordId(String query) throws Exception {
        if (query == null) return null;
        String prefKey = "kw_" + query;
        String cached = a.prefs.getString(prefKey, null);
        if (cached != null && !cached.isEmpty()) return cached;

        JSONArray res = new JSONObject(tmdbGet("/search/keyword?query="
                + URLEncoder.encode(query, "UTF-8") + "&page=1")).optJSONArray("results");
        if (res == null || res.length() == 0) return null;
        String id = null;
        for (int i = 0; i < res.length(); i++) {
            JSONObject r = res.getJSONObject(i);
            if (query.equalsIgnoreCase(str(r, "name"))) {
                id = String.valueOf(r.optInt("id"));
                break;
            }
        }
        if (id == null) id = String.valueOf(res.getJSONObject(0).optInt("id"));
        a.prefs.edit().putString(prefKey, id).apply();
        return id;
    }

    // ---------- Person ----------

    private void fetchPerson() {
        loading = true;
        final int gen = generation;
        final int id = personId;
        final String dept = personDept;
        new Thread(() -> {
            List<Item> use = null;
            final Map<String, Integer> votes = new HashMap<>();
            try {
                JSONObject o = new JSONObject(tmdbGet("/person/" + id + "/combined_credits?language=en-US"));
                boolean acting = dept.isEmpty() || "Acting".equals(dept);
                JSONArray list = o.optJSONArray(acting ? "cast" : "crew");
                List<Item> kept = new ArrayList<>();
                List<Item> all = new ArrayList<>();
                Set<String> keys = new HashSet<>();
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject raw = list.getJSONObject(i);
                        if (!acting && !dept.equals(str(raw, "department"))) continue;
                        if (str(raw, "character").toLowerCase().contains("self")) continue;
                        if (hasGenre(raw, 10767) || hasGenre(raw, 10763) || hasGenre(raw, 10764)) continue;
                        Item it = parseItem(raw, null);
                        if (it == null || it.poster == null || it.poster.isEmpty()) continue;
                        if (!keys.add(it.key())) continue;
                        Languages.remember(it, raw);
                        votes.put(it.key(), raw.optInt("vote_count", 0));
                        all.add(it);
                        if (Languages.keep(raw)) kept.add(it);
                    }
                }
                // Your languages first; if this person's work is all in other languages, show it all
                use = kept.isEmpty() ? all : kept;
                sortByVotes(use, votes);
                if (use.size() > PERSON_MAX) use = new ArrayList<>(use.subList(0, PERSON_MAX));
            } catch (Exception ignored) {
            }
            final List<Item> result = use;
            a.runOnUiThread(() -> {
                if (gen != generation) return;
                loading = false;
                if (result == null) {
                    status.setText("Couldn't load. Check the internet connection, then try again.");
                    return;
                }
                personAll = result;
                personVotes.clear();
                personVotes.putAll(votes);
                showPerson();
            });
        }).start();
    }

    private void showPerson() {
        List<Item> list = new ArrayList<>();
        for (Item it : personAll) {
            if (personShow == 1 && !"movie".equals(it.type)) continue;
            if (personShow == 2 && !"tv".equals(it.type)) continue;
            list.add(it);
        }
        if (personSort == 1) {
            Collections.sort(list, new Comparator<Item>() {
                @Override
                public int compare(Item x, Item y) {
                    String dx = x.date == null ? "" : x.date;
                    String dy = y.date == null ? "" : y.date;
                    return dy.compareTo(dx);
                }
            });
        }
        for (Item it : list) if (seen.add(it.key())) addPoster(it);
        if (count == 0) {
            status.setVisibility(View.VISIBLE);
            status.setText("Nothing found.");
        } else {
            status.setVisibility(View.GONE);
        }
        infoMeta.setText(deptLabel(personDept) + SEP + count + (count == 1 ? " title" : " titles"));
    }

    private static boolean hasGenre(JSONObject raw, int id) {
        JSONArray g = raw.optJSONArray("genre_ids");
        if (g == null) return false;
        for (int i = 0; i < g.length(); i++) if (g.optInt(i) == id) return true;
        return false;
    }

    private static void sortByVotes(List<Item> list, final Map<String, Integer> votes) {
        Collections.sort(list, new Comparator<Item>() {
            @Override
            public int compare(Item x, Item y) {
                Integer vx = votes.get(x.key());
                Integer vy = votes.get(y.key());
                return Integer.compare(vy == null ? 0 : vy, vx == null ? 0 : vx);
            }
        });
    }

    // ---------- Posters ----------

    private void addPoster(final Item it) {
        if (rowView == null || inRow == COLS) {
            rowView = new LinearLayout(a);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            rowView.setClipChildren(false);
            rowView.setClipToPadding(false);
            grid.addView(rowView, new LinearLayout.LayoutParams(MATCH, WRAP));
            inRow = 0;
        }
        final int index = count;
        View p = discover.buildPoster(it, PW, PH, false, () -> discover.openInfo(it));
        Languages.tag(a, p, it);

        final View.OnFocusChangeListener base = p.getOnFocusChangeListener();
        p.setOnFocusChangeListener((v, f) -> {
            if (base != null) base.onFocusChange(v, f);
            if (f) onPosterFocus(it, index);
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(PW), dp(PH));
        lp.setMargins(dp(7), dp(9), dp(7), dp(9));
        rowView.addView(p, lp);
        inRow++;
        count++;
    }

    private void onPosterFocus(Item it, int index) {
        infoTitle.setText(it.title);
        String lang = Languages.foreignName(it);
        infoMeta.setText(meta(it) + (lang == null ? "" : SEP + lang));

        pendingBg = it;
        a.handler.removeCallbacks(bgRun);
        a.handler.postDelayed(bgRun, 350);

        if (index >= count - COLS * 2) loadMore();
    }
}
