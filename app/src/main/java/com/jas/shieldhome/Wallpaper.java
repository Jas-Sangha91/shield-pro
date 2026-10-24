package com.jas.shieldhome;

import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jas.shieldhome.Tmdb.Item;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.jas.shieldhome.Tmdb.TMDB_IMG;
import static com.jas.shieldhome.Tmdb.hasToken;
import static com.jas.shieldhome.Tmdb.meta;
import static com.jas.shieldhome.Tmdb.parseItem;
import static com.jas.shieldhome.Tmdb.tmdbGet;
import static com.jas.shieldhome.Ui.MATCH;
import static com.jas.shieldhome.Ui.SEP;
import static com.jas.shieldhome.Ui.WRAP;

/**
 * Trending artwork, softly blurred and dimmed, slowly rotating behind the Home and Apps pages,
 * with a small caption saying what it is. Press Play on the remote to open its info card.
 */
final class Wallpaper {

    private static final long EVERY_MS = 30 * 1000L;
    private static final long REFRESH_MS = 3 * 60 * 60 * 1000L;
    private static final long FADE_MS = 1500L;

    private final MainActivity a;
    private final FrameLayout root;
    private final ImageView[] layers = new ImageView[2];
    private final LinearLayout caption;
    private final TextView captionTitle, captionMeta;
    private int front = 0;

    private final List<Item> items = new ArrayList<>();
    private int index = -1;
    private Item shown = null;
    private long loadedAt = 0;
    private boolean loading = false;
    private boolean running = false;
    private boolean onPage = true;
    private boolean hasImage = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!showing()) return;
            if (items.isEmpty() || System.currentTimeMillis() - loadedAt > REFRESH_MS) load();
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

        // Dark layer on top: strongest behind the header, a little lighter in the middle
        View scrim = new View(a);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xE60A0D14, 0xC70A0D14, 0xE00A0D14}));
        root.addView(scrim, new FrameLayout.LayoutParams(MATCH, MATCH));

        // Caption, bottom right: what the artwork is from
        caption = new LinearLayout(a);
        caption.setOrientation(LinearLayout.VERTICAL);
        caption.setGravity(Gravity.END);
        caption.setAlpha(0f);
        captionTitle = new TextView(a);
        a.style(captionTitle, 14, true, 0xCCFFFFFF);
        captionTitle.setSingleLine(true);
        captionTitle.setEllipsize(TextUtils.TruncateAt.END);
        captionTitle.setMaxWidth(a.dp(420));
        captionMeta = new TextView(a);
        a.style(captionMeta, 12, false, 0x99FFFFFF);
        captionMeta.setSingleLine(true);
        captionMeta.setPadding(0, a.dp(2), 0, 0);
        caption.addView(captionTitle, new LinearLayout.LayoutParams(WRAP, WRAP));
        caption.addView(captionMeta, new LinearLayout.LayoutParams(WRAP, WRAP));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.END);
        clp.setMargins(0, 0, a.dp(48), a.dp(24));
        root.addView(caption, clp);
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

    /** Shown on Home and Apps, hidden on Discover. */
    void setShownOnPage(boolean show) {
        if (onPage == show) return;
        onPage = show;
        update();
    }

    /** The film or series currently behind the tiles, or null if the wallpaper isn't showing. */
    Item currentItem() {
        return showing() && hasImage ? shown : null;
    }

    private boolean showing() {
        return running && onPage && isOn() && hasToken();
    }

    private void update() {
        a.handler.removeCallbacks(tick);
        if (!showing()) {
            root.animate().alpha(0f).setDuration(400).start();
            return;
        }
        if (hasImage) root.animate().alpha(1f).setDuration(400).start();
        if (items.isEmpty() || System.currentTimeMillis() - loadedAt > REFRESH_MS) load();
        else if (!hasImage) next();
        a.handler.postDelayed(tick, EVERY_MS);
    }

    private void load() {
        if (loading) return;
        loading = true;
        new Thread(() -> {
            final List<Item> found = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            try {
                for (int pageNo = 1; pageNo <= 2; pageNo++) {
                    JSONArray res = new org.json.JSONObject(
                            tmdbGet("/trending/all/week?language=en-US&page=" + pageNo)).getJSONArray("results");
                    for (int i = 0; i < res.length(); i++) {
                        Item it = parseItem(res.getJSONObject(i), null);
                        if (it == null || it.backdrop.isEmpty() || !seen.add(it.key())) continue;
                        found.add(it);
                    }
                }
            } catch (Exception ignored) {
            }
            Collections.shuffle(found);
            a.runOnUiThread(() -> {
                loading = false;
                if (found.isEmpty()) return;
                items.clear();
                items.addAll(found);
                loadedAt = System.currentTimeMillis();
                index = -1;
                next();
            });
        }).start();
    }

    private void next() {
        if (items.isEmpty()) return;
        index = (index + 1) % items.size();
        final Item it = items.get(index);
        // A small image, softened further, gives a gentle out-of-focus look
        a.images.fetchInto(TMDB_IMG + "w300" + it.backdrop, b -> {
            if (b == null || !showing()) return;
            ImageView back = layers[1 - front];
            ImageView old = layers[front];
            back.setImageBitmap(soften(b));
            back.animate().alpha(1f).setDuration(FADE_MS).start();
            old.animate().alpha(0f).setDuration(FADE_MS).start();
            front = 1 - front;
            shown = it;

            caption.animate().alpha(0f).setDuration(400).withEndAction(() -> {
                captionTitle.setText(it.title);
                captionMeta.setText(meta(it) + SEP + "Press Play for info");
                caption.animate().alpha(1f).setDuration(700).start();
            }).start();

            if (!hasImage) {
                hasImage = true;
                root.animate().alpha(1f).setDuration(600).start();
            }
        });
    }

    /** Shrinks the image and scales it back up, which blurs it softly. */
    private static Bitmap soften(Bitmap b) {
        try {
            Bitmap tiny = Bitmap.createScaledBitmap(b, 80, 45, true);
            return Bitmap.createScaledBitmap(tiny, 320, 180, true);
        } catch (Exception e) {
            return b;
        }
    }
}
