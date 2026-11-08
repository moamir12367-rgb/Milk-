package com.milkman.pro;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

/** Lock screen ke upar dikhne wali alarm screen (app jaisa premium look): Stop / Snooze. */
public class AlarmActivity extends Activity {
    private static final int NAVY_TOP = 0xFF141A33;
    private static final int NAVY_BOTTOM = 0xFF2A3566;
    private static final int ORANGE = 0xFFFF7A3D;
    private static final int PEACH = 0xFFFFB48A;

    private ObjectAnimator pulse;

    private final BroadcastReceiver stopped = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable shape(int fill, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView text(String s, int sp, int color, String family) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final String id = getIntent().getStringExtra("id");
        final String title = getIntent().getStringExtra("title") == null
                ? ReminderScheduler.TITLE : getIntent().getStringExtra("title");
        final String body = getIntent().getStringExtra("body") == null ? "" : getIntent().getStringExtra("body");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{NAVY_TOP, NAVY_BOTTOM});
        root.setBackground(bg);
        root.setPadding(dp(22), dp(56), dp(22), dp(36));

        // upar chhota "MILKMAN PRO" chip
        TextView chip = text("MILKMAN PRO", 11, PEACH, "sans-serif-medium");
        chip.setLetterSpacing(0.15f);
        chip.setPadding(dp(16), dp(7), dp(16), dp(7));
        chip.setBackground(shape(0x24FFFFFF, 20));
        root.addView(chip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // abhi ka time + date
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("hh:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(70);
        clock.setTextColor(Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(26);
        root.addView(clock, clp);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(14);
        date.setTextColor(0xB3FFFFFF);
        date.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        date.setGravity(Gravity.CENTER);
        root.addView(date, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // beech mein dhadakta hua orange alarm icon
        FrameLayout iconBox = new FrameLayout(this);
        final View ring = new View(this);
        GradientDrawable ringBg = new GradientDrawable();
        ringBg.setShape(GradientDrawable.OVAL);
        ringBg.setColor(0x55FF7A3D);
        ring.setBackground(ringBg);
        iconBox.addView(ring, new FrameLayout.LayoutParams(dp(120), dp(120), Gravity.CENTER));

        TextView icon = text("⏰", 38, Color.WHITE, "sans-serif");
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setColor(ORANGE);
        icon.setBackground(iconBg);
        iconBox.addView(icon, new FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER));

        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(150), dp(150));
        ilp.topMargin = dp(30);
        root.addView(iconBox, ilp);

        pulse = ObjectAnimator.ofPropertyValuesHolder(ring,
                PropertyValuesHolder.ofFloat("scaleX", 0.8f, 1.3f),
                PropertyValuesHolder.ofFloat("scaleY", 0.8f, 1.3f),
                PropertyValuesHolder.ofFloat("alpha", 0.9f, 0f));
        pulse.setDuration(1300);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.setInterpolator(new LinearInterpolator());
        pulse.start();

        // reminder ka card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(shape(0x1FFFFFFF, 26));
        card.setPadding(dp(20), dp(18), dp(20), dp(20));

        TextView t = text(title, 17, PEACH, "sans-serif-medium");
        card.addView(t);
        TextView m = text(body, 20, Color.WHITE, "sans-serif-medium");
        m.setPadding(0, dp(8), 0, 0);
        m.setLineSpacing(0f, 1.15f);
        card.addView(m);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(26);
        root.addView(card, cardLp);

        // neeche ke buttons ko dhakelne wali khaali jagah
        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(62));
        lp.topMargin = dp(14);

        Button stop = new Button(this);
        stop.setText("ENTER PENDING MILK");
        stop.setAllCaps(true);
        stop.setTextSize(17);
        stop.setLetterSpacing(0.05f);
        stop.setTextColor(Color.WHITE);
        stop.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        stop.setStateListAnimator(null);
        stop.setBackground(shape(ORANGE, 31));
        stop.setOnClickListener(v -> {
            startService(new Intent(this, AlarmService.class).setAction(AlarmService.ACTION_STOP));
            finish();
        });
        root.addView(stop, lp);

        Button snooze = new Button(this);
        snooze.setText(AlarmService.SNOOZE_MIN + " minute baad yaad dilao");
        snooze.setAllCaps(false);
        snooze.setTextSize(15);
        snooze.setTextColor(Color.WHITE);
        snooze.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        snooze.setStateListAnimator(null);
        GradientDrawable outline = shape(0x00000000, 31);
        outline.setStroke(dp(2), 0x66FFFFFF);
        snooze.setBackground(outline);
        snooze.setOnClickListener(v -> {
            startService(new Intent(this, AlarmService.class).setAction(AlarmService.ACTION_SNOOZE)
                    .putExtra("id", id).putExtra("title", title).putExtra("body", body));
            finish();
        });
        root.addView(snooze, lp);

        setContentView(root);

        ContextCompat.registerReceiver(this, stopped, new IntentFilter(AlarmService.ACTION_STOPPED),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    /** Alarm screen khuli ho aur doosra reminder aa jaye to naya text dikhao. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        recreate();
    }

    @Override
    protected void onDestroy() {
        if (pulse != null) pulse.cancel();
        try {
            unregisterReceiver(stopped);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
