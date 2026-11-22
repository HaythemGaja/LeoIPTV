package com.leo.iptv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

// Search across the loaded provider's live + VOD + series names.
public class SearchActivity extends Activity {
    private Profile profile;
    private EditText query;
    private TextView statusView;
    private ListView results;
    private List<Item> all = new ArrayList<Item>();
    private List<Item> shown = new ArrayList<Item>();

    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_search);
        profile = Prefs.current(this);
        query = (EditText) findViewById(R.id.s_query);
        statusView = (TextView) findViewById(R.id.s_status);
        results = (ListView) findViewById(R.id.s_results);
        if (profile == null) { finish(); return; }
        results.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Item it = shown.get(pos);
                if (it.isLive() || "MOVIE".equals(it.type)) Play.play(SearchActivity.this, shown, pos);
                else if (profile.isXtream()) playSeries(it);
                else Play.playOne(SearchActivity.this, it);
            }
        });
        findViewById(R.id.btn_search).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { runSearch(); }
        });
        statusView.setText("Loading content…");
        if (profile.isXtream()) {
            loadKind("LIVE");
        } else {
            Api.loadM3u(profile.m3uUrl, new Api.Cb<List<Item>>() {
                public void done(final List<Item> items, final String err) {
                    runOnUiThread(new Runnable() { public void run() {
                        if (err != null) { statusView.setText(err); return; }
                        all = items;
                        statusView.setText("Type a name and search (" + all.size() + " items loaded).");
                    }});
                }
            });
        }
    }
    private void loadKind(final String kind) {
        Api.streams(profile, kind, null, new Api.Cb<List<Item>>() {
            public void done(final List<Item> items, final String err) {
                all.addAll(items);
                if ("LIVE".equals(kind)) loadKind("MOVIE");
                else if ("MOVIE".equals(kind)) loadKind("SERIES");
                else runOnUiThread(new Runnable() { public void run() {
                    statusView.setText("Type a name and search (" + all.size() + " items loaded).");
                }});
            }
        });
    }
    private void runSearch() {
        String q = query.getText().toString().trim().toLowerCase();
        shown = new ArrayList<Item>();
        if (q.length() > 0) {
            for (Item it : all) if (it.name.toLowerCase().contains(q)) shown.add(it);
        }
        List<String> names = new ArrayList<String>();
        for (Item it : shown) names.add(it.name + "  •  " + it.type + "  •  " + it.group);
        results.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, names));
        statusView.setText(shown.isEmpty() ? "No matches." : shown.size() + " matches");
    }
    private void playSeries(final Item it) {
        statusView.setText("Loading series…");
        Api.seriesInfo(profile, it.id, new Api.Cb<Item>() {
            public void done(final Item series, final String err) {
                runOnUiThread(new Runnable() { public void run() {
                    statusView.setText("");
                    if (err != null || series == null || series.episodes.isEmpty()) {
                        new AlertDialog.Builder(SearchActivity.this)
                                .setTitle(it.name)
                                .setMessage(err != null ? err : "No episodes from this provider.")
                                .setPositiveButton("Close", null).show();
                        return;
                    }
                    final List<Item> eps = series.episodes;
                    String[] names = new String[eps.size()];
                    for (int i = 0; i < eps.size(); i++) names[i] = eps.get(i).name;
                    new AlertDialog.Builder(SearchActivity.this)
                            .setTitle(series.name)
                            .setItems(names, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface d, int which) {
                                    Play.play(SearchActivity.this, eps, which);
                                }
                            })
                            .setNegativeButton("Close", null).show();
                }});
            }
        });
    }
}
