package com.leo.iptv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

// In-memory logo cache. Loads asynchronously, never blocks the UI;
// on failure the caller keeps its initials placeholder.
// v2.0: bounded LRU (200 entries) so a huge channel list cannot grow the
// cache without limit and slow the app down.
public class LogoCache {
    private static final int MAX_ENTRIES = 200;
    private static final Map<String, Bitmap> cache =
            new java.util.LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
                protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) {
                    return size() > MAX_ENTRIES;
                }
            };

    public static void load(final String url, final ImageView view) {
        if (url == null || url.length() == 0) return;
        Bitmap b = cache.get(url);
        if (b != null) { view.setImageBitmap(b); return; }
        view.setTag(url);
        new Thread(new Runnable() { public void run() {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                InputStream in = c.getInputStream();
                final Bitmap bmp = BitmapFactory.decodeStream(in);
                in.close();
                if (bmp != null) {
                    cache.put(url, bmp);
                    view.post(new Runnable() { public void run() {
                        if (url.equals(view.getTag())) view.setImageBitmap(bmp);
                    }});
                }
            } catch (Exception ignored) {}
        }}).start();
    }
}
