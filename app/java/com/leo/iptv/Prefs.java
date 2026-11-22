package com.leo.iptv;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;

public class Prefs {
    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("leo_iptv", Context.MODE_PRIVATE);
    }
    public static List<Profile> profiles(Context c) {
        return Profile.listFromJson(sp(c).getString("profiles", ""));
    }
    public static void saveProfiles(Context c, List<Profile> ps) {
        sp(c).edit().putString("profiles", Profile.listToJson(ps)).apply();
    }
    public static void addOrReplaceProfile(Context c, Profile p) {
        List<Profile> ps = profiles(c);
        for (int i = ps.size() - 1; i >= 0; i--)
            if (ps.get(i).name.equals(p.name)) ps.remove(i);
        ps.add(p);
        saveProfiles(c, ps);
    }
    public static void deleteProfile(Context c, String name) {
        List<Profile> ps = profiles(c);
        for (int i = ps.size() - 1; i >= 0; i--)
            if (ps.get(i).name.equals(name)) ps.remove(i);
        saveProfiles(c, ps);
        if (name.equals(currentName(c))) sp(c).edit().remove("current").apply();
    }
    public static String currentName(Context c) { return sp(c).getString("current", ""); }
    public static void setCurrent(Context c, String name) {
        sp(c).edit().putString("current", name).apply();
    }
    public static Profile current(Context c) {
        String n = currentName(c);
        for (Profile p : profiles(c)) if (p.name.equals(n)) return p;
        return null;
    }
    public static List<Item> favorites(Context c) { return Item.listFromJson(sp(c).getString("favorites", "")); }
    public static boolean isFav(Context c, Item it) {
        for (Item f : favorites(c)) if (f.key().equals(it.key())) return true;
        return false;
    }
    public static void toggleFav(Context c, Item it) {
        List<Item> f = favorites(c);
        boolean found = false;
        for (int i = f.size() - 1; i >= 0; i--) {
            if (f.get(i).key().equals(it.key())) { f.remove(i); found = true; }
        }
        if (!found) f.add(it);
        sp(c).edit().putString("favorites", Item.listToJson(f)).apply();
    }
    public static void clearFavs(Context c) { sp(c).edit().remove("favorites").apply(); }
    public static List<Item> recent(Context c) { return Item.listFromJson(sp(c).getString("recent", "")); }
    public static void addRecent(Context c, Item it) {
        List<Item> r = recent(c);
        for (int i = r.size() - 1; i >= 0; i--) if (r.get(i).key().equals(it.key())) r.remove(i);
        r.add(0, it);
        while (r.size() > 8) r.remove(r.size() - 1);
        sp(c).edit().putString("recent", Item.listToJson(r)).apply();
    }
    public static void clearRecent(Context c) { sp(c).edit().remove("recent").apply(); }
    // v2.2: how many item cards fit across one row (3–6, default 4).
    // Fewer per row = bigger cards and posters.
    public static int itemsPerRow(Context c) {
        int n = sp(c).getInt("items_per_row", 4);
        if (n < 3) return 3;
        if (n > 6) return 6;
        return n;
    }
    public static void setItemsPerRow(Context c, int n) {
        if (n < 3) n = 3;
        if (n > 6) n = 6;
        sp(c).edit().putInt("items_per_row", n).apply();
    }
}
