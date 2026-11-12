package com.sateam.danzinject;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.StatFs;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainDashboardActivity extends Activity {
    static {
        System.loadLibrary("danzinject");
    }

    private LicenseManager licenseManager;
    private String userRole;
    private String activeKey;
    private String userHwid;
    private long expiresAt;

    private LinearLayout tabProfileContent;
    private LinearLayout tabGameToolsContent;
    private LinearLayout tabDevKeygenContent;

    private TextView uptimeTimerView;
    private long startTimestampMs;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private String selectedTargetPkg = "com.dts.freefireth";
    private int danzWarePerfLevel = 90;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        licenseManager = new LicenseManager(this);
        userRole = getIntent().getStringExtra("ROLE");
        activeKey = getIntent().getStringExtra("KEY");
        userHwid = getIntent().getStringExtra("HWID");
        expiresAt = getIntent().getLongExtra("EXPIRES", 0L);
        startTimestampMs = System.currentTimeMillis();

        if (!Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        }

        startService(new Intent(this, OverlayEngineService.class));

        buildDashboardLayout();
        startUptimeClock();
    }

    private void buildDashboardLayout() {
        LinearLayout master = new LinearLayout(this);
        master.setOrientation(LinearLayout.VERTICAL);
        master.setBackgroundColor(Color.parseColor("#090B0E"));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(32, 36, 32, 24);
        topBar.setBackgroundColor(Color.parseColor("#10141A"));

        ImageView smallLogo = new ImageView(this);
        int logoRes = getResources().getIdentifier("danz_logo", "drawable", getPackageName());
        if (logoRes != 0) smallLogo.setImageResource(logoRes);
        LinearLayout.LayoutParams lpLogo = new LinearLayout.LayoutParams(84, 84);
        lpLogo.rightMargin = 20;
        topBar.addView(smallLogo, lpLogo);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        TextView headTitle = new TextView(this);
        headTitle.setText("DANZ TOOLS // SHIZUKU A11+");
        headTitle.setTextColor(Color.parseColor("#00E676"));
        headTitle.setTextSize(17f);
        headTitle.setTypeface(Typeface.DEFAULT_BOLD);

        TextView roleBadge = new TextView(this);
        roleBadge.setText("ROLE: " + userRole + " | HWID: " + userHwid + " | ADB: ACTIVE");
        roleBadge.setTextColor(Color.parseColor("#B829FF"));
        roleBadge.setTextSize(11f);
        titleCol.addView(headTitle);
        titleCol.addView(roleBadge);
        topBar.addView(titleCol);
        master.addView(topBar);

        ScrollView bodyScroll = new ScrollView(this);
        bodyScroll.setFillViewport(true);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        bodyScroll.setLayoutParams(scrollLp);

        LinearLayout pageHolder = new LinearLayout(this);
        pageHolder.setOrientation(LinearLayout.VERTICAL);
        pageHolder.setPadding(28, 24, 28, 40);

        tabProfileContent = buildProfileTab();
        tabGameToolsContent = buildGameToolsTab();
        tabDevKeygenContent = buildDeveloperKeygenTab();

        tabGameToolsContent.setVisibility(View.GONE);
        tabDevKeygenContent.setVisibility(View.GONE);

        pageHolder.addView(tabProfileContent);
        pageHolder.addView(tabGameToolsContent);
        pageHolder.addView(tabDevKeygenContent);
        bodyScroll.addView(pageHolder);
        master.addView(bodyScroll);

        LinearLayout bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setBackgroundColor(Color.parseColor("#12161F"));
        bottomNav.setPadding(16, 16, 16, 16);

        Button btnTabProfile = makeNavButton("Profile");
        Button btnTabTools   = makeNavButton("⚡ DANZ TOOLS");
        Button btnTabKeygen  = makeNavButton("🔑 Create Key");

        btnTabProfile.setOnClickListener(v -> switchTab(0));
        btnTabTools.setOnClickListener(v -> switchTab(1));
        btnTabKeygen.setOnClickListener(v -> switchTab(2));

        LinearLayout.LayoutParams navItemLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        navItemLp.setMargins(8, 0, 8, 0);
        bottomNav.addView(btnTabProfile, navItemLp);
        bottomNav.addView(btnTabTools, navItemLp);

        if ("DEVELOPER".equals(userRole) || "RESELLER".equals(userRole)) {
            bottomNav.addView(btnTabKeygen, navItemLp);
        }

        master.addView(bottomNav);
        setContentView(master);
    }

    private void switchTab(int idx) {
        tabProfileContent.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        tabGameToolsContent.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        tabDevKeygenContent.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
    }

    private LinearLayout buildProfileTab() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout envCard = makeCard("#11261D", "#00E676");
        envCard.addView(makeTitle("Skor Lingkungan: 48 (Shizuku ADB UID 2000 Verified)", "#00E676"));
        envCard.addView(makeSub("Khusus Android 11+ • Tanpa Root • Stabilitas & Performa Maksimal"));
        col.addView(envCard);

        LinearLayout userCard = makeCard("#141923", "#2A3546");
        userCard.addView(makeTitle("Profil Pengguna & Lisensi DANZ TOOLS", "#FFFFFF"));
        userCard.addView(makeSub("HWID: " + userHwid + "\nVerified Access ✔\nKey Aktif: " + activeKey +
                "\nBerlaku sampai: " + LicenseManager.formatTimestamp(expiresAt)));
        col.addView(userCard);

        LinearLayout svcCard = makeCard("#11221C", "#00E676");
        svcCard.addView(makeTitle("Layanan Shizuku [ONLINE]", "#00E676"));
        uptimeTimerView = new TextView(this);
        uptimeTimerView.setText("00:00:00 Waktu Aktif");
        uptimeTimerView.setTextColor(Color.WHITE);
        uptimeTimerView.setTextSize(22f);
        uptimeTimerView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        svcCard.addView(uptimeTimerView);
        svcCard.addView(makeSub("PID: " + Process.myPid() + "   SHELL UID: 2000 (ADB)   VER: 2.0.0(SHIZUKU)"));
        col.addView(svcCard);

        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long totalRamGb = (mi.totalMem / (1024 * 1024 * 1024)) + 1;
        int usedRamPct = (int) (((mi.totalMem - mi.availMem) * 100L) / mi.totalMem);

        StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
        long totalDiskGb = (statFs.getTotalBytes() / (1024 * 1024 * 1024));
        int usedDiskPct = (int) (((statFs.getTotalBytes() - statFs.getAvailableBytes()) * 100L) / statFs.getTotalBytes());

        LinearLayout sysCard = makeCard("#141923", "#2A3546");
        sysCard.addView(makeTitle("Informasi Sistem (" + Build.MODEL + " - Android " + Build.VERSION.RELEASE + ")", "#FFFFFF"));
        sysCard.addView(makeSub(
                "• RAM Usage: " + usedRamPct + "% (Total " + totalRamGb + " GB)\n" +
                "• Storage: " + usedDiskPct + "% (Total " + totalDiskGb + " GB)\n" +
                "• CPU ABI: " + Build.SUPPORTED_ABIS[0] + " (Android API " + Build.VERSION.SDK_INT + ")\n" +
                "• Mode Eksekusi: Wireless ADB Shizuku Binder"
        ));
        col.addView(sysCard);

        return col;
    }

    private LinearLayout buildGameToolsTab() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout combatCard = makeCard("#191229", "#B829FF");
        combatCard.addView(makeTitle("👑 DANZ TOOLS // MAIN ENGINE (SHIZUKU LIVE)", "#B829FF"));
        combatCard.addView(makeSub("Sinkron langsung dengan Floating Menu DANZ TOOLS di dalam Free Fire"));

        combatCard.addView(makeEngineSwitch("1. DRAG HS (Auto Cranial Snap)", 1));
        combatCard.addView(makeEngineSwitch("2. AIMDRAG (Y-Axis Touch Velocity Boost)", 2));
        combatCard.addView(makeEngineSwitch("3. AIM SMOOTH (Humanoid Lerp Aim)", 3));
        combatCard.addView(makeEngineSwitch("4. CONTROL RECOIL (Touch Dampening & Stability)", 4));

        Switch swDanzWarePerf = new Switch(this);
        swDanzWarePerf.setText("5. DANZ WARE (Extreme Performance & FPS Booster [Sorenware Gamo])");
        swDanzWarePerf.setTextColor(Color.parseColor("#00FF9D"));
        swDanzWarePerf.setPadding(0, 12, 0, 12);
        swDanzWarePerf.setOnCheckedChangeListener((b, checked) -> {
            OverlayEngineService.setCombatFeature(5, checked);
            ShizukuShellManager.applyDanzWarePerformanceBoost(selectedTargetPkg, checked, danzWarePerfLevel);
            Toast.makeText(this, checked
                    ? "DANZWARE Performance Boost AKTIF (120FPS Unlock + Fixed Power Mode + Skiagl)"
                    : "DANZWARE Performance Boost Dinonaktifkan", Toast.LENGTH_SHORT).show();
        });
        combatCard.addView(swDanzWarePerf);

        combatCard.addView(makeSliderWithLabels("Tingkat Boost Performa DANZWARE (FPS & GPU Scale)", 100, 90,
                val -> danzWarePerfLevel = val));
        col.addView(combatCard);

        LinearLayout dpiCard = makeCard("#141923", "#00E676");
        dpiCard.addView(makeTitle("DPI Smart Costum [VIP - Shizuku ADB]", "#00E676"));
        dpiCard.addView(makeSub("Mengubah kerapatan piksel layar secara instan melalui Shizuku UID 2000."));

        final EditText dpiInput = new EditText(this);
        dpiInput.setHint("Masukkan DPI (contoh: 580)");
        dpiInput.setTextColor(Color.WHITE);
        dpiInput.setHintTextColor(Color.GRAY);
        dpiCard.addView(dpiInput);

        Button applyDpiBtn = makeActionButton("KETUK PENGATURAN DPI", "#00E676");
        applyDpiBtn.setOnClickListener(v -> {
            String val = dpiInput.getText().toString().trim();
            if (!val.isEmpty()) {
                ShizukuShellManager.execAdbAsync("wm density " + val);
                Toast.makeText(this, "Shizuku ADB: DPI diatur ke " + val, Toast.LENGTH_SHORT).show();
            }
        });
        dpiCard.addView(applyDpiBtn);

        Button resetDpiBtn = makeActionButton("KETUK UNTUK MENGATUR ULANG DPI", "#263238");
        resetDpiBtn.setOnClickListener(v -> {
            ShizukuShellManager.execAdbAsync("wm density reset && wm size reset");
            Toast.makeText(this, "Shizuku ADB: DPI & Resolusi di-reset ke bawaan!", Toast.LENGTH_SHORT).show();
        });
        dpiCard.addView(resetDpiBtn);
        col.addView(dpiCard);

        LinearLayout sensCard = makeCard("#141923", "#00E676");
        sensCard.addView(makeTitle("Patch Sensitivitas 1.0", "#FFFFFF"));
        sensCard.addView(makeSub("Pilih paket target Free Fire yang akan diinjeksi profil performanya."));

        LinearLayout pkgRow = new LinearLayout(this);
        pkgRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnFfTh  = makeActionButton("com.dts.freefireth", "#00E676");
        Button btnFfMax = makeActionButton("com.dts.freefiremax", "#263238");

        btnFfTh.setOnClickListener(v -> {
            selectedTargetPkg = "com.dts.freefireth";
            OverlayEngineService.setTargetPackage(selectedTargetPkg);
            Toast.makeText(this, "Target: Free Fire Original", Toast.LENGTH_SHORT).show();
        });
        btnFfMax.setOnClickListener(v -> {
            selectedTargetPkg = "com.dts.freefiremax";
            OverlayEngineService.setTargetPackage(selectedTargetPkg);
            Toast.makeText(this, "Target: Free Fire MAX", Toast.LENGTH_SHORT).show();
        });
        pkgRow.addView(btnFfTh);
        pkgRow.addView(btnFfMax);
        sensCard.addView(pkgRow);

        sensCard.addView(makeSliderWithLabels("Tingkat Sensitivitas [VIP] (Terendah -> Tertinggi)", 4, 3,
                progress -> ShizukuShellManager.execAdbAsync(
                        "settings put system pointer_speed " + (progress + 3))));
        col.addView(sensCard);

        LinearLayout touchCard = makeCard("#141923", "#00E676");
        touchCard.addView(makeTitle("Akselerator Sentuh [Shizuku]", "#FFFFFF"));
        touchCard.addView(makeSub("Mempercepat respon multi-tap dan gesture layar di Android 11+."));
        touchCard.addView(makeSliderWithLabels("Klik Batas Waktu [VIP]", 100, 90,
                progress -> ShizukuShellManager.execAdbAsync(
                        "settings put secure long_press_timeout " + Math.max(150, 400 - (progress * 2)))));
        touchCard.addView(makeSliderWithLabels("Batas Waktu Berganda [VIP] (Bawaan -> Perangkat)", 4, 4,
                progress -> ShizukuShellManager.execAdbAsync(
                        "settings put secure multi_press_timeout " + Math.max(100, 300 - (progress * 40)))));
        col.addView(touchCard);

        LinearLayout sfCard = makeCard("#141923", "#00E676");
        Switch swSf = new Switch(this);
        swSf.setText("Pelempar Permukaan [VIP] (SurfaceFlinger 120Hz Hardware Compositing)");
        swSf.setTextColor(Color.WHITE);
        swSf.setOnCheckedChangeListener((b, checked) -> {
            ShizukuShellManager.execAdbAsync(
                    "service call SurfaceFlinger 1008 i32 " + (checked ? 0 : 1) +
                    " && setprop debug.sf.latch_unsignaled " + (checked ? 1 : 0));
            Toast.makeText(this, checked ? "SurfaceFlinger Hardware Compositing Aktif" : "Nonaktif", Toast.LENGTH_SHORT).show();
        });
        sfCard.addView(swSf);
        col.addView(sfCard);

        LinearLayout goCard = makeCard("#141923", "#00E676");
        goCard.addView(makeTitle("Optimalkan GoPhone [VIP - Shizuku]", "#00E676"));
        goCard.addView(makeSub("Mengurangi beban CPU & GPU latar belakang Android 11+ secara instan."));
        goCard.addView(makeCheckTweak("Freezer Scheduler",
                "device_config put activity_manager use_freezer true",
                "device_config put activity_manager use_freezer false"));
        goCard.addView(makeCheckTweak("Max Cached Processes (4 Proses)",
                "device_config put activity_manager max_cached_processes 4",
                "device_config put activity_manager max_cached_processes 32"));
        goCard.addView(makeCheckTweak("Memory Compaction",
                "device_config put activity_manager use_compaction true",
                "device_config put activity_manager use_compaction false"));
        goCard.addView(makeCheckTweak("Imperceptible Kill Background",
                "am kill-all", ""));
        col.addView(goCard);

        LinearLayout cacheAngleCard = makeCard("#141923", "#2A3546");
        Switch swCache = new Switch(this);
        swCache.setText("Bersihkan Cache (Trim System & Game Cache via Shizuku)");
        swCache.setTextColor(Color.WHITE);
        swCache.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                ShizukuShellManager.execAdbAsync("pm trim-caches 4096M && am kill-all");
                Toast.makeText(this, "Cache sistem & memori dibersihkan via Shizuku!", Toast.LENGTH_SHORT).show();
            }
        });
        cacheAngleCard.addView(swCache);

        Switch swAngle = new Switch(this);
        swAngle.setText("ANGLE Rendering (OpenGL ES melalui Vulkan ANGLE)");
        swAngle.setTextColor(Color.WHITE);
        swAngle.setOnCheckedChangeListener((b, checked) -> {
            ShizukuShellManager.execAdbAsync(
                    "settings put global angle_gl_driver_selection_pkgs " + selectedTargetPkg +
                    " && settings put global angle_gl_driver_selection_values " + (checked ? "angle" : "default"));
            Toast.makeText(this, checked ? "Vulkan ANGLE Driver diaktifkan untuk " + selectedTargetPkg : "Default Driver", Toast.LENGTH_SHORT).show();
        });
        cacheAngleCard.addView(swAngle);
        col.addView(cacheAngleCard);

        LinearLayout chCard = makeCard("#141923", "#00E676");
        chCard.addView(makeTitle("Kustomisasi Bidik Anda (Custom Crosshair)", "#00E676"));
        chCard.addView(makeSub("Memudahkan membidik ketika menggunakan senjata tanpa crosshair."));

        Switch swCrosshair = new Switch(this);
        swCrosshair.setText("Tampilkan Custom Crosshair di Layar");
        swCrosshair.setTextColor(Color.WHITE);
        swCrosshair.setOnCheckedChangeListener((b, isChecked) -> OverlayEngineService.setCrosshairVisible(isChecked));
        chCard.addView(swCrosshair);

        chCard.addView(makeSliderWithLabels("Rotasi Sudut Bidik (0° - 360°)", 360, 45,
                OverlayEngineService::setCrosshairAngle));
        chCard.addView(makeSliderWithLabels("Ukuran Crosshair (Size)", 120, 58,
                OverlayEngineService::setCrosshairSize));
        chCard.addView(makeSliderWithLabels("Opacity Crosshair", 255, 230,
                OverlayEngineService::setCrosshairAlpha));

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        int[] palette = {Color.WHITE, Color.CYAN, Color.RED, Color.parseColor("#FF8800"), Color.YELLOW, Color.GREEN, Color.parseColor("#B829FF")};
        for (int c : palette) {
            Button cBtn = new Button(this);
            cBtn.setBackgroundColor(c);
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(72, 72);
            cLp.setMargins(8, 12, 8, 12);
            cBtn.setOnClickListener(v -> OverlayEngineService.setCrosshairColor(c));
            colorRow.addView(cBtn, cLp);
        }
        chCard.addView(colorRow);
        col.addView(chCard);

        LinearLayout rogCard = makeCard("#141923", "#00E676");
        rogCard.addView(makeTitle("Pemantauan ROG (Real-time ROG Style HUD)", "#00E676"));
        rogCard.addView(makeSub("Pemantauan Suhu, Jam, GPU, CPU, RAM, Battery, dan FPS secara real-time."));

        Switch swRog = new Switch(this);
        swRog.setText("Aktifkan Floating Bar Pemantauan ROG");
        swRog.setTextColor(Color.WHITE);
        swRog.setChecked(true);
        swRog.setOnCheckedChangeListener((b, isChecked) -> OverlayEngineService.setRogBarVisible(isChecked));
        rogCard.addView(swRog);

        String[] rogMetrics = {"CPU Information", "RAM Information", "Temperature Information", "Battery Information", "FPS Information", "Time Information"};
        for (int i = 0; i < rogMetrics.length; i++) {
            final int idx = i;
            CheckBox cb = new CheckBox(this);
            cb.setText(rogMetrics[i]);
            cb.setTextColor(Color.WHITE);
            cb.setChecked(true);
            cb.setOnCheckedChangeListener((b, checked) -> OverlayEngineService.setRogMetricEnabled(idx, checked));
            rogCard.addView(cb);
        }
        col.addView(rogCard);

        return col;
    }

    private LinearLayout buildDeveloperKeygenTab() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout genCard = makeCard("#1A102B", "#B829FF");
        genCard.addView(makeTitle("🔑 DANZ TOOLS — PANEL DEVELOPER / RESELLER", "#B829FF"));
        genCard.addView(makeSub("Sistem 1 Key 1 Device (Otomatis mengunci HWID perangkat pertama yang login dengan key tersebut)."));

        genCard.addView(makeTitle("Pilih Durasi / Paket Lisensi:", "#FFFFFF"));

        final Spinner tierSpinner = new Spinner(this);
        String[] tierNames = {
                "1 Day (1 Hari - 1 Key 1 Device)",
                "3 Day (3 Hari - 1 Key 1 Device)",
                "7 Day (7 Hari - 1 Key 1 Device)",
                "15 Day (15 Hari - 1 Key 1 Device)",
                "30 Day (30 Hari - 1 Key 1 Device)",
                "Permanent (Selamanya - 1 Key 1 Device)",
                "Reseller (Hak Akses Buat Key - 1 Key 1 Device)"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, tierNames);
        tierSpinner.setAdapter(adapter);
        tierSpinner.setBackgroundColor(Color.parseColor("#2A1E44"));
        genCard.addView(tierSpinner);

        final EditText optionalHwidInput = new EditText(this);
        optionalHwidInput.setHint("Opsional: Kunci langsung ke HWID tertentu (Kosongkan = Auto Bind saat user login)");
        optionalHwidInput.setTextColor(Color.WHITE);
        optionalHwidInput.setHintTextColor(Color.parseColor("#8878A6"));
        genCard.addView(optionalHwidInput);

        final TextView outputKeyBox = new TextView(this);
        outputKeyBox.setText("Key baru akan muncul di sini...");
        outputKeyBox.setTextColor(Color.parseColor("#00FF9D"));
        outputKeyBox.setTextSize(15f);
        outputKeyBox.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        outputKeyBox.setPadding(16, 24, 16, 24);
        genCard.addView(outputKeyBox);

        final TextView dbDumpView = new TextView(this);
        dbDumpView.setTextColor(Color.parseColor("#D4C4E8"));
        dbDumpView.setTextSize(11f);
        dbDumpView.setTypeface(Typeface.MONOSPACE);

        Button btnCreateKey = makeActionButton("GENERATE KEY (1 KEY 1 DEVICE) & SALIN", "#B829FF");
        btnCreateKey.setOnClickListener(v -> {
            int pos = tierSpinner.getSelectedItemPosition();
            LicenseManager.Tier[] tiers = LicenseManager.Tier.values();
            LicenseManager.Tier chosen = tiers[pos];

            String newKey = licenseManager.generateLicenseKey(chosen, optionalHwidInput.getText().toString());
            outputKeyBox.setText("KEY CREATED:\n" + newKey + "\nPaket: " + chosen.label);

            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("DANZ_KEY", newKey));
            dbDumpView.setText(licenseManager.dumpAllKeysSummary());
            Toast.makeText(this, "Key " + chosen.label + " berhasil dibuat & disalin!", Toast.LENGTH_SHORT).show();
        });
        genCard.addView(btnCreateKey);

        final EditText resetKeyInput = new EditText(this);
        resetKeyInput.setHint("Masukkan Key untuk Reset HWID (Ganti HP)");
        resetKeyInput.setTextColor(Color.WHITE);
        resetKeyInput.setHintTextColor(Color.GRAY);
        genCard.addView(resetKeyInput);

        Button btnResetHwid = makeActionButton("RESET HWID KEY TERSEBUT", "#263238");
        btnResetHwid.setOnClickListener(v -> {
            boolean ok = licenseManager.resetHwidForKey(resetKeyInput.getText().toString());
            dbDumpView.setText(licenseManager.dumpAllKeysSummary());
            Toast.makeText(this, ok ? "HWID Key berhasil di-reset!" : "Key tidak ditemukan", Toast.LENGTH_SHORT).show();
        });
        genCard.addView(btnResetHwid);

        genCard.addView(makeTitle("\nDaftar Key Aktif & Status Ikatan HWID:", "#FFFFFF"));
        dbDumpView.setText(licenseManager.dumpAllKeysSummary());
        genCard.addView(dbDumpView);

        col.addView(genCard);
        return col;
    }

    private interface SliderCallback {
        void onValue(int val);
    }

    private LinearLayout makeSliderWithLabels(final String title, int max, int def, final SliderCallback cb) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 12, 0, 12);

        final TextView tv = new TextView(this);
        tv.setText(title + " : " + def);
        tv.setTextColor(Color.parseColor("#00E676"));
        tv.setTextSize(12f);

        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(def);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tv.setText(title + " : " + progress);
                cb.onValue(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        box.addView(tv);
        box.addView(sb);
        return box;
    }

    private Switch makeEngineSwitch(String label, final int featId) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextColor(Color.WHITE);
        sw.setPadding(0, 10, 0, 10);
        sw.setOnCheckedChangeListener((b, checked) -> OverlayEngineService.setCombatFeature(featId, checked));
        return sw;
    }

    private CheckBox makeCheckTweak(String label, final String cmdOn, final String cmdOff) {
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setTextColor(Color.WHITE);
        cb.setOnCheckedChangeListener((b, checked) -> {
            String cmd = checked ? cmdOn : cmdOff;
            if (!cmd.isEmpty()) {
                ShizukuShellManager.execAdbAsync(cmd);
            }
        });
        return cb;
    }

    private LinearLayout makeCard(String bgHex, String strokeHex) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(28, 28, 28, 28);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(bgHex));
        gd.setCornerRadius(20f);
        gd.setStroke(2, Color.parseColor(strokeHex));
        card.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 24;
        card.setLayoutParams(lp);
        return card;
    }

    private TextView makeTitle(String text, String colorHex) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(colorHex));
        tv.setTextSize(15f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private TextView makeSub(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#9BA7B8"));
        tv.setTextSize(12f);
        tv.setPadding(0, 6, 0, 10);
        return tv;
    }

    private Button makeActionButton(String label, String bgHex) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(bgHex));
        gd.setCornerRadius(14f);
        b.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 10;
        b.setLayoutParams(lp);
        return b;
    }

    private Button makeNavButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor("#1D2432"));
        return b;
    }

    private void startUptimeClock() {
        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                long elapsedSec = (System.currentTimeMillis() - startTimestampMs) / 1000L;
                long hrs = elapsedSec / 3600L;
                long mins = (elapsedSec % 3600L) / 60L;
                long secs = elapsedSec % 60L;
                if (uptimeTimerView != null) {
                    uptimeTimerView.setText(String.format(Locale.US, "%02d:%02d:%02d Waktu Aktif", hrs, mins, secs));
                }
                uiHandler.postDelayed(this, 1000L);
            }
        }, 1000L);
    }
}
