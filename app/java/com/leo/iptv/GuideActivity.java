package com.leo.iptv;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

// TV Guide: real short-EPG only, fetched on demand for the selected category
// only, one request per channel, cached for the session (Api.epgCache).
public class GuideActivity extends Activity {
    private Profile profile;
    private ListView catList, chanList;
    private TextView statusView;
    private List<Api.Cat> cats = new ArrayList<Api.Cat>();
    private List<Item> channels = new ArrayList<Item>();
    private List<String> epgLines = new ArrayList<String>();
    private List<String> nextLines = new ArrayList<String>();
    private List<Item> allM3u = new ArrayList<Item>();
    private BaseAdapter adapter;

    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_guide);
        profile = Prefs.current(this);
        catList = (ListView) findViewById(R.id.g_cats);
        chanList = (ListView) findViewById(R.id.g_chans);
        statusView = (TextView) findViewById(R.id.g_status);
        Ui.bindClock((TextView) findViewById(R.id.g_clock));
        if (profile == null) { finish(); return; }
        adapter = new BaseAdapter() {
            public int getCount() { return channels.size(); }
            public Object getItem(int p) { return channels.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int pos, View cv, ViewGroup parent) {
                View v = cv;
                if (v == null) v = getLayoutInflater().inflate(R.layout.guide_row, parent, false);
                Item row = channels.get(pos);
                TextView tile = (TextView) v.findViewById(R.id.g_tile);
                tile.setText(row.initials());
                tile.setBackgroundColor(row.color());
                ((TextView) v.findViewById(R.id.g_name)).setText(row.name);
                ((TextView) v.findViewById(R.id.g_epg)).setText(epgLines.get(pos));
                ((TextView) v.findViewById(R.id.g_next)).setText(nextLines.get(pos));
                return v;
            }
        };
        chanList.setAdapter(adapter);
        chanList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Play.play(GuideActivity.this, channels, pos);
            }
        });
        catList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (profile.isXtream()) loadChannels(pos == 0 ? null : cats.get(pos - 1).id);
                else loadM3uChannels(pos == 0 ? "All" : ((String) p.getItemAtPosition(pos)));
            }
        });
        statusView.setText("Loading categories…");
        if (profile.isXtream()) {
            Api.categories(profile, "LIVE", new Api.Cb<List<Api.Cat>>() {
                public void done(final List<Api.Cat> cs, final String err) {
                    runOnUiThread(new Runnable() { public void run() {
                        if (err != null) { statusView.setText(err); return; }
                        cats = cs;
                        List<String> names = new ArrayList<String>();
                        names.add("All categories");
                        for (Api.Cat c : cs) names.add(c.name);
                        catList.setAdapter(new ArrayAdapter<String>(GuideActivity.this,
                                android.R.layout.simple_list_item_1, names));
                        loadChannels(null);
                    }});
                }
            });
        } else {
            Api.loadM3u(profile.m3uUrl, new Api.Cb<List<Item>>() {
                public void done(final List<Item> items, final String err) {
                    runOnUiThread(new Runnable() { public void run() {
                        if (err != null) { statusView.setText(err); return; }
                        allM3u = items;
                        List<String> groups = new ArrayList<String>();
                        groups.add("All categories");
                        for (Item it : items)
                            if (it.isLive() && !groups.contains(it.group)) groups.add(it.group);
                        catList.setAdapter(new ArrayAdapter<String>(GuideActivity.this,
                                android.R.layout.simple_list_item_1, groups));
                        loadM3uChannels("All");
                    }});
                }
            });
        }
    }
    private void loadChannels(String catId) {
        statusView.setText("Loading channels…");
        Api.streams(profile, "LIVE", catId, new Api.Cb<List<Item>>() {
            public void done(final List<Item> items, final String err) {
                runOnUiThread(new Runnable() { public void run() {
                    if (err != null) { statusView.setText(err); return; }
                    setChannels(items);
                }});
            }
        });
    }
    private void loadM3uChannels(String group) {
        List<Item> out = new ArrayList<Item>();
        for (Item it : allM3u) {
            if (!it.isLive()) continue;
            if (!"All".equals(group) && !it.group.equals(group)) continue;
            out.add(it);
        }
        setChannels(out);
    }
    private void setChannels(List<Item> items) {
        channels = items;
        epgLines = new ArrayList<String>();
        nextLines = new ArrayList<String>();
        for (int i = 0; i < items.size(); i++) { epgLines.add("Loading guide…"); nextLines.add(""); }
        adapter.notifyDataSetChanged();
        statusView.setText(channels.isEmpty() ? "No channels in this category." : channels.size() + " channels");
        if (!profile.isXtream()) {
            for (int i = 0; i < epgLines.size(); i++) { epgLines.set(i, "No guide data from this provider"); nextLines.set(i, ""); }
            adapter.notifyDataSetChanged();
            return;
        }
        for (int i = 0; i < channels.size(); i++) {
            final int pos = i;
            final Item ch = channels.get(i);
            if (ch.id.length() == 0) { epgLines.set(pos, "No guide data from this provider"); nextLines.set(pos, ""); continue; }
            Api.shortEpg(profile, ch.id, new Api.Cb<Api.Epg>() {
                public void done(final Api.Epg e, String err) {
                    runOnUiThread(new Runnable() { public void run() {
                        if (pos >= epgLines.size() || channels.get(pos) != ch) return;
                        if (e.now.length() > 0) {
                            epgLines.set(pos, "Now: " + e.now);
                            nextLines.set(pos, e.next.length() > 0 ? "Next: " + e.next : "");
                        } else {
                            epgLines.set(pos, "No guide data from this provider");
                            nextLines.set(pos, "");
                        }
                        adapter.notifyDataSetChanged();
                    }});
                }
            });
        }
    }
}
