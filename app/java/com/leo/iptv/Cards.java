package com.leo.iptv;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.HashSet;
import java.util.Set;

// v3.0: one card builder shared by Home and Browse. The poster area is the
// card: 2:3 posters for movies/series, 16:9 tiles for live channel logos.
// Below it: name + one meta line (live "Now:", else rating / year / group).
// Continue Watching cards show the progress bar at the poster's bottom edge.
public class Cards {
    public interface Listener {
        void onFocus(Item it);
        void onClick(Item it);
    }

    // Live "Now:" EPG requests in flight (shared, so a channel is only
    // fetched once per session even across activities).
    private static final Set<String> epgInFlight = new HashSet<String>();

    public static int cardWidthDp(Activity a) {
        int n = Prefs.itemsPerRow(a);
        android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
        int screenWdp = (int) (dm.widthPixels / dm.density + 0.5f);
        int mainWdp = screenWdp - 216 - 40 - 20; // sidebar + padding
        int w = (mainWdp - (n - 1) * 14) / n;
        return Math.max(110, w);
    }

    public static View build(final Activity a, final Item it, final Profile profile,
                             final ProgressStore.Entry prog, final Listener listener) {
        int cardW = Ui.dp(a, Cards.cardWidthDp(a));
        View v = a.getLayoutInflater().inflate(R.layout.grid_item, null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(a, 14);
        v.setLayoutParams(lp);

        View posterBox = v.findViewById(R.id.poster_box);
        int posterH = it.isLive() ? Cards.cardWidthDp(a) * 9 / 16
                : Cards.cardWidthDp(a) * 3 / 2;
        posterBox.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(a, posterH)));

        TextView tile = (TextView) v.findViewById(R.id.tile);
        ImageView logo = (ImageView) v.findViewById(R.id.logo);
        if (it.logo != null && it.logo.length() > 0) {
            tile.setVisibility(View.GONE);
            logo.setVisibility(View.VISIBLE);
            if (it.isLive()) {
                logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int pad = Ui.dp(a, 14);
                logo.setPadding(pad, pad, pad, pad);
            } else {
                logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }
            LogoCache.load(it.logo, logo);
        } else {
            logo.setVisibility(View.GONE);
            tile.setVisibility(View.VISIBLE);
            tile.setText(it.initials());
            tile.setBackgroundColor(it.color());
        }

        ((TextView) v.findViewById(R.id.item_name)).setText(it.name);

        final TextView nowView = (TextView) v.findViewById(R.id.item_now);
        nowView.setTag(it.key());
        if (it.isLive() && profile != null && profile.isXtream() && it.id.length() > 0) {
            final String epgKey = profile.name + "|" + it.id;
            Api.Epg cached = Api.epgCache.get(epgKey);
            if (cached != null) {
                nowView.setText(cached.now.length() > 0 ? "Now: " + cached.now : "");
            } else if (!epgInFlight.contains(epgKey)) {
                nowView.setText("");
                epgInFlight.add(epgKey);
                Api.shortEpg(profile, it.id, new Api.Cb<Api.Epg>() {
                    public void done(final Api.Epg e, String err) {
                        a.runOnUiThread(new Runnable() { public void run() {
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
            if (meta.length() == 0) meta = it.group;
            nowView.setText(meta);
        } else {
            nowView.setText(it.group);
        }

        ProgressBar bar = (ProgressBar) v.findViewById(R.id.item_progress);
        if (prog != null && !it.isLive() && prog.duration > 0) {
            bar.setVisibility(View.VISIBLE);
            bar.setMax(100);
            bar.setProgress(prog.pct());
        } else {
            bar.setVisibility(View.GONE);
        }

        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View view, boolean hasFocus) {
                view.animate().cancel();
                if (hasFocus) {
                    view.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start();
                    view.setElevation(Ui.dp(a, 8));
                    if (listener != null) listener.onFocus(it);
                } else {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    view.setElevation(0);
                }
            }
        });
        v.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) { if (listener != null) listener.onClick(it); }
        });
        return v;
    }
}
