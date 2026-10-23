package com.jas.shieldhome;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
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
import static com.jas.shieldhome.Ui.timeOfDayColors;

/** The full-screen search for apps, films and series. */
final class SearchScreen {

    private final MainActivity a;

    SearchScreen(MainActivity a) {
        this.a = a;
    }

    private int dp(int v) {
        return a.dp(v);
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
        field.setHint("Search your apps, films and series");
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
                    final List<Item> found = new ArrayList<>();
                    try {
                        JSONArray res = new JSONObject(tmdbGet("/search/multi?language=en-US&include_adult=false&page=1&query="
                                + URLEncoder.encode(q, "UTF-8"))).getJSONArray("results");
                        for (int i = 0; i < res.length() && found.size() < 20; i++) {
                            Item it = parseItem(res.getJSONObject(i), null);
                            if (it != null) found.add(it);
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
                        final String q, final List<Item> films, final boolean searchFilms) {
        results.removeAllViews();

        if (q.isEmpty()) {
            List<String> recent = loadRecent();
            if (recent.isEmpty()) {
                note(results, "Type to search your apps and every film and series.");
                return;
            }
            a.addHeaderTo(results, "Recent searches");
            LinearLayout pills = new LinearLayout(a);
            pills.setOrientation(LinearLayout.HORIZONTAL);
            pills.setPadding(dp(12), dp(6), dp(12), dp(6));
            pills.setClipChildren(false);
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

        // Films and series
        if (searchFilms) {
            if (films == null) {
                note(results, "Searching films and series\u2026");
            } else if (films.isEmpty()) {
                note(results, apps.isEmpty() ? "No results for \"" + q + "\"." : "No films or series found.");
            } else {
                a.addHeaderTo(results, "Films and series");
                LinearLayout row = row();
                for (final Item it : films) {
                    row.addView(a.discover.buildPoster(it, POSTER_W, POSTER_H, false, () -> {
                        saveRecent(q);
                        a.discover.openInfo(it);
                    }), a.size(POSTER_W, POSTER_H, 8));
                }
                results.addView(band(row), new LinearLayout.LayoutParams(MATCH, WRAP));
            }
        } else if (apps.isEmpty()) {
            note(results, "No apps match \"" + q + "\".");
        }
    }

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
