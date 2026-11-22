package com.leo.iptv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
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

// v3.0 Browse: one screen per mode (Live TV / Movies / Series / Favorites).
// Slim sidebar (nav + categories), a hero that follows the focused card,
// the paged main row, then Continue Watching / Recently Added / Favorites.
// Data is cached per profile + mode + category: cached data shows first,
// Refresh re-fetches from the provider. Search filters the current category.
public class BrowseActivity extends Activity {
    private static final int ROW_PAGE = 24;
    private static final int RECENT_MAX = 20;
    private static final int FAV_MAX = 20;
    private static final int CW_MAX = 20;

    private Profile profile;
    private String mode;
    private ListView sideList;
    private LinearLayout rowContainer, recentRow, favRow, cwRow;
    private TextView statusView, rowLabel, mainLabel;
    private TextView heroKicker, heroName, heroInfo, heroEpg;
    private TextView recentLabel, favLabel, cwLabel;
    private ImageView backdrop;
    private android.widget.Button playBtn, favBtn, searchBtn, refreshBtn;
    private final Map<String, ProgressStore.Entry> progressMap = new HashMap<String, ProgressStore.Entry>();

    private List<Api.Cat> cats = new ArrayList<Api.Cat>();
    private List<String> catNames = new ArrayList<String>();
    private List<Item> baseItems = new ArrayList<Item>();
    private List<Item> shown = new ArrayList<Item>();
    private List<Item> allM3u = new ArrayList<Item>();
    private String currentCatId = null;
    private String currentCatName = "All";
    private String currentQuery = "";
    private boolean lastFromCache = false;
    private boolean m3uFromCache = false;
    private int rowStart = 0;
    private int selPos = 0;
    private Item heroItem;

    private static final int ROW_NAV = 0, ROW_HEADER = 1, ROW_CAT = 2;
    private static class SideRow {
        int kind; String label; String navMode; String catId; String catName;
        SideRow(int k, String l) { kind = k; label = l; }
    }
    private List<SideRow> sideRows = new ArrayList<SideRow>();
    private BaseAdapter sideAdapter;

    private String modeLabel() {
        return "FAV".equals(mode) ? "Favorites"
                : "LIVE".equals(mode) ? "Live TV"
                : "MOVIE".equals(mode) ? "Movies" : "Series";
    }

    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_browse);
        profile = Prefs.current(this);
        mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "LIVE";
        statusView = (TextView) findViewById(R.id.b_status);
        rowLabel = (TextView) findViewById(R.id.b_row_label);
        mainLabel = (TextView) findViewById(R.id.b_main_label);
        heroKicker = (TextView) findViewById(R.id.b_hero_kicker);
        heroName = (TextView) findViewById(R.id.b_hero_name);
        heroInfo = (TextView) findViewById(R.id.b_hero_info);
        heroEpg = (TextView) findViewById(R.id.b_hero_epg);
        backdrop = (ImageView) findViewById(R.id.b_backdrop);
        recentLabel = (TextView) findViewById(R.id.b_recent_label);
        recentRow = (LinearLayout) findViewById(R.id.b_recent_row);
        favLabel = (TextView) findViewById(R.id.b_fav_label);
        favRow = (LinearLayout) findViewById(R.id.b_fav_row);
        cwLabel = (TextView) findViewById(R.id.b_cw_label);
        cwRow = (LinearLayout) findViewById(R.id.b_cw_row);
        rowContainer = (LinearLayout) findViewById(R.id.b_row);
        sideList = (ListView) findViewById(R.id.b_sidebar);
        playBtn = (android.widget.Button) findViewById(R.id.b_play);
        favBtn = (android.widget.Button) findViewById(R.id.b_hero_fav);
        searchBtn = (android.widget.Button) findViewById(R.id.b_search);
        refreshBtn = (android.widget.Button) findViewById(R.id.b_refresh);
        if (profile == null) { finish(); return; }
        Ui.bindClock((TextView) findViewById(R.id.b_clock));
        if ("FAV".equals(mode)) refreshBtn.setVisibility(View.GONE);
        searchBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openSearchDialog(); }
        });
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { refreshCurrent(); }
        });
        playBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (heroItem != null) onItemActivated(heroItem, indexInShown(heroItem));
            }
        });
        favBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (heroItem == null) return;
                Prefs.toggleFav(BrowseActivity.this, heroItem);
                updateFavBtn();
                buildExtraRows();
                if ("FAV".equals(mode)) {
                    baseItems = Prefs.favorites(BrowseActivity.this);
                    finishLoad(false);
                }
            }
        });
        sideList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                handleSideClick(pos);
            }
        });
        buildSidebar();
        if ("FAV".equals(mode)) {
            baseItems = Prefs.favorites(this);
            finishLoad(false);
        } else {
            statusView.setText("Loading…");
            if (profile.isXtream()) loadXtreamCats();
            else loadM3u();
        }
    }
    protected void onResume() {
        super.onResume();
        if ("FAV".equals(mode) && profile != null) {
            baseItems = Prefs.favorites(this);
            finishLoad(false);
        }
    }

    // ---- Sidebar ----
    private void buildSidebar() {
        sideRows.clear();
        addNavRow("Home", "HOME");
        addNavRow("Live TV", "LIVE");
        addNavRow("Movies", "MOVIE");
        addNavRow("Series", "SERIES");
        addNavRow("Favorites", "FAV");
        addNavRow("TV Guide", "GUIDE");
        if (!"FAV".equals(mode)) {
            sideRows.add(new SideRow(ROW_HEADER, "CATEGORIES"));
            addCatRow("All categories", null, "All");
            if (profile.isXtream()) {
                for (Api.Cat c : cats) addCatRow(c.name, c.id, c.name);
            } else {
                for (String g : catNames) {
                    if ("All categories".equals(g)) continue;
                    addCatRow(g, null, g);
                }
            }
        }
        sideAdapter = new BaseAdapter() {
            public int getCount() { return sideRows.size(); }
            public Object getItem(int p) { return sideRows.get(p); }
            public long getItemId(int p) { return p; }
            public int getViewTypeCount() { return 2; }
            public int getItemViewType(int p) {
                return sideRows.get(p).kind == ROW_HEADER ? 1 : 0;
            }
            public boolean isEnabled(int p) { return sideRows.get(p).kind != ROW_HEADER; }
            public View getView(int pos, View cv, ViewGroup parent) {
                SideRow r = sideRows.get(pos);
                if (r.kind == ROW_HEADER) {
                    TextView h = new TextView(BrowseActivity.this);
                    h.setText(r.label);
                    h.setTextSize(12);
                    h.setTextColor(0xFF8C99B8);
                    h.setPadding(Ui.dp(BrowseActivity.this, 12), Ui.dp(BrowseActivity.this, 16),
                            Ui.dp(BrowseActivity.this, 12), Ui.dp(BrowseActivity.this, 6));
                    return h;
                }
                View v = cv;
                if (v == null || "header".equals(v.getTag()))
                    v = getLayoutInflater().inflate(R.layout.sidebar_row, parent, false);
                v.setTag("row");
                TextView tile = (TextView) v.findViewById(R.id.side_tile);
                TextView name = (TextView) v.findViewById(R.id.side_name);
                if (r.kind == ROW_NAV) {
                    if ("HOME".equals(r.navMode)) { tile.setText("H"); tile.setBackgroundColor(0xFF00C2A8); }
                    else if ("LIVE".equals(r.navMode)) { tile.setText("TV"); tile.setBackgroundColor(0xFFFF5252); }
                    else if ("MOVIE".equals(r.navMode)) { tile.setText("M"); tile.setBackgroundColor(0xFFE50914); }
                    else if ("SERIES".equals(r.navMode)) { tile.setText("S"); tile.setBackgroundColor(0xFF0891B2); }
                    else if ("FAV".equals(r.navMode)) { tile.setText("★"); tile.setBackgroundColor(0xFFFFC107); }
                    else { tile.setText("G"); tile.setBackgroundColor(0xFF7C3AED); }
                } else {
                    Item tmp = new Item("", r.label, "LIVE", "", "");
                    tile.setText(tmp.initials());
                    tile.setBackgroundColor(tmp.color());
                }
                name.setText(r.label);
                boolean current = r.kind == ROW_NAV && r.navMode.equals(mode);
                name.setTextColor(current ? 0xFF00C2A8 : 0xFFFFFFFF);
                name.setTypeface(null, current ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.NORMAL);
                return v;
            }
        };
        sideList.setAdapter(sideAdapter);
        for (int i = 0; i < sideRows.size(); i++) {
            SideRow r = sideRows.get(i);
            if (r.kind != ROW_CAT) continue;
            boolean match = currentCatId != null ? currentCatId.equals(r.catId)
                    : (r.catId == null && currentCatName.equals(r.catName));
            if (match) { sideList.setItemChecked(i, true); break; }
        }
    }
    private void addNavRow(String label, String navMode) {
        SideRow r = new SideRow(ROW_NAV, label);
        r.navMode = navMode;
        sideRows.add(r);
    }
    private void addCatRow(String label, String catId, String catName) {
        SideRow r = new SideRow(ROW_CAT, label);
        r.catId = catId; r.catName = catName;
        sideRows.add(r);
    }
    private void handleSideClick(int pos) {
        if (pos < 0 || pos >= sideRows.size()) return;
        SideRow r = sideRows.get(pos);
        if (r.kind == ROW_HEADER) return;
        if (r.kind == ROW_NAV) {
            if ("HOME".equals(r.navMode)) {
                startActivity(new Intent(this, HomeActivity.class));
                finish();
                return;
            }
            if ("GUIDE".equals(r.navMode)) {
                startActivity(new Intent(this, GuideActivity.class));
                return;
            }
            if (r.navMode.equals(mode)) return;
            Intent i = new Intent(this, BrowseActivity.class);
            i.putExtra("mode", r.navMode);
            startActivity(i);
            finish();
            return;
        }
        currentCatId = r.catId;
        currentCatName = r.catName;
        currentQuery = "";
        sideList.setItemChecked(pos, true);
        loadItems(false);
    }

    // ---- Loading (cache first, provider second) ----
    private String catKey() { return currentCatId != null ? currentCatId : "all"; }
    private void loadXtreamCats() {
        List<Api.Cat> cached = DataCache.loadCats(this, profile.name, mode);
        if (cached != null) {
            cats = cached;
            buildSidebar();
            loadItems(false);
            return;
        }
        Api.categories(profile, mode, new Api.Cb<List<Api.Cat>>() {
            public void done(final List<Api.Cat> cs, final String err) {
                runOnUiThread(new Runnable() { public void run() {
                    if (err != null) { statusView.setText(err); return; }
                    cats = cs;
                    DataCache.saveCats(BrowseActivity.this, profile.name, mode, cs);
                    buildSidebar();
                    loadItems(false);
                }});
            }
        });
    }
    private void loadM3u() {
        List<Item> cached = DataCache.loadM3u(this, profile.name);
        if (cached != null) {
            m3uFromCache = true;
            allM3u = cached;
            deriveGroups();
            buildSidebar();
            loadItems(false);
            return;
        }
        Api.loadM3u(profile.m3uUrl, new Api.Cb<List<Item>>() {
            public void done(final List<Item> items, final String err) {
                runOnUiThread(new Runnable() { public void run() {
                    if (err != null) { statusView.setText(err); return; }
                    m3uFromCache = false;
                    allM3u = items;
                    DataCache.saveM3u(BrowseActivity.this, profile.name, items);
                    deriveGroups();
                    buildSidebar();
                    loadItems(false);
                }});
            }
        });
    }
    private void deriveGroups() {
        List<String> groups = new ArrayList<String>();
        groups.add("All categories");
        for (Item it : allM3u) {
            if (!it.type.equals(mode)) continue;
            if (!groups.contains(it.group)) groups.add(it.group);
        }
        catNames = groups;
    }
    private void loadItems(final boolean forceRefresh) {
        statusView.setText(forceRefresh ? "Refreshing…" : "Loading…");
        if (!profile.isXtream()) {
            List<Item> out = new ArrayList<Item>();
            for (Item it : allM3u) {
                if (!it.type.equals(mode)) continue;
                if (!"All".equals(currentCatName) && !it.group.equals(currentCatName)) continue;
                out.add(it);
            }
            baseItems = out;
            finishLoad(m3uFromCache);
            return;
        }
        if (!forceRefresh) {
            List<Item> cached = DataCache.loadItems(this, profile.name, mode, catKey());
            if (cached != null) {
                baseItems = cached;
                finishLoad(true);
                return;
            }
        }
        Api.streams(profile, mode, currentCatId, new Api.Cb<List<Item>>() {
            public void done(final List<Item> items, final String err) {
                runOnUiThread(new Runnable() { public void run() {
                    if (err != null) { statusView.setText(err); return; }
                    baseItems = items;
                    DataCache.saveItems(BrowseActivity.this, profile.name, mode, catKey(), items);
                    finishLoad(false);
                }});
            }
        });
    }
    private void finishLoad(boolean fromCache) {
        lastFromCache = fromCache;
        applyFilter(currentQuery, true);
    }
    private void refreshCurrent() {
        if ("FAV".equals(mode)) return;
        statusView.setText("Refreshing…");
        if (!profile.isXtream()) {
            Api.loadM3u(profile.m3uUrl, new Api.Cb<List<Item>>() {
                public void done(final List<Item> items, final String err) {
                    runOnUiThread(new Runnable() { public void run() {
                        if (err != null) { statusView.setText(err); return; }
                        m3uFromCache = false;
                        allM3u = items;
                        DataCache.saveM3u(BrowseActivity.this, profile.name, items);
                        deriveGroups();
                        buildSidebar();
                        loadItems(false);
                    }});
                }
            });
            return;
        }
        if (currentCatId == null) {
            Api.categories(profile, mode, new Api.Cb<List<Api.Cat>>() {
                public void done(final List<Api.Cat> cs, final String err) {
                    runOnUiThread(new Runnable() { public void run() {
                        if (err != null) { statusView.setText(err); return; }
                        cats = cs;
                        DataCache.saveCats(BrowseActivity.this, profile.name, mode, cs);
                        buildSidebar();
                        loadItems(true);
                    }});
                }
            });
        } else {
            loadItems(true);
        }
    }

    // ---- Search (filters the current category, by name or group) ----
    private void openSearchDialog() {
        final String before = currentQuery;
        final EditText input = new EditText(this);
        input.setText(currentQuery);
        input.setHint("Search " + modeLabel() + "…");
        input.setTextSize(20);
        input.setSingleLine(true);
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { applyFilter(s.toString(), false); }
        });
        new AlertDialog.Builder(this)
                .setTitle("Search " + modeLabel())
                .setView(input)
                .setPositiveButton("Done", null)
                .setNeutralButton("Clear search", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { applyFilter("", false); }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { applyFilter(before, false); }
                })
                .show();
    }
    private void applyFilter(String query, boolean takeFocus) {
        currentQuery = query == null ? "" : query;
        progressMap.clear();
        for (ProgressStore.Entry e : ProgressStore.load(this))
            progressMap.put(e.item.key(), e);
        shown.clear();
        if (currentQuery.length() == 0) {
            shown.addAll(baseItems);
        } else {
            String q = currentQuery.toLowerCase();
            for (Item it : baseItems) {
                if (it.name.toLowerCase().contains(q) || it.group.toLowerCase().contains(q))
                    shown.add(it);
            }
        }
        rowStart = 0;
        selPos = 0;
        buildRow();
        buildExtraRows();
        if (!shown.isEmpty()) {
            updateHero(shown.get(0));
            if (takeFocus) focusCard(0);
        } else {
            clearHero();
        }
        updateStatus();
    }

    // ---- Main row (horizontal, windowed 24 cards per page) ----
    private void buildRow() {
        rowContainer.removeAllViews();
        if (shown.isEmpty()) { updateStatus(); return; }
        int end = Math.min(rowStart + ROW_PAGE, shown.size());
        final int windowEnd = end;
        for (int i = rowStart; i < end; i++) {
            final int pos = i;
            final Item it = shown.get(i);
            View card = Cards.build(this, it, profile, progressMap.get(it.key()),
                    new Cards.Listener() {
                        public void onFocus(Item f) {
                            int p = indexInShown(f);
                            if (p >= 0) selPos = p;
                            updateHero(f);
                        }
                        public void onClick(Item f) { onItemActivated(f, pos); }
                    });
            card.setOnKeyListener(new View.OnKeyListener() {
                public boolean onKey(View v, int keyCode, KeyEvent e) {
                    if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && pos == windowEnd - 1
                            && windowEnd < shown.size()) {
                        rowStart = windowEnd;
                        buildRow();
                        focusCard(0);
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && pos == rowStart && rowStart > 0) {
                        rowStart = Math.max(0, rowStart - ROW_PAGE);
                        buildRow();
                        focusCard(rowContainer.getChildCount() - 1);
                        return true;
                    }
                    return false;
                }
            });
            rowContainer.addView(card);
        }
        updateStatus();
    }
    private void focusCard(int index) {
        if (rowContainer.getChildCount() > index)
            rowContainer.getChildAt(index).requestFocus();
    }
    private int indexInShown(Item it) {
        if (it == null) return -1;
        for (int i = 0; i < shown.size(); i++)
            if (it.key().equals(shown.get(i).key())) return i;
        return -1;
    }

    // ---- Continue Watching / Recently Added / Favorites rows ----
    private void buildExtraRows() {
        if ("FAV".equals(mode)) {
            favLabel.setVisibility(View.GONE);
            ((LinearLayout) favRow.getParent()).setVisibility(View.GONE);
        } else {
            favLabel.setVisibility(View.VISIBLE);
            ((LinearLayout) favRow.getParent()).setVisibility(View.VISIBLE);
            List<Item> favs = new ArrayList<Item>();
            String q = currentQuery.toLowerCase();
            for (Item f : Prefs.favorites(this)) {
                if (!f.type.equals(mode)) continue;
                if (!"All".equals(currentCatName) && !f.group.equals(currentCatName)) continue;
                if (currentQuery.length() > 0
                        && !f.name.toLowerCase().contains(q) && !f.group.toLowerCase().contains(q))
                    continue;
                favs.add(f);
            }
            favRow.removeAllViews();
            int n = Math.min(FAV_MAX, favs.size());
            for (int i = 0; i < n; i++) {
                final Item it = favs.get(i);
                favRow.addView(Cards.build(this, it, profile, progressMap.get(it.key()),
                        new Cards.Listener() {
                            public void onFocus(Item f) { updateHero(f); }
                            public void onClick(Item f) { onItemActivated(f, indexInShown(f)); }
                        }));
            }
        }
        List<Item> dated = new ArrayList<Item>();
        for (Item it : shown) if (it.added > 0) dated.add(it);
        if (dated.isEmpty()) {
            recentLabel.setVisibility(View.GONE);
            ((LinearLayout) recentRow.getParent()).setVisibility(View.GONE);
        } else {
            Collections.sort(dated, new Comparator<Item>() {
                public int compare(Item a, Item b) {
                    return a.added < b.added ? 1 : (a.added > b.added ? -1 : 0);
                }
            });
            recentLabel.setVisibility(View.VISIBLE);
            ((LinearLayout) recentRow.getParent()).setVisibility(View.VISIBLE);
            recentRow.removeAllViews();
            int n = Math.min(RECENT_MAX, dated.size());
            for (int i = 0; i < n; i++) {
                final Item it = dated.get(i);
                recentRow.addView(Cards.build(this, it, profile, progressMap.get(it.key()),
                        new Cards.Listener() {
                            public void onFocus(Item f) { updateHero(f); }
                            public void onClick(Item f) { onItemActivated(f, indexInShown(f)); }
                        }));
            }
        }
        buildContinueWatching();
    }

    private void buildContinueWatching() {
        String q = currentQuery.toLowerCase();
        java.util.Set<String> favKeys = null;
        if ("FAV".equals(mode)) {
            favKeys = new java.util.HashSet<String>();
            for (Item f : Prefs.favorites(this)) favKeys.add(f.key());
        }
        List<ProgressStore.Entry> cw = new ArrayList<ProgressStore.Entry>();
        for (ProgressStore.Entry e : progressMap.values()) {
            Item it = e.item;
            if (it.isLive()) continue;
            if ("FAV".equals(mode)) {
                if (favKeys == null || !favKeys.contains(it.key())) continue;
            } else if (!it.type.equals(mode)) continue;
            if (!"All".equals(currentCatName) && !it.group.equals(currentCatName)) continue;
            if (currentQuery.length() > 0
                    && !it.name.toLowerCase().contains(q) && !it.group.toLowerCase().contains(q))
                continue;
            cw.add(e);
        }
        Collections.sort(cw, new Comparator<ProgressStore.Entry>() {
            public int compare(ProgressStore.Entry a, ProgressStore.Entry b) {
                return a.updated < b.updated ? 1 : (a.updated > b.updated ? -1 : 0);
            }
        });
        cwRow.removeAllViews();
        if (cw.isEmpty()) {
            cwLabel.setVisibility(View.GONE);
            ((LinearLayout) cwRow.getParent()).setVisibility(View.GONE);
            return;
        }
        cwLabel.setVisibility(View.VISIBLE);
        ((LinearLayout) cwRow.getParent()).setVisibility(View.VISIBLE);
        int n = Math.min(CW_MAX, cw.size());
        for (int i = 0; i < n; i++) {
            final ProgressStore.Entry e = cw.get(i);
            cwRow.addView(Cards.build(this, e.item, profile, e, new Cards.Listener() {
                public void onFocus(Item f) { updateHero(f); }
                public void onClick(Item f) {
                    Play.playOneResume(BrowseActivity.this, f, e.position);
                }
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
        heroName.setText("Nothing here yet from this provider.");
        heroInfo.setText("");
        heroEpg.setText("");
        backdrop.setVisibility(View.GONE);
        updateFavBtn();
    }
    private void updateFavBtn() {
        favBtn.setText(heroItem != null && Prefs.isFav(this, heroItem) ? "★ Favorite" : "☆ Favorite");
    }
    private void updateStatus() {
        rowLabel.setText(modeLabel() + ("All".equals(currentCatName) ? "" : "  •  " + currentCatName));
        mainLabel.setText("All".equals(currentCatName) ? "All categories" : currentCatName);
        if (baseItems.isEmpty() && shown.isEmpty()) {
            statusView.setText("Nothing here yet from this provider.");
            return;
        }
        String s = shown.size() + " items";
        if (lastFromCache) s += "  •  saved data";
        if (currentQuery.length() > 0) s += "  •  Search: '" + currentQuery + "'";
        if (shown.size() > ROW_PAGE)
            s += "  •  " + (rowStart + 1) + "–" + Math.min(rowStart + ROW_PAGE, shown.size())
                    + " of " + shown.size();
        statusView.setText(s);
    }

    // ---- Activation ----
    private void onItemActivated(final Item it, int pos) {
        if (it.isLive() || "FAV".equals(mode)) {
            if (!it.isLive()) {
                ProgressStore.Entry e = progressMap.get(it.key());
                if (e != null && e.duration > 0) {
                    if (pos < 0 || pos >= shown.size()) Play.playOneResume(this, it, e.position);
                    else Play.playWithResume(this, shown, pos, e.position);
                    return;
                }
            }
            if (pos < 0 || pos >= shown.size()) Play.playOne(this, it);
            else Play.play(this, shown, pos);
            return;
        }
        if ("MOVIE".equals(mode)) { showMovieDetail(it); return; }
        if (profile.isXtream()) {
            statusView.setText("Loading series…");
            Api.seriesInfo(profile, it.id, new Api.Cb<Item>() {
                public void done(final Item series, final String err) {
                    runOnUiThread(new Runnable() { public void run() {
                        updateStatus();
                        if (err != null || series == null) {
                            new AlertDialog.Builder(BrowseActivity.this)
                                    .setTitle(it.name)
                                    .setMessage(err != null ? err : "No episode data from this provider.")
                                    .setPositiveButton("Close", null).show();
                            return;
                        }
                        if (series.episodes.isEmpty()) {
                            new AlertDialog.Builder(BrowseActivity.this)
                                    .setTitle(series.name)
                                    .setMessage(series.plot.length() > 0 ? series.plot
                                            : "No episodes from this provider.")
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
                        new AlertDialog.Builder(BrowseActivity.this)
                                .setTitle(series.name + (series.plot.length() > 0 ? "\n" + series.plot : ""))
                                .setItems(names, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface d, int which) {
                                        ProgressStore.Entry e = progressMap.get(eps.get(which).key());
                                        if (e != null && e.duration > 0)
                                            Play.playWithResume(BrowseActivity.this, eps, which, e.position);
                                        else Play.play(BrowseActivity.this, eps, which);
                                    }
                                })
                                .setNegativeButton("Close", null).show();
                    }});
                }
            });
        } else {
            Play.playOne(this, it);
        }
    }
    private void showMovieDetail(final Item it) {
        String msg = "";
        if (it.rating.length() > 0) msg += "Rating: " + it.rating + "\n";
        if (it.year.length() > 0) msg += "Year: " + it.year + "\n";
        if (it.duration.length() > 0) msg += "Duration: " + it.duration + "\n";
        if (it.plot.length() > 0) msg += "\n" + it.plot;
        if (msg.length() == 0) msg = "No details from this provider.";
        final ProgressStore.Entry e = progressMap.get(it.key());
        final String playLabel = (e != null && e.duration > 0)
                ? "Resume from " + ProgressStore.fmtTime(e.position) : "Play";
        if (e != null && e.duration > 0)
            msg += "\n\nResume from " + ProgressStore.fmtTime(e.position)
                    + " of " + ProgressStore.fmtTime(e.duration) + ".";
        new AlertDialog.Builder(this)
                .setTitle(it.name)
                .setMessage(msg)
                .setPositiveButton(playLabel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        if (e != null && e.duration > 0)
                            Play.playOneResume(BrowseActivity.this, it, e.position);
                        else Play.playOne(BrowseActivity.this, it);
                    }
                })
                .setNeutralButton(Prefs.isFav(BrowseActivity.this, it) ? "Remove favorite" : "Add favorite",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                Prefs.toggleFav(BrowseActivity.this, it);
                            }
                        })
                .setNegativeButton("Close", null).show();
    }
}
