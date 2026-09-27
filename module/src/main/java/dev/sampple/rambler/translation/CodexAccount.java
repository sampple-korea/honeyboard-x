package dev.sampple.rambler.translation;

import android.content.Context;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Account metadata is fetched in the app process; no credentials are returned to the IME. */
public final class CodexAccount {
    private final CodexAuth auth;
    public CodexAccount(Context context) { auth = new CodexAuth(context); }
    public JSONObject fetch(String path) throws Exception {
        String url = "models".equals(path) ? "https://chatgpt.com/backend-api/codex/models?client_version=0.155.1" :
                "usage".equals(path) ? "https://chatgpt.com/backend-api/wham/usage" : null;
        if (url == null) throw new IllegalArgumentException("Invalid metadata endpoint");
        JSONObject token = auth.access(false);
        try { return request(url, token); }
        catch (Http401 e) { return request(url, auth.accessAfter401(token.optString("access_token"))); }
    }
    public List<String> models() throws Exception {
        JSONObject json = fetch("models");
        JSONArray array = json.optJSONArray("models"); if (array == null) array = json.optJSONArray("data");
        List<String> out = new ArrayList<>();
        if (array != null) for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i); if (item == null) continue;
            String slug = item.optString("slug", item.optString("id"));
            if (!slug.isBlank() && !out.contains(slug)) out.add(slug);
        }
        return out;
    }
    private static JSONObject request(String url, JSONObject token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(15000); c.setReadTimeout(20000);
            c.setRequestProperty("Authorization", "Bearer " + token.getString("access_token"));
            if (!token.optString("_account_id").isEmpty()) c.setRequestProperty("Chatgpt-Account-Id", token.optString("_account_id"));
            c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("Version", "0.155.1");
            c.setRequestProperty("Originator", "codex_cli_rs");
            c.setRequestProperty("User-Agent", "codex_cli_rs/0.155.1 (Android; arm64)");
            int code = c.getResponseCode();
            if (code == 401) throw new Http401();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            try (InputStream in = c.getInputStream()) { return new JSONObject(new String(in.readNBytes(8 * 1024 * 1024), StandardCharsets.UTF_8)); }
        } finally { c.disconnect(); }
    }
    private static class Http401 extends Exception { }
}
