package com.jas.shieldhome;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
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
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import static com.jas.shieldhome.Tmdb.TMDB_IMG;
import static com.jas.shieldhome.Tmdb.hasToken;
import static com.jas.shieldhome.Tmdb.meta;
import static com.jas.shieldhome.Tmdb.parseItem;
import static com.jas.shieldhome.Tmdb.tmdbGet;
import static com.jas.shieldhome.Ui.MATCH;
import static com.jas.shieldhome.Ui.SOFT_TEXT;
import static com.jas.shieldhome.Ui.WRAP;
import static com.jas.shieldhome.Ui.str;

/**
 * Screensaver: film and series artwork, slowly drifting and fading, with the title and clock.
 * Shows the official title logo when TMDB has one, otherwise the title as text.
 * Left and right on the remote skip pictures; any other button exits; volume still works.
 */
public class ArtworkDream extends DreamService {

    private static final long EVERY_MS = 20 * 1000L;
    private static final long FADE_MS = 2000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Images images = new Images();
    private final List<Item> items = new ArrayList<>();
    private final Map<String, String> logoPaths = new HashMap<>();
    private final Random random = new Random();
    private final ImageView[] layers = new ImageView[2];
    private int front = 0;
    private int index = -1;
    private boolean dreaming = false;
    private boolean loading = false;

    private LinearLayout caption;
    private ImageView logoView;
    private TextView titleView, metaView;
    private LinearLayout clockBox;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!dreaming) return;
            next();
            handler.postDelayed(this, EVERY_MS);
        }
    };

    // ---------- Lifecycle ----------

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Interactive so left and right can skip; other buttons exit (handled below)
        setInteractive(true);
        setFullscreen(true);
        setContentView(buildScreen());
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        dreaming = true;
        if (items.isEmpty()) {
            load();
        } else {
            next();
            startTicker();
        }
    }

    @Override
    public void onDreamingStopped() {
        dreaming = false;
        handler.removeCallbacksAndMessages(null);
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        dreaming = false;
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    private void startTicker() {
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, EVERY_MS);
    }

    // ---------- Remote ----------

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();

        // Let the volume buttons work as normal
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return false;
        }

        // Left and right skip pictures
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && !items.isEmpty()) {
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    index = (index - 2 + items.size() * 2) % items.size();
                }
                next();
                startTicker();
            }
            return true;
        }

        // Anything else ends the screensaver
        if (e.getAction() == KeyEvent.ACTION_DOWN) exit();
        return true;
    }

    private void exit() {
        if (Build.VERSION.SDK_INT >= 23) wakeUp();
        else finish();
    }

    // ---------- Screen ----------

    private View buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        for (int i = 0; i < 2; i++) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setAlpha(0f);
            root.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
            layers[i] = iv;
        }

        // Darker at the top and bottom so the clock and title are easy to read
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x80000000, 0x00000000, 0x00000000, 0xB3000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(MATCH, MATCH));

        // Title (logo or text) and details, bottom left
        caption = new LinearLayout(this);
        caption.setOrientation(LinearLayout.VERTICAL);
        caption.setAlpha(0f);

        logoView = new ImageView(this);
        logoView.setAdjustViewBounds(true);
        logoView.setScaleType(ImageView.ScaleType.FIT_START);
        logoView.setMaxHeight(dp(110));
        logoView.setMaxWidth(dp(480));
        logoView.setVisibility(View.GONE);
        caption.addView(logoView, new LinearLayout.LayoutParams(WRAP, WRAP));

        titleView = new TextView(this);
        style(titleView, 34, true, 0xFFFFFFFF);
        titleView.setSingleLine(true);
        caption.addView(titleView, new LinearLayout.LayoutParams(WRAP, WRAP));

        metaView = new TextView(this);
        style(metaView, 18, false, SOFT_TEXT);
        metaView.setPadding(0, dp(8), 0, 0);
        caption.addView(metaView, new LinearLayout.LayoutParams(WRAP, WRAP));

        FrameLayout.LayoutParams cap = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.START);
        cap.setMargins(dp(64), 0, dp(64), dp(56));
        root.addView(caption, cap);

        // Clock, top right (uses Sangha Home's clock time zone if one is set; no city name shown)
        SharedPreferences prefs = getSharedPreferences("home", MODE_PRIVATE);
        String tz = prefs.getString("clock_tz", null);
        clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.END);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("HH:mm");
        time.setFormat24Hour("HH:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        time.setTextColor(0xFFFFFFFF);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        time.setTimeZone(tz);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE d MMMM");
        date.setFormat24Hour("EEEE d MMMM");
        style(date, 20, false, SOFT_TEXT);
        date.setPadding(0, dp(4), 0, 0);
        date.setTimeZone(tz);
        clockBox.addView(time, new LinearLayout.LayoutParams(WRAP, WRAP));
        clockBox.addView(date, new LinearLayout.LayoutParams(WRAP, WRAP));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.END);
        clp.setMargins(0, dp(48), dp(64), 0);
        root.addView(clockBox, clp);

        return root;
    }

    // ---------- Artwork ----------

    private void load() {
        if (loading || !hasToken()) return;
        loading = true;
        new Thread(() -> {
            final List<Item> found = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            String[][] sources = {
                    {"/trending/all/week?language=en-US&page=1", null},
                    {"/trending/all/week?language=en-US&page=2", null},
                    {"/movie/top_rated?language=en-US&page=1", "movie"},
                    {"/tv/top_rated?language=en-US&page=1", "tv"}};
            for (String[] s : sources) {
                try {
                    JSONArray res = new JSONObject(tmdbGet(s[0])).getJSONArray("results");
                    for (int i = 0; i < res.length(); i++) {
                        Item it = parseItem(res.getJSONObject(i), s[1]);
                        if (it == null || it.backdrop.isEmpty() || !seen.add(it.key())) continue;
                        found.add(it);
                    }
                } catch (Exception ignored) {
                }
            }
            Collections.shuffle(found);
            handler.post(() -> {
                loading = false;
                if (found.isEmpty() || !dreaming) return;
                items.clear();
                items.addAll(found);
                index = -1;
                next();
                startTicker();
            });
        }).start();
    }

    private void next() {
        if (items.isEmpty() || !dreaming) return;
        index = (index + 1) % items.size();
        final Item it = items.get(index);
        final int myIndex = index;
        findLogo(it, logoPath -> images.fetchInto(TMDB_IMG + "w1280" + it.backdrop, b -> {
            if (b == null || !dreaming || myIndex != index) return;
            if (logoPath.isEmpty()) {
                show(it, b, null);
            } else {
                images.fetchInto(TMDB_IMG + "w500" + logoPath, logo -> {
                    if (dreaming && myIndex == index) show(it, b, logo);
                });
            }
        }));
    }

    /** Puts the picture and its title (logo or text) on screen. */
    private void show(final Item it, Bitmap b, final Bitmap logo) {
        ImageView back = layers[1 - front];
        ImageView old = layers[front];
        back.setImageBitmap(b);
        drift(back);
        back.animate().alpha(1f).setDuration(FADE_MS).start();
        old.animate().alpha(0f).setDuration(FADE_MS).start();
        front = 1 - front;

        caption.animate().alpha(0f).setDuration(500).withEndAction(() -> {
            if (logo != null) {
                logoView.setImageBitmap(logo);
                logoView.setVisibility(View.VISIBLE);
                titleView.setVisibility(View.GONE);
            } else {
                titleView.setText(it.title);
                titleView.setVisibility(View.VISIBLE);
                logoView.setVisibility(View.GONE);
            }
            metaView.setText(meta(it));
            // Shift the text slightly each time so nothing stays in one spot
            caption.setTranslationX(nudge());
            caption.setTranslationY(nudge());
            clockBox.setTranslationX(nudge());
            clockBox.setTranslationY(nudge());
            caption.animate().alpha(1f).setDuration(900).start();
        }).start();
    }

    /** Finds the official English title logo (PNG) for a film or series, or "" if there isn't one. */
    private void findLogo(final Item it, final Consumer<String> done) {
        String cached = logoPaths.get(it.key());
        if (cached != null) {
            done.accept(cached);
            return;
        }
        new Thread(() -> {
            String path = "";
            try {
                JSONObject o = new JSONObject(tmdbGet("/" + it.type + "/" + it.id
                        + "/images?include_image_language=en,null"));
                JSONArray logos = o.optJSONArray("logos");
                if (logos != null) {
                    // First choice: English; second: no language. PNG only (SVG can't be shown).
                    for (int pass = 0; pass < 2 && path.isEmpty(); pass++) {
                        for (int i = 0; i < logos.length(); i++) {
                            JSONObject l = logos.getJSONObject(i);
                            String lang = str(l, "iso_639_1");
                            String fp = str(l, "file_path");
                            boolean langOk = pass == 0 ? "en".equals(lang) : lang.isEmpty();
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
            handler.post(() -> {
                logoPaths.put(it.key(), p);
                done.accept(p);
            });
        }).start();
    }

    /** Slow zoom and sideways drift across the picture. */
    private void drift(ImageView v) {
        float dx = (random.nextBoolean() ? 1f : -1f) * dp(30);
        ObjectAnimator an = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1.05f, 1.15f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.05f, 1.15f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, -dx / 2f, dx / 2f));
        an.setDuration(EVERY_MS + 4000);
        an.setInterpolator(new LinearInterpolator());
        an.setAutoCancel(true);
        an.start();
    }

    // ---------- Helpers ----------

    private float nudge() {
        return (random.nextFloat() * 2f - 1f) * dp(16);
    }

    private void style(TextView tv, int sp, boolean bold, int color) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
