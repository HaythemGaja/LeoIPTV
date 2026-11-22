package com.leo.iptv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

public class LoginActivity extends Activity {
    private EditText xName, xHost, xUser, xPass, mName, mUrl;
    private TextView status;
    private LinearLayout savedList;

    public void onCreate(Bundle b) {
        super.onCreate(b);
        // v2.2: remember him. If a current profile is saved, go straight to
        // the Dashboard — the login screen must never make him re-enter a
        // login that is already on the device. "Switch User" clears the
        // current profile first, so that path still shows this screen.
        if (Prefs.current(this) != null) {
            openDashboard();
            finish();
            return;
        }
        setContentView(R.layout.activity_login);
        xName = (EditText) findViewById(R.id.et_x_name);
        xHost = (EditText) findViewById(R.id.et_x_host);
        xUser = (EditText) findViewById(R.id.et_x_user);
        xPass = (EditText) findViewById(R.id.et_x_pass);
        mName = (EditText) findViewById(R.id.et_m_name);
        mUrl = (EditText) findViewById(R.id.et_m_url);
        status = (TextView) findViewById(R.id.login_status);
        savedList = (LinearLayout) findViewById(R.id.saved_list);

        findViewById(R.id.btn_save_xtream).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { saveXtream(); }
        });
        findViewById(R.id.btn_save_m3u).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { saveM3u(); }
        });
        refreshSaved();
    }
    protected void onResume() { super.onResume(); refreshSaved(); }

    private void setStatus(final String s) {
        runOnUiThread(new Runnable() { public void run() { status.setText(s); } });
    }
    private void saveXtream() {
        final String name = xName.getText().toString().trim();
        final String host = Api.normalizeHost(xHost.getText().toString());
        final String user = xUser.getText().toString().trim();
        final String pass = xPass.getText().toString().trim();
        if (name.length() == 0 || host.length() == 0 || user.length() == 0 || pass.length() == 0) {
            status.setText("Fill in profile name, host, username and password.");
            return;
        }
        status.setText("Checking login…");
        final Profile p = new Profile(name, "XTREAM", host, user, pass, "", "");
        Api.validateXtream(p, new Api.Cb<String>() {
            public void done(final String line, String err) {
                if (err != null) { setStatus(err); return; }
                p.accountLine = line;
                Prefs.addOrReplaceProfile(LoginActivity.this, p);
                Prefs.setCurrent(LoginActivity.this, name);
                setStatus("Saved. Opening dashboard…");
                runOnUiThread(new Runnable() { public void run() { openDashboard(); } });
            }
        });
    }
    private void saveM3u() {
        final String name = mName.getText().toString().trim();
        final String url = mUrl.getText().toString().trim();
        if (name.length() == 0 || url.length() == 0) {
            status.setText("Fill in profile name and M3U URL.");
            return;
        }
        status.setText("Checking playlist…");
        Api.validateM3u(url, new Api.Cb<List<Item>>() {
            public void done(List<Item> items, String err) {
                if (err != null) { setStatus(err); return; }
                Profile p = new Profile(name, "M3U", "", "", "", url, "M3U playlist");
                Prefs.addOrReplaceProfile(LoginActivity.this, p);
                Prefs.setCurrent(LoginActivity.this, name);
                setStatus("Saved (" + items.size() + " entries). Opening dashboard…");
                runOnUiThread(new Runnable() { public void run() { openDashboard(); } });
            }
        });
    }
    private void refreshSaved() {
        savedList.removeAllViews();
        final List<Profile> ps = Prefs.profiles(this);
        if (ps.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("Add your IPTV account or playlist to start");
            t.setTextSize(17); t.setTextColor(0xFF9AA7BD);
            savedList.addView(t);
            return;
        }
        for (final Profile p : ps) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            Button enter = new Button(this);
            enter.setText(p.name + (p.isXtream() ? " (Xtream)" : " (M3U)"));
            enter.setTextSize(17); enter.setTextColor(0xFFFFFFFF);
            enter.setBackgroundResource(R.drawable.button_selector);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            enter.setLayoutParams(lp);
            enter.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Prefs.setCurrent(LoginActivity.this, p.name);
                    openDashboard();
                }
            });
            Button del = new Button(this);
            del.setText("Delete");
            del.setTextSize(17); del.setTextColor(0xFFFFFFFF);
            del.setBackgroundResource(R.drawable.button_selector);
            del.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    // v2.2: confirm first — one wrong D-pad tap used to wipe
                    // the login with no way back.
                    new AlertDialog.Builder(LoginActivity.this)
                            .setTitle("Delete profile '" + p.name + "'?")
                            .setMessage("This removes the login from this device.")
                            .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface d, int w) {
                                    Prefs.deleteProfile(LoginActivity.this, p.name);
                                    refreshSaved();
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });
            row.addView(enter); row.addView(del);
            savedList.addView(row);
        }
    }
    private void openDashboard() {
        startActivity(new Intent(this, DashboardActivity.class));
    }
}
