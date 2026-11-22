package com.leo.iptv;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// v3.0 shared UI helpers: dp conversion, TV focus animation, the top-bar clock.
public class Ui {
    public static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    // TV focus: the focused view grows slightly and lifts, so focus is
    // obvious from across the room. The background selector adds the
    // accent outline on top of this.
    public static void focusScale(final View v) {
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View view, boolean hasFocus) {
                view.animate().cancel();
                if (hasFocus) {
                    view.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start();
                    view.setElevation(dp(view.getContext(), 8));
                } else {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    view.setElevation(0);
                }
            }
        });
    }

    // Top-bar clock, refreshed every 20s. Format: "Sun, Nov 22  •  5:45 PM".
    public static void bindClock(final TextView t) {
        final Handler h = new Handler();
        final SimpleDateFormat fmt = new SimpleDateFormat("EEE, MMM d  •  h:mm a", Locale.US);
        final Runnable tick = new Runnable() {
            public void run() {
                t.setText(fmt.format(new Date()));
                h.postDelayed(this, 20000);
            }
        };
        tick.run();
        t.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {}
            public void onViewDetachedFromWindow(View v) { h.removeCallbacks(tick); }
        });
    }
}
