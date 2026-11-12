package com.sateam.danzinject;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class OverlayEngineService extends Service {
    static {
        System.loadLibrary("danzinject");
    }

    private static native void nativeInitEngine(String targetPkg);
    private static native void nativeStopEngine();
    private static native void nativeSetFeature(int featureId, boolean enabled);
    private static native void nativeSetParam(int paramId, float value);

    private static OverlayEngineService instance;
    private static String currentTargetPkg = "com.dts.freefireth";
    private static int currentDanzPerfLevel = 90;

    private WindowManager wm;
    private View floatingMenuRoot;
    private CustomCrosshairView crosshairView;
    private TextView rogMonitorTextView;
    private View rogMonitorContainer;

    private final boolean[] rogMetricFlags = {true, true, true, true, true, true};
    private int currentFps = 60;
    private int frameCounter = 0;
    private long lastFpsSecondNs = System.nanoTime();
    private final Handler hudHandler = new Handler(Looper.getMainLooper());

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        nativeInitEngine(currentTargetPkg);
        setupFloatingDanzToolsMenu();
        setupCustomCrosshairOverlay();
        setupRogMonitorOverlay();
        startFpsAndRogTicker();
    }

    private void setupFloatingDanzToolsMenu() {
        int flag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 40;
        params.y = 220;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        final ImageView floatingLogoBadge = new ImageView(this);
        int resId = getResources().getIdentifier("danz_logo", "drawable", getPackageName());
        if (resId != 0) {
            floatingLogoBadge.setImageResource(resId);
        }
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(125, 125);
        floatingLogoBadge.setLayoutParams(badgeLp);

        final LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(24, 20, 24, 20);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#EE0E0B16"));
        bg.setCornerRadius(20f);
        bg.setStroke(3, Color.parseColor("#B829FF"));
        panel.setBackground(bg);
        panel.setVisibility(View.GONE);

        TextView header = new TextView(this);
        header.setText("👑 DANZ TOOLS // SHIZUKU A11+");
        header.setTextColor(Color.parseColor("#B829FF"));
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setTextSize(13f);
        panel.addView(header);

        panel.addView(createSwitch("1. DRAG HS (Head Snap)", 1));
        panel.addView(createSwitch("2. AIMDRAG (Y-Velocity)", 2));
        panel.addView(createSeek("AimDrag Speed", 10, 200, 145));
        panel.addView(createSwitch("3. AIM SMOOTH (Lerp)", 3));
        panel.addView(createSeek("Smooth Rate", 11, 100, 35));
        panel.addView(createSwitch("4. CONTROL RECOIL", 4));
        panel.addView(createSeek("Recoil Dampen %", 12, 100, 85));

        Switch swDanzPerf = new Switch(this);
        swDanzPerf.setText("5. DANZ WARE (Max Performance Boost)");
        swDanzPerf.setTextColor(Color.parseColor("#00FF9D"));
        swDanzPerf.setTextSize(11f);
        swDanzPerf.setOnCheckedChangeListener((b, checked) -> {
            nativeSetFeature(5, checked);
            ShizukuShellManager.applyDanzWarePerformanceBoost(currentTargetPkg, checked, currentDanzPerfLevel);
            Toast.makeText(OverlayEngineService.this,
                    checked ? "DANZWARE Performance Boost ON!" : "DANZWARE Performance Boost OFF",
                    Toast.LENGTH_SHORT).show();
        });
        panel.addView(swDanzPerf);

        panel.addView(createSeek("DanzWare Boost Level %", 13, 100, 90));

        Button hideBtn = new Button(this);
        hideBtn.setText("MINIMIZE KE LOGO");
        hideBtn.setTextColor(Color.WHITE);
        hideBtn.setBackgroundColor(Color.parseColor("#2A1B40"));
        hideBtn.setOnClickListener(v -> {
            panel.setVisibility(View.GONE);
            floatingLogoBadge.setVisibility(View.VISIBLE);
        });
        panel.addView(hideBtn);

        floatingLogoBadge.setOnClickListener(v -> {
            floatingLogoBadge.setVisibility(View.GONE);
            panel.setVisibility(View.VISIBLE);
        });

        View.OnTouchListener dragTouch = new View.OnTouchListener() {
            private int ix, iy;
            private float tx, ty;
            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        ix = params.x; iy = params.y;
                        tx = ev.getRawX(); ty = ev.getRawY();
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        params.x = ix + (int) (ev.getRawX() - tx);
                        params.y = iy + (int) (ev.getRawY() - ty);
                        wm.updateViewLayout(floatingMenuRoot, params);
                        return true;
                }
                return false;
            }
        };
        floatingLogoBadge.setOnTouchListener(dragTouch);
        header.setOnTouchListener(dragTouch);

        root.addView(floatingLogoBadge);
        root.addView(panel);
        floatingMenuRoot = root;
        wm.addView(floatingMenuRoot, params);
    }

    private void setupCustomCrosshairOverlay() {
        int flag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        WindowManager.LayoutParams chParams = new WindowManager.LayoutParams(
                220, 220, flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        chParams.gravity = Gravity.CENTER;

        crosshairView = new CustomCrosshairView(this);
        crosshairView.setVisibility(View.GONE);
        wm.addView(crosshairView, chParams);
    }

    private static class CustomCrosshairView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float angleDeg = 45f;
        private float sizePx = 58f;
        private int color = Color.WHITE;
        private int alpha = 230;

        public CustomCrosshairView(Context ctx) {
            super(ctx);
            paint.setStrokeWidth(4.5f);
            paint.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setColor(color);
            paint.setAlpha(alpha);

            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f;
            float half = sizePx * 0.5f;

            canvas.save();
            canvas.rotate(angleDeg, cx, cy);
            canvas.drawLine(cx - half, cy, cx - 8f, cy, paint);
            canvas.drawLine(cx + 8f, cy, cx + half, cy, paint);
            canvas.drawLine(cx, cy - half, cx, cy - 8f, paint);
            canvas.drawLine(cx, cy + 8f, cx, cy + half, paint);
            canvas.drawCircle(cx, cy, 3f, paint);
            canvas.restore();
        }
    }

    private void setupRogMonitorOverlay() {
        int flag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        final WindowManager.LayoutParams rogParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        rogParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        rogParams.y = 30;

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(24, 10, 24, 10);
        GradientDrawable rogBg = new GradientDrawable();
        rogBg.setColor(Color.parseColor("#DD0B0E14"));
        rogBg.setCornerRadius(14f);
        rogBg.setStroke(2, Color.parseColor("#FF004D"));
        bar.setBackground(rogBg);

        rogMonitorTextView = new TextView(this);
        rogMonitorTextView.setTextColor(Color.WHITE);
        rogMonitorTextView.setTextSize(11f);
        rogMonitorTextView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        bar.addView(rogMonitorTextView);

        bar.setOnTouchListener(new View.OnTouchListener() {
            private int ix, iy;
            private float tx, ty;
            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        ix = rogParams.x; iy = rogParams.y;
                        tx = ev.getRawX(); ty = ev.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        rogParams.x = ix + (int) (ev.getRawX() - tx);
                        rogParams.y = iy + (int) (ev.getRawY() - ty);
                        wm.updateViewLayout(rogMonitorContainer, rogParams);
                        return true;
                }
                return false;
            }
        });

        rogMonitorContainer = bar;
        wm.addView(rogMonitorContainer, rogParams);
    }

    private void startFpsAndRogTicker() {
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                frameCounter++;
                if (frameTimeNanos - lastFpsSecondNs >= 1_000_000_000L) {
                    currentFps = frameCounter;
                    frameCounter = 0;
                    lastFpsSecondNs = frameTimeNanos;
                }
                if (instance != null) {
                    Choreographer.getInstance().postFrameCallback(this);
                }
            }
        });

        hudHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateRogBarText();
                if (instance != null) {
                    hudHandler.postDelayed(this, 1000L);
                }
            }
        }, 1000L);
    }

    private void updateRogBarText() {
        if (rogMonitorTextView == null) return;

        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        int ramPct = (int) (((mi.totalMem - mi.availMem) * 100L) / mi.totalMem);

        Intent batt = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int battPct = 100;
        int tempC = 36;
        if (batt != null) {
            int level = batt.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batt.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) battPct = (level * 100) / scale;
            int rawTemp = batt.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 360);
            tempC = rawTemp / 10;
        }

        StringBuilder sb = new StringBuilder("DANZ ROG ");
        if (rogMetricFlags[0]) sb.append("| SHIZUKU:OK ");
        if (rogMetricFlags[1]) sb.append("| RAM:").append(ramPct).append("% ");
        if (rogMetricFlags[2]) sb.append("| ").append(tempC).append("°C ");
        if (rogMetricFlags[3]) sb.append("| BAT:").append(battPct).append("% ");
        if (rogMetricFlags[4]) sb.append("| FPS:").append(currentFps).append(" ");
        if (rogMetricFlags[5]) {
            String now = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            sb.append("| ").append(now);
        }
        rogMonitorTextView.setText(sb.toString());
    }

    public static void setTargetPackage(String pkg) {
        currentTargetPkg = pkg;
        nativeInitEngine(pkg);
    }

    public static void setCombatFeature(int featId, boolean enabled) {
        nativeSetFeature(featId, enabled);
    }

    public static void setCrosshairVisible(boolean visible) {
        if (instance != null && instance.crosshairView != null) {
            instance.crosshairView.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public static void setCrosshairAngle(int deg) {
        if (instance != null && instance.crosshairView != null) {
            instance.crosshairView.angleDeg = (float) deg;
            instance.crosshairView.invalidate();
        }
    }

    public static void setCrosshairSize(int size) {
        if (instance != null && instance.crosshairView != null) {
            instance.crosshairView.sizePx = Math.max(16f, (float) size);
            instance.crosshairView.invalidate();
        }
    }

    public static void setCrosshairAlpha(int alpha) {
        if (instance != null && instance.crosshairView != null) {
            instance.crosshairView.alpha = alpha;
            instance.crosshairView.invalidate();
        }
    }

    public static void setCrosshairColor(int color) {
        if (instance != null && instance.crosshairView != null) {
            instance.crosshairView.color = color;
            instance.crosshairView.invalidate();
        }
    }

    public static void setRogBarVisible(boolean visible) {
        if (instance != null && instance.rogMonitorContainer != null) {
            instance.rogMonitorContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public static void setRogMetricEnabled(int idx, boolean enabled) {
        if (instance != null && idx >= 0 && idx < instance.rogMetricFlags.length) {
            instance.rogMetricFlags[idx] = enabled;
            instance.updateRogBarText();
        }
    }

    private Switch createSwitch(String label, final int featId) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(Color.WHITE);
        s.setTextSize(11f);
        s.setOnCheckedChangeListener((b, checked) -> nativeSetFeature(featId, checked));
        return s;
    }

    private LinearLayout createSeek(final String label, final int paramId, int max, int def) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final TextView tv = new TextView(this);
        tv.setText(label + ": " + def);
        tv.setTextColor(Color.parseColor("#D4C4E8"));
        tv.setTextSize(10f);

        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(def);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tv.setText(label + ": " + progress);
                if (paramId == 13) {
                    currentDanzPerfLevel = progress;
                }
                nativeSetParam(paramId, (float) progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        box.addView(tv);
        box.addView(sb);
        return box;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        nativeStopEngine();
        if (wm != null) {
            if (floatingMenuRoot != null) wm.removeView(floatingMenuRoot);
            if (crosshairView != null) wm.removeView(crosshairView);
            if (rogMonitorContainer != null) wm.removeView(rogMonitorContainer);
        }
    }
}
