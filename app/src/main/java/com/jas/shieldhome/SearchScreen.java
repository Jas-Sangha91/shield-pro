package com.jas.shieldhome;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jas.shieldhome.Tmdb.Item;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static com.jas.shieldhome.Tmdb.hasToken;
import static com.jas.shieldhome.Tmdb.parseItem;
import static com.jas.shieldhome.Tmdb.tmdbGet;
import static com.jas.shieldhome.Ui.GRID_GAP;
import static com.jas.shieldhome.Ui.GRID_H;
import static com.jas.shieldhome.Ui.GRID_W;
import static com.jas.shieldhome.Ui.ICON_IDLE;
import static com.jas.shieldhome.Ui.MATCH;
import static com.jas.shieldhome.Ui.POSTER_H;
import static com.jas.shieldhome.Ui.POSTER_W;
import static com.jas.shieldhome.Ui.SOFT_TEXT;
import static com.jas.shieldhome.Ui.WRAP;
import static com.jas.shieldhome.Ui.str;
import static com.jas.shieldhome.Ui.timeOfDayColors;

/** The full-screen search for apps, films, series, people and genres. */
final class SearchScreen {

    private static final String DOT = " \u00B7 ";

    // Words that open Browse: {search term, genre, subgenre or null}. Hyphens count as spaces.
    private static final String[][] SHORTCUTS = {
            {"horror", "Horror", null}, {"scary", "Horror", null}, {"spooky", "Horror", null},
            {"slasher", "Horror", "Slasher"}, {"slashers", "Horror", "Slasher"},
            {"supernatural", "Horror", "Supernatural"},
            {"found footage", "Horror", "Found footage"},
            {"zombie", "Horror", "Zombie"}, {"zombies", "Horror", "Zombie"},
            {"ghost", "Horror", "Ghost"}, {"ghosts", "Horror", "Ghost"},
            {"haunted", "Horror", "Haunted house"}, {"haunted house", "Horror", "Haunted house"},
            {"vampire", "Horror", "Vampire"}, {"vampires", "Horror", "Vampire"},
            {"creature", "Horror", "Creature feature"}, {"monster", "Horror", "Creature feature"},
            {"monsters", "Horror", "Creature feature"},
            {"body horror", "Horror", "Body horror"},
            {"psychological", "Horror", "Psychological"},
            {"comedy", "Comedy", null}, {"comedies", "Comedy", null}, {"funny", "Comedy", null},
            {"romcom", "Comedy", "Romantic comedy"}, {"rom com", "Comedy", "Romantic comedy"},
            {"romcoms", "Comedy", "Romantic comedy"}, {"romantic comedy", "Comedy", "Romantic comedy"},
            {"dark comedy", "Comedy", "Dark comedy"}, {"black comedy", "Comedy", "Dark comedy"},
            {"spoof", "Comedy", "Spoof"}, {"parody", "Comedy", "Spoof"},
            {"stand up", "Comedy", "Stand-up"}, {"standup", "Comedy", "Stand-up"},
            {"action", "Action", null},
            {"thriller", "Thriller", null}, {"thrillers", "Thriller", null}, {"suspense", "Thriller", null},
            {"sci fi", "Sci-fi", null}, {"scifi", "Sci-fi", null}, {"science fiction", "Sci-fi", null},
            {"drama", "Drama", null}, {"dramas", "Drama", null},
            {"crime", "Crime", null}, {"gangster", "Crime", null},
            {"mystery", "Mystery", null}, {"whodunnit", "Mystery", null}, {"whodunit", "Mystery", null},
            {"romance", "Romance", null}, {"romantic", "Romance", null}, {"love story", "Romance", null},
            {"fantasy", "Fantasy", null},
            {"animation", "Animation", null}, {"animated", "Animation", null},
            {"cartoon", "Animation", null}, {"cartoons", "Animation", null}, {"anime", "Animation", null},
            {"family", "Family", null}, {"kids", "Family", null},
            {"documentary", "Documentary", null}, {"documentaries", "Documentary", null},
            {"war", "War", null},
            {"western", "Western", null}, {"westerns", "Western", null}};

    private static final class Person {
        final int id;
        final String name, profile, dept;

        Person(int id, String name, String profile, String dept) {
            this.id = id;
            this.name = name;
            this.profile = profile;
            this.dept = dept;
        }
    }

    private static final class Found {
        final List<Item> films = new ArrayList<>();
        final List<Person> people = new ArrayList<>();
        boolean personFirst = false;
    }

    private final MainActivity a;
    private BrowsePage browse;

    SearchScreen(MainActivity a) {
        this.a = a;
    }

    private int dp(int v) {
        return a.dp(v);
    }

    private BrowsePage browse() {
        if (browse == null) browse = new BrowsePage(a, a.discover);
        return browse;
    }

    void open() {
        final Dialog d = new Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);

        FrameLayout root = new FrameLayout(a);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, timeOfDayColors()));

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(48), dp(32), dp(48), dp(20));
        root.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));

        TextView heading = new TextView(a);
        a.style(heading, 16, true, SOFT_TEXT);
        heading.setText("Search");
        heading.setPadding(dp(8), 0, 0, dp(10));
        col.addView(heading, new LinearLayout.LayoutParams(WRAP, WRAP));

        final EditText field = new EditText(a);
        field.setSingleLine(true);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        field.setTextColor(0xFFFFFFFF);
        field.setHintTextColor(0x80FFFFFF);
        field.setHint("Search apps, films, series, people or genres");
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH
                | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        final GradientDrawable fieldBg = new GradientDrawable();
        fieldBg.setCornerRadius(dp(28));
        fieldBg.setColor(0x1FFFFFFF);
        field.setBackground(fieldBg);
        field.setPadding(dp(22), dp(14), dp(22), dp(14));
        Drawable icon = a.getDrawable(R.drawable.ic_search).mutate();
        icon.setTint(ICON_IDLE);
        icon.setBounds(0, 0, dp(22), dp(22));
        field.setCompoundDrawables(icon, null, null, null);
        field.setCompoundDrawablePadding(dp(14));
        field.setOnFocusChangeListener((v, f) -> fieldBg.setStroke(f ? dp(2) : 0, 0xFFFFFFFF));
        col.addView(field, new LinearLayout.LayoutParams(MATCH, WRAP));

        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(dp(24));
        final LinearLayout results = new LinearLayout(a);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setClipChildren(false);
        results.setClipToPadding(false);
        results.setPadding(0, dp(4), 0, dp(40));
        sv.addView(results, new FrameLayout.LayoutParams(MATCH, WRAP));
        col.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        final boolean hasTmdb = hasToken();
        final Runnable[] pending = new Runnable[1];
        final int[] generation = {0};

        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                final String q = s.toString().trim();
                if (pending[0] != null) a.handler.removeCallbacks(pending[0]);
                final int gen = ++generation[0];
                boolean searchFilms = hasTmdb && q.length() >= 2;
                render(results, field, d, q, null, searchFilms);
                if (!searchFilms) return;
                pending[0] = () -> new Thread(() -> {
                    final Found found = new Found();
                    try {
                        JSONArray res = new JSONObject(tmdbGet("/search/multi?language=en-US&include_adult=false&page=1&query="
                                + URLEncoder.encode(q, "UTF-8"))).getJSONArray("results");
                        for (int i = 0; i < res.length(); i++) {
                            JSONObject r = res.getJSONObject(i);
                            if ("person".equals(str(r, "media_type"))) {
                                if (i == 0) found.personFirst = true;
                                String prof = str(r, "profile_path");
                                if (prof.isEmpty() || "null".equals(prof) || found.people.size() >= 8) continue;
                                found.people.add(new Person(r.optInt("id"), str(r, "name"), prof,
                                        str(r, "known_for_department")));
                                continue;
                            }
                            if (found.films.size() >= 20) continue;
                            Item it = parseItem(r, null);
                            if (it == null) continue;
                            Languages.remember(it, r);
                            found.films.add(it);
                        }
                    } catch (Exception ignored) {
                    }
                    a.runOnUiThread(() -> {
                        if (gen == generation[0] && d.isShowing()) render(results, field, d, q, found, true);
                    });
                }).start();
                a.handler.postDelayed(pending[0], 450);
            }
        });

        field.setOnEditorActionListener((v, actionId, ev) -> {
            String q = field.getText().toString().trim();
            if (!q.isEmpty()) saveRecent(q);
            InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
            results.requestFocus();
            return true;
        });

        render(results, field, d, "", null, false);

        d.setContentView(root);
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }
        d.setOnDismissListener(di -> {
            if (pending[0] != null) a.handler.removeCallbacks(pending[0]);
            generation[0]++;
        });
        d.show();
        field.requestFocus();
    }

    private void render(final LinearLayout results, final EditText field, final Dialog d,
                        final String q, final Found found, final boolean searchFilms) {
        results.removeAllViews();

        if (q.isEmpty()) {
            List<String> recent = loadRecent();
            if (recent.isEmpty()) {
                note(results, "Type to search your apps, films, series and people, or a genre like horror.");
                return;
            }
            a.addHeaderTo(results, "Recent searches");
            LinearLayout pills = row();
            pills.setPadding(dp(12), dp(6), dp(12), dp(6));
            for (final String term : recent) {
                TextView p = a.pillButton(term, () -> {
                    field.setText(term);
                    field.setSelection(term.length());
                }, null);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
                lp.rightMargin = dp(10);
                pills.addView(p, lp);
            }
            results.addView(band(pills), new LinearLayout.LayoutParams(MATCH, WRAP));
            return;
        }

        // Genre shortcuts (instant)
        List<int[]> cuts = findShortcuts(q);
        if (!cuts.isEmpty()) {
            a.addHeaderTo(results, "Browse");
            LinearLayout pills = row();
            pills.setPadding(dp(12), dp(6), dp(12), dp(6));
            for (final int[] c : cuts) {
                TextView p = a.pillButton(shortcutLabel(c), () -> {
                    saveRecent(q);
                    browse().openWith(c[0], c[1], c[2]);
                }, null);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
                lp.rightMargin = dp(10);
                pills.addView(p, lp);
            }
            results.addView(band(pills), new LinearLayout.LayoutParams(MATCH, WRAP));
        }

        // Apps
        String lower = q.toLowerCase(Locale.UK);
        List<String> apps = new ArrayList<>();
        for (Map.Entry<String, ResolveInfo> e : a.installed.entrySet()) {
            String label = e.getValue().loadLabel(a.pm).toString().toLowerCase(Locale.UK);
            if (label.contains(lower)) apps.add(e.getKey());
            if (apps.size() >= 10) break;
        }
        if (!apps.isEmpty()) {
            a.addHeaderTo(results, "Apps");
            LinearLayout row = row();
            for (final String pkg : apps) {
                row.addView(a.buildAppTile(pkg, () -> {
                    saveRecent(q);
                    d.dismiss();
                    a.launch(pkg);
                }), a.size(GRID_W, GRID_H, GRID_GAP));
            }
            results.addView(band(row), new LinearLayout.LayoutParams(MATCH, WRAP));
        }

        // Films, series and people
        if (searchFilms) {
            if (found == null) {
                note(results, "Searching films, series and people\u2026");
            } else if (found.films.isEmpty() && found.people.isEmpty()) {
                if (apps.isEmpty() && cuts.isEmpty()) note(results, "No results for \"" + q + "\".");
                else note(results, "No films, series or people found.");
            } else {
                if (found.personFirst) addPeople(results, found.people, q);
                addFilms(results, found.films, q);
                if (!found.personFirst) addPeople(results, found.people, q);
            }
        } else if (apps.isEmpty() && cuts.isEmpty()) {
            note(results, "No apps match \"" + q + "\".");
        }
    }

    private void addFilms(LinearLayout results, List<Item> films, final String q) {
        if (films.isEmpty()) return;
        a.addHeaderTo(results, "Films and series");
        LinearLayout row = row();
        for (final Item it : films) {
            View p = a.discover.buildPoster(it, POSTER_W, POSTER_H, false, () -> {
                saveRecent(q);
                a.discover.openInfo(it);
            });
            Languages.tag(a, p, it);
            row.addView(p, a.size(POSTER_W, POSTER_H, 8));
        }
        results.addView(band(row), new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private void addPeople(LinearLayout results, List<Person> people, final String q) {
        if (people.isEmpty()) return;
        a.addHeaderTo(results, "People");
        LinearLayout row = row();
        for (final Person p : people) {
            row.addView(personTile(p, q), a.size(POSTER_W, POSTER_H, 8));
        }
        results.addView(band(row), new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    /** A photo tile with the person's name and job along the bottom. */
    private View personTile(final Person p, final String q) {
        Item photo = new Item();
        photo.id = p.id;
        photo.type = "person";
        photo.title = p.name;
        photo.poster = p.profile;
        photo.backdrop = "";
        photo.overview = "";
        photo.date = "";
        photo.year = "";

        View t = a.discover.buildPoster(photo, POSTER_W, POSTER_H, false, () -> {
            saveRecent(q);
            browse().openPerson(p.id, p.name, p.dept);
        });
        // No film menu for people
        t.setOnLongClickListener(v -> true);

        LinearLayout band = new LinearLayout(a);
        band.setOrientation(LinearLayout.VERTICAL);
        band.setBackgroundColor(0xB3000000);
        band.setPadding(dp(6), dp(4), dp(6), dp(5));

        TextView name = new TextView(a);
        a.style(name, 12, true, 0xFFFFFFFF);
        name.setText(p.name);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        band.addView(name, new LinearLayout.LayoutParams(MATCH, WRAP));

        String job = jobLabel(p.dept);
        if (!job.isEmpty()) {
            TextView dept = new TextView(a);
            a.style(dept, 10, false, SOFT_TEXT);
            dept.setText(job);
            dept.setSingleLine(true);
            band.addView(dept, new LinearLayout.LayoutParams(MATCH, WRAP));
        }

        if (t instanceof FrameLayout) {
            ((FrameLayout) t).addView(band, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM));
        }
        return t;
    }

    private static String jobLabel(String dept) {
        if (dept == null || dept.isEmpty()) return "";
        if (dept.equals("Acting")) return "Actor";
        if (dept.equals("Directing")) return "Director";
        if (dept.equals("Writing")) return "Writer";
        if (dept.equals("Production")) return "Producer";
        return dept;
    }

    // ---------- Genre shortcuts ----------

    private static String normalise(String s) {
        return s.toLowerCase(Locale.UK).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Each result is {genre index, subgenre index or -1, type: 0 films, 1 series, -1 last used}. */
    private List<int[]> findShortcuts(String q) {
        List<int[]> out = new ArrayList<>();
        String n = normalise(q);
        if (n.isEmpty()) return out;

        String[] tokens = n.split(" ");
        int type = -1;
        for (String t : tokens) {
            if (t.equals("series") || t.equals("show") || t.equals("shows") || t.equals("tv")) type = 1;
            else if (t.equals("film") || t.equals("films") || t.equals("movie") || t.equals("movies")) type = 0;
        }
        boolean single = tokens.length == 1;

        List<int[]> hits = new ArrayList<>();
        for (String[] s : SHORTCUTS) {
            String term = s[0];
            boolean match = Pattern.compile("(^| )" + Pattern.quote(term) + "( |$)").matcher(n).find()
                    || (single && n.length() >= 4 && !term.contains(" ") && term.startsWith(n));
            if (!match) continue;
            int g = BrowsePage.genreIndex(s[1]);
            if (g < 0) continue;
            int sub = s[2] == null ? -1 : BrowsePage.subIndex(g, s[2]);
            boolean dup = false;
            for (int[] h : hits) if (h[0] == g && h[1] == sub) dup = true;
            if (!dup) hits.add(new int[]{g, sub, type});
        }

        // If a subgenre matched, drop its plain genre (e.g. "body horror" shouldn't also show Horror)
        for (int[] h : hits) {
            if (h[1] < 0) {
                boolean hasSub = false;
                for (int[] o : hits) if (o[0] == h[0] && o[1] >= 0) hasSub = true;
                if (hasSub) continue;
            }
            out.add(h);
            if (out.size() >= 3) break;
        }
        return out;
    }

    /** "Horror films · Slasher" */
    private String shortcutLabel(int[] c) {
        int type = c[2] >= 0 ? c[2] : BrowsePage.savedType(a);
        BrowsePage.Genre g = BrowsePage.GENRES[c[0]];
        String label = g.name + (type == 1 ? " series" : " films");
        if (c[1] >= 0 && g.subs != null) label += DOT + g.subs[c[1]][0];
        return label;
    }

    // ---------- Layout helpers ----------

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(4), dp(12), dp(4));
        row.setClipChildren(false);
        row.setClipToPadding(false);
        return row;
    }

    private HorizontalScrollView band(LinearLayout inner) {
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.addView(inner, new FrameLayout.LayoutParams(WRAP, WRAP));
        return hs;
    }

    private void note(LinearLayout parent, String text) {
        TextView t = new TextView(a);
        a.style(t, 16, false, SOFT_TEXT);
        t.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.topMargin = dp(24);
        lp.leftMargin = dp(12);
        parent.addView(t, lp);
    }

    // ---------- Recent searches ----------

    private List<String> loadRecent() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(a.prefs.getString("recent_searches", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    private void saveRecent(String q) {
        List<String> list = loadRecent();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).equalsIgnoreCase(q)) list.remove(i);
        }
        list.add(0, q);
        while (list.size() > 6) list.remove(list.size() - 1);
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        a.prefs.edit().putString("recent_searches", arr.toString()).apply();
    }
}
