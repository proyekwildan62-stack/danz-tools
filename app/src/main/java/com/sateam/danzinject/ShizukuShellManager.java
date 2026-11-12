package com.sateam.danzinject;

import android.content.pm.PackageManager;
import android.os.Build;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

public class ShizukuShellManager {
    public static final int SHIZUKU_REQ_CODE = 6767;

    public static boolean isAndroid11OrAbove() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    public static boolean isShizukuRunning() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasShizukuPermission() {
        if (!isShizukuRunning()) return false;
        try {
            if (Shizuku.isPreV11()) return false;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void requestPermission() {
        try {
            if (isShizukuRunning() && !hasShizukuPermission()) {
                Shizuku.requestPermission(SHIZUKU_REQ_CODE);
            }
        } catch (Throwable ignored) {}
    }

    public static String execAdbCommand(String command) {
        if (!hasShizukuPermission()) {
            return "ERROR: Shizuku ADB belum aktif!";
        }
        StringBuilder output = new StringBuilder();
        try {
            Method newProcessMethod = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            newProcessMethod.setAccessible(true);

            ShizukuRemoteProcess proc = (ShizukuRemoteProcess) newProcessMethod.invoke(
                    null, new String[]{"sh"}, null, null);

            if (proc != null) {
                OutputStream os = proc.getOutputStream();
                os.write((command + "\nexit\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();

                BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                reader.close();
                proc.waitFor();
                proc.destroy();
            }
        } catch (Throwable e) {
            return "ERR: " + e.getMessage();
        }
        return output.toString().trim();
    }

    public static void execAdbAsync(final String command) {
        new Thread(() -> execAdbCommand(command)).start();
    }

    public static void applyDanzWarePerformanceBoost(final String targetPkg, final boolean enable, final int boostLevel) {
        new Thread(() -> {
            if (!hasShizukuPermission()) return;

            if (enable) {
                String downscaleRatio = (boostLevel >= 80) ? "0.85" : (boostLevel >= 50 ? "0.90" : "disabled");
                StringBuilder batch = new StringBuilder();
                batch.append("cmd game mode performance ").append(targetPkg).append(";\n");
                batch.append("cmd game set --mode 2 --downscale ").append(downscaleRatio).append(" --fps 120 ").append(targetPkg).append(";\n");
                batch.append("cmd power set-fixed-performance-mode-enabled true;\n");
                batch.append("cmd thermalservice override-status 0;\n");
                batch.append("setprop debug.sf.latch_unsignaled 1;\n");
                batch.append("setprop debug.sf.disable_backpressure 1;\n");
                batch.append("setprop debug.hwui.renderer skiagl;\n");
                batch.append("setprop debug.egl.hw 1;\n");
                batch.append("setprop debug.performance.tuning 1;\n");
                batch.append("settings put global window_animation_scale 0.0;\n");
                batch.append("settings put global transition_animation_scale 0.0;\n");
                batch.append("settings put global animator_duration_scale 0.0;\n");
                batch.append("cmd package compile -m speed -f ").append(targetPkg).append(";\n");
                batch.append("am kill-all;\n");
                batch.append("pm trim-caches 4096M;\n");
                execAdbCommand(batch.toString());
            } else {
                StringBuilder restore = new StringBuilder();
                restore.append("cmd game mode standard ").append(targetPkg).append(";\n");
                restore.append("cmd power set-fixed-performance-mode-enabled false;\n");
                restore.append("cmd thermalservice reset;\n");
                restore.append("setprop debug.sf.latch_unsignaled 0;\n");
                restore.append("setprop debug.sf.disable_backpressure 0;\n");
                restore.append("settings put global window_animation_scale 1.0;\n");
                restore.append("settings put global transition_animation_scale 1.0;\n");
                restore.append("settings put global animator_duration_scale 1.0;\n");
                execAdbCommand(restore.toString());
            }
        }).start();
    }
}
