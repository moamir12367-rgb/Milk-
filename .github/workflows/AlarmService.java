package com.milkman.pro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/** Alarm ki tarah lambi ringtone + vibration bajata hai, jab tak Stop / Snooze na dabao (max 3 minute). */
public class AlarmService extends Service {
    static final String ACTION_START = "com.milkman.pro.ALARM_START";
    static final String ACTION_STOP = "com.milkman.pro.ALARM_STOP";
    static final String ACTION_SNOOZE = "com.milkman.pro.ALARM_SNOOZE";
    static final String ACTION_STOPPED = "com.milkman.pro.ALARM_STOPPED";
    static final String CHANNEL = "milk_alarm";
    static final int NOTIF_ID = 7001;
    static final long MAX_RING_MS = 3 * 60 * 1000L;
    static final int SNOOZE_MIN = 10;

    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String curId = "", curTitle = "", curBody = "";

    private final Runnable timeout = new Runnable() {
        @Override
        public void run() {
            // 3 minute tak kisi ne band nahi kiya: awaaz band, par ek normal notification chhod do
            ReminderScheduler.notifyNow(AlarmService.this, curTitle, curBody + " (missed)", NOTIF_ID + 1);
            shutdown();
        }
    };

    static void start(Context c, String id, String title, String body) {
        Intent i = new Intent(c, AlarmService.class).setAction(ACTION_START)
                .putExtra("id", id).putExtra("title", title).putExtra("body", body);
        ContextCompat.startForegroundService(c, i);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            shutdown();
            return START_NOT_STICKY;
        }
        if (ACTION_SNOOZE.equals(action)) {
            ReminderScheduler.scheduleSnooze(this, intent.getStringExtra("id"),
                    intent.getStringExtra("title"), intent.getStringExtra("body"), SNOOZE_MIN);
            shutdown();
            return START_NOT_STICKY;
        }

        curId = intent != null && intent.getStringExtra("id") != null ? intent.getStringExtra("id") : "";
        curTitle = intent != null && intent.getStringExtra("title") != null ? intent.getStringExtra("title") : ReminderScheduler.TITLE;
        curBody = intent != null && intent.getStringExtra("body") != null ? intent.getStringExtra("body") : "";

        createChannel();
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }

        acquireWakeLock();
        startRinging();
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, MAX_RING_MS);
        return START_NOT_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Milkman alarm", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Alarm jaise reminder (ringtone + vibration)");
            ch.setSound(null, null);          // awaaz hum khud bajate hain
            ch.enableVibration(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        Intent act = new Intent(this, AlarmActivity.class)
                .putExtra("id", curId).putExtra("title", curTitle).putExtra("body", curBody)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent full = PendingIntent.getActivity(this, 1, act, flags);

        PendingIntent stopPi = PendingIntent.getService(this, 2,
                new Intent(this, AlarmService.class).setAction(ACTION_STOP), flags);
        PendingIntent snoozePi = PendingIntent.getService(this, 3,
                new Intent(this, AlarmService.class).setAction(ACTION_SNOOZE)
                        .putExtra("id", curId).putExtra("title", curTitle).putExtra("body", curBody), flags);

        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(curTitle)
                .setContentText(curBody)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(curBody))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(full, true)
                .setContentIntent(full)
                .addAction(0, "Enter pending milk", stopPi)
                .addAction(0, SNOOZE_MIN + " min baad", snoozePi)
                .build();
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "milkman:alarm");
            wakeLock.acquire(MAX_RING_MS + 5000);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private void startRinging() {
        stopRinging();
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            player = null;
        }
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                vibrator = vm.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            }
            if (vibrator != null) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 700, 400}, 0),
                        new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            }
        } catch (Exception ignored) {
        }
    }

    private void stopRinging() {
        try {
            if (player != null) {
                if (player.isPlaying()) player.stop();
                player.release();
            }
        } catch (Exception ignored) {
        }
        player = null;
        try {
            if (vibrator != null) vibrator.cancel();
        } catch (Exception ignored) {
        }
    }

    private void shutdown() {
        handler.removeCallbacks(timeout);
        stopRinging();
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {
        }
        sendBroadcast(new Intent(ACTION_STOPPED).setPackage(getPackageName()));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(timeout);
        stopRinging();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
