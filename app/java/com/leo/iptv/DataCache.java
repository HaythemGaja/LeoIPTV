package com.leo.iptv;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

// v2.1: per-profile, per-category data cache. Categories + item lists are
// stored as JSON files under getFilesDir()/leo_cache/. The cache is a
// SEPARATE store — it never touches the leo_iptv SharedPreferences or the
// Prefs keys (profiles, current, favorites, recent). A corrupt cache entry
// is treated as "no cache", so the caller fetches from the provider.
public class DataCache {
    private static String safe(String s) {
        if (s == null) return "";
        String t = s.replaceAll("[^A-Za-z0-9._-]", "_");
        if (t.length() > 60) t = t.substring(0, 60);
        return t;
    }
    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "leo_cache");
        if (!d.exists()) d.mkdirs();
        return d;
    }
    private static File file(Context c, String profile, String kind, String key) {
        return new File(dir(c), safe(profile) + "__" + safe(kind) + "__" + safe(key) + ".json");
    }
    public static void save(Context c, String profile, String kind, String key, String json) {
        if (json == null) return;
        try {
            FileOutputStream out = new FileOutputStream(file(c, profile, kind, key));
            out.write(json.getBytes("UTF-8"));
            out.close();
        } catch (Exception ignored) {}
    }
    public static String load(Context c, String profile, String kind, String key) {
        try {
            File f = file(c, profile, kind, key);
            if (!f.exists()) return null;
            FileInputStream in = new FileInputStream(f);
            byte[] b = new byte[(int) f.length()];
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) >= 0) n += r;
            in.close();
            return new String(b, 0, n, "UTF-8");
        } catch (Exception e) { return null; }
    }
    // Categories: JSON array of {id, name}.
    public static void saveCats(Context c, String profile, String mode, List<Api.Cat> cats) {
        try {
            JSONArray a = new JSONArray();
            for (Api.Cat cat : cats) {
                JSONObject o = new JSONObject();
                o.put("id", cat.id); o.put("name", cat.name);
                a.put(o);
            }
            save(c, profile, "cats", mode, a.toString());
        } catch (Exception ignored) {}
    }
    public static List<Api.Cat> loadCats(Context c, String profile, String mode) {
        String s = load(c, profile, "cats", mode);
        if (s == null) return null;
        try {
            JSONArray a = new JSONArray(s);
            List<Api.Cat> out = new ArrayList<Api.Cat>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                out.add(new Api.Cat(o.optString("id", ""), o.optString("name", "Unnamed")));
            }
            return out;
        } catch (Exception e) { return null; }
    }
    // Items for one category. Returns null when there is no cache or the
    // cached JSON is corrupt; an empty list is a real result (a category
    // with no items) and is returned as-is.
    public static List<Item> loadItems(Context c, String profile, String mode, String catKey) {
        String s = load(c, profile, "items", mode + "|" + catKey);
        if (s == null) return null;
        try { new JSONArray(s); } catch (Exception e) { return null; }
        return Item.listFromJson(s);
    }
    public static void saveItems(Context c, String profile, String mode, String catKey, List<Item> items) {
        save(c, profile, "items", mode + "|" + catKey, Item.listToJson(items));
    }
    // Whole M3U playlist (all types) for one profile.
    public static List<Item> loadM3u(Context c, String profile) {
        String s = load(c, profile, "m3u", "all");
        if (s == null) return null;
        try { new JSONArray(s); } catch (Exception e) { return null; }
        return Item.listFromJson(s);
    }
    public static void saveM3u(Context c, String profile, List<Item> items) {
        save(c, profile, "m3u", "all", Item.listToJson(items));
    }
    // Delete every cached file for one profile (called when the profile is deleted).
    public static void clearProfile(Context c, String profile) {
        File d = dir(c);
        String prefix = safe(profile) + "__";
        File[] files = d.listFiles();
        if (files == null) return;
        for (File f : files) if (f.getName().startsWith(prefix)) f.delete();
    }
}
