package com.leo.iptv;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// v2.1: a real media player. The transport row is always visible: Play/Pause
// button, seek bar (VOD only — live shows a LIVE badge instead), current /
// total time, and Favorite. A spinner shows while loading or buffering, the
// stream is sent the provider User-Agent header, and a failed stream offers
// a Retry button. D-pad: UP/DOWN changes channel, OK pauses/plays,
// LEFT/RIGHT seeks ±30s (VOD), MENU toggles favorite, BACK exits.
public class PlayerActivity extends Activity {
    private VideoView video;
    private TextView nameView, groupView, nowView, progressView, liveView, errorView;
    private Button favBtn, playBtn, retryBtn;
    private SeekBar seekBar;
    private ProgressBar loading;
    private LinearLayout errorPanel;
    private List<Item> items;
    private Profile profile;
    private int pos = 0;
    private boolean prepared = false;
    private boolean dragging = false;
    // v2.2: resume position (ms) for VOD, from Continue Watching. 0 = start.
    private int resumeMs = 0;
    private boolean resumed = false;
    private int saveTick = 0;
    private Handler handler = new Handler();
    private Runnable progressTick = new Runnable() {
        public void run() {
            Item it = current();
            if (it != null) {
                if (it.isLive()) progressView.setText("Live");
                else if (video.getDuration() > 0) {
                    progressView.setText(fmt(video.getCurrentPosition()) + " / " + fmt(video.getDuration()));
                    if (prepared && !dragging) seekBar.setProgress(video.getCurrentPosition());
                    // v2.2: save progress every ~15s while playing.
                    if (prepared && video.isPlaying() && ++saveTick >= 15) {
                        saveTick = 0;
                        saveProgress();
                    }
                } else progressView.setText("Loading…");
            }
            handler.postDelayed(this, 1000);
        }
    };
    // v2.2: persist the current VOD position (live is never tracked).
    // ProgressStore drops the entry when finished (>= 90%) or barely
    // started (< 10s), so this one call covers pause, exit and completion.
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

    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_player);
        video = (VideoView) findViewById(R.id.video);
        nameView = (TextView) findViewById(R.id.p_name);
        groupView = (TextView) findViewById(R.id.p_group);
        nowView = (TextView) findViewById(R.id.p_now);
        progressView = (TextView) findViewById(R.id.p_progress);
        liveView = (TextView) findViewById(R.id.p_live);
        errorView = (TextView) findViewById(R.id.error_box);
        errorPanel = (LinearLayout) findViewById(R.id.error_panel);
        loading = (ProgressBar) findViewById(R.id.p_loading);
        favBtn = (Button) findViewById(R.id.btn_fav);
        playBtn = (Button) findViewById(R.id.btn_play);
        retryBtn = (Button) findViewById(R.id.btn_retry);
        seekBar = (SeekBar) findViewById(R.id.seek_bar);
        items = Item.listFromJson(getIntent().getStringExtra("items"));
        pos = getIntent().getIntExtra("pos", 0);
        resumeMs = getIntent().getIntExtra("resume", 0);
        String profName = getIntent().getStringExtra("profile");
        if (profName != null) {
            for (Profile p : Prefs.profiles(this)) if (p.name.equals(profName)) profile = p;
        }
        video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            public void onPrepared(MediaPlayer mp) {
                prepared = true;
                loading.setVisibility(View.GONE);
                Item it = current();
                if (it != null && !it.isLive() && video.getDuration() > 0) {
                    seekBar.setMax(video.getDuration());
                    seekBar.setKeyProgressIncrement(30000);
                    seekBar.setVisibility(View.VISIBLE);
                    // v2.2: resume where Continue Watching left off.
                    if (!resumed && resumeMs > 0 && resumeMs < video.getDuration()) {
                        resumed = true;
                        video.seekTo(resumeMs);
                        progressView.setText("Resuming from " + fmt(resumeMs));
                    }
                    // Resume applies once, to the first item only — clear it so
                    // switching channels with UP/DOWN never seeks the new item
                    // to the old item's position.
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
            public void onCompletion(MediaPlayer mp) {
                // v2.2: finished — ProgressStore drops the entry (>= 90%).
                saveProgress();
                updatePlayLabel();
            }
        });
        playBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { togglePlay(); }
        });
        favBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Item it = current();
                if (it == null) return;
                Prefs.toggleFav(PlayerActivity.this, it);
                updateFavLabel();
            }
        });
        retryBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                playCurrent();
                playBtn.requestFocus();
            }
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
        playBtn.requestFocus();
        progressTick.run();
    }
    protected void onDestroy() { super.onDestroy(); saveProgress(); handler.removeCallbacks(progressTick); }
    protected void onPause() {
        super.onPause();
        // v2.2: keep the resume point when leaving the player.
        saveProgress();
        try { video.pause(); } catch (Exception ignored) {}
    }

    private void showError(Item it) {
        // v2.2: hide the spinner here too — the setVideoURI-throws path used
        // to leave it spinning over the error panel.
        loading.setVisibility(View.GONE);
        String host = it == null ? "unknown host" : hostOf(it.url);
        errorView.setText("Could not play this stream.\nHost: " + host
                + "\n\nTry another channel, or press BACK to return.");
        errorPanel.setVisibility(View.VISIBLE);
        retryBtn.requestFocus();
    }
    private void updateFavLabel() {
        Item it = current();
        favBtn.setText(it != null && Prefs.isFav(this, it) ? "★ Favorite" : "☆ Favorite");
    }
    private void updatePlayLabel() {
        boolean playing = false;
        try { playing = video.isPlaying(); } catch (Exception ignored) {}
        playBtn.setText(playing ? "Pause" : "Play");
    }
    private void togglePlay() {
        try {
            if (video.isPlaying()) video.pause(); else video.start();
        } catch (Exception ignored) {}
        updatePlayLabel();
    }
    private void playCurrent() {
        // v2.2: keep the previous item's resume point before switching.
        saveProgress();
        final Item it = current();
        if (it == null) { finish(); return; }
        prepared = false;
        resumed = false;
        saveTick = 0;
        errorPanel.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        seekBar.setVisibility(View.GONE);
        nameView.setText(it.name);
        groupView.setText(it.group + " • " + it.type);
        liveView.setVisibility(it.isLive() ? View.VISIBLE : View.GONE);
        updateFavLabel();
        updatePlayLabel();
        Prefs.addRecent(this, it);
        // Real guide only: Xtream short EPG for this stream, else the honest line.
        if (it.isLive() && profile != null && profile.isXtream() && it.id.length() > 0) {
            nowView.setText("Loading guide…");
            Api.shortEpg(profile, it.id, new Api.Cb<Api.Epg>() {
                public void done(final Api.Epg e, String err) {
                    runOnUiThread(new Runnable() { public void run() {
                        if (current() != it) return;
                        if (e.now.length() > 0)
                            nowView.setText("Now: " + e.now + (e.next.length() > 0 ? "\nNext: " + e.next : ""));
                        else nowView.setText("No guide data from this provider");
                    }});
                }
            });
        } else if (it.isLive()) {
            nowView.setText("No guide data from this provider");
        } else {
            nowView.setText(it.plot.length() > 0 ? it.plot : "");
        }
        try {
            // Some providers reject streams without a User-Agent; send the
            // same one the API calls use.
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("User-Agent", Api.USER_AGENT);
            video.stopPlayback();
            video.setVideoURI(Uri.parse(it.url), headers);
            // Playback starts in onPrepared.
        } catch (Exception e) { showError(it); }
    }
    private static String hostOf(String url) {
        try { String h = Uri.parse(url).getHost(); return h == null ? "unknown host" : h; }
        catch (Exception e) { return "unknown host"; }
    }
    // dispatchKeyEvent runs before focus navigation, so UP/DOWN always
    // changes channel even while a transport control has focus. LEFT/RIGHT
    // and OK are left to the focused control when one has focus (the seek
    // bar seeks natively in 30s steps; a button takes the click).
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
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
                Item it = current();
                if (it != null && !it.isLive()) {
                    try {
                        int p = video.getCurrentPosition()
                                + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -30000 : 30000);
                        video.seekTo(Math.max(0, p));
                    } catch (Exception ignored) {}
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(e);
    }
}
