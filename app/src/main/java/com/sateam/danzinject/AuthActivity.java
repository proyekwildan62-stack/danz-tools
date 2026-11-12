package com.sateam.danzinject;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

public class AuthActivity extends Activity {
    private LicenseManager licenseManager;
    private EditText keyInput;
    private TextView shizukuStatusText;

    private final Shizuku.OnRequestPermissionResultListener permListener =
            (requestCode, grantResult) -> {
                if (requestCode == ShizukuShellManager.SHIZUKU_REQ_CODE) {
                    refreshShizukuStatusUi();
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        licenseManager = new LicenseManager(this);
        Shizuku.addRequestPermissionResultListener(permListener);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#08070C"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(48, 70, 48, 60);

        ImageView logoView = new ImageView(this);
        int resId = getResources().getIdentifier("danz_logo", "drawable", getPackageName());
        if (resId != 0) {
            logoView.setImageResource(resId);
        }
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(320, 320);
        logoLp.bottomMargin = 16;
        root.addView(logoView, logoLp);

        TextView title = new TextView(this);
        title.setText("DANZ TOOLS");
        title.setTextColor(Color.parseColor("#B829FF"));
        title.setTextSize(26f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("ANDROID 11+ EXCLUSIVE • SHIZUKU ADB ENGINE");
        subtitle.setTextColor(Color.parseColor("#00FF9D"));
        subtitle.setTextSize(11f);
        subtitle.setPadding(0, 4, 0, 28);
        root.addView(subtitle);

        LinearLayout shizukuCard = new LinearLayout(this);
        shizukuCard.setOrientation(LinearLayout.VERTICAL);
        shizukuCard.setPadding(32, 28, 32, 28);
        GradientDrawable shizukuBg = new GradientDrawable();
        shizukuBg.setColor(Color.parseColor("#101820"));
        shizukuBg.setCornerRadius(20f);
        shizukuBg.setStroke(2, Color.parseColor("#00E676"));
        shizukuCard.setBackground(shizukuBg);

        shizukuStatusText = new TextView(this);
        shizukuStatusText.setTextSize(12f);
        shizukuStatusText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        shizukuCard.addView(shizukuStatusText);

        Button btnBindShizuku = new Button(this);
        btnBindShizuku.setText("HUBUNGKAN / IZINKAN ADB SHIZUKU");
        btnBindShizuku.setTextColor(Color.WHITE);
        btnBindShizuku.setBackgroundColor(Color.parseColor("#1B3B2B"));
        btnBindShizuku.setOnClickListener(v -> {
            if (!ShizukuShellManager.isAndroid11OrAbove()) {
                Toast.makeText(this, "Wajib Android 11+!", Toast.LENGTH_LONG).show();
                return;
            }
            if (!ShizukuShellManager.isShizukuRunning()) {
                Intent launchShizuku = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                if (launchShizuku != null) {
                    startActivity(launchShizuku);
                } else {
                    Toast.makeText(this, "Aplikasi Shizuku belum aktif!", Toast.LENGTH_LONG).show();
                }
            } else {
                ShizukuShellManager.requestPermission();
            }
            refreshShizukuStatusUi();
        });
        shizukuCard.addView(btnBindShizuku);

        LinearLayout.LayoutParams shizukuLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        shizukuLp.bottomMargin = 24;
        root.addView(shizukuCard, shizukuLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(36, 36, 36, 36);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#13101C"));
        cardBg.setCornerRadius(24f);
        cardBg.setStroke(3, Color.parseColor("#8A2BE2"));
        card.setBackground(cardBg);

        TextView hwidLabel = new TextView(this);
        hwidLabel.setText("DEVICE HWID (1 KEY 1 DEVICE): " + licenseManager.getDeviceHwid());
        hwidLabel.setTextColor(Color.parseColor("#00FF9D"));
        hwidLabel.setTextSize(12f);
        hwidLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        card.addView(hwidLabel);

        Button copyHwidBtn = new Button(this);
        copyHwidBtn.setText("SALIN HWID SAYA");
        copyHwidBtn.setTextColor(Color.WHITE);
        copyHwidBtn.setBackgroundColor(Color.parseColor("#251D38"));
        copyHwidBtn.setOnClickListener(v -> {
            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("HWID", licenseManager.getDeviceHwid()));
            Toast.makeText(this, "HWID disalin!", Toast.LENGTH_SHORT).show();
        });
        card.addView(copyHwidBtn);

        TextView keyHint = new TextView(this);
        keyHint.setText("\nMasukkan License Key DANZ TOOLS:");
        keyHint.setTextColor(Color.WHITE);
        keyHint.setTextSize(13f);
        card.addView(keyHint);

        keyInput = new EditText(this);
        keyInput.setHint("Masukkan Key (User / Reseller / Developer)");
        keyInput.setHintTextColor(Color.parseColor("#6A5D80"));
        keyInput.setTextColor(Color.WHITE);
        keyInput.setSingleLine(true);
        keyInput.setText(licenseManager.getSavedKey());
        card.addView(keyInput);

        Button loginBtn = new Button(this);
        loginBtn.setText("VERIFIKASI SHIZUKU & MASUK");
        loginBtn.setTextColor(Color.WHITE);
        loginBtn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#8A2BE2"));
        btnBg.setCornerRadius(16f);
        loginBtn.setBackground(btnBg);
        loginBtn.setOnClickListener(v -> attemptLogin());

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = 24;
        card.addView(loginBtn, btnLp);

        root.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        scroll.addView(root);
        setContentView(scroll);

        refreshShizukuStatusUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshShizukuStatusUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(permListener);
    }

    private void refreshShizukuStatusUi() {
        boolean isA11 = ShizukuShellManager.isAndroid11OrAbove();
        boolean running = ShizukuShellManager.isShizukuRunning();
        boolean granted = ShizukuShellManager.hasShizukuPermission();

        StringBuilder sb = new StringBuilder();
        sb.append("• OS Android : Android ").append(Build.VERSION.RELEASE)
          .append(" (SDK ").append(Build.VERSION.SDK_INT).append(") ")
          .append(isA11 ? "[OK 11+]" : "[DITOLAK <11]").append("\n");
        sb.append("• Shizuku ADB: ").append(running ? "RUNNING (UID 2000)" : "OFFLINE (Wajib Nyalakan)").append("\n");
        sb.append("• Izin Akses : ").append(granted ? "GRANTED ✔" : "BELUM DIIZINKAN ✖");

        shizukuStatusText.setText(sb.toString());
        shizukuStatusText.setTextColor((isA11 && running && granted)
                ? Color.parseColor("#00E676")
                : Color.parseColor("#FF5252"));
    }

    private void attemptLogin() {
        if (!ShizukuShellManager.isAndroid11OrAbove()) {
            Toast.makeText(this, "DITOLAK: DANZ TOOLS khusus Android 11 ke atas!", Toast.LENGTH_LONG).show();
            return;
        }
        if (!ShizukuShellManager.isShizukuRunning() || !ShizukuShellManager.hasShizukuPermission()) {
            Toast.makeText(this, "WAJIB AKTIFKAN & IZINKAN ADB SHIZUKU TERLEBIH DAHULU!", Toast.LENGTH_LONG).show();
            ShizukuShellManager.requestPermission();
            return;
        }

        String entered = keyInput.getText().toString();
        LicenseManager.ValidationResult res = licenseManager.validateAndBindKey(entered);
        Toast.makeText(this, res.message, Toast.LENGTH_LONG).show();

        if (res.valid) {
            Intent i = new Intent(this, MainDashboardActivity.class);
            i.putExtra("ROLE", res.role);
            i.putExtra("KEY", res.key);
            i.putExtra("HWID", res.hwid);
            i.putExtra("EXPIRES", res.expiresAt);
            startActivity(i);
            finish();
        }
    }
}
