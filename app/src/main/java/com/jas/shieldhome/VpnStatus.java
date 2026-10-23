package com.jas.shieldhome;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.view.View;
import android.widget.Toast;

import org.json.JSONObject;

import static com.jas.shieldhome.Ui.VPN_OFF;
import static com.jas.shieldhome.Ui.VPN_ON;
import static com.jas.shieldhome.Ui.httpGet;
import static com.jas.shieldhome.Ui.str;

/** The VPN button: green or amber dot, and the city when connected (location by ipwho.is). */
final class VpnStatus {

    static final long EVERY_MS = 15 * 60 * 1000L;
    private static final String VPN_APP = "com.surfshark.vpnclient.android";

    private final MainActivity a;
    private final ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback callback;
    private Boolean lastState = null;
    private String lastWhere = null;
    private long lastIpFetch = 0;

    VpnStatus(MainActivity a) {
        this.a = a;
        this.cm = (ConnectivityManager) a.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    void openApp() {
        Intent i = a.pm.getLeanbackLaunchIntentForPackage(VPN_APP);
        if (i == null) i = a.pm.getLaunchIntentForPackage(VPN_APP);
        if (i != null) a.startSafe(i);
        else Toast.makeText(a, "Surfshark isn't installed.", Toast.LENGTH_LONG).show();
    }

    void register() {
        if (callback != null || cm == null) return;
        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network n) {
                a.handler.postDelayed(() -> update(true), 2000);
            }

            @Override
            public void onLost(Network n) {
                a.handler.postDelayed(() -> update(true), 2000);
            }

            @Override
            public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
                a.handler.postDelayed(() -> update(false), 2000);
            }
        };
        try {
            cm.registerDefaultNetworkCallback(callback);
        } catch (Exception e) {
            callback = null;
        }
    }

    void unregister() {
        if (callback == null || cm == null) return;
        try {
            cm.unregisterNetworkCallback(callback);
        } catch (Exception ignored) {
        }
        callback = null;
    }

    private boolean isOn() {
        if (cm == null) return false;
        try {
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities c = cm.getNetworkCapabilities(n);
                if (c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    void update(boolean force) {
        final boolean on = isOn();
        boolean changed = lastState == null || lastState != on;
        lastState = on;
        if (changed) {
            lastWhere = null;
            show(on, null);
        }
        long now = System.currentTimeMillis();
        if (!(changed || force || now - lastIpFetch > EVERY_MS)) return;
        lastIpFetch = now;
        new Thread(() -> {
            String where = null;
            try {
                JSONObject o = new JSONObject(httpGet("https://ipwho.is/", null));
                if (o.optBoolean("success", false)) {
                    String city = str(o, "city");
                    where = city.isEmpty() ? str(o, "country") : city;
                }
            } catch (Exception ignored) {
            }
            final String w = where;
            a.runOnUiThread(() -> show(on, w));
        }).start();
    }

    private void show(boolean on, String where) {
        if (where != null && !where.isEmpty()) lastWhere = where;
        if (a.vpnDotBg != null) a.vpnDotBg.setColor(on ? VPN_ON : VPN_OFF);
        if (a.vpnCityView != null) {
            if (on && lastWhere != null) {
                a.vpnCityView.setText(lastWhere);
                a.vpnCityView.setVisibility(View.VISIBLE);
            } else {
                a.vpnCityView.setVisibility(View.GONE);
            }
        }
    }
}
