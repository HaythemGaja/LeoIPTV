package com.leo.iptv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// v3.0 Home: the hub. Slim sidebar (profile + nav), a hero that follows the
// focused card, and rows: Continue Watching, Recently Added, Favorites.
// Rows come from on-device data first (progress, favorites, cache); the
// Recently Added row fills in from the provider in the background when the
// cache has nothing.
public class HomeActivity extends Activity {
    private Profile profile;
    private ListView navList;
    private TextView statusView, heroKicker, heroName, heroInfo, heroEpg;
    private TextView cwLabel, recentLabel, favLabel;
    private ImageView backdrop;
    private LinearLayout cwRow, recentRow, favRow;
    private android.widget.Button playBtn, favBtn;
    private Item heroItem;
    private final Map<String, ProgressStore.Entry> progressMap = new HashMap<String, ProgressStore.Entry>();

    private static class NavRow { String label, tile; int color; }
    private final List<NavRow> navRows = new ArrayList<NavRow>();

    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_home);
        profile = Prefs.current(this);
        if (profile == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        navList = (ListView) findViewById(R.id.h_nav);
        statusView = (TextView) findViewById(R.id.h_status);
        heroKicker = (TextView) findViewById(R.id.h_hero_kicker);
        heroName = (TextView) findViewById(R.id.h_hero_name);
        heroInfo = (TextView) findViewById(R.id.h_hero_info);
        heroEpg = (TextView) findViewById(R.id.h_hero_epg);
        backdrop = (ImageView) findViewById(R.id.h_backdrop);
        cwLabel = (TextView) findViewById(R.id.h_cw_label);
        recentLabel = (TextView) findViewById(R.id.h_recent_label);
        favLabel = (TextView) findViewById(R.id.h_fav_label);
        cwRow = (LinearLayout) findViewById(R.id.h_cw_row);
        recentRow = (LinearLayout) findViewById(R.id.h_recent_row);
        favRow = (LinearLayout) findViewById(R.id.h_fav_row);
        playBtn = (android.widget.Button) findViewById(R.id.h_hero_play);
        favBtn = (android.widget.Button) findViewById(R.id.h_hero_fav);
        ((TextView) findViewById(R.id.h_profile)).setText(profile.name);
        Ui.bindClock((TextView) findViewById(R.id.h_clock));

        addNav("Search", "Q", 0xFF00C2A8);
        addNav("Live TV", "TV", 0xFFFF5252);
        addNav("Movies", "M", 0xFFE50914);
        addNav("Series", "S", 0xFF0891B2);
        addNav("Favorites", "★", 0xFFFFC107);
        addNav("TV Guide", "G", 0xFF7C3AED);
        addNav("Settings", "⚙", 0xFF64748B);
        addNav("Switch user", "⇄", 0xFF64748B);
        navList.setAdapter(new BaseAdapter() {
            public int getCount() { return navRows.size(); }
            public Object getItem(int p) { return navRows.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int pos, View cv, ViewGroup parent) {
                View v = cv;
                if (v == null) v = getLayoutInflater().inflate(R.layout.sidebar_row, parent, false);
                NavRow r = navRows.get(pos);
                TextView tile = (TextView) v.findViewById(R.id.side_tile);
                tile.setText(r.tile);
                tile.setBackgroundColor(r.color);
                ((TextView) v.findViewById(R.id.side_name)).setText(r.label);
                return v;
            }
        });
        navList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                switch (pos) {
                    case 0: startActivity(new Intent(HomeActivity.this, SearchActivity.class)); break;
                    case 1: openBrowse("LIVE"); break;
                    case 2: openBrowse("MOVIE"); break;
                    case 3: openBrowse("SERIES"); break;
                    case 4: openBrowse("FAV"); break;
                    case 5: startActivity(new Intent(HomeActivity.this, GuideActivity.class)); break;
                    case 6: startActivity(new Intent(HomeActivity.this, SettingsActivity.class)); break;
                    case 7:
                        Prefs.setCurrent(HomeActivity.this, "");
                        startActivity(new Intent(HomeActivity.this, LoginActivity.class));
                        finish();
                        break;
                }
            }
        });
        playBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { playHero(); }
        });
        favBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (heroItem == null) return;
                Prefs.toggleFav(HomeActivity.this, heroItem);
                updateFavBtn();
                buildRows();
            }
        });
    }

    private void addNav(String label, String tile, int color) {
        NavRow r = new NavRow();
        r.label = label; r.tile = tile; r.color = color;
        navRows.add(r);
    }
    private void openBrowse(String mode) {
        Intent i = new Intent(this, BrowseActivity.class);
        i.putExtra("mode", mode);
        startActivity(i);
    }

    protected void onResume() {
        super.onResume();
        profile = Prefs.current(this);
        if (profile == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        progressMap.clear();
        for (ProgressStore.Entry e : ProgressStore.load(this))
            progressMap.put(e.item.key(), e);
        buildRows();
        loadRecentRow();
    }

    // ---- Rows ----
    private void buildRows() {
        // Continue Watching, newest first.
        List<ProgressStore.Entry> cw = new ArrayList<ProgressStore.Entry>();
        for (ProgressStore.Entry e : progressMap.values())
            if (!e.item.isLive() && e.duration > 0) cw.add(e);
        Collections.sort(cw, new Comparator<ProgressStore.Entry>() {
            public int compare(ProgressStore.Entry a, ProgressStore.Entry b) {
                return a.updated < b.updated ? 1 : (a.updated > b.updated ? -1 : 0);
            }
        });
        cwRow.removeAllViews();
        if (cw.isEmpty()) {
            cwLabel.setVisibility(View.GONE);
            ((LinearLayout) cwRow.getParent()).setVisibility(View.GONE);
        } else {
            cwLabel.setVisibility(View.VISIBLE);
            ((LinearLayout) cwRow.getParent()).setVisibility(View.VISIBLE);
            int n = Math.min(20, cw.size());
            for (int i = 0; i < n; i++) {
                final ProgressStore.Entry e = cw.get(i);
                cwRow.addView(Cards.build(this, e.item, profile, e, new Cards.Listener() {
                    public void onFocus(Item it) { updateHero(it); }
                    public void onClick(Item it) { Play.playOneResume(HomeActivity.this, it, e.position); }
                }));
            }
        }
        // Favorites.
        List<Item> favs = Prefs.favorites(this);
        favRow.removeAllViews();
        if (favs.isEmpty()) {
            favLabel.setVisibility(View.GONE);
            ((LinearLayout) favRow.getParent()).setVisibility(View.GONE);
        } else {
            favLabel.setVisibility(View.VISIBLE);
            ((LinearLayout) favRow.getParent()).setVisibility(View.VISIBLE);
            int n = Math.min(20, favs.size());
            for (int i = 0; i < n; i++) {
                final Item it = favs.get(i);
                favRow.addView(Cards.build(this, it, profile, progressMap.get(it.key()),
                        new Cards.Listener() {
                            public void onFocus(Item f) { updateHero(f); }
                            public void onClick(Item f) { activate(f); }
                        }));
            }
        }
        // Hero: first continue-watching item, else first favorite.
        if (heroItem == null) {
            if (!cw.isEmpty()) updateHero(cw.get(0).item);
            else if (!favs.isEmpty()) updateHero(favs.get(0));
            else clearHero();
        }
        statusView.setText(favs.size() + " favorites  •  " + cw.size() + " in progress");
    }

    // Recently Added: dated items from the cached (or freshly fetched)
    // MOVIE + SERIES lists, newest first. Hidden when nothing has a date.
    private void loadRecentRow() {
        List<Item> movies = profile.isXtream()
                ? DataCache.loadItems(this, profile.name, "MOVIE", "all") : null;
        List<Item> series = profile.isXtream()
                ? DataCache.loadItems(this, profile.name, "SERIES", "all") : null;
        if (!profile.isXtream()) {
            List<Item> all = DataCache.loadM3u(this, profile.name);
            if (all != null) {
                List<Item> dated = new ArrayList<Item>();
                for (Item it : all) if (it.added > 0) dated.add(it);
                fillRecentRow(dated);
            }
            return;
        }
        if (movies != null || series != null) {
            List<Item> dated = new ArrayList<Item>();
            if (movies != null) for (Item it : movies) if (it.added > 0) dated.add(it);
            if (series != null) for (Item it : series) if (it.added > 0) dated.add(it);
            fillRecentRow(dated);
            if (movies != null && series != null) return;
        }
        // Fetch what is missing, once, in the background.
        if (movies == null) fetchKind("MOVIE");
        if (series == null) fetchKind("SERIES");
    }
    private void fetchKind(final String kind) {
        Api.streams(profile, kind, null, new Api.Cb<List<Item>>() {
            public void done(final List<Item> items, final String err) {
                if (err == null) DataCache.saveItems(HomeActivity.this, profile.name, kind, "all", items);
                runOnUiThread(new Runnable() { public void run() { loadRecentRow(); } });
            }
        });
    }
    private void fillRecentRow(List<Item> dated) {
        Collections.sort(dated, new Comparator<Item>() {
            public int compare(Item a, Item b) {
                return a.added < b.added ? 1 : (a.added > b.added ? -1 : 0);
            }
        });
        recentRow.removeAllViews();
        if (dated.isEmpty()) {
            recentLabel.setVisibility(View.GONE);
            ((LinearLayout) recentRow.getParent()).setVisibility(View.GONE);
            return;
        }
        recentLabel.setVisibility(View.VISIBLE);
        ((LinearLayout) recentRow.getParent()).setVisibility(View.VISIBLE);
        int n = Math.min(20, dated.size());
        for (int i = 0; i < n; i++) {
            final Item it = dated.get(i);
            recentRow.addView(Cards.build(this, it, profile, progressMap.get(it.key()),
                    new Cards.Listener() {
                        public void onFocus(Item f) { updateHero(f); }
                        public void onClick(Item f) { activate(f); }
                    }));
        }
    }

    // ---- Hero ----
    private void updateHero(final Item it) {
        heroItem = it;
        heroKicker.setText((it.isLive() ? "LIVE TV" : ("MOVIE".equals(it.type) ? "MOVIE" : "SERIES"))
                + (it.group.length() > 0 ? "  •  " + it.group : ""));
        heroName.setText(it.name);
        String info = "";
        if (it.rating.length() > 0) info += "★ " + it.rating;
        if (it.year.length() > 0) info += (info.length() > 0 ? "  •  " : "") + it.year;
        if (it.duration.length() > 0) info += (info.length() > 0 ? "  •  " : "") + it.duration;
        heroInfo.setText(info);
        if (it.logo != null && it.logo.length() > 0) {
            backdrop.setVisibility(View.VISIBLE);
            backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LogoCache.load(it.logo, backdrop);
        } else {
            backdrop.setVisibility(View.GONE);
        }
        if (it.isLive() && profile.isXtream() && it.id.length() > 0) {
            heroEpg.setText("Loading guide…");
            Api.shortEpg(profile, it.id, new Api.Cb<Api.Epg>() {
                public void done(final Api.Epg e, String err) {
                    runOnUiThread(new Runnable() { public void run() {
                        if (heroItem != it) return;
                        if (e.now.length() > 0)
                            heroEpg.setText("Now: " + e.now + (e.next.length() > 0 ? "  •  Next: " + e.next : ""));
                        else heroEpg.setText("No guide data from this provider");
                    }});
                }
            });
        } else if (it.isLive()) {
            heroEpg.setText("No guide data from this provider");
        } else {
            heroEpg.setText(it.plot.length() > 0 ? it.plot : "");
        }
        ProgressStore.Entry e = progressMap.get(it.key());
        playBtn.setText(e != null && e.duration > 0 && !it.isLive()
                ? "▶ Resume " + ProgressStore.fmtTime(e.position) : "▶ Play");
        updateFavBtn();
    }
    private void clearHero() {
        heroItem = null;
        heroKicker.setText("");
        heroName.setText("Welcome to Leo IPTV");
        heroInfo.setText("");
        heroEpg.setText("Pick Live TV, Movies or Series from the menu to start watching.");
        backdrop.setVisibility(View.GONE);
        playBtn.setVisibility(View.GONE);
        favBtn.setVisibility(View.GONE);
    }
    private void updateFavBtn() {
        playBtn.setVisibility(View.VISIBLE);
        favBtn.setVisibility(View.VISIBLE);
        favBtn.setText(heroItem != null && Prefs.isFav(this, heroItem) ? "★ Favorite" : "☆ Favorite");
    }
    private void playHero() {
        if (heroItem == null) return;
        activate(heroItem);
    }

    // ---- Activation ----
    private void activate(final Item it) {
        ProgressStore.Entry e = progressMap.get(it.key());
        if (it.isLive()) { Play.playOne(this, it); return; }
        if ("SERIES".equals(it.type) && profile.isXtream()) { openSeries(it); return; }
        if (e != null && e.duration > 0) Play.playOneResume(this, it, e.position);
        else Play.playOne(this, it);
    }
    private void openSeries(final Item it) {
        statusView.setText("Loading series…");
        Api.seriesInfo(profile, it.id, new Api.Cb<Item>() {
            public void done(final Item series, final String err) {
                runOnUiThread(new Runnable() { public void run() {
                    statusView.setText("");
                    if (err != null || series == null || series.episodes.isEmpty()) {
                        new AlertDialog.Builder(HomeActivity.this)
                                .setTitle(it.name)
                                .setMessage(err != null ? err
                                        : (series != null && series.plot.length() > 0 ? series.plot
                                                : "No episodes from this provider."))
                                .setPositiveButton("Close", null).show();
                        return;
                    }
                    final List<Item> eps = series.episodes;
                    String[] names = new String[eps.size()];
                    for (int i = 0; i < eps.size(); i++) {
                        ProgressStore.Entry e = progressMap.get(eps.get(i).key());
                        names[i] = eps.get(i).name
                                + (e != null && e.duration > 0
                                        ? "  •  Resume " + ProgressStore.fmtTime(e.position) : "");
                    }
                    new AlertDialog.Builder(HomeActivity.this)
                            .setTitle(series.name)
                            .setItems(names, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface d, int which) {
                                    ProgressStore.Entry e = progressMap.get(eps.get(which).key());
                                    if (e != null && e.duration > 0)
                                        Play.playWithResume(HomeActivity.this, eps, which, e.position);
                                    else Play.play(HomeActivity.this, eps, which);
                                }
                            })
                            .setNegativeButton("Close", null).show();
                }});
            }
        });
    }
}
