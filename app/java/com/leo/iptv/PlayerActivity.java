package com.leo.iptv;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// v3.0 player: full-screen video with an auto-hiding overlay. Any key shows
// the overlay; it hides again after 5 seconds. Top: channel logo, name,
// LIVE badge, clock. Bottom: Now/Next guide, a live program progress bar
// (when the provider gives program times) or the VOD seek bar, and one
// transport row (-30s, Play/Pause, +30s, Favorite).
// D-pad: UP/DOWN changes channel, OK pauses/plays, LEFT/RIGHT seeks ±30s
// (VOD), MENU toggles favorite, BACK exits.
public class PlayerActivity extends Activity {
    private static final int HIDE_AFTER_MS = 5000;

    private VideoView video;
    private TextView nameView, groupView, nowView, nextView, progressView, liveView, errorView, clockView;
    private ImageView logoView;
    private Button favBtn, playBtn, retryBtn, back30Btn, fwd30Btn;
    private SeekBar seekBar;
    private ProgressBar loading, liveProgress;
    private LinearLayout errorPanel, overlay;
    private List<Item> items;
    private Profile profile;
    private int pos = 0;
    private boolean prepared = false;
    private boolean dragging = false;
    private int resumeMs = 0;
    private boolean resumed = false;
    private int saveTick = 0;
    private Handler handler = new Handler();

    private Runnable progressTick = new Runnable() {
        public void run() {
            Item it = current();
            if (it != null) {
                if (it.isLive()) {
                    progressView.setText("Live");
                } else if (video.getDuration() > 0) {
                    progressView.setText(fmt(video.getCurrentPosition()) + " / " + fmt(video.getDuration()));
                    if (prepared && !dragging) seekBar.setProgress(video.getCurrentPosition());
                    if (prepared && video.isPlaying() && ++saveTick >= 15) {
                        saveTick = 0;
                        saveProgress();
                    }
                } else {
                    progressView.setText("Loading…");
                }
            }
            handler.postDelayed(this, 1000);
        }
    };
    private Runnable hideTick = new Runnable() {
        public void run() { overlay.setVisibility(View.GONE); }
    };

    private void saveProgress() {
        Item it = current();
        if (it == null || it.isLive() || !prepared) return;
        int dur = 0, posMs = 0;
        try { dur = video.getDuration(); posMs = video.getCurrentPosition(); } catch (Exception ignored) {}
        if (dur > 0 && posMs > 0)
            ProgressStore.save(this, it, posMs, dur, profile != null ? profile.name : "");
    }
    private static String fmt(int ms) {
        int s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }
    private Item current() {
        if (items == null || items.isEmpty() || pos < 0 || pos >= items.size()) return null;
        return items.get(pos);
    }
    private void showOverlay() {
        overlay.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideTick);
        handler.postDelayed(hideTick, HIDE_AFTER_MS);
    }

    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_player);
        video = (VideoView) findViewById(R.id.video);
        overlay = (LinearLayout) findViewById(R.id.overlay);
        nameView = (TextView) findViewById(R.id.p_name);
        groupView = (TextView) findViewById(R.id.p_group);
        nowView = (TextView) findViewById(R.id.p_now);
        nextView = (TextView) findViewById(R.id.p_next);
        progressView = (TextView) findViewById(R.id.p_progress);
        liveView = (TextView) findViewById(R.id.p_live);
        clockView = (TextView) findViewById(R.id.p_clock);
        logoView = (ImageView) findViewById(R.id.p_logo);
        errorView = (TextView) findViewById(R.id.error_box);
        errorPanel = (LinearLayout) findViewById(R.id.error_panel);
        loading = (ProgressBar) findViewById(R.id.p_loading);
        liveProgress = (ProgressBar) findViewById(R.id.p_live_progress);
        favBtn = (Button) findViewById(R.id.btn_fav);
        playBtn = (Button) findViewById(R.id.btn_play);
        retryBtn = (Button) findViewById(R.id.btn_retry);
        back30Btn = (Button) findViewById(R.id.btn_back30);
        fwd30Btn = (Button) findViewById(R.id.btn_fwd30);
        seekBar = (SeekBar) findViewById(R.id.seek_bar);
        items = Item.listFromJson(getIntent().getStringExtra("items"));
        pos = getIntent().getIntExtra("pos", 0);
        resumeMs = getIntent().getIntExtra("resume", 0);
        String profName = getIntent().getStringExtra("profile");
        if (profName != null) {
            for (Profile p : Prefs.profiles(this)) if (p.name.equals(profName)) profile = p;
        }
        Ui.bindClock(clockView);

        video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            public void onPrepared(MediaPlayer mp) {
                prepared = true;
                loading.setVisibility(View.GONE);
                Item it = current();
                if (it != null && !it.isLive() && video.getDuration() > 0) {
                    seekBar.setMax(video.getDuration());
                    seekBar.setKeyProgressIncrement(30000);
                    seekBar.setVisibility(View.VISIBLE);
                    if (!resumed && resumeMs > 0 && resumeMs < video.getDuration()) {
                        resumed = true;
                        video.seekTo(resumeMs);
                        progressView.setText("Resuming from " + fmt(resumeMs));
                    }
                    resumeMs = 0;
                } else {
                    seekBar.setVisibility(View.GONE);
                }
                video.start();
                updatePlayLabel();
            }
        });
        video.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START)
                    loading.setVisibility(View.VISIBLE);
                else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END)
                    loading.setVisibility(View.GONE);
                return true;
            }
        });
        video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            public boolean onError(MediaPlayer mp, int what, int extra) {
                prepared = false;
                loading.setVisibility(View.GONE);
                showError(current());
                return true;
            }
        });
        video.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mp) { saveProgress(); }
        });
        favBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Item it = current();
                if (it == null) return;
                Prefs.toggleFav(PlayerActivity.this, it);
                updateFavLabel();
            }
        });
        playBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { togglePlay(); }
        });
        retryBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                playCurrent();
                playBtn.requestFocus();
            }
        });
        back30Btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { seekBy(-30000); }
        });
        fwd30Btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { seekBy(30000); }
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser && prepared) {
                    try { video.seekTo(progress); } catch (Exception ignored) {}
                }
            }
            public void onStartTrackingTouch(SeekBar s) { dragging = true; }
            public void onStopTrackingTouch(SeekBar s) { dragging = false; }
        });
        playCurrent();
        showOverlay();
        progressTick.run();
    }
    protected void onDestroy() {
        super.onDestroy();
        saveProgress();
        handler.removeCallbacks(progressTick);
        handler.removeCallbacks(hideTick);
    }
    protected void onPause() {
        super.onPause();
        saveProgress();
        try { video.pause(); } catch (Exception ignored) {}
    }

    private void showError(Item it) {
        loading.setVisibility(View.GONE);
        String host = it == null ? "unknown host" : hostOf(it.url);
        errorView.setText("Could not play this stream.\nHost: " + host
                + "\n\nTry another channel, or press BACK to return.");
        errorPanel.setVisibility(View.VISIBLE);
        showOverlay();
        retryBtn.requestFocus();
    }
    private void updateFavLabel() {
        Item it = current();
        favBtn.setText(it != null && Prefs.isFav(this, it) ? "★ Favorite" : "☆ Favorite");
    }
    private void updatePlayLabel() {
        boolean playing = false;
        try { playing = video.isPlaying(); } catch (Exception ignored) {}
        playBtn.setText(playing ? "❚❚ Pause" : "▶ Play");
    }
    private void togglePlay() {
        try {
            if (video.isPlaying()) video.pause(); else video.start();
        } catch (Exception ignored) {}
        updatePlayLabel();
    }
    private void seekBy(int deltaMs) {
        Item it = current();
        if (it == null || it.isLive()) return;
        try {
            int p = video.getCurrentPosition() + deltaMs;
            video.seekTo(Math.max(0, p));
        } catch (Exception ignored) {}
    }
    private void playCurrent() {
        saveProgress();
        final Item it = current();
        if (it == null) { finish(); return; }
        prepared = false;
        resumed = false;
        saveTick = 0;
        errorPanel.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        seekBar.setVisibility(View.GONE);
        liveProgress.setVisibility(View.GONE);
        nameView.setText(it.name);
        groupView.setText(it.group + (it.group.length() > 0 ? " • " : "") + it.type);
        liveView.setVisibility(it.isLive() ? View.VISIBLE : View.GONE);
        back30Btn.setVisibility(it.isLive() ? View.GONE : View.VISIBLE);
        fwd30Btn.setVisibility(it.isLive() ? View.GONE : View.VISIBLE);
        if (it.logo != null && it.logo.length() > 0) {
            logoView.setVisibility(View.VISIBLE);
            LogoCache.load(it.logo, logoView);
        } else {
            logoView.setVisibility(View.GONE);
        }
        updateFavLabel();
        updatePlayLabel();
        Prefs.addRecent(this, it);
        if (it.isLive() && profile != null && profile.isXtream() && it.id.length() > 0) {
            nowView.setText("Loading guide…");
            nextView.setText("");
            Api.shortEpg(profile, it.id, new Api.Cb<Api.Epg>() {
                public void done(final Api.Epg e, String err) {
                    runOnUiThread(new Runnable() { public void run() {
                        if (current() != it) return;
                        if (e.now.length() > 0) {
                            nowView.setText("Now: " + e.now);
                            nextView.setText(e.next.length() > 0 ? "Next: " + e.next : "");
                            if (e.nowEnd > e.nowStart) {
                                long now = System.currentTimeMillis() / 1000L;
                                int pct = (int) ((now - e.nowStart) * 100 / (e.nowEnd - e.nowStart));
                                if (pct >= 0 && pct <= 100) {
                                    liveProgress.setProgress(pct);
                                    liveProgress.setVisibility(View.VISIBLE);
                                }
                            }
                        } else {
                            nowView.setText("No guide data from this provider");
                            nextView.setText("");
                        }
                    }});
                }
            });
        } else if (it.isLive()) {
            nowView.setText("No guide data from this provider");
            nextView.setText("");
        } else {
            nowView.setText(it.name);
            nextView.setText(it.plot.length() > 0 ? it.plot : "");
        }
        try {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("User-Agent", Api.USER_AGENT);
            video.stopPlayback();
            video.setVideoURI(Uri.parse(it.url), headers);
        } catch (Exception e) { showError(it); }
    }
    private static String hostOf(String url) {
        try { String h = Uri.parse(url).getHost(); return h == null ? "unknown host" : h; }
        catch (Exception e) { return "unknown host"; }
    }
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            showOverlay();
            int code = e.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (items != null && items.size() > 1) {
                    pos = code == KeyEvent.KEYCODE_DPAD_UP
                            ? (pos - 1 + items.size()) % items.size()
                            : (pos + 1) % items.size();
                    playCurrent();
                    return true;
                }
            }
            if (code == KeyEvent.KEYCODE_MENU) {
                Item cur = current();
                if (cur != null) { Prefs.toggleFav(this, cur); updateFavLabel(); }
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                View f = getCurrentFocus();
                if (f instanceof Button) return super.dispatchKeyEvent(e);
                togglePlay();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                View f = getCurrentFocus();
                if (f instanceof Button || f instanceof SeekBar) return super.dispatchKeyEvent(e);
                seekBy(code == KeyEvent.KEYCODE_DPAD_LEFT ? -30000 : 30000);
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }
}
