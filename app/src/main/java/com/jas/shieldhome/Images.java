package com.jas.shieldhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads posters and artwork in the background and keeps recent ones in memory. */
final class Images {

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    Bitmap fetch(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream in = c.getInputStream()) {
                return BitmapFactory.decodeStream(in, null, o);
            } finally {
                c.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    void prefetch(final String url) {
        if (cache.get(url) != null) return;
        pool.execute(() -> {
            Bitmap b = fetch(url);
            if (b != null) cache.put(url, b);
        });
    }

    void load(final String url, final ImageView into) {
        into.setTag(url);
        Bitmap cached = cache.get(url);
        if (cached != null) {
            into.setImageBitmap(cached);
            return;
        }
        into.setImageDrawable(null);
        pool.execute(() -> {
            Bitmap b = fetch(url);
            if (b == null) return;
            cache.put(url, b);
            main.post(() -> {
                if (url.equals(into.getTag())) into.setImageBitmap(b);
            });
        });
    }
}
