package com.serhat.autosub.live;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

public class LiveLauncherActivity extends Activity {
    private static final int REQ_OVERLAY = 1, REQ_PERM = 2, REQ_PROJ = 3;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT < 29) {
            Toast.makeText(this, "Needs Android 10+", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        next();
    }

    private void next() {
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())), REQ_OVERLAY);
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS}, REQ_PERM);
        } else {
            MediaProjectionManager m =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(m.createScreenCaptureIntent(), REQ_PROJ);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PROJ) {
            if (res == RESULT_OK) {
                Intent i = new Intent(this, LiveCaptionService.class)
                    .putExtra(LiveCaptionService.EXTRA_CODE, res)
                    .putExtra(LiveCaptionService.EXTRA_DATA, data);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
                else startService(i);
            }
            finish();
        } else if (Settings.canDrawOverlays(this)) {
            next();
        } else {
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) next();
        else finish();
    }
}
