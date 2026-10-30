package com.jas.shieldhome;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.provider.Settings;
import android.view.View;

import static com.jas.shieldhome.Ui.DARK;
import static com.jas.shieldhome.Ui.ICON_IDLE;
import static com.jas.shieldhome.Ui.VPN_OFF;

/**
 * The network button in the top bar: Ethernet, Wi-Fi (with signal strength) or no internet (amber).
 * Looks at the real connection underneath any VPN. OK opens Network & Internet.
 */
final class NetworkStatus {

    static final int OFFLINE = 0, ETHERNET = 1, WIFI = 2, OTHER = 3;
    private static final long EVERY_MS = 15 * 1000L;

    private final MainActivity a;
    private final ConnectivityManager cm;
    private final NetIcon icon;
    private View dot;
    private ConnectivityManager.NetworkCallback callback;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            a.handler.postDelayed(this, EVERY_MS);
        }
    };

    NetworkStatus(MainActivity a) {
        this.a = a;
        this.cm = (ConnectivityManager) a.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.icon = new NetIcon(a.dp(17));
    }

    Drawable icon() {
        return icon;
    }

    void setDot(View d) {
        dot = d;
    }

    void register() {
        if (callback == null && cm != null) {
            callback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network n) {
                    a.handler.post(NetworkStatus.this::refresh);
                }

                @Override
                public void onLost(Network n) {
                    a.handler.post(NetworkStatus.this::refresh);
                }

                @Override
                public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
                    a.handler.post(NetworkStatus.this::refresh);
                }
            };
            try {
                cm.registerDefaultNetworkCallback(callback);
            } catch (Exception e) {
                callback = null;
            }
        }
        a.handler.removeCallbacks(tick);
        a.handler.post(tick);
    }

    void unregister() {
        a.handler.removeCallbacks(tick);
        if (callback != null && cm != null) {
            try {
                cm.unregisterNetworkCallback(callback);
            } catch (Exception ignored) {
            }
        }
        callback = null;
    }

    void openSettings() {
        Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS);
        if (i.resolveActivity(a.pm) == null) i = new Intent(Settings.ACTION_SETTINGS);
        a.startSafe(i);
    }

    /** Works out the connection and updates the icon and dot. */
    private void refresh() {
        if (cm == null) return;
        int state = OFFLINE;
        int bars = 0;
        try {
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities c = cm.getNetworkCapabilities(n);
                if (c == null || c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
                if (!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                if (!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) continue;
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    state = ETHERNET;
                    break;
                }
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    state = WIFI;
                    bars = barsFor(c);
                } else if (state == OFFLINE) {
                    state = OTHER;
                }
            }
        } catch (Exception e) {
            return; // couldn't tell, so keep what's showing
        }
        icon.set(state, bars);
        if (dot != null) dot.setVisibility(state == OFFLINE ? View.VISIBLE : View.GONE);
    }

    private static int barsFor(NetworkCapabilities c) {
        if (Build.VERSION.SDK_INT < 29) return 3;
        int rssi = c.getSignalStrength();
        if (rssi == Integer.MIN_VALUE) return 3;
        if (rssi >= -60) return 3;
        if (rssi >= -72) return 2;
        return 1;
    }

    /** The icon, drawn in code so there are no image files: network, Wi-Fi arcs, or a crossed-out globe. */
    private static final class NetIcon extends Drawable {
        private final int size;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int color = ICON_IDLE;
        private int state = ETHERNET;
        private int bars = 3;

        NetIcon(int size) {
            this.size = size;
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        void set(int state, int bars) {
            if (this.state == state && this.bars == bars) return;
            this.state = state;
            this.bars = bars;
            invalidateSelf();
        }

        @Override
        public void setTintList(ColorStateList tint) {
            color = tint != null ? tint.getDefaultColor() : ICON_IDLE;
            invalidateSelf();
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height());
            float left = b.centerX() - s / 2f;
            float top = b.centerY() - s / 2f;
            float cx = b.centerX();
            // Amber when offline, except when highlighted (dark on the white button)
            int c = (state == OFFLINE && color != DARK) ? VPN_OFF : color;
            paint.setColor(c);
            paint.setStrokeWidth(s * 0.11f);
            paint.setStyle(Paint.Style.STROKE);

            if (state == WIFI) {
                float baseY = top + s * 0.86f;
                float[] radii = {s * 0.24f, s * 0.47f, s * 0.70f};
                for (int i = 0; i < 3; i++) {
                    paint.setAlpha(i < bars ? 255 : 77);
                    float r = radii[i];
                    canvas.drawArc(new RectF(cx - r, baseY - r, cx + r, baseY + r), 225f, 90f, false, paint);
                }
                paint.setAlpha(255);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, baseY, s * 0.08f, paint);
            } else if (state == ETHERNET) {
                float sq = s * 0.28f;
                float topSqTop = top + s * 0.04f;
                float midY = top + s * 0.52f;
                float botTop = top + s * 0.68f;
                float lx = left + s * 0.04f + sq / 2f;
                float rx = left + s * 0.96f - sq / 2f;
                canvas.drawRect(cx - sq / 2f, topSqTop, cx + sq / 2f, topSqTop + sq, paint);
                canvas.drawRect(lx - sq / 2f, botTop, lx + sq / 2f, botTop + sq, paint);
                canvas.drawRect(rx - sq / 2f, botTop, rx + sq / 2f, botTop + sq, paint);
                canvas.drawLine(cx, topSqTop + sq, cx, midY, paint);
                canvas.drawLine(lx, midY, rx, midY, paint);
                canvas.drawLine(lx, midY, lx, botTop, paint);
                canvas.drawLine(rx, midY, rx, botTop, paint);
            } else {
                // A globe (connected some other way), crossed out when offline
                float cy = b.centerY();
                float r = s * 0.42f;
                canvas.drawCircle(cx, cy, r, paint);
                canvas.drawOval(new RectF(cx - r * 0.45f, cy - r, cx + r * 0.45f, cy + r), paint);
                canvas.drawLine(cx - r, cy, cx + r, cy, paint);
                if (state == OFFLINE) {
                    canvas.drawLine(left + s * 0.08f, top + s * 0.08f, left + s * 0.92f, top + s * 0.92f, paint);
                }
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
