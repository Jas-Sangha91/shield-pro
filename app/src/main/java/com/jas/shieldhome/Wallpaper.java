package com.jas.shieldhome;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jas.shieldhome.Tmdb.Item;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.jas.shieldhome.Tmdb.TMDB_IMG;
import static com.jas.shieldhome.Tmdb.hasToken;
import static com.jas.shieldhome.Tmdb.parseItem;
import static com.jas.shieldhome.Tmdb.tmdbGet;
import static com.jas.shieldhome.Ui.MATCH;
import static com.jas.shieldhome.Ui.WRAP;
import static com.jas.shieldhome.Ui.str;

/**
 * Artwork behind the screen, Netflix style.
 * Home: artwork rotates every 30 seconds (your languages plus big international hits), with the
 * title's official logo top left (or the title in text when there's no logo). Play/Pause opens its info card.
 * Discover: the artwork follows whichever poster is highlighted. Apps: plain background.
 */
final class Wallpaper {

    private static final long EVERY_MS = 30 * 1000L;
    private static final long REFRESH_MS = 3 * 60 * 60 * 1000L;
    private static final long FADE_MS = 1500L;
    private static final long FOLLOW_FADE_MS = 700L;
    private static final long FOLLOW_DELAY_MS = 350L;

    private static final int BG = 0xFF0A0D14;
    private static final float ART_HEIGHT = 0.72f;

    // The title logo: small and slightly faded, so it doesn't look like an advert
    private static final float TITLE_ALPHA = 0.85f;
    private static final int LOGO_MAX_H_DP = 44;
    private static final int LOGO_MAX_W_DP = 300;
    private static final int TITLE_TEXT = 0xE6F1F1F1;

    // Where Home artwork comes from: {request, type}
    private static final String[][] SOURCES = {
            {"/trending/all/week?language=en-US&page=1", null},
            {"/trending/all/week?language=en-US&page=2", null},
            {"/discover/movie?language=en-US&sort_by=popularity.desc&with_original_language=hi&vote_count.gte=20", "movie"},
            {"/discover/tv?language=en-US&sort_by=popularity.desc&with_original_language=hi&vote_count.gte=10", "tv"},
            {"/discover/movie?language=en-US&sort_by=popularity.desc&with_original_language=pa&vote_count.gte=3", "movie"}};

    private interface Done {
        void got(String value);
    }

    private final MainActivity a;
    private final FrameLayout root;
    private final ImageView[] layers = new ImageView[2];
    private final LinearLayout titleBox;
    private final ImageView logoView;
    private final TextView titleText;
    private final Map<String, String> logoPaths = new HashMap<>();
    private int front = 0;
    private boolean hasImage = false;
    private boolean running = false;
    private int lastPage = MainActivity.PAGE_HOME;

    // Home rotation
    private final List<Item> items = new ArrayList<>();
    private int index = -1;
    private Item shown = null;
    private long loadedAt = 0;
    private boolean loading = false;

    // Discover follow
    private Item pendingFollow = null;
    private String followKey = null;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!rotating()) return;
            if (items.isEmpty() || System.currentTimeMillis() - loadedAt > REFRESH_MS) load();
            else next();
            a.handler.postDelayed(this, EVERY_MS);
        }
    };

    private final Runnable followRun = new Runnable() {
        @Override
        public void run() {
            final Item it = pendingFollow;
            if (it == null || !following()) return;
            if (it.key().equals(followKey)) return;
            a.images.fetchInto(TMDB_IMG + "w1280" + it.backdrop, b -> {
                if (b == null || !following() || pendingFollow != it) return;
                followKey = it.key();
                crossfade(b, FOLLOW_FADE_MS);
                hasImage = true;
                root.animate().alpha(1f).setDuration(500).start();
            });
        }
    };

    Wallpaper(MainActivity a) {
        this.a = a;
        root = new FrameLayout(a);
        root.setAlpha(0f);
        root.setBackgroundColor(BG);

        int artHeight = Math.round(a.getResources().getDisplayMetrics().heightPixels * ART_HEIGHT);
        FrameLayout art = new FrameLayout(a);
        root.addView(art, new FrameLayout.LayoutParams(MATCH, artHeight, Gravity.TOP));

        for (int i = 0; i < 2; i++) {
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setAlpha(0f);
            art.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
            layers[i] = iv;
        }

        View dim = new View(a);
        dim.setBackgroundColor(0x330A0D14);
        art.addView(dim, new FrameLayout.LayoutParams(MATCH, MATCH));

        View left = new View(a);
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF20A0D14, 0xA60A0D14, 0x1A0A0D14, 0x000A0D14}));
        art.addView(left, new FrameLayout.LayoutParams(MATCH, MATCH));

        View top = new View(a);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xD90A0D14, 0x660A0D14, 0x000A0D14}));
        art.addView(top, new FrameLayout.LayoutParams(MATCH, Math.round(artHeight * 0.45f), Gravity.TOP));

        View clockShade = new View(a);
        GradientDrawable patch = new GradientDrawable();
        patch.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        patch.setGradientCenter(0.88f, 0.12f);
        patch.setGradientRadius(a.dp(340));
        patch.setColors(new int[]{0xCC0A0D14, 0x660A0D14, 0x000A0D14});
        clockShade.setBackground(patch);
        art.addView(clockShade, new FrameLayout.LayoutParams(MATCH, MATCH));

        View bottom = new View(a);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x000A0D14, 0x990A0D14, BG}));
        art.addView(bottom, new FrameLayout.LayoutParams(MATCH, artHeight / 2, Gravity.BOTTOM));

        // Title logo (or title text), top left under the tabs, where the weather used to be
        titleBox = new LinearLayout(a);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setAlpha(0f);

        logoView = new ImageView(a);
        logoView.setAdjustViewBounds(true);
        logoView.setScaleType(ImageView.ScaleType.FIT_START);
        logoView.setMaxHeight(a.dp(LOGO_MAX_H_DP));
        logoView.setMaxWidth(a.dp(LOGO_MAX_W_DP));
        logoView.setVisibility(View.GONE);
        titleBox.addView(logoView, new LinearLayout.LayoutParams(WRAP, WRAP));

        titleText = new TextView(a);
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        titleText.setTextColor(TITLE_TEXT);
        titleText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        titleText.setSingleLine(true);
        titleText.setEllipsize(TextUtils.TruncateAt.END);
        titleText.setMaxWidth(a.dp(520));
        titleText.setIncludeFontPadding(false);
        titleText.setShadowLayer(8f, 0f, 2f, 0xCC000000);
        titleText.setVisibility(View.GONE);
        titleBox.addView(titleText, new LinearLayout.LayoutParams(WRAP, WRAP));

        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.START);
        tlp.setMargins(a.dp(42), a.dp(64), 0, 0);
        root.addView(titleBox, tlp);
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
        a.handler.removeCallbacks(followRun);
    }

    void setShownOnPage(boolean ignored) {
        if (a.page == lastPage) return;
        lastPage = a.page;
        if (a.page == MainActivity.PAGE_DISCOVER) {
            pendingFollow = null;
            followKey = null;
        }
        update();
        if (rotating() && !items.isEmpty()) next();
    }

    void follow(Item it) {
        if (it == null || it.backdrop == null || it.backdrop.isEmpty()) return;
        pendingFollow = it;
        a.handler.removeCallbacks(followRun);
        a.handler.postDelayed(followRun, FOLLOW_DELAY_MS);
    }

    boolean isShowingPicture() {
        return (rotating() && hasImage) || (following() && followKey != null);
    }

    Item currentItem() {
        return rotating() && hasImage ? shown : null;
    }

    private boolean rotating() {
        return running && a.page == MainActivity.PAGE_HOME && isOn() && hasToken();
    }

    private boolean following() {
        return running && a.page == MainActivity.PAGE_DISCOVER && hasToken();
    }

    private void update() {
        a.handler.removeCallbacks(tick);

        if (following()) {
            titleBox.animate().alpha(0f).setDuration(300).start();
            root.animate().alpha(followKey != null ? 1f : 0f).setDuration(400).start();
            return;
        }

        if (!rotating()) {
            a.handler.removeCallbacks(followRun);
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
            for (String[] s : SOURCES) {
                try {
                    JSONArray res = new JSONObject(tmdbGet(s[0])).getJSONArray("results");
                    for (int i = 0; i < res.length(); i++) {
                        JSONObject raw = res.getJSONObject(i);
                        if (!Languages.keep(raw)) continue;
                        Item it = parseItem(raw, s[1]);
                        if (it == null || it.backdrop.isEmpty() || !seen.add(it.key())) continue;
                        Languages.remember(it, raw);
                        found.add(it);
                    }
                } catch (Exception ignored) {
                }
            }
            Collections.shuffle(found);
            a.runOnUiThread(() -> {
                loading = false;
                if (found.isEmpty()) return;
                items.clear();
                items.addAll(found);
                loadedAt = System.currentTimeMillis();
                index = -1;
                if (rotating()) next();
            });
        }).start();
    }

    private void next() {
        if (items.isEmpty()) return;
        index = (index + 1) % items.size();
        final Item it = items.get(index);
        final int myIndex = index;
        findLogo(it, logoPath -> a.images.fetchInto(TMDB_IMG + "w1280" + it.backdrop, b -> {
            if (b == null || !rotating() || myIndex != index) return;
            if (logoPath.isEmpty()) {
                show(it, b, null);
            } else {
                a.images.fetchInto(TMDB_IMG + "w500" + logoPath, logo -> {
                    if (rotating() && myIndex == index) show(it, b, logo);
                });
            }
        }));
    }

    private void show(final Item it, Bitmap b, Bitmap logo) {
        crossfade(b, FADE_MS);
        shown = it;

        // Very dark logos would vanish on the dark background, so they fall back to text
        final Bitmap useLogo = (logo != null && !tooDark(logo)) ? logo : null;
        titleBox.animate().alpha(0f).setDuration(400).withEndAction(() -> {
            if (useLogo != null) {
                logoView.setImageBitmap(useLogo);
                logoView.setVisibility(View.VISIBLE);
                titleText.setVisibility(View.GONE);
            } else {
                titleText.setText(it.title);
                titleText.setVisibility(View.VISIBLE);
                logoView.setVisibility(View.GONE);
            }
            if (rotating()) titleBox.animate().alpha(TITLE_ALPHA).setDuration(700).start();
        }).start();

        if (!hasImage) hasImage = true;
        root.animate().alpha(1f).setDuration(600).start();
    }

    private void crossfade(Bitmap b, long ms) {
        ImageView back = layers[1 - front];
        ImageView old = layers[front];
        back.setImageBitmap(b);
        back.animate().alpha(1f).setDuration(ms).start();
        old.animate().alpha(0f).setDuration(ms).start();
        front = 1 - front;
    }

    // ---------- Title logos ----------

    /** Finds the official title logo (PNG), as the screensaver does: English, then Hindi or Punjabi, then no language. */
    private void findLogo(final Item it, final Done done) {
        String cached = logoPaths.get(it.key());
        if (cached != null) {
            done.got(cached);
            return;
        }
        new Thread(() -> {
            String path = "";
            try {
                JSONObject o = new JSONObject(tmdbGet("/" + it.type + "/" + it.id
                        + "/images?include_image_language=en,hi,pa,null"));
                JSONArray logos = o.optJSONArray("logos");
                if (logos != null) {
                    for (int pass = 0; pass < 3 && path.isEmpty(); pass++) {
                        for (int i = 0; i < logos.length(); i++) {
                            JSONObject l = logos.getJSONObject(i);
                            String lang = str(l, "iso_639_1");
                            if ("null".equals(lang)) lang = "";
                            String fp = str(l, "file_path");
                            boolean langOk;
                            if (pass == 0) langOk = "en".equals(lang);
                            else if (pass == 1) langOk = "hi".equals(lang) || "pa".equals(lang);
                            else langOk = lang.isEmpty();
                            if (langOk && fp.toLowerCase().endsWith(".png")) {
                                path = fp;
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            final String p = path;
            a.runOnUiThread(() -> {
                logoPaths.put(it.key(), p);
                done.got(p);
            });
        }).start();
    }

    /** True when the visible part of a logo is mostly black or very dark. */
    private static boolean tooDark(Bitmap logo) {
        try {
            int w = 48;
            int h = Math.max(1, logo.getHeight() * w / Math.max(1, logo.getWidth()));
            Bitmap small = Bitmap.createScaledBitmap(logo, w, h, true);
            long sum = 0;
            int n = 0;
            for (int y = 0; y < small.getHeight(); y++) {
                for (int x = 0; x < small.getWidth(); x++) {
                    int c = small.getPixel(x, y);
                    if (Color.alpha(c) < 128) continue;
                    sum += (299L * Color.red(c) + 587L * Color.green(c) + 114L * Color.blue(c)) / 1000;
                    n++;
                }
            }
            if (small != logo) small.recycle();
            return n > 0 && sum / n < 70;
        } catch (Exception e) {
            return false;
        }
    }
}
