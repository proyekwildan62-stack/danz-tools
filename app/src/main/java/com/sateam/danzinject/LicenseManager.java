package com.sateam.danzinject;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class LicenseManager {
    private static final String PREF_NAME       = "danz_license_vault";
    private static final String KEY_DB_JSON     = "license_records_json";
    private static final String KEY_ACTIVE_CODE = "active_license_key";
    private static final String HMAC_SECRET     = "DANZ_INJECT_SA_TEAM_SECRET_2026_#6767";

    public static final String MASTER_DEV_KEY   = "DANZDEV";

    public enum Tier {
        DAY_1("1DAY", "1 Hari", 1L * 86400000L, "USER"),
        DAY_3("3DAY", "3 Hari", 3L * 86400000L, "USER"),
        DAY_7("7DAY", "7 Hari", 7L * 86400000L, "USER"),
        DAY_15("15DAY", "15 Hari", 15L * 86400000L, "USER"),
        DAY_30("30DAY", "30 Hari", 30L * 86400000L, "USER"),
        PERMANENT("PERM", "Permanent", 36500L * 86400000L, "VIP_PERM"),
        RESELLER("RSLR", "Reseller Access", 3650L * 86400000L, "RESELLER");

        public final String code;
        public final String label;
        public final long durationMs;
        public final String role;

        Tier(String code, String label, long durationMs, String role) {
            this.code = code;
            this.label = label;
            this.durationMs = durationMs;
            this.role = role;
        }
    }

    public static class ValidationResult {
        public final boolean valid;
        public final String message;
        public final String role;
        public final String key;
        public final String hwid;
        public final long expiresAt;

        public ValidationResult(boolean valid, String message, String role, String key, String hwid, long expiresAt) {
            this.valid = valid;
            this.message = message;
            this.role = role;
            this.key = key;
            this.hwid = hwid;
            this.expiresAt = expiresAt;
        }
    }

    private final SharedPreferences prefs;
    private final String deviceHwid;

    public LicenseManager(Context ctx) {
        this.prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.deviceHwid = computeDeviceHwid(ctx);
        ensureMasterKeySeeded();
    }

    public String getDeviceHwid() {
        return deviceHwid;
    }

    private String computeDeviceHwid(Context ctx) {
        String androidId = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        String raw = (androidId != null ? androidId : "NO_AID") + "|" + Build.BOARD + "|" + Build.BRAND + "|" + Build.HARDWARE;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString().toUpperCase(Locale.US);
        } catch (Exception e) {
            return "HWIDFALLBACK01";
        }
    }

    private void ensureMasterKeySeeded() {
        JSONObject db = loadDb();
        if (!db.has(MASTER_DEV_KEY)) {
            try {
                JSONObject rec = new JSONObject();
                rec.put("tier", "DEV_MASTER");
                rec.put("role", "DEVELOPER");
                rec.put("durationMs", 36500L * 86400000L);
                rec.put("boundHwid", "");
                rec.put("activatedAt", 0L);
                rec.put("expiresAt", System.currentTimeMillis() + (36500L * 86400000L));
                db.put(MASTER_DEV_KEY, rec);
                saveDb(db);
            } catch (JSONException ignored) {}
        }
    }

    public String generateLicenseKey(Tier tier, String targetHwidLockOptional) {
        SecureRandom rng = new SecureRandom();
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder randPart = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            randPart.append(chars.charAt(rng.nextInt(chars.length())));
        }

        String sig = signBlock(tier.code + "|" + randPart).substring(0, 4).toUpperCase(Locale.US);
        String newKey = "DANZ-" + tier.code + "-" + randPart + "-" + sig;

        JSONObject db = loadDb();
        try {
            JSONObject rec = new JSONObject();
            rec.put("tier", tier.label);
            rec.put("role", tier.role);
            rec.put("durationMs", tier.durationMs);
            rec.put("boundHwid", targetHwidLockOptional != null ? targetHwidLockOptional.trim().toUpperCase(Locale.US) : "");
            rec.put("activatedAt", 0L);
            rec.put("expiresAt", 0L);
            db.put(newKey, rec);
            saveDb(db);
        } catch (JSONException ignored) {}

        return newKey;
    }

    public ValidationResult validateAndBindKey(String inputKey) {
        if (inputKey == null || inputKey.trim().isEmpty()) {
            return new ValidationResult(false, "Key tidak boleh kosong!", "NONE", "", deviceHwid, 0L);
        }

        String cleanKey = inputKey.trim().toUpperCase(Locale.US);
        JSONObject db = loadDb();

        if (!db.has(cleanKey)) {
            if (!tryImportSelfSignedKey(cleanKey, db)) {
                return new ValidationResult(false, "Key tidak terdaftar atau salah ketik!", "NONE", cleanKey, deviceHwid, 0L);
            }
        }

        try {
            JSONObject rec = db.getJSONObject(cleanKey);
            String boundHwid = rec.optString("boundHwid", "");
            long activatedAt = rec.optLong("activatedAt", 0L);
            long durationMs  = rec.optLong("durationMs", 86400000L);
            long expiresAt   = rec.optLong("expiresAt", 0L);
            String role      = rec.optString("role", "USER");

            if (!boundHwid.isEmpty() && !boundHwid.equalsIgnoreCase(deviceHwid)) {
                return new ValidationResult(
                        false,
                        "DITOLAK (1 Key 1 Device)! Key ini sudah terikat di HWID: " + boundHwid,
                        role, cleanKey, deviceHwid, expiresAt
                );
            }

            long now = System.currentTimeMillis();
            if (activatedAt == 0L) {
                activatedAt = now;
                expiresAt = now + durationMs;
                rec.put("activatedAt", activatedAt);
                rec.put("expiresAt", expiresAt);
            }

            if (now > expiresAt) {
                return new ValidationResult(
                        false,
                        "Key sudah EXPIRED pada " + formatTimestamp(expiresAt),
                        role, cleanKey, deviceHwid, expiresAt
                );
            }

            rec.put("boundHwid", deviceHwid);
            db.put(cleanKey, rec);
            saveDb(db);
            prefs.edit().putString(KEY_ACTIVE_CODE, cleanKey).apply();

            return new ValidationResult(
                    true,
                    "Akses Diterima (" + role + ") - Aktif s/d " + formatTimestamp(expiresAt),
                    role, cleanKey, deviceHwid, expiresAt
            );
        } catch (JSONException e) {
            return new ValidationResult(false, "Kesalahan pembacaan database lisensi.", "NONE", cleanKey, deviceHwid, 0L);
        }
    }

    private boolean tryImportSelfSignedKey(String key, JSONObject db) {
        String[] parts = key.split("-");
        if (parts.length != 4 || !"DANZ".equals(parts[0])) {
            return false;
        }
        String tierCode = parts[1];
        String randPart = parts[2];
        String providedSig = parts[3];

        String expectedSig = signBlock(tierCode + "|" + randPart).substring(0, 4).toUpperCase(Locale.US);
        if (!expectedSig.equals(providedSig)) {
            return false;
        }

        Tier matchedTier = null;
        for (Tier t : Tier.values()) {
            if (t.code.equals(tierCode)) {
                matchedTier = t;
                break;
            }
        }
        if (matchedTier == null) return false;

        try {
            JSONObject rec = new JSONObject();
            rec.put("tier", matchedTier.label);
            rec.put("role", matchedTier.role);
            rec.put("durationMs", matchedTier.durationMs);
            rec.put("boundHwid", "");
            rec.put("activatedAt", 0L);
            rec.put("expiresAt", 0L);
            db.put(key, rec);
            saveDb(db);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    public boolean resetHwidForKey(String targetKey) {
        JSONObject db = loadDb();
        String clean = targetKey.trim().toUpperCase(Locale.US);
        if (!db.has(clean)) return false;
        try {
            JSONObject rec = db.getJSONObject(clean);
            rec.put("boundHwid", "");
            db.put(clean, rec);
            saveDb(db);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    public String dumpAllKeysSummary() {
        JSONObject db = loadDb();
        StringBuilder sb = new StringBuilder();
        Iterator<String> keys = db.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            JSONObject r = db.optJSONObject(k);
            if (r != null) {
                String role = r.optString("role", "USER");
                String hwid = r.optString("boundHwid", "");
                sb.append("• ").append(k)
                  .append(" [").append(role).append("] ")
                  .append(hwid.isEmpty() ? "(Belum Terikat)" : "(HWID: " + hwid + ")")
                  .append("\n");
            }
        }
        return sb.toString();
    }

    public String getSavedKey() {
        return prefs.getString(KEY_ACTIVE_CODE, "");
    }

    public static String formatTimestamp(long ms) {
        if (ms <= 0L) return "-";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(ms));
    }

    private String signBlock(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(raw, Base64.NO_WRAP).replaceAll("[^A-Za-z0-9]", "");
        } catch (Exception e) {
            return "9999";
        }
    }

    private JSONObject loadDb() {
        String raw = prefs.getString(KEY_DB_JSON, "{}");
        try {
            return new JSONObject(raw);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private void saveDb(JSONObject db) {
        prefs.edit().putString(KEY_DB_JSON, db.toString()).apply();
    }
}
