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
import static com.jas.shieldhome.Tmdb.meta;
import static com.jas.shieldhome.Tmdb.parseItem;
import static com.jas.shieldhome.Tmdb.tmdbGet;
import static com.jas.shieldhome.Ui.MATCH;
import static com.jas.shieldhome.Ui.SEP;
import static com.jas.shieldhome.Ui.WRAP;
import static com.jas.shieldhome.Ui.str;

/**
 * Artwork behind the screen, Netflix style.
 * Home: recent films and series (see Latest.java) rotate every 30 seconds, following your languages.
 * The artwork has the top half of the screen. On the left: the title's official logo (or the title in
 * bold text), a details line and a short description. Scrolling down to Watch and below fades it all away.
 * Play/Pause opens the info card while the artwork is showing.
 * Discover: the artwork follows whichever poster is highlighted. Apps: plain background.
 */
final class Wallpaper {

    /** How much lower the dock sits on Home while the artwork is on (below the shorter Home header). */
    static final int HERO_GAP_DP = 190;

    private static final long EVERY_MS = 30 * 1000L;
    private static final long REFRESH_MS = 3 * 60 * 60 * 1000L;
    private static final long FADE_MS = 1500L;
    private static final long FOLLOW_FADE_MS = 700L;
    private static final long FOLLOW_DELAY_MS = 350L;

    private static final int BG = 0xFF0A0D14;
    private static final float ART_HEIGHT = 0.72f;

    // The title block: slightly faded, so it doesn't look like an advert
    private static final float TITLE_ALPHA = 0.9f;
    private static final int LOGO_MAX_H_DP = 70;
    private static final int LOGO_MAX_W_DP = 420;
    private static final int TEXT_MAX_W_DP = 480;
    private static final int TITLE_TEXT = 0xF2F1F1F1;
    private static final int DETAILS_TEXT = 0xD9F1F1F1;
    private static final int OVERVIEW_TEXT = 0xA6F1F1F1;
    // The title block is centred in this band: from under the tabs to just above the dock
    private static final int TITLE_TOP_DP = 64;
    private static final int TITLE_AREA_DP = 240;
    // How far the artwork and title drift up as they fade away
    private static final int DRIFT_DP = 48;

    private interface Done {
        void got(String value);
    }

    private final MainActivity a;
    private final FrameLayout root;
    private final FrameLayout art;
    private final ImageView[] layers = new ImageView[2];
    private final FrameLayout titleArea;
    private final LinearLayout titleBox;
    private final ImageView logoView;
    private final TextView titleText;
    private final TextView detailsText;
    private final TextView overviewText;
    private final Map<String, String> logoPaths = new HashMap<>();
    private int front = 0;
    private boolean hasImage = false;
    private boolean running = false;
    private int lastPage = MainActivity.PAGE_HOME;

    // Scrolling on Home
    private int scrollY = 0;
    private boolean scrolledAway = false;

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
        art = new FrameLayout(a);
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

        // Title block, centred on the left of the artwork: logo or title, details, description
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

        // For titles without a logo: bold condensed lettering, sized to the length of the title
        titleText = new TextView(a);
        titleText.setTextColor(TITLE_TEXT);
        titleText.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        titleText.setLetterSpacing(-0.01f);
        titleText.setMaxLines(2);
        titleText.setEllipsize(TextUtils.TruncateAt.END);
        titleText.setMaxWidth(a.dp(TEXT_MAX_W_DP + 40));
        titleText.setIncludeFontPadding(false);
        titleText.setLineSpacing(0f, 0.95f);
        titleText.setShadowLayer(10f, 0f, 2f, 0xCC000000);
        titleText.setVisibility(View.GONE);
        titleBox.addView(titleText, new LinearLayout.LayoutParams(WRAP, WRAP));

        detailsText = new TextView(a);
        detailsText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        detailsText.setTextColor(DETAILS_TEXT);
        detailsText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        detailsText.setSingleLine(true);
        detailsText.setEllipsize(TextUtils.TruncateAt.END);
        detailsText.setMaxWidth(a.dp(TEXT_MAX_W_DP));
        detailsText.setShadowLayer(8f, 0f, 2f, 0xCC000000);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        dlp.topMargin = a.dp(12);
        titleBox.addView(detailsText, dlp);

        overviewText = new TextView(a);
        overviewText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        overviewText.setTextColor(OVERVIEW_TEXT);
        overviewText.setMaxLines(2);
        overviewText.setEllipsize(TextUtils.TruncateAt.END);
        overviewText.setMaxWidth(a.dp(TEXT_MAX_W_DP));
        overviewText.setLineSpacing(0f, 1.12f);
        overviewText.setShadowLayer(8f, 0f, 2f, 0xCC000000);
        overviewText.setVisibility(View.GONE);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(WRAP, WRAP);
        olp.topMargin = a.dp(6);
        titleBox.addView(overviewText, olp);

        titleArea = new FrameLayout(a);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER_VERTICAL | Gravity.START);
        tlp.leftMargin = a.dp(42);
        titleArea.addView(titleBox, tlp);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(MATCH, a.dp(TITLE_AREA_DP), Gravity.TOP);
        alp.topMargin = a.dp(TITLE_TOP_DP);
        root.addView(titleArea, alp);
    }

    View view() {
        return root;
    }

    boolean isOn() {
        return a.prefs.getBoolean("wallpaper_on", true);
    }

    /** True when Home has the artwork at the top (so the dock sits lower). */
    boolean heroOn() {
        return isOn() && hasToken();
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

    /** Called as the page scrolls: the artwork and title fade away as you go below the dock. */
    void setScroll(int y) {
        scrollY = y;
        applyScroll();
    }

    void follow(Item it) {
        if (it == null || it.backdrop == null || it.backdrop.isEmpty()) return;
        pendingFollow = it;
        a.handler.removeCallbacks(followRun);
        a.handler.postDelayed(followRun, FOLLOW_DELAY_MS);
    }

    boolean isShowingPicture() {
        return (rotating() && hasImage && !scrolledAway) || (following() && followKey != null);
    }

    Item currentItem() {
        return rotating() && hasImage && !scrolledAway ? shown : null;
    }

    private boolean rotating() {
        return running && a.page == MainActivity.PAGE_HOME && isOn() && hasToken();
    }

    private boolean following() {
        return running && a.page == MainActivity.PAGE_DISCOVER && hasToken();
    }

    /** 0 = on the dock (full artwork), 1 = scrolled past it (artwork gone). Always 0 off Home. */
    private float progress() {
        if (!rotating()) return 0f;
        float p = scrollY / (float) a.dp(HERO_GAP_DP);
        return Math.max(0f, Math.min(1f, p));
    }

    private void applyScroll() {
        float p = progress();
        float drift = -p * a.dp(DRIFT_DP);
        art.setAlpha(1f - p);
        art.setTranslationY(drift);
        titleArea.setAlpha(1f - Math.min(1f, p * 2f));
        titleArea.setTranslationY(drift);

        boolean away = p >= 0.5f;
        if (away != scrolledAway) {
            scrolledAway = away;
            a.refreshGlow();
        }
    }

    private void update() {
        a.handler.removeCallbacks(tick);
        applyScroll();

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
            for (String[] s : Latest.sources()) {
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
                titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, titleSize(it.title));
                titleText.setText(it.title);
                titleText.setVisibility(View.VISIBLE);
                logoView.setVisibility(View.GONE);
            }

            // ★ 7.8 · 2026 · Series · Korean
            String lang = Languages.foreignName(it);
            detailsText.setText(meta(it) + (lang == null ? "" : SEP + lang));

            String overview = it.overview == null ? "" : it.overview.trim();
            overviewText.setText(overview);
            overviewText.setVisibility(overview.isEmpty() ? View.GONE : View.VISIBLE);

            if (rotating()) titleBox.animate().alpha(TITLE_ALPHA).setDuration(700).start();
        }).start();

        if (!hasImage) {
            hasImage = true;
            a.refreshGlow();
        }
        root.animate().alpha(1f).setDuration(600).start();
    }

    /** Short titles are big, long ones step down so they fit on two lines. */
    private static int titleSize(String title) {
        int n = title == null ? 0 : title.length();
        if (n <= 12) return 48;
        if (n <= 20) return 42;
        if (n <= 30) return 36;
        return 30;
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
