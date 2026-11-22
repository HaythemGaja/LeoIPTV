package com.leo.iptv;

import android.app.Activity;
import android.content.Intent;
import java.util.List;

public class Play {
    public static void play(Activity a, List<Item> items, int pos) {
        playWithResume(a, items, pos, 0);
    }
    // v2.2: resume a VOD item from a saved position (ms). 0 = from the start.
    public static void playWithResume(Activity a, List<Item> items, int pos, int resumeMs) {
        Profile p = Prefs.current(a);
        Intent i = new Intent(a, PlayerActivity.class);
        i.putExtra("items", Item.listToJson(items));
        i.putExtra("pos", pos);
        i.putExtra("resume", resumeMs);
        if (p != null) i.putExtra("profile", p.name);
        a.startActivity(i);
    }
    public static void playOne(Activity a, Item it) {
        java.util.List<Item> one = new java.util.ArrayList<Item>();
        one.add(it);
        play(a, one, 0);
    }
    public static void playOneResume(Activity a, Item it, int resumeMs) {
        java.util.List<Item> one = new java.util.ArrayList<Item>();
        one.add(it);
        playWithResume(a, one, 0, resumeMs);
    }
}
