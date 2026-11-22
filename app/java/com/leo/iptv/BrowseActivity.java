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
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// v2.1: Haystack-style layout — left sidebar (search / refresh / mode nav /
// categories), top horizontal row of cards, large hero below, then Recently
// Added and Favorites rows. Data is cached per profile + mode + category:
// if saved data exists it shows immediately; otherwise it is fetched and
// saved for next time. Refresh re-fetches the current category (and the
// category list while "All categories" is selected). Search filters the
// current category's items by name/group.
public class BrowseActivity extends Activity {
    private static final int ROW_PAGE = 24;
    private static final int RECENT_MAX = 20;
    private static final int FAV_MAX = 20;   // v2.2: cap the Favorites row
    private static final int CW_MAX = 20;    // v2.2: cap Continue Watching

    private Profile profile;
    private String mode;
    private ListView sideList;
    private LinearLayout rowContainer, recentRow, favRow, cwRow;
    private HorizontalScrollView recentScroll, favScroll, cwScroll;
    private TextView statusView, rowLabel, modePill, heroName, heroInfo, heroEpg, heroTile;
    private TextView recentLabel, favLabel, cwLabel;
    private ImageView heroLogo;
    private Button playBtn, searchBtn, refreshBtn;
    // v2.2: in-progress items, keyed by item key, loaded once per filter.
    private Map<String, ProgressStore.Entry> progressMap = new HashMap<String, ProgressStore.Entry>();
    // v2.0: keys of short-EPG requests already in flight, so scrolling the
    // row does not fire the same channel's EPG request over and over.
    private Set<String> epgInFlight = new HashSet<String>();

    private List<Api.Cat> cats = new ArrayList<Api.Cat>();
    private List<String> catNames = new ArrayList<String>();
    private List<Item> baseItems = new ArrayList<Item>();  // full current category
    private List<Item> shown = new ArrayList<Item>();      // filtered view (search)
    private List<Item> allM3u = new ArrayList<Item>();
    private String currentCatId = null;
    private String currentCatName = "All";
    private String currentQuery = "";
    private boolean lastFromCache = false;
    private boolean m3uFromCache = false;
    private int rowStart = 0;
    private int selPos = 0;

    // Sidebar rows.
    private static final int ROW_NAV = 0, ROW_HEADER = 1, ROW_CAT = 2;
    private static class SideRow {
        int kind; String label; String navMode; String catId; String catName;
        SideRow(int k, String l) { kind = k; label = l; }
    }
    private List<SideRow> sideRows = new ArrayList<SideRow>();
    private SideAdapter sideAdapter;

    private String modeLabel() {
        return "FAV".equals(mode) ? "Favorites"
                : "LIVE".equals(mode) ? "Live TV"
                : "MOVIE".equals(mode) ? "Movies" : "Series";
    }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_browse);
        profile = Prefs.current(this);
        mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "LIVE";
        statusView = (TextView) findViewById(R.id.b_status);
        rowLabel = (TextView) findViewById(R.id.b_row_label);
        modePill = (TextView) findViewById(R.id.b_mode_pill);
        heroName = (TextView) findViewById(R.id.b_hero_name);
        heroInfo = (TextView) findViewById(R.id.b_hero_info);
        heroEpg = (TextView) findViewById(R.id.b_hero_epg);
        heroTile = (TextView) findViewById(R.id.b_hero_tile);
        heroLogo = (ImageView) findViewById(R.id.b_hero_logo);
        recentLabel = (TextView) findViewById(R.id.b_recent_label);
        recentScroll = (HorizontalScrollView) findViewById(R.id.b_recent_scroll);
        recentRow = (LinearLayout) findViewById(R.id.b_recent_row);
        favLabel = (TextView) findViewById(R.id.b_fav_label);
        favScroll = (HorizontalScrollView) findViewById(R.id.b_fav_scroll);
        favRow = (LinearLayout) findViewById(R.id.b_fav_row);
        cwLabel = (TextView) findViewById(R.id.b_cw_label);
        cwScroll = (HorizontalScrollView) findViewById(R.id.b_cw_scroll);
        cwRow = (LinearLayout) findViewById(R.id.b_cw_row);
        rowContainer = (LinearLayout) findViewById(R.id.b_row);
        sideList = (ListView) findViewById(R.id.b_sidebar);
        playBtn = (Button) findViewById(R.id.b_play);
        searchBtn = (Button) findViewById(R.id.b_search);
        refreshBtn = (Button) findViewById(R.id.b_refresh);
        if (profile == null) { finish(); return; }
        modePill.setText(modeLabel());
        if ("FAV".equals(mode)) refreshBtn.setVisibility(View.GONE);
        searchBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openSearchDialog(); }
        });
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { refreshCurrent(); }
        });
        playBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!shown.isEmpty() && selPos >= 0 && selPos < shown.size())
                    onItemActivated(shown.get(selPos), selPos);
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
        if ("FAV".equals(mode)) {
            baseItems = Prefs.favorites(this);
            finishLoad(false);
        }
    }

    // ---- Sidebar ----
    private void buildSidebar() {
        sideRows.clear();
        addNavRow("Live TV", "LIVE");
        addNavRow("Movies", "MOVIE");
        addNavRow("Series", "SERIES");
        addNavRow("Favorites", "FAV");
        addNavRow("TV Guide", "GUIDE");
        if (!"FAV".equals(mode)) {
            SideRow h = new SideRow(ROW_HEADER, "Categories");
            sideRows.add(h);
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
        sideAdapter = new SideAdapter();
        sideList.setAdapter(sideAdapter);
        // Highlight the selected category row (choiceMode singleChoice).
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
        // Category row.
        currentCatId = r.catId;
        currentCatName = r.catName;
        currentQuery = "";
        sideList.setItemChecked(pos, true);
        loadItems(false);
    }
    private class SideAdapter extends BaseAdapter {
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
                h.setText("CATEGORIES");
                h.setTextSize(14);
                h.setTextColor(0xFF9AA7BD);
                h.setPadding(dp(12), dp(16), dp(12), dp(6));
                return h;
            }
            View v = cv;
            if (v == null) v = getLayoutInflater().inflate(R.layout.sidebar_row, parent, false);
            TextView tile = (TextView) v.findViewById(R.id.side_tile);
            TextView name = (TextView) v.findViewById(R.id.side_name);
            String initials;
            int color;
            if (r.kind == ROW_NAV) {
                if ("LIVE".equals(r.navMode)) { initials = "TV"; color = 0xFFFF5252; }
                else if ("MOVIE".equals(r.navMode)) { initials = "M"; color = 0xFFE50914; }
                else if ("SERIES".equals(r.navMode)) { initials = "S"; color = 0xFF0891B2; }
                else if ("FAV".equals(r.navMode)) { initials = "★"; color = 0xFFFFC107; }
                else { initials = "G"; color = 0xFF7C3AED; }
            } else {
                Item tmp = new Item("", r.label, "LIVE", "", "");
                initials = tmp.initials();
                color = tmp.color();
            }
            tile.setText(initials);
            tile.setBackgroundColor(color);
            name.setText(r.label);
            // The current mode's nav row reads in the accent color.
            if (r.kind == ROW_NAV && r.navMode.equals(mode)) {
                name.setTextColor(0xFF00C2A8);
                name.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                name.setTextColor(0xFFFFFFFF);
                name.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
            return v;
        }
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
            // M3U: the whole playlist is cached; filtering is local.
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
    // Refresh: re-fetch the current category from the provider and re-save
    // it. While "All categories" is selected, the category list is refreshed
    // too, so new categories appear. M3U re-downloads the whole playlist.
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
            public void afterTextChanged(Editable s) {
                // Live filter, but do not steal focus from the dialog.
                applyFilter(s.toString(), false);
            }
        });
        new AlertDialog.Builder(this)
                .setTitle("Search " + modeLabel())
                .setView(input)
                .setPositiveButton("Done", null)
                .setNeutralButton("Clear search", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        applyFilter("", false);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        applyFilter(before, false);
                    }
                })
                .show();
    }
    private void applyFilter(String query, boolean takeFocus) {
        currentQuery = query == null ? "" : query;
        // v2.2: progress for the Continue Watching row and the card bars.
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

    // v2.2: card width from the "items per row" setting (3–6, default 4).
    // The main area is ~3/4 of the screen width (sidebar weight 1, main
    // weight 3); fewer per row = bigger cards and posters.
    private int cardWidthDp() {
        int n = Prefs.itemsPerRow(this);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenWdp = (int) (dm.widthPixels / dm.density + 0.5f);
        int mainWdp = (screenWdp - 32) * 3 / 4 - 16;
        int w = (mainWdp - (n - 1) * 12) / n;
        return Math.max(100, w);
    }

    // ---- Main row (horizontal, windowed 24 cards per page) ----
    private void buildRow() {
        rowContainer.removeAllViews();
        if (shown.isEmpty()) { updateStatus(); return; }
        int end = Math.min(rowStart + ROW_PAGE, shown.size());
        final int windowEnd = end;
        for (int i = rowStart; i < end; i++) {
            addCard(rowContainer, shown.get(i), i, true, windowEnd);
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
    // One card in any horizontal row. `paged` adds the page-turn keys for
    // the main row; the Recently Added / Favorites / Continue Watching rows
    // show all their cards. The click action defaults to the normal
    // activation (dialogs for movies/series); pass a custom one to resume
    // directly (Continue Watching).
    private void addCard(final LinearLayout row, final Item it, final int pos,
                         final boolean paged, final int windowEnd) {
        addCard(row, it, pos, paged, windowEnd, new View.OnClickListener() {
            public void onClick(View v) { onItemActivated(it, pos); }
        });
    }
    private void addCard(final LinearLayout row, final Item it, final int pos,
                         final boolean paged, final int windowEnd,
                         final View.OnClickListener click) {
        View card = getLayoutInflater().inflate(R.layout.grid_item, row, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(cardWidthDp()),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(12);
        card.setLayoutParams(lp);
        bindCard(card, it);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    int p = indexInShown(it);
                    if (p >= 0) selPos = p;
                    updateHero(it);
                }
            }
        });
        card.setOnClickListener(click);
        if (paged) {
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
        }
        row.addView(card);
    }
    // v2.2 card content: the poster IS the card. Movies/series posters are
    // 2:3, live channel logos are square — the poster area is sized to
    // match, and the initials tile only shows when there is no poster at
    // all (it used to sit on top of the small logo). Items in Continue
    // Watching also show a progress bar with position / total and time left.
    private void bindCard(View v, final Item it) {
        int cardW = cardWidthDp();
        View posterBox = v.findViewById(R.id.poster_box);
        int posterH = it.isLive() ? cardW : cardW * 3 / 2;
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(posterH));
        posterBox.setLayoutParams(plp);
        TextView tile = (TextView) v.findViewById(R.id.tile);
        ImageView logo = (ImageView) v.findViewById(R.id.logo);
        if (it.logo.length() > 0) {
            tile.setVisibility(View.GONE);
            logo.setVisibility(View.VISIBLE);
            LogoCache.load(it.logo, logo);
        } else {
            logo.setVisibility(View.GONE);
            tile.setVisibility(View.VISIBLE);
            tile.setText(it.initials());
            tile.setBackgroundColor(it.color());
        }
        ((TextView) v.findViewById(R.id.item_name)).setText(it.name);
        ((TextView) v.findViewById(R.id.item_group)).setText(it.group);
        final TextView nowView = (TextView) v.findViewById(R.id.item_now);
        nowView.setTag(it.key());
        if (it.isLive() && profile.isXtream() && it.id.length() > 0) {
            final String epgKey = profile.name + "|" + it.id;
            Api.Epg cached = Api.epgCache.get(epgKey);
            if (cached != null) {
                nowView.setText(cached.now.length() > 0 ? "Now: " + cached.now : "");
            } else if (!epgInFlight.contains(epgKey)) {
                nowView.setText("");
                epgInFlight.add(epgKey);
                Api.shortEpg(profile, it.id, new Api.Cb<Api.Epg>() {
                    public void done(final Api.Epg e, String err) {
                        runOnUiThread(new Runnable() { public void run() {
                            epgInFlight.remove(epgKey);
                            if (!it.key().equals(nowView.getTag())) return;
                            nowView.setText(e.now.length() > 0 ? "Now: " + e.now : "");
                        }});
                    }
                });
            } else nowView.setText("");
        } else if (!it.isLive()) {
            String meta = "";
            if (it.rating.length() > 0) meta += "★ " + it.rating;
            if (it.year.length() > 0) meta += (meta.length() > 0 ? "  •  " : "") + it.year;
            nowView.setText(meta);
        } else nowView.setText("");
        android.widget.ProgressBar bar = (android.widget.ProgressBar) v.findViewById(R.id.item_progress);
        TextView progText = (TextView) v.findViewById(R.id.item_progress_text);
        ProgressStore.Entry e = progressMap.get(it.key());
        if (e != null && !it.isLive() && e.duration > 0) {
            bar.setVisibility(View.VISIBLE);
            bar.setMax(100);
            bar.setProgress(e.pct());
            progText.setVisibility(View.VISIBLE);
            progText.setText(ProgressStore.fmtTime(e.position) + " / " + ProgressStore.fmtTime(e.duration)
                    + "  •  " + ProgressStore.fmtLeft(e.position, e.duration));
        } else {
            bar.setVisibility(View.GONE);
            progText.setVisibility(View.GONE);
        }
    }

    // ---- Recently Added + Favorites + Continue Watching rows ----
    private void buildExtraRows() {
        // v2.2: in Favorites mode the top row already IS the favorites — the
        // Favorites row used to show the wrong "No favorites yet" message
        // here (favorites have type LIVE/MOVIE/SERIES, never "FAV"), so
        // hide the row entirely in that mode.
        if ("FAV".equals(mode)) {
            favLabel.setVisibility(View.GONE);
            favScroll.setVisibility(View.GONE);
        } else {
            favLabel.setVisibility(View.VISIBLE);
            favScroll.setVisibility(View.VISIBLE);
            // Favorites in this mode + category (and matching the search query).
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
            if (favs.isEmpty()) {
                TextView t = new TextView(this);
                t.setText("No favorites in this category yet. Press MENU on a playing item to add one.");
                t.setTextSize(16);
                t.setTextColor(0xFF9AA7BD);
                t.setPadding(dp(4), dp(8), dp(4), dp(8));
                favRow.addView(t);
            } else {
                // v2.2: capped at 20, like Recently Added.
                int n = Math.min(FAV_MAX, favs.size());
                for (int i = 0; i < n; i++)
                    addCard(favRow, favs.get(i), indexInShown(favs.get(i)), false, 0);
            }
        }
        // Recently Added: items in the current view (so category + search
        // both apply) that carry a provider date, newest first, top 20.
        // No dated items at all -> the row is hidden, never fabricated.
        List<Item> dated = new ArrayList<Item>();
        for (Item it : shown) if (it.added > 0) dated.add(it);
        if (dated.isEmpty()) {
            recentLabel.setVisibility(View.GONE);
            recentScroll.setVisibility(View.GONE);
        } else {
            Collections.sort(dated, new Comparator<Item>() {
                public int compare(Item a, Item b) {
                    return a.added < b.added ? 1 : (a.added > b.added ? -1 : 0);
                }
            });
            recentLabel.setVisibility(View.VISIBLE);
            recentScroll.setVisibility(View.VISIBLE);
            recentRow.removeAllViews();
            int n = Math.min(RECENT_MAX, dated.size());
            for (int i = 0; i < n; i++) {
                Item it = dated.get(i);
                addCard(recentRow, it, indexInShown(it), false, 0);
            }
        }
        buildContinueWatching();
    }

    // v2.2: Continue Watching — VOD items started but not finished, in this
    // mode + category (+ search), newest first, top 20. In Favorites mode
    // it shows in-progress favorites. Tapping a card resumes it directly
    // from the saved position.
    private void buildContinueWatching() {
        String q = currentQuery.toLowerCase();
        java.util.Set<String> favKeys = null;
        if ("FAV".equals(mode)) {
            favKeys = new HashSet<String>();
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
        cwLabel.setVisibility(View.VISIBLE);
        cwScroll.setVisibility(View.VISIBLE);
        cwRow.removeAllViews();
        if (cw.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("Nothing in progress yet — start a movie or episode and it will show up here.");
            t.setTextSize(16);
            t.setTextColor(0xFF9AA7BD);
            t.setPadding(dp(4), dp(8), dp(4), dp(8));
            cwRow.addView(t);
            return;
        }
        int n = Math.min(CW_MAX, cw.size());
        for (int i = 0; i < n; i++) {
            final ProgressStore.Entry e = cw.get(i);
            final Item it = e.item;
            addCard(cwRow, it, indexInShown(it), false, 0, new View.OnClickListener() {
                public void onClick(View v) {
                    Play.playOneResume(BrowseActivity.this, it, e.position);
                }
            });
        }
    }

    // ---- Hero ----
    private void updateHero(final Item it) {
        heroName.setText(it.name);
        if (it.logo.length() > 0) {
            heroLogo.setVisibility(View.VISIBLE);
            heroTile.setVisibility(View.GONE);
            LogoCache.load(it.logo, heroLogo);
        } else {
            heroLogo.setVisibility(View.GONE);
            heroTile.setVisibility(View.VISIBLE);
            heroTile.setText(it.initials());
            heroTile.setBackgroundColor(it.color());
        }
        String info = it.group;
        if (it.rating.length() > 0) info += "  •  ★ " + it.rating;
        if (it.year.length() > 0) info += "  •  " + it.year;
        if (it.duration.length() > 0) info += "  •  " + it.duration;
        heroInfo.setText(info);
        if (it.isLive() && profile.isXtream() && it.id.length() > 0) {
            heroEpg.setText("Loading guide…");
            Api.shortEpg(profile, it.id, new Api.Cb<Api.Epg>() {
                public void done(final Api.Epg e, String err) {
                    runOnUiThread(new Runnable() { public void run() {
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
    }
    private void clearHero() {
        heroName.setText("Nothing here yet from this provider.");
        heroInfo.setText("");
        heroEpg.setText("");
        heroLogo.setVisibility(View.GONE);
        heroTile.setVisibility(View.GONE);
    }
    private void updateStatus() {
        rowLabel.setText("All".equals(currentCatName) ? "All categories" : currentCatName);
        if (baseItems.isEmpty() && shown.isEmpty()) {
            statusView.setText("Nothing here yet from this provider.");
            return;
        }
        String s = shown.size() + " items  •  " + currentCatName;
        if (lastFromCache) s += "  •  saved data";
        if (currentQuery.length() > 0) s += "  •  Search: '" + currentQuery + "'";
        if (shown.size() > ROW_PAGE)
            s += "  •  Showing " + (rowStart + 1) + "–" + Math.min(rowStart + ROW_PAGE, shown.size())
                    + " of " + shown.size();
        statusView.setText(s);
    }

    // ---- Activation (unchanged from v2.0) ----
    private void onItemActivated(final Item it, int pos) {
        if (it.isLive() || "FAV".equals(mode)) {
            // v2.2: a VOD favorite resumes from its saved position.
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
        // Series
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
                            // v2.2: mark episodes that can be resumed.
                            ProgressStore.Entry e = progressMap.get(eps.get(i).key());
                            names[i] = eps.get(i).name
                                    + (e != null && e.duration > 0
                                            ? "  •  Resume " + ProgressStore.fmtTime(e.position) : "");
                        }
                        new AlertDialog.Builder(BrowseActivity.this)
                                .setTitle(series.name + (series.plot.length() > 0 ? "\n" + series.plot : ""))
                                .setItems(names, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface d, int which) {
                                        // v2.2: resume the episode if it was started.
                                        ProgressStore.Entry e = progressMap.get(eps.get(which).key());
                                        if (e != null && e.duration > 0)
                                            Play.playWithResume(BrowseActivity.this, eps, which, e.position);
                                        else
                                            Play.play(BrowseActivity.this, eps, which);
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
        // v2.2: offer to resume from the saved position when one exists.
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
                        else
                            Play.playOne(BrowseActivity.this, it);
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
