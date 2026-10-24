package com.jas.shieldhome;

import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jas.shieldhome.Tmdb.TMDB_IMG;
import static com.jas.shieldhome.Tmdb.hasToken;
import static com.jas.shieldhome.Tmdb.tmdbGet;
import static com.jas.shieldhome.Ui.MATCH;
import static com.jas.shieldhome.Ui.str;

/** Discover's artwork slowly rotating behind the Home page, dimmed so the tiles stay easy to read. */
final class Wallpaper {

    private static final long EVERY_MS = 30 * 1000L;
    private static final long REFRESH_MS = 3 * 60 * 60 * 1000L;
    private static final long FADE_MS = 1500L;

    private final MainActivity a;
    private final FrameLayout root;
    private final ImageView[] layers = new ImageView[2];
    private int front = 0;

    private final List<String> backdrops = new ArrayList<>();
    private int index = -1;
    private long loadedAt = 0;
    private boolean loading = false;
    private boolean running = false;
    private boolean onHome = true;
    private boolean hasImage = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!showing()) return;
            if (backdrops.isEmpty() || System.currentTimeMillis() - loadedAt > REFRESH_MS) load();
            else next();
            a.handler.postDelayed(this, EVERY_MS);
        }
    };

    Wallpaper(MainActivity a) {
        this.a = a;
        root = new FrameLayout(a);
        root.setAlpha(0f);
        for (int i = 0; i < 2; i++) {
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setAlpha(0f);
            root.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
            layers[i] = iv;
        }
        // Dark layer on top: strongest behind the header, lighter in the middle
        View scrim = new View(a);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xE60A0D14, 0xB30A0D14, 0xD90A0D14}));
        root.addView(scrim, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    View view() {
        return root;
    }

    boolean isOn() {
        return a.prefs.getBoolean("wallpaper_on", true);
    }

    void setOn(boolean on) {
        a.prefs.edit().putBoolean("wallpaper_on", on).apply();
        update();
    }

    void start() {
        running = true;
        update();
    }

    void stop() {
        running = false;
        a.handler.removeCallbacks(tick);
    }

    void setHomeVisible(boolean home) {
        if (onHome == home) return;
        onHome = home;
        update();
    }

    private boolean showing() {
        return running && onHome && isOn() && hasToken();
    }

    private void update() {
        a.handler.removeCallbacks(tick);
        if (!showing()) {
            root.animate().alpha(0f).setDuration(400).start();
            return;
        }
        if (hasImage) root.animate().alpha(1f).setDuration(400).start();
        if (backdrops.isEmpty() || System.currentTimeMillis() - loadedAt > REFRESH_MS) load();
        else if (!hasImage) next();
        a.handler.postDelayed(tick, EVERY_MS);
    }

    private void load() {
        if (loading) return;
        loading = true;
        new Thread(() -> {
            final List<String> found = new ArrayList<>();
            try {
                for (int pageNo = 1; pageNo <= 2; pageNo++) {
                    JSONArray res = new JSONObject(tmdbGet("/trending/all/week?language=en-US&page=" + pageNo))
                            .getJSONArray("results");
                    for (int i = 0; i < res.length(); i++) {
                        JSONObject o = res.getJSONObject(i);
                        String b = str(o, "backdrop_path");
                        if (!b.isEmpty() && !found.contains(b)) found.add(b);
                    }
                }
            } catch (Exception ignored) {
            }
            Collections.shuffle(found);
            a.runOnUiThread(() -> {
                loading = false;
                if (found.isEmpty()) return;
                backdrops.clear();
                backdrops.addAll(found);
                loadedAt = System.currentTimeMillis();
                index = -1;
                next();
            });
        }).start();
    }

    private void next() {
        if (backdrops.isEmpty()) return;
        index = (index + 1) % backdrops.size();
        final String url = TMDB_IMG + "w1280" + backdrops.get(index);
        a.images.fetchInto(url, b -> {
            if (b == null || !showing()) return;
            ImageView back = layers[1 - front];
            ImageView old = layers[front];
            back.setImageBitmap(b);
            back.animate().alpha(1f).setDuration(FADE_MS).start();
            old.animate().alpha(0f).setDuration(FADE_MS).start();
            front = 1 - front;
            if (!hasImage) {
                hasImage = true;
                root.animate().alpha(1f).setDuration(600).start();
            }
        });
    }
}
