package dev.sampple.rambler.translation;

import dev.sampple.rambler.I18n;
import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import org.json.JSONObject;

/** Codex CLI OAuth PKCE. App-UID-only encrypted state survives browser/app restarts. */
public final class CodexAuth {
    private static final String CLIENT = "app_EMoamEEZ73f0CkXaXp7hrann";
    private static final String REDIRECT = "http://localhost:1455/auth/callback";
    private static final long PENDING_MS = 900000L; // Match B's 15-minute pending window.
    private static final Object AUTH_LOCK = new Object(); // Serialize refresh across provider/UI instances.
    private final TokenStore store, pendingStore;
    private String verifier, state;
    public CodexAuth(Context context) {
        store = new TokenStore(context); pendingStore = new TokenStore(context, true);
        JSONObject saved = pendingStore.read();
        if (validPending(saved)) {
            verifier = saved.optString("verifier", null); state = saved.optString("state", null);
        } else if (saved != null) pendingStore.clear();
    }
    private static boolean validPending(JSONObject saved) {
        if (saved == null) return false;
        long age = System.currentTimeMillis() - saved.optLong("created_at", 0);
        return age >= 0 && age < PENDING_MS && saved.optString("verifier").matches("[A-Za-z0-9_-]{43,128}")
                && saved.optString("state").matches("[A-Za-z0-9_-]{32,128}");
    }
    public String loginUrl() throws Exception {
        synchronized (AUTH_LOCK) {
            byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
            String newVerifier = Base64.encodeToString(random, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            new SecureRandom().nextBytes(random);
            String newState = Base64.encodeToString(random, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            pendingStore.write(new JSONObject().put("verifier", newVerifier).put("state", newState)
                    .put("created_at", System.currentTimeMillis()));
            verifier = newVerifier; state = newState;
            String challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            return Uri.parse("https://auth.openai.com/oauth/authorize").buildUpon()
                    .appendQueryParameter("client_id", CLIENT).appendQueryParameter("redirect_uri", REDIRECT)
                    .appendQueryParameter("response_type", "code").appendQueryParameter("scope", "openid profile email offline_access")
                    .appendQueryParameter("state", state).appendQueryParameter("code_challenge", challenge)
                    .appendQueryParameter("code_challenge_method", "S256").appendQueryParameter("codex_cli_simplified_flow", "true")
                    .appendQueryParameter("originator", "codex_cli_rs").appendQueryParameter("id_token_add_organizations", "true")
                    .build().toString();
        }
    }
    /** Accept full URL, percent-encoded full URL or code+state query. A code alone cannot prove PKCE state. */
    static String[] parseCallback(String pasted, String expectedState) {
        String raw = pasted == null ? "" : pasted.trim();
        if (raw.length() > 8192) throw new IllegalArgumentException(I18n.t("callback_url_is_too_long_paste_only_the_address_bar_url"));
        if (raw.isEmpty()) throw new IllegalArgumentException(I18n.t("enter_the_sign_in_callback_url_from_your_browser"));
        if (raw.startsWith("http%3A") || raw.startsWith("http%3a")) raw = Uri.decode(raw);
        if (raw.startsWith("code=") || raw.startsWith("state=") || raw.startsWith("error=")) raw = "?" + raw;
        if (raw.startsWith("?")) raw = REDIRECT + raw;
        Uri uri;
        try { uri = Uri.parse(raw); }
        catch (Exception e) { throw new IllegalArgumentException(I18n.t("check_the_callback_url_format")); }
        if (!"http".equalsIgnoreCase(uri.getScheme()) || !isLoopback(uri.getHost()) || uri.getPort() != 1455
                || !"/auth/callback".equals(uri.getPath()) || uri.getFragment() != null || uri.getUserInfo() != null)
            throw new IllegalArgumentException(I18n.t("enter_the_localhost_1455_auth_callback_url_a_code_alone_is"));
        String received, code, error;
        try {
            if (uri.getQueryParameterNames().size() > 20) throw new IllegalArgumentException(I18n.t("too_many_parameters_in_the_callback_url"));
            for (String key : new String[]{"code", "state", "error"})
                if (uri.getQueryParameters(key).size() > 1) throw new IllegalArgumentException(I18n.t("auth_duplicate_key", key));
            received = uri.getQueryParameter("state"); code = uri.getQueryParameter("code"); error = uri.getQueryParameter("error");
        } catch (UnsupportedOperationException e) { throw new IllegalArgumentException(I18n.t("check_the_callback_url_query_format")); }
        if (expectedState == null || received == null || !MessageDigest.isEqual(expectedState.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8)))
            throw new IllegalArgumentException(I18n.t("sign_in_state_doesn_t_match_copy_the_callback_url_from_the"));
        if (error != null && !error.isEmpty()) throw new IllegalArgumentException(I18n.t("sign_in_denied") + error.substring(0, Math.min(100, error.length())));
        if (code == null || code.isBlank()) throw new IllegalArgumentException(I18n.t("callback_url_has_no_authorization_code_paste_the_full_url"));
        return new String[]{code, received};
    }
    private static boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host) || "[::1]".equals(host);
    }
    public void finish(String callback) throws Exception {
        synchronized (AUTH_LOCK) {
            JSONObject saved = pendingStore.read();
            if (!validPending(saved)) {
                pendingStore.clear(); verifier = null; state = null;
                throw new IllegalStateException(I18n.t("sign_in_expired_start_again_from_account"));
            }
            String expectedState = saved.getString("state"), expectedVerifier = saved.getString("verifier");
            String[] parsed = parseCallback(callback, expectedState);
            // B exchanges authorization_code with JSON; form-encoded requests fail intermittently at this endpoint.
            JSONObject token = request(new JSONObject().put("grant_type", "authorization_code")
                    .put("client_id", CLIENT).put("code", parsed[0]).put("redirect_uri", REDIRECT)
                    .put("code_verifier", expectedVerifier));
            JSONObject enriched = enrich(token);
            store.write(enriched); pendingStore.clear(); verifier = null; state = null;
        }
    }
    public boolean connected() { synchronized (AUTH_LOCK) { return store.read() != null; } }
    public boolean pending() {
        synchronized (AUTH_LOCK) {
            JSONObject saved = pendingStore.read();
            if (validPending(saved)) return true;
            if (saved != null) pendingStore.clear(); verifier = null; state = null; return false;
        }
    }
    public void signOut() {
        synchronized (AUTH_LOCK) { store.clear(); pendingStore.clear(); verifier = null; state = null; }
    }
    /** Avoid rotating a refresh token twice when two 401 responses race. */
    public JSONObject accessAfter401(String rejectedAccessToken) throws Exception {
        synchronized (AUTH_LOCK) {
            JSONObject current = store.read();
            if (current == null) throw new IllegalStateException(I18n.t("sign_in_to_your_codex_account"));
            if (!current.optString("access_token").equals(rejectedAccessToken)) return current;
            return access(true);
        }
    }
    public JSONObject access(boolean force) throws Exception {
        synchronized (AUTH_LOCK) {
            JSONObject token = store.read();
            if (token == null) throw new IllegalStateException(I18n.t("sign_in_to_your_codex_account"));
            long expiry = token.optLong("expire_at", 0);
            if (force || (expiry > 0 && expiry - System.currentTimeMillis() < 14400000L)) {
                String refresh = token.optString("refresh_token");
                if (!refresh.isEmpty()) {
                    try {
                        // B refreshes with form-urlencoded, unlike its JSON authorization_code exchange.
                        JSONObject updated = requestForm("grant_type=refresh_token&refresh_token=" + Uri.encode(refresh)
                                + "&client_id=" + Uri.encode(CLIENT));
                        for (String name : new String[]{"refresh_token", "id_token", "_account_id", "_plan_type"})
                            if (updated.optString(name).isEmpty() && token.has(name)) updated.put(name, token.get(name));
                        token = enrich(updated); store.write(token);
                    } catch (Exception error) {
                        if (force || (expiry > 0 && expiry <= System.currentTimeMillis())) throw error;
                        // Transient refresh error: keep the still-valid access token, do not clear the account.
                    }
                } else if (force || (expiry > 0 && expiry <= System.currentTimeMillis()))
                    throw new IllegalStateException(I18n.t("sign_in_expired_sign_in_again"));
            }
            if (token.optLong("expire_at", 0) > 0 && token.optLong("expire_at") <= System.currentTimeMillis())
                throw new IllegalStateException(I18n.t("sign_in_expired_sign_in_again"));
            return token;
        }
    }
    private static JSONObject enrich(JSONObject token) throws Exception {
        long expires = token.optLong("expires_in", 0);
        if (expires > 0) token.put("expire_at", System.currentTimeMillis() + expires * 1000);
        else {
            String[] parts = token.optString("access_token").split("\\.");
            if (parts.length > 1) try {
                long exp = new JSONObject(new String(Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP), StandardCharsets.UTF_8)).optLong("exp");
                if (exp > 0) token.put("expire_at", exp * 1000);
            } catch (Exception ignored) { }
        }
        String[] parts = token.optString("id_token").split("\\.");
        if (parts.length > 1) try {
            JSONObject claims = new JSONObject(new String(Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP), StandardCharsets.UTF_8));
            JSONObject auth = claims.optJSONObject("https://api.openai.com/auth");
            String id = claims.optString("chatgpt_account_id", "");
            if (id.isEmpty() && auth != null) id = auth.optString("chatgpt_account_id", "");
            if (!id.isEmpty()) token.put("_account_id", id);
        } catch (Exception ignored) { }
        if (token.optString("_account_id").isEmpty() && !token.optString("account_id").isEmpty())
            token.put("_account_id", token.optString("account_id"));
        return token;
    }
    private static JSONObject request(JSONObject payload) throws Exception {
        return requestBytes(payload.toString().getBytes(StandardCharsets.UTF_8), "application/json");
    }
    private static JSONObject requestForm(String form) throws Exception {
        return requestBytes(form.getBytes(StandardCharsets.UTF_8), "application/x-www-form-urlencoded");
    }
    private static JSONObject requestBytes(byte[] bytes, String contentType) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://auth.openai.com/oauth/token").openConnection();
        try {
            conn.setRequestMethod("POST"); conn.setConnectTimeout(15000); conn.setReadTimeout(20000);
            conn.setRequestProperty("Content-Type", contentType); conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true); conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = conn.getOutputStream()) { out.write(bytes); }
            int code = conn.getResponseCode();
            try (InputStream in = code < 400 ? conn.getInputStream() : conn.getErrorStream()) {
                if (in == null) throw new IllegalStateException(I18n.t("auth_http", code));
                String response = new String(in.readNBytes(65536), StandardCharsets.UTF_8);
                if (code < 200 || code >= 300) {
                    String detail = I18n.t("request_failed");
                    try {
                        JSONObject json = new JSONObject(response);
                        detail = json.optString("error_description", json.optString("error", detail));
                    } catch (Exception ignored) { }
                    if (code == 400 || code == 401)
                        throw new IllegalStateException(I18n.t("auth_code_expired", code, detail.substring(0, Math.min(120, detail.length()))));
                    throw new IllegalStateException(I18n.t("auth_http_detail", code, detail.substring(0, Math.min(120, detail.length()))));
                }
                JSONObject result = new JSONObject(response);
                if (result.optString("access_token").isEmpty()) throw new IllegalStateException(I18n.t("sign_in_response_has_no_access_token"));
                return result;
            }
        } finally { conn.disconnect(); }
    }
}
