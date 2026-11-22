package com.leo.iptv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

// One playable entry from the user's own provider. No bundled content.
public class Item {
    public String name, group, type, url, desc;
    public String id = "", catId = "", logo = "", rating = "", plot = "";
    public String year = "", duration = "", ext = "";
    // v2.1: Unix timestamp (seconds) of when the provider added this item.
    // 0 = provider gave no date (live streams and M3U entries usually have
    // none). Only items with added > 0 appear in the Recently Added row.
    public long added = 0;
    public List<Item> episodes = new ArrayList<Item>();

    public Item(String name, String group, String type, String url, String desc) {
        this.name = name; this.group = group; this.type = type; this.url = url; this.desc = desc;
    }
    public String key() { return name + "\n" + url; }
    public boolean isLive() { return "LIVE".equals(type); }

    public String initials() {
        String[] p = name.replaceAll("[^A-Za-z0-9 ]", "").trim().split("\\s+");
        if (p.length == 0 || p[0].length() == 0) return "?";
        if (p.length == 1) return p[0].substring(0, Math.min(2, p[0].length())).toUpperCase();
        return ("" + p[0].charAt(0) + p[1].charAt(0)).toUpperCase();
    }
    public int color() {
        String g = group.toLowerCase();
        if (g.contains("news")) return 0xFF2563EB;
        if (g.contains("sport")) return 0xFF16A34A;
        if (g.contains("movie")) return 0xFFE50914;
        if (g.contains("kid")) return 0xFFD97706;
        if (g.contains("doc")) return 0xFF7C3AED;
        if (g.contains("music")) return 0xFFDB2777;
        if (g.contains("series")) return 0xFF0891B2;
        return 0xFF00C2A8;
    }
    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("name", name); o.put("group", group); o.put("type", type);
            o.put("url", url); o.put("desc", desc);
            o.put("id", id); o.put("catId", catId); o.put("logo", logo);
            o.put("rating", rating); o.put("plot", plot); o.put("year", year);
            o.put("duration", duration); o.put("ext", ext);
            o.put("added", added);
            JSONArray ep = new JSONArray();
            for (Item e : episodes) {
                JSONObject eo = new JSONObject();
                eo.put("name", e.name); eo.put("url", e.url); eo.put("group", e.group);
                eo.put("type", e.type); eo.put("id", e.id);
                ep.put(eo);
            }
            o.put("episodes", ep);
            return o;
        } catch (Exception e) { return new JSONObject(); }
    }
    public static Item fromJson(JSONObject o) {
        Item it = new Item(o.optString("name", "?"), o.optString("group", ""),
                o.optString("type", "LIVE"), o.optString("url", ""), o.optString("desc", ""));
        it.id = o.optString("id", ""); it.catId = o.optString("catId", "");
        it.logo = o.optString("logo", ""); it.rating = o.optString("rating", "");
        it.plot = o.optString("plot", ""); it.year = o.optString("year", "");
        it.duration = o.optString("duration", ""); it.ext = o.optString("ext", "");
        it.added = o.optLong("added", 0);
        JSONArray ep = o.optJSONArray("episodes");
        if (ep != null) {
            for (int i = 0; i < ep.length(); i++) {
                JSONObject eo = ep.optJSONObject(i);
                if (eo != null) {
                    Item e = new Item(eo.optString("name"), it.group, "SERIES", eo.optString("url"), "");
                    e.id = eo.optString("id", "");
                    it.episodes.add(e);
                }
            }
        }
        return it;
    }
    public static String listToJson(List<Item> items) {
        JSONArray a = new JSONArray();
        for (Item it : items) a.put(it.toJson());
        return a.toString();
    }
    public static List<Item> listFromJson(String s) {
        List<Item> out = new ArrayList<Item>();
        if (s == null || s.length() == 0) return out;
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) out.add(fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {}
        return out;
    }
}
