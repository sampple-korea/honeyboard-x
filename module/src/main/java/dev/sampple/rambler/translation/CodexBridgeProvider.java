package dev.sampple.rambler.translation;

import dev.sampple.rambler.I18n;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import java.security.MessageDigest;
import java.util.Locale;

/** Only the signed Samsung Keyboard can call the exported network bridge. */
public final class CodexBridgeProvider extends ContentProvider {
    public static final Uri URI = Uri.parse("content://dev.sampple.honeyboardx.bridge");
    private static final String HONEYBOARD = "com.samsung.android.honeyboard";
    private static final String SIGNER = "34df0e7a9f1cf1892e45c056b4973cd81ccf148a4050d11aea4ac5a65f900a42";
    private CodexTransport transport;
    @Override public boolean onCreate() {
        Context c = getContext(); if (c == null) return false;
        transport = new CodexTransport(c);
        try { c.grantUriPermission(HONEYBOARD, URI, 3); } catch (RuntimeException ignored) { }
        return true;
    }
    private void authorize() {
        int uid = Binder.getCallingUid();
        PackageManager pm = getContext().getPackageManager();
        String[] names = pm.getPackagesForUid(uid);
        boolean nameMatches = false;
        if (names != null) for (String name : names) if (HONEYBOARD.equals(name)) nameMatches = true;
        if (!nameMatches) throw new SecurityException("Unauthorized bridge caller");
        try {
            PackageInfo info = pm.getPackageInfo(HONEYBOARD, PackageManager.PackageInfoFlags.of(0x8000000L));
            SigningInfo signing = info.signingInfo;
            if (signing != null) for (Signature signature : signing.hasMultipleSigners() ? signing.getApkContentsSigners() : signing.getSigningCertificateHistory()) {
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
                StringBuilder hex = new StringBuilder();
                for (byte b : hash) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
                if (SIGNER.equals(hex.toString())) return;
            }
        } catch (Exception ignored) { }
        throw new SecurityException("Unsupported Samsung Keyboard signer");
    }
    private static Bundle result(boolean ok, String text, boolean fallback) {
        Bundle b = new Bundle(); b.putBoolean("ok", ok); b.putBoolean("fallback", fallback);
        b.putString(ok ? "result" : "error", text); return b;
    }
    @Override public Bundle call(String method, String id, Bundle args) {
        authorize();
        if ("cancel".equals(method)) { transport.cancel(id); return result(true, "", false); }
        if ("status".equals(method)) {
            Bundle status = result(true, "", false);
            status.putBoolean("authenticated", new CodexAuth(getContext()).connected());
            return status;
        }
        if (!"translate".equals(method)) return result(false, I18n.t("unsupported_request"), false);
        if (args == null) return result(false, I18n.t("invalid_request"), false);
        TranslationOptions opts = new TranslationOptions(args.getBoolean("enabled", false),
                args.getString("model", "gpt-5.6-luna"), args.getString("reasoning", "low"),
                args.getString("prompt", TranslationOptions.DEFAULT_PROMPT), args.getBoolean("fallback", false));
        if (!opts.enabled) return result(false, I18n.t("translation_is_off"), opts.fallback);
        if (id == null || id.isEmpty()) return result(false, I18n.t("invalid_request"), opts.fallback);
        String input = args.getString("input", ""), target = args.getString("target", "");
        if (input.isBlank() || target.isBlank() || input.length() > 32768) return result(false, I18n.t("invalid_translation_length_or_language"), opts.fallback);
        try { return result(true, transport.translate(id, input, args.getString("source", "Auto"), target, opts), opts.fallback); }
        catch (java.util.concurrent.CancellationException e) { return result(false, I18n.t("canceled"), false); }
        catch (Exception e) {
            String message = e.getMessage() == null ? I18n.t("translation_failed") : e.getMessage();
            return result(false, message.substring(0, Math.min(260, message.length())), opts.fallback);
        }
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String sort) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
