package com.leo.iptv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// All provider access. Every call runs on a background thread; callbacks are
// invoked on that thread, so callers must post UI work to the main thread.
// Parsing is defensive (opt* everywhere): a bad/missing field yields an
// empty value or a readable error, never a crash.
public class Api {
    public interface Cb<T> { void done(T result, String error); }
    public static class Cat { public String id, name; public Cat(String i, String n) { id = i; name = n; } }
    public static class Epg {
        public String now = "", next = "";
        public long nowStart = 0, nowEnd = 0; // epoch seconds; 0 = unknown
    }

    // v3.0: one User-Agent for API calls and for the stream itself (the
    // player sends it as a header, since some providers reject bare streams).
    public static final String USER_AGENT = "LeoIPTV/3.0";

    // Session cache for TV Guide now/next, so each channel is fetched once.
    public static final Map<String, Epg> epgCache = new HashMap<String, Epg>();

    public static String fetch(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(25000);
        c.setRequestProperty("User-Agent", USER_AGENT);
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in == null) throw new Exception("No response from server (HTTP " + code + ")");
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = r.readLine()) != null) sb.append(line).append("\n");
        r.close();
        if (code >= 400) throw new Exception("Server returned HTTP " + code);
        return sb.toString();
    }

    public static String normalizeHost(String h) {
        if (h == null) return "";
        h = h.trim();
        while (h.endsWith("/")) h = h.substring(0, h.length() - 1);
        if (h.length() > 0 && !h.startsWith("http://") && !h.startsWith("https://")) h = "http://" + h;
        return h;
    }
    public static String fmtExp(String exp) {
        try {
            long s = Long.parseLong(exp);
            if (s <= 0) return "";
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(s * 1000L));
        } catch (Exception e) { return exp; }
    }
    private static String q(Profile p) throws Exception {
        return "username=" + URLEncoder.encode(p.user, "UTF-8")
                + "&password=" + URLEncoder.encode(p.pass, "UTF-8");
    }
    private static String xtreamUrl(Profile p, String action) throws Exception {
        return p.host + "/player_api.php?" + q(p) + (action == null ? "" : "&action=" + action);
    }
    private static void bg(Runnable r) { new Thread(r).start(); }

    // Validate an Xtream login. Returns account line on success, error otherwise.
    public static void validateXtream(final Profile p, final Cb<String> cb) {
        bg(new Runnable() { public void run() {
            try {
                JSONObject root = new JSONObject(fetch(xtreamUrl(p, null)));
                JSONObject info = root.optJSONObject("user_info");
                if (info == null) { cb.done(null, "Login failed: provider did not return account info."); return; }
                String status = info.optString("status", "");
                String msg = info.optString("message", "");
                String exp = info.optString("exp_date", "");
                String max = info.optString("max_connections", "");
                if (!"Active".equalsIgnoreCase(status)) {
                    String e = "Login failed: account status is " + (status.length() > 0 ? status : "unknown") + ".";
                    if (msg.length() > 0) e += " " + msg;
                    if (exp.length() > 0) {
                        try {
                            long s = Long.parseLong(exp);
                            if (s > 0 && s * 1000L < System.currentTimeMillis())
                                e += " Account expired on " + fmtExp(exp) + ".";
                            else if (s > 0) e += " Expiry: " + fmtExp(exp) + ".";
                        } catch (Exception ignored) {}
                    }
                    cb.done(null, e); return;
                }
                String line = "Status: Active";
                if (exp.length() > 0 && !"0".equals(exp)) line += "  •  Expires: " + fmtExp(exp);
                if (max.length() > 0) line += "  •  Max connections: " + max;
                cb.done(line, null);
            } catch (Exception e) {
                cb.done(null, "Login failed: " + safeMsg(e));
            }
        }});
    }

    // Validate an M3U URL: must contain at least one #EXTINF entry.
    public static void validateM3u(final String url, final Cb<List<Item>> cb) {
        bg(new Runnable() { public void run() {
            try {
                List<Item> items = PlaylistParser.parseM3U(fetch(url));
                if (items.isEmpty())
                    cb.done(null, "This playlist contains no entries. Check the URL and try again.");
                else cb.done(items, null);
            } catch (Exception e) {
                cb.done(null, "Could not load playlist: " + safeMsg(e));
            }
        }});
    }

    public static void loadM3u(final String url, final Cb<List<Item>> cb) {
        bg(new Runnable() { public void run() {
            try { cb.done(PlaylistParser.parseM3U(fetch(url)), null); }
            catch (Exception e) { cb.done(new ArrayList<Item>(), "Could not load playlist: " + safeMsg(e)); }
        }});
    }

    public static void categories(final Profile p, final String kind, final Cb<List<Cat>> cb) {
        bg(new Runnable() { public void run() {
            try {
                String action = "LIVE".equals(kind) ? "get_live_categories"
                        : "MOVIE".equals(kind) ? "get_vod_categories" : "get_series_categories";
                JSONArray a = new JSONArray(fetch(xtreamUrl(p, action)));
                List<Cat> out = new ArrayList<Cat>();
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o == null) continue;
                    out.add(new Cat(o.optString("category_id"), o.optString("category_name", "Unnamed")));
                }
                cb.done(out, null);
            } catch (Exception e) { cb.done(new ArrayList<Cat>(), "Could not load categories: " + safeMsg(e)); }
        }});
    }

    // Streams of one kind, optionally restricted to one category.
    public static void streams(final Profile p, final String kind, final String catId, final Cb<List<Item>> cb) {
        bg(new Runnable() { public void run() {
            try {
                String action = "LIVE".equals(kind) ? "get_live_streams"
                        : "MOVIE".equals(kind) ? "get_vod_streams" : "get_series";
                JSONArray a = new JSONArray(fetch(xtreamUrl(p, action)));
                List<Item> out = new ArrayList<Item>();
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o == null) continue;
                    String cid = o.optString("category_id");
                    if (catId != null && catId.length() > 0 && !catId.equals(cid)) continue;
                    String sid = "SERIES".equals(kind) ? o.optString("series_id") : o.optString("stream_id");
                    if (sid.length() == 0) continue;
                    String name = o.optString("name", "Unnamed");
                    String ext = o.optString("container_extension", "mp4");
                    String url;
                    if ("LIVE".equals(kind)) url = p.host + "/live/" + p.user + "/" + p.pass + "/" + sid + ".ts";
                    else if ("MOVIE".equals(kind)) url = p.host + "/movie/" + p.user + "/" + p.pass + "/" + sid + "." + ext;
                    else url = "";
                    Item it = new Item(name, o.optString("category_name", ""), kind, url, "");
                    it.id = sid; it.catId = cid;
                    it.logo = o.optString("stream_icon", o.optString("cover", ""));
                    it.rating = o.optString("rating", "");
                    it.plot = o.optString("plot", "");
                    it.year = o.optString("releasedate", o.optString("year", ""));
                    it.duration = o.optString("duration", "");
                    it.ext = ext;
                    it.added = parseAdded(o.optString("added", ""));
                    out.add(it);
                }
                cb.done(out, null);
            } catch (Exception e) { cb.done(new ArrayList<Item>(), "Could not load content: " + safeMsg(e)); }
        }});
    }

    // Full seasons/episodes for one series. Episode URLs use the series endpoint.
    public static void seriesInfo(final Profile p, final String seriesId, final Cb<Item> cb) {
        bg(new Runnable() { public void run() {
            try {
                JSONObject root = new JSONObject(fetch(xtreamUrl(p, "get_series_info&series_id="
                        + URLEncoder.encode(seriesId, "UTF-8"))));
                JSONObject info = root.optJSONObject("info");
                Item series = new Item(info != null ? info.optString("name", "Series") : "Series", "", "SERIES", "", "");
                if (info != null) {
                    series.plot = info.optString("plot", "");
                    series.rating = info.optString("rating", "");
                    series.year = info.optString("releaseDate", info.optString("year", ""));
                    series.logo = info.optString("cover", "");
                }
                JSONObject eps = root.optJSONObject("episodes");
                if (eps != null) {
                    java.util.Iterator<String> seasons = eps.keys();
                    java.util.List<String> keys = new ArrayList<String>();
                    while (seasons.hasNext()) keys.add(seasons.next());
                    java.util.Collections.sort(keys);
                    for (String season : keys) {
                        JSONArray arr = eps.optJSONArray(season);
                        if (arr == null) continue;
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject ep = arr.optJSONObject(i);
                            if (ep == null) continue;
                            String eid = ep.optString("id");
                            if (eid.length() == 0) continue;
                            String ext = ep.optString("container_extension", "mp4");
                            String title = ep.optString("title", "");
                            if (title.length() == 0) title = "Episode " + ep.optString("episode_num", String.valueOf(i + 1));
                            Item e = new Item("S" + season + "E" + ep.optString("episode_num", String.valueOf(i + 1))
                                    + " — " + title, "", "SERIES",
                                    p.host + "/series/" + p.user + "/" + p.pass + "/" + eid + "." + ext, "");
                            e.id = eid; e.ext = ext;
                            series.episodes.add(e);
                        }
                    }
                }
                cb.done(series, null);
            } catch (Exception e) { cb.done(null, "Could not load series details: " + safeMsg(e)); }
        }});
    }

    // Real short EPG for one live stream. Empty now/next = provider has none.
    public static void shortEpg(final Profile p, final String streamId, final Cb<Epg> cb) {
        final String key = p.name + "|" + streamId;
        Epg cached = epgCache.get(key);
        if (cached != null) { cb.done(cached, null); return; }
        bg(new Runnable() { public void run() {
            Epg epg = new Epg();
            try {
                JSONObject root = new JSONObject(fetch(xtreamUrl(p, "get_short_epg&stream_id="
                        + URLEncoder.encode(streamId, "UTF-8"))));
                JSONArray arr = root.optJSONArray("epg_listings");
                if (arr != null && arr.length() > 0) {
                    JSONObject now = arr.optJSONObject(0);
                    if (now != null) {
                        // v3.0: Xtream returns EPG titles base64-encoded on
                        // most providers; decode when it looks encoded.
                        epg.now = decodeTitle(now.optString("title", ""));
                        epg.nowStart = now.optLong("start_timestamp", 0);
                        epg.nowEnd = now.optLong("stop_timestamp", 0);
                    }
                    if (arr.length() > 1) {
                        JSONObject next = arr.optJSONObject(1);
                        if (next != null) epg.next = decodeTitle(next.optString("title", ""));
                    }
                }
            } catch (Exception ignored) {}
            epgCache.put(key, epg);
            cb.done(epg, null);
        }});
    }

    // v3.0: providers usually base64-encode EPG titles. Decode when the
    // string looks like base64 and decodes to printable text; otherwise
    // keep it as-is.
    private static String decodeTitle(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() == 0) return "";
        if (!t.matches("[A-Za-z0-9+/=\\r\\n]+")) return t;
        try {
            byte[] raw = android.util.Base64.decode(t, android.util.Base64.DEFAULT);
            String d = new String(raw, "UTF-8").trim();
            for (int i = 0; i < d.length(); i++) {
                char c = d.charAt(i);
                if (c < 0x20 && c != '\n') return t;
            }
            return d.length() > 0 ? d : t;
        } catch (Exception e) {
            return t;
        }
    }

    // v2.1: provider "added" is a Unix timestamp string (seconds). 0 when
    // the provider gives no date — never fabricate one.
    private static long parseAdded(String s) {
        if (s == null || s.length() == 0) return 0;
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }
    public static String safeMsg(Exception e) {
        String m = e.getMessage();
        return (m == null || m.length() == 0) ? e.toString() : m;
    }
}
