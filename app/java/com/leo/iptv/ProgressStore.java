package com.leo.iptv;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

// v2.2: Continue Watching — playback progress for VOD items (movies and
// series episodes), stored as one JSON file under getFilesDir(). This is a
// SEPARATE store: it never touches the leo_iptv SharedPreferences or the
// Prefs keys (profiles, current, favorites, recent). Live channels are
// never tracked (nothing to resume). An entry is dropped when the item is
// watched to >= 90% (finished) or when less than 10 seconds were watched
// (an accidental tap). A corrupt file is treated as "no progress".
public class ProgressStore {
    private static final String FILE = "leo_progress.json";
    private static final int MAX_ENTRIES = 50;
    private static final long MIN_WATCH_MS = 10000;   // less than this: not worth resuming
    private static final int FINISHED_PCT = 90;       // >= this: finished, drop the entry

    public static class Entry {
        public Item item;
        public int position;    // ms watched
        public int duration;    // ms total, 0 = unknown
        public long updated;    // epoch ms of the last save
        public String profile;  // profile name the item belongs to ("" if unknown)

        public Entry(Item item, int position, int duration, long updated, String profile) {
            this.item = item; this.position = position; this.duration = duration;
            this.updated = updated; this.profile = profile == null ? "" : profile;
        }
        public int pct() {
            if (duration <= 0) return 0;
            int p = position * 100 / duration;
            return p < 0 ? 0 : (p > 100 ? 100 : p);
        }
        public JSONObject toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("item", item.toJson());
                o.put("position", position);
                o.put("duration", duration);
                o.put("updated", updated);
                o.put("profile", profile);
                return o;
            } catch (Exception e) { return new JSONObject(); }
        }
        public static Entry fromJson(JSONObject o) {
            if (o == null) return null;
            JSONObject io = o.optJSONObject("item");
            if (io == null) return null;
            Item it = Item.fromJson(io);
            return new Entry(it, o.optInt("position", 0), o.optInt("duration", 0),
                    o.optLong("updated", 0), o.optString("profile", ""));
        }
    }

    private static File file(Context c) { return new File(c.getFilesDir(), FILE); }

    public static List<Entry> load(Context c) {
        List<Entry> out = new ArrayList<Entry>();
        try {
            File f = file(c);
            if (!f.exists()) return out;
            FileInputStream in = new FileInputStream(f);
            byte[] b = new byte[(int) f.length()];
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) >= 0) n += r;
            in.close();
            JSONArray a = new JSONArray(new String(b, 0, n, "UTF-8"));
            for (int i = 0; i < a.length(); i++) {
                Entry e = Entry.fromJson(a.optJSONObject(i));
                if (e != null && e.item != null) out.add(e);
            }
        } catch (Exception ignored) {}
        return out;
    }
    private static void saveAll(Context c, List<Entry> list) {
        try {
            JSONArray a = new JSONArray();
            for (Entry e : list) a.put(e.toJson());
            FileOutputStream out = new FileOutputStream(file(c));
            out.write(a.toString().getBytes("UTF-8"));
            out.close();
        } catch (Exception ignored) {}
    }
    public static Entry get(Context c, Item it) {
        if (it == null) return null;
        for (Entry e : load(c)) if (e.item.key().equals(it.key())) return e;
        return null;
    }
    // Record progress. Live items and items with no duration are ignored.
    // Finishing (>= 90%) or barely starting (< 10s) removes the entry.
    public static void save(Context c, Item it, int position, int duration) {
        save(c, it, position, duration, "");
    }
    public static void save(Context c, Item it, int position, int duration, String profile) {
        if (it == null || it.isLive() || duration <= 0 || position <= 0) return;
        List<Entry> list = load(c);
        for (int i = list.size() - 1; i >= 0; i--)
            if (list.get(i).item.key().equals(it.key())) list.remove(i);
        if (position < MIN_WATCH_MS) { saveAll(c, list); return; }
        if (position * 100 / duration >= FINISHED_PCT) { saveAll(c, list); return; }
        list.add(0, new Entry(it, position, duration, System.currentTimeMillis(),
                profile == null ? "" : profile));
        while (list.size() > MAX_ENTRIES) list.remove(list.size() - 1);
        saveAll(c, list);
    }
    public static void remove(Context c, String key) {
        if (key == null) return;
        List<Entry> list = load(c);
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).item.key().equals(key)) { list.remove(i); changed = true; }
        }
        if (changed) saveAll(c, list);
    }
    public static void clear(Context c) {
        try { file(c).delete(); } catch (Exception ignored) {}
    }
    // Drop every entry that belongs to one profile (called when the profile
    // is deleted, so a removed account leaves no stale resume rows).
    public static void clearProfile(Context c, String profile) {
        if (profile == null) return;
        List<Entry> list = load(c);
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (profile.equals(list.get(i).profile)) { list.remove(i); changed = true; }
        }
        if (changed) saveAll(c, list);
    }
    // "1:05:30" or "12:34".
    public static String fmtTime(int ms) {
        int s = ms / 1000;
        int h = s / 3600;
        int m = (s % 3600) / 60;
        int sec = s % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, sec);
        return String.format("%d:%02d", m, sec);
    }
    // "45 min left" / "1:15:30 left".
    public static String fmtLeft(int position, int duration) {
        int left = duration - position;
        if (left <= 0) return "";
        return fmtTime(left) + " left";
    }
}
