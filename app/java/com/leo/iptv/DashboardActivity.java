package com.leo.iptv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class DashboardActivity extends Activity {
    private LinearLayout recentList, cwList;

    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_dashboard);
        recentList = (LinearLayout) findViewById(R.id.recent_list);
        cwList = (LinearLayout) findViewById(R.id.cw_list);
        addTile(R.id.tile_live, "Live TV", BrowseActivity.class, "LIVE");
        addTile(R.id.tile_movies, "Movies", BrowseActivity.class, "MOVIE");
        addTile(R.id.tile_series, "Series", BrowseActivity.class, "SERIES");
        addTile(R.id.tile_guide, "TV Guide", GuideActivity.class, null);
        addTile(R.id.tile_fav, "Favorites", BrowseActivity.class, "FAV");
        addTile(R.id.tile_search, "Search", SearchActivity.class, null);
        addTile(R.id.tile_settings, "Settings", SettingsActivity.class, null);
        findViewById(R.id.tile_switch).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.setCurrent(DashboardActivity.this, "");
                startActivity(new Intent(DashboardActivity.this, LoginActivity.class));
                finish();
            }
        });
    }
    protected void onResume() {
        super.onResume();
        Profile p = Prefs.current(this);
        if (p == null) { startActivity(new Intent(this, LoginActivity.class)); finish(); return; }
        ((TextView) findViewById(R.id.dash_name)).setText(p.name);
        ((TextView) findViewById(R.id.dash_account)).setText(
                p.accountLine != null && p.accountLine.length() > 0 ? p.accountLine
                        : (p.isXtream() ? "Xtream Codes" : "M3U playlist"));
        refreshRecent();
        refreshContinueWatching();
    }
    private void addTile(int id, String label, final Class<?> cls, final String mode) {
        Button t = (Button) findViewById(id);
        t.setText(label);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(DashboardActivity.this, cls);
                if (mode != null) i.putExtra("mode", mode);
                startActivity(i);
            }
        });
    }
    private void refreshRecent() {
        recentList.removeAllViews();
        java.util.List<Item> r = Prefs.recent(this);
        if (r.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("Nothing played yet.");
            t.setTextSize(16); t.setTextColor(0xFF9AA7BD);
            recentList.addView(t);
            return;
        }
        for (final Item it : r) {
            Button b = new Button(this);
            b.setText(it.name + "  •  " + it.group);
            b.setTextSize(16); b.setTextColor(0xFFFFFFFF);
            b.setBackgroundResource(R.drawable.button_selector);
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { Play.playOne(DashboardActivity.this, it); }
            });
            recentList.addView(b);
        }
    }
    // v2.2: Continue Watching on the home screen — movies and episodes
    // started but not finished, newest first, with a progress bar. Tapping
    // one resumes from the saved position.
    private void refreshContinueWatching() {
        cwList.removeAllViews();
        java.util.List<ProgressStore.Entry> entries = ProgressStore.load(this);
        java.util.Collections.sort(entries, new java.util.Comparator<ProgressStore.Entry>() {
            public int compare(ProgressStore.Entry a, ProgressStore.Entry b) {
                return a.updated < b.updated ? 1 : (a.updated > b.updated ? -1 : 0);
            }
        });
        if (entries.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("Nothing in progress yet.");
            t.setTextSize(16); t.setTextColor(0xFF9AA7BD);
            cwList.addView(t);
            return;
        }
        int n = Math.min(10, entries.size());
        for (int i = 0; i < n; i++) {
            final ProgressStore.Entry e = entries.get(i);
            final Item it = e.item;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            Button b = new Button(this);
            b.setText(it.name + "  •  " + ProgressStore.fmtTime(e.position)
                    + " / " + ProgressStore.fmtTime(e.duration)
                    + "  •  " + ProgressStore.fmtLeft(e.position, e.duration));
            b.setTextSize(16); b.setTextColor(0xFFFFFFFF);
            b.setBackgroundResource(R.drawable.button_selector);
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Play.playOneResume(DashboardActivity.this, it, e.position);
                }
            });
            android.widget.ProgressBar bar = new android.widget.ProgressBar(this, null,
                    android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgress(e.pct());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 10);
            lp.bottomMargin = 12;
            bar.setLayoutParams(lp);
            row.addView(b);
            row.addView(bar);
            cwList.addView(row);
        }
    }
}
