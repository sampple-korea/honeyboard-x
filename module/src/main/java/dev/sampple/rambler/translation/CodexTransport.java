package dev.sampple.rambler.translation;

import dev.sampple.rambler.I18n;
import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONObject;

/** App-process network bridge: SSE responses, per-request cancellation, 401 refresh and 10-minute timeout. */
public final class CodexTransport {
    private final CodexAuth auth;
    private final Context context;
    private final ConcurrentHashMap<String, HttpURLConnection> running = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cancelled = new ConcurrentHashMap<>();
    public CodexTransport(Context context) { this.context = context; auth = new CodexAuth(context); }
    public void cancel(String id) {
        if (id == null || id.isEmpty()) return;
        cancelled.put(id, System.currentTimeMillis());
        HttpURLConnection conn = running.remove(id);
        if (conn != null) conn.disconnect();
        if (cancelled.size() > 256) cancelled.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue() > 60000);
    }
    /** Model used automatically while the Codex 5-hour or weekly usage shows 0% remaining. */
    public static final String RESERVE_MODEL = "gpt-reserve";
    private static final long USAGE_TTL_MS = 60 * 1000;
    private static volatile long usageCheckedAt;
    private static volatile boolean usageExhaustedCached;

    public String translate(String id, String input, String source, String target, TranslationOptions opts) throws Exception {
        if (cancelled.containsKey(id)) throw new CancellationException();
        try {
            TranslationOptions first = quotaExhausted(false) ? reserve(opts) : opts;
            try { return withRefresh(id, input, source, target, first); }
            catch (Exception error) {
                // The cached usage may be up to a minute old: on a limit error, re-check it now.
                if (RESERVE_MODEL.equals(first.model) || !usageExhausted(error) || !quotaExhausted(true)) throw error;
                if (cancelled.containsKey(id)) throw new CancellationException();
                return withRefresh(id, input, source, target, reserve(opts));
            }
        } finally { cancelled.remove(id); }
    }

    private String withRefresh(String id, String input, String source, String target, TranslationOptions opts) throws Exception {
        JSONObject token = auth.access(false);
        try { return attempt(id, input, source, target, opts, token); }
        catch (HttpFailure error) {
            if (error.code != 401) throw error;
            return attempt(id, input, source, target, opts, auth.accessAfter401(token.optString("access_token")));
        }
    }

    private static TranslationOptions reserve(TranslationOptions opts) {
        return new TranslationOptions(opts.enabled, RESERVE_MODEL, opts.reasoning, opts.prompt, opts.fallback);
    }

    /** True when the account's Codex rate limit shows 0% remaining in any window (5-hour or weekly). */
    private static final java.util.concurrent.ExecutorService USAGE = java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final java.util.concurrent.atomic.AtomicBoolean refreshing = new java.util.concurrent.atomic.AtomicBoolean();

    /** Non-forced checks never delay a translation: a stale cache is refreshed in the background. */
    private boolean quotaExhausted(boolean force) {
        long now = System.currentTimeMillis();
        if (!force) {
            if (now - usageCheckedAt >= USAGE_TTL_MS && refreshing.compareAndSet(false, true)) {
                USAGE.execute(() -> { try { refreshUsage(); } finally { refreshing.set(false); } });
            }
            return usageExhaustedCached;
        }
        return refreshUsage();
    }

    private boolean refreshUsage() {
        long now = System.currentTimeMillis();
        try {
            JSONObject usage = new CodexAccount(context).fetch("usage");
            boolean exhausted = anyWindowFull(usage.optJSONObject("rate_limit"), 0)
                    || anyWindowFull(usage.optJSONObject("rate_limits"), 0);
            usageExhaustedCached = exhausted;
            usageCheckedAt = now;
            return exhausted;
        } catch (Exception error) {
            // Usage lookup is best effort: never block a translation on it.
            usageCheckedAt = now;
            return usageExhaustedCached;
        }
    }

    private static boolean anyWindowFull(JSONObject o, int depth) {
        if (o == null || depth > 5) return false;
        if (o.has("used_percent") || o.has("remaining_percent")) {
            double used = o.has("used_percent") ? o.optDouble("used_percent") : 100 - o.optDouble("remaining_percent");
            return Double.isFinite(used) && used >= 100;
        }
        java.util.Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (anyWindowFull(o.optJSONObject(k), depth + 1)) return true;
            JSONArray arr = o.optJSONArray(k);
            if (arr != null) for (int i = 0; i < arr.length(); i++)
                if (anyWindowFull(arr.optJSONObject(i), depth + 1)) return true;
        }
        return false;
    }

    /** 429 or an explicit usage/rate-limit error (HTTP body or streamed response.failed). */
    static boolean usageExhausted(Throwable error) {
        if (error instanceof HttpFailure && ((HttpFailure) error).code == 429) return true;
        String m = error.getMessage() == null ? "" : error.getMessage().toLowerCase(java.util.Locale.ROOT);
        return m.contains("usage limit") || m.contains("usage_limit") || m.contains("rate limit")
                || m.contains("rate_limit") || m.contains("quota");
    }
    private String attempt(String id, String input, String source, String target, TranslationOptions opts, JSONObject token) throws Exception {
        if (cancelled.containsKey(id)) throw new CancellationException();
        String instructions = opts.prompt.trim().replace("{source_language}", source == null || source.isBlank() ? "Auto" : source)
                .replace("{target_language}", target);
        if (!opts.prompt.contains("{source_language}") || !opts.prompt.contains("{target_language}"))
            instructions += "\nSource language: " + (source == null ? "Auto" : source) + "\nTarget language: " + target;
        JSONObject body = new JSONObject().put("model", opts.model).put("instructions", instructions)
                .put("input", new JSONArray().put(new JSONObject().put("role", "user").put("content", input)))
                .put("stream", true).put("store", false).put("parallel_tool_calls", true);
        if (!opts.reasoning.isEmpty()) {
            body.put("include", new JSONArray().put("reasoning.encrypted_content"));
            body.put("reasoning", new JSONObject().put("effort", opts.reasoning).put("summary", "auto"));
        }
        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection conn = (HttpURLConnection) new URL("https://chatgpt.com/backend-api/codex/responses").openConnection();
        running.put(id, conn);
        try {
            if (cancelled.containsKey(id)) throw new CancellationException();
            conn.setRequestMethod("POST"); conn.setDoOutput(true); conn.setConnectTimeout(20000); conn.setReadTimeout(600000);
            conn.setRequestProperty("Authorization", "Bearer " + token.getString("access_token"));
            conn.setRequestProperty("Version", "0.155.1"); conn.setRequestProperty("Openai-Beta", "responses=experimental");
            conn.setRequestProperty("User-Agent", "codex_cli_rs/0.155.1 (Android; arm64)");
            conn.setRequestProperty("Originator", "codex_cli_rs"); conn.setRequestProperty("Accept", "text/event-stream");
            conn.setRequestProperty("Content-Type", "application/json");
            String account = token.optString("_account_id");
            if (!account.isEmpty()) conn.setRequestProperty("Chatgpt-Account-Id", account);
            conn.setFixedLengthStreamingMode(data.length);
            try (OutputStream out = conn.getOutputStream()) { out.write(data); }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                String detail = "HTTP " + code;
                try (InputStream error = conn.getErrorStream()) {
                    if (error != null) {
                        JSONObject json = new JSONObject(new String(error.readNBytes(65536), StandardCharsets.UTF_8));
                        JSONObject nested = json.optJSONObject("error");
                        String message = json.optString("detail", nested == null ? "" : nested.optString("message"));
                        if (!message.isEmpty()) detail += ": " + message.substring(0, Math.min(message.length(), 240));
                    }
                } catch (Exception ignored) { }
                throw new HttpFailure(code, detail);
            }
            try (InputStream in = conn.getInputStream()) { return parseStream(in, () -> cancelled.containsKey(id)); }
        } finally { running.remove(id, conn); conn.disconnect(); }
    }
    interface Cancelled { boolean get(); }
    static String parseStream(InputStream in, Cancelled cancelled) throws Exception {
        StringBuilder delta = new StringBuilder(), event = new StringBuilder();
        String doneText = null, completedText = null;
        boolean completed = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (cancelled.get()) throw new CancellationException();
                if (line.isEmpty()) {
                    if (event.length() == 0) continue;
                    String raw = event.toString(); event.setLength(0);
                    if ("[DONE]".equals(raw)) continue;
                    JSONObject json = new JSONObject(raw); String type = json.optString("type");
                    JSONObject response = json.optJSONObject("response"), error = json.optJSONObject("error");
                    if (error != null || "error".equals(type) || "response.failed".equals(type) || "response.incomplete".equals(type))
                        throw new IllegalStateException(error != null ? error.optString("message", I18n.t("codex_error")) :
                                response != null && response.optJSONObject("error") != null ? response.optJSONObject("error").optString("message", I18n.t("codex_error")) : I18n.t("codex_response_is_incomplete"));
                    switch (type) {
                        case "response.output_text.delta": delta.append(json.optString("delta")); break;
                        case "response.output_text.done": doneText = json.optString("text"); break;
                        case "response.completed":
                            if (response != null && !"completed".equals(response.optString("status", "completed")))
                                throw new IllegalStateException(I18n.t("invalid_codex_response_status"));
                            completed = true;
                            if (response != null) {
                                JSONArray output = response.optJSONArray("output"); StringBuilder text = new StringBuilder();
                                if (output != null) for (int i = 0; i < output.length(); i++) {
                                    JSONObject item = output.optJSONObject(i);
                                    if (item == null || !"message".equals(item.optString("type"))) continue;
                                    JSONArray content = item.optJSONArray("content");
                                    if (content != null) for (int j = 0; j < content.length(); j++) {
                                        JSONObject part = content.optJSONObject(j);
                                        if (part != null && "output_text".equals(part.optString("type"))) text.append(part.optString("text"));
                                    }
                                }
                                if (text.length() > 0) completedText = text.toString();
                            }
                            break;
                    }
                } else if (line.startsWith("data:")) {
                    if (event.length() > 0) event.append('\n');
                    event.append(line.substring(5).stripLeading());
                }
            }
            if (event.length() > 0 && !completed && !"[DONE]".equals(event.toString())) {
                JSONObject json = new JSONObject(event.toString());
                JSONObject response = json.optJSONObject("response");
                if ("response.completed".equals(json.optString("type"))) {
                    completed = response == null || "completed".equals(response.optString("status", "completed"));
                    if (response != null) {
                        JSONArray output = response.optJSONArray("output"); StringBuilder text = new StringBuilder();
                        if (output != null) for (int i = 0; i < output.length(); i++) {
                            JSONObject item = output.optJSONObject(i); if (item == null || !"message".equals(item.optString("type"))) continue;
                            JSONArray content = item.optJSONArray("content");
                            if (content != null) for (int j = 0; j < content.length(); j++) {
                                JSONObject part = content.optJSONObject(j);
                                if (part != null && "output_text".equals(part.optString("type"))) text.append(part.optString("text"));
                            }
                        }
                        if (text.length() > 0) completedText = text.toString();
                    }
                }
            }
        }
        if (cancelled.get()) throw new CancellationException();
        if (!completed) throw new IllegalStateException(I18n.t("connection_closed_before_codex_finished_responding"));
        String result = completedText != null ? completedText : doneText != null && !doneText.isEmpty() ? doneText : delta.toString();
        if (result.isBlank()) throw new IllegalStateException(I18n.t("codex_returned_an_empty_translation"));
        return result;
    }
    private static final class HttpFailure extends Exception {
        final int code;
        HttpFailure(int code, String message) { super(message); this.code = code; }
    }
}
