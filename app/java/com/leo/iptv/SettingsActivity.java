package com.leo.iptv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private Button rowBtn;
    private void updateRowBtn() {
        rowBtn.setText("Items per row: " + Prefs.itemsPerRow(this) + "  (3–6)");
    }
    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        final Profile p = Prefs.current(this);
        ((TextView) findViewById(R.id.set_profile)).setText(p != null ? p.name : "");
        rowBtn = (Button) findViewById(R.id.btn_items_per_row);
        updateRowBtn();
        rowBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // v2.2: choose how many cards fit across a row (3–6).
                // Fewer per row = bigger cards and posters.
                final String[] options = {
                        "3 per row — biggest posters",
                        "4 per row",
                        "5 per row",
                        "6 per row — smallest posters"};
                int current = Prefs.itemsPerRow(SettingsActivity.this) - 3;
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Items per row")
                        .setSingleChoiceItems(options, current,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface d, int which) {
                                        Prefs.setItemsPerRow(SettingsActivity.this, which + 3);
                                        updateRowBtn();
                                        d.dismiss();
                                    }
                                })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        findViewById(R.id.btn_switch).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.setCurrent(SettingsActivity.this, "");
                startActivity(new Intent(SettingsActivity.this, LoginActivity.class));
                finish();
            }
        });
        findViewById(R.id.btn_delete_user).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (p == null) return;
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Delete current user?")
                        .setMessage("Remove \"" + p.name + "\" from this device? Favorites and recent are kept.")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                DataCache.clearProfile(SettingsActivity.this, p.name);
                                ProgressStore.clearProfile(SettingsActivity.this, p.name);
                                Prefs.deleteProfile(SettingsActivity.this, p.name);
                                startActivity(new Intent(SettingsActivity.this, LoginActivity.class));
                                finish();
                            }
                        })
                        .setNegativeButton("Cancel", null).show();
            }
        });
        findViewById(R.id.btn_clear_fav).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Prefs.clearFavs(SettingsActivity.this); }
        });
        findViewById(R.id.btn_clear_recent).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Prefs.clearRecent(SettingsActivity.this); }
        });
        findViewById(R.id.btn_clear_cw).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { ProgressStore.clear(SettingsActivity.this); }
        });
        findViewById(R.id.btn_about).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("About Leo IPTV 3.0")
                        .setMessage("A player for your own IPTV account or playlist.\n\n"
                                + "Content comes only from the Xtream Codes login or M3U playlist you add. "
                                + "No channels are bundled. Guide data is shown only when your provider supplies it.\n\n"
                                + "Credentials are stored only on this device.")
                        .setPositiveButton("Close", null).show();
            }
        });
    }
}
