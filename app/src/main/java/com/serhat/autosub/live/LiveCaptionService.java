package com.serhat.autosub.live;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import java.io.File;

public class LiveCaptionService extends Service {
    static final String EXTRA_CODE = "code";
    static final String EXTRA_DATA = "data";
    static final String ACTION_STOP = "STOP";
    private static final String CH = "live_caption";
    private static final int RATE = 16000;

    private MediaProjection projection;
    private AudioRecord record;
    private Thread worker;
    private volatile boolean running;
    private CaptionOverlay overlay;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())
                || Build.VERSION.SDK_INT < 29) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;
        startForegroundCompat();
        MediaProjectionManager mpm =
            (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent data = intent.getParcelableExtra(EXTRA_DATA);
        projection = mpm.getMediaProjection(intent.getIntExtra(EXTRA_CODE, 0), data);
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { stopSelf(); }
        }, new Handler(Looper.getMainLooper()));
        overlay = new CaptionOverlay(this);
        overlay.show();
        running = true;
        worker = new Thread(this::loop, "live-caption");
        worker.start();
        return START_NOT_STICKY;
    }

    private void startForegroundCompat() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                CH, "Live captions", NotificationManager.IMPORTANCE_LOW));
        }
        Intent stop = new Intent(this, LiveCaptionService.class).setAction(ACTION_STOP);
        PendingIntent pi = PendingIntent.getService(this, 0, stop,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        Notification n = b.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Live captions running")
            .setContentText("Tap to stop")
            .setContentIntent(pi).build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1201, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1201, n);
        }
    }

    private void loop() {
        try {
            overlay.setText("Loading model...");
            File dir = findVoskModel();
            if (dir == null) throw new Exception("No Vosk model installed");
            LibVosk.setLogLevel(LogLevel.WARNINGS);
            Model model = new Model(dir.getAbsolutePath());
            Recognizer rec = new Recognizer(model, RATE);

            AudioPlaybackCaptureConfiguration cfg =
                new AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build();
            AudioFormat fmt = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
            int buf = Math.max(AudioRecord.getMinBufferSize(RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), RATE * 2);
            record = new AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(cfg)
                .setAudioFormat(fmt)
                .setBufferSizeInBytes(buf).build();
            record.startRecording();
            overlay.setText("Listening...");

            byte[] b = new byte[4096];
            while (running) {
                int n = record.read(b, 0, b.length);
                if (n <= 0) continue;
                boolean fin = rec.acceptWaveForm(b, n);
                JSONObject o = new JSONObject(fin ? rec.getResult() : rec.getPartialResult());
                String t = o.optString(fin ? "text" : "partial", "");
                if (fin || !t.isEmpty()) overlay.setText(t);
            }
            rec.close();
            model.close();
        } catch (Exception e) {
            overlay.setText("Error: " + e.getMessage());
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
        } finally {
            stopSelf();
        }
    }

    private File findVoskModel() {
        File ext = getExternalFilesDir(null);
        File[] bases = {
            ext != null ? new File(ext, "vosk_models") : null,
            new File(getFilesDir(), "vosk_models")
        };
        for (File base : bases) {
            if (base == null) continue;
            File[] dirs = base.listFiles();
            if (dirs == null) continue;
            for (File d : dirs) {
                File r = voskRoot(d);
                if (r != null) return r;
            }
        }
        return null;
    }

    private File voskRoot(File d) {
        if (!d.isDirectory()) return null;
        if (new File(d, "am").exists()) return d;
        File[] kids = d.listFiles();
        if (kids != null) {
            for (File k : kids) {
                if (k.isDirectory() && new File(k, "am").exists()) return k;
            }
        }
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (worker != null) {
            try { worker.join(1500); } catch (InterruptedException ignored) {}
        }
        if (record != null) {
            try { record.stop(); } catch (Exception ignored) {}
            record.release();
        }
        if (projection != null) projection.stop();
        if (overlay != null) overlay.hide();
        super.onDestroy();
    }
}
