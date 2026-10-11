package com.serhat.autosub.live;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

class CaptionOverlay {
    private final WindowManager wm;
    private final TextView tv;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean shown;

    CaptionOverlay(Context c) {
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        tv = new TextView(c);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(18);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(32, 16, 32, 16);
        tv.setBackgroundColor(0xB3000000);
        tv.setVisibility(View.GONE);
    }

    void show() {
        ui.post(() -> {
            int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.BOTTOM;
            lp.y = 160;
            wm.addView(tv, lp);
            shown = true;
        });
    }

    void setText(String s) {
        ui.post(() -> {
            tv.setText(s);
            tv.setVisibility(s.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    void hide() {
        ui.post(() -> {
            if (shown) { wm.removeView(tv); shown = false; }
        });
    }
}
