package dev.sampple.rambler.translation;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** App-UID-only encrypted token store; not exposed through LSPosed remote preferences. */
public final class TokenStore {
    private final String alias;
    private final boolean pending;
    private final SharedPreferences prefs;
    public TokenStore(Context context) { this(context, false); }
    public TokenStore(Context context, boolean pending) {
        this.pending = pending;
        alias = pending ? "honeyboardx_codex_oauth_pending" : "honeyboardx_codex_oauth_v1";
        prefs = context.getSharedPreferences(pending ? "honeyboardx_oauth_pending" : "honeyboardx_oauth", 0);
    }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.getKey(alias, null) instanceof SecretKey) return (SecretKey) store.getKey(alias, null);
        KeyGenerator generator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias, 3).setBlockModes("GCM")
                .setEncryptionPaddings("NoPadding").setKeySize(256).build());
        return generator.generateKey();
    }
    public synchronized void clear() { prefs.edit().remove("token_bundle_v1").commit(); }
    public synchronized JSONObject read() {
        String blob = prefs.getString("token_bundle_v1", null);
        if (blob == null) return null;
        try {
            String[] parts = blob.split("\\.", 2);
            if (parts.length != 2) throw new IllegalArgumentException("bad token blob");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            return new JSONObject(new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8));
        } catch (Exception ignored) { clear(); return null; }
    }
    public synchronized void write(JSONObject token) throws Exception {
        if (!pending && token.optString("access_token").isEmpty()) throw new IllegalArgumentException("access_token missing");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        String value = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "." +
                Base64.encodeToString(cipher.doFinal(token.toString().getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        if (!prefs.edit().putString("token_bundle_v1", value).commit()) throw new IllegalStateException("Token save failed");
    }
}
