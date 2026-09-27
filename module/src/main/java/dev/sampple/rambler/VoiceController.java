package dev.sampple.rambler;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.SurroundingText;
import android.widget.Toast;

import dev.sampple.rambler.s3.S3Session;
import dev.sampple.rambler.s3.OggOpusWriter;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main-thread owner of the panel, editor transaction and logical S3 session.
 * Host MUST call close() before onStartInput/restartInput, onFinishInput/View,
 * onWindowHidden, onDestroy and replacement of the input root. Identity checks
 * cannot distinguish two editor generations when a host reuses the same objects.
 */
public final class VoiceController {
    private static final String TAG = "RamblerVoice";
    private static final int IDLE = 0, LISTENING = 1, PROCESSING = 2, ERROR = 3;
    private static final int TEXT_LIMIT = 2000;
    private static final ExecutorService CLOSER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rambler-s3-close"); t.setDaemon(true); return t;
    });
    private final InputMethodService service;
    private final View root;
    private final String languageTag;
    private final VoiceOptions options;
    private boolean feedbackActive;
    private android.media.ToneGenerator tone;
    private final Runnable onClosed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private VoicePanel panel;
    private ModuleMain.HeaderMount headerMount;
    private boolean showing, wanted, notified, changingEditor;
    /** Set when the user taps the mic to stop: close the header once the last result lands. */
    private boolean closeWhenDone, retryAvailable;
    private long generation;
    private InputConnection connection;
    private EditorInfo editor;
    private String editorPackage;
    private int editorField, editorType;
    private int trackedSelectionStart = -1, trackedSelectionEnd = -1;
    private boolean headerOnlyPreview;
    private Snapshot expected;
    private String selectedBeforeComposition;
    private int compositionStart = -1;
    private boolean compositionReplacedSelection;
    private String compositionText;
    private Run run;
    private Capture capture;
    private long restartNotBefore;

    private final View.OnAttachStateChangeListener attachment = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View v) { guarded(() -> { }); }
        @Override public void onViewDetachedFromWindow(View v) { close(); }
    };
    private final Runnable editorWatch = () -> guarded(this::watchEditor);

    public VoiceController(InputMethodService service, View root, String languageTag, VoiceOptions options, Runnable onClosed) {
        this.service = service;
        this.root = root;
        this.languageTag = languageTag;
        this.options = options;
        this.onClosed = onClosed;
    }

    public void show() { dispatch(this::showInternal); }
    public void close() { dispatch(this::closeInternal); }
    /** Call synchronously before a real key/editor mutation, on the IME main thread. */
    public void onExternalInput() { dispatch(this::pauseForExternalInput); }
    /** Call after an editor's external selection notification. */
    public void onExternalSelectionChanged(int start, int end) { dispatch(() -> {
        trackedSelectionStart = start; trackedSelectionEnd = end;
        if (showing && !changingEditor && !matchesExpected()) pauseForExternalInput();
    }); }
    public boolean isShowing() {
        try { return showing; } catch (Throwable error) { log("isShowing", error); return false; }
    }

    private void showInternal() {
        if (showing) return;
        notified = false;
        editor = service.getCurrentInputEditorInfo();
        connection = service.getCurrentInputConnection();
        if (connection == null || editor == null || password(editor)) {
            toast(I18n.t("voice_input_isn_t_available_in_password_fields"));
            closeInternal(); return;
        }
        trackedSelectionStart = editor.initialSelStart; trackedSelectionEnd = editor.initialSelEnd;
        editorPackage = editor.packageName; editorField = editor.fieldId; editorType = editor.inputType;
        if (!hasPermission()) {
            toast(I18n.t("allow_microphone_access_in_samsung_keyboard_settings_permi"));
            // Do not touch PermissionActivity's shared/static callback.
            Intent settings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", service.getPackageName(), null));
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { service.startActivity(settings); } catch (Throwable error) { log("open permission settings", error); }
            closeInternal(); return;
        }
        expected = snapshot();
        if (expected == null) {
            toast(I18n.t("this_field_doesn_t_support_safe_voice_editing")); closeInternal(); return;
        }
        generation++;
        panel = new VoicePanel(root.getContext(), () -> dispatch(this::tapMic),
                () -> dispatch(this::longPressMic), this::close, options.aurora);
        headerMount = ModuleMain.mountVoiceHeader(root, panel, this::close);
        if (headerMount == null) {
            toast(I18n.t("can_t_show_the_voice_input_panel")); closeInternal(); return;
        }
        root.addOnAttachStateChangeListener(attachment);
        showing = true; wanted = true; closeWhenDone = false;
        panel.start();
        main.postDelayed(editorWatch, 750);
        startSession();
    }

    private void tapMic() {
        if (wanted) stopListening(true);
        else if (run != null && !run.ended) closeWhenDone = true;
        else if (retryAvailable) resumeListening();
        else closeInternal();
    }

    private void longPressMic() {
        if (wanted) stopListening(false);
        else if (!closeWhenDone) resumeListening();
    }

    private void stopListening(boolean closeAfter) {
        if (!showing || !editorValid()) { closeInternal(); return; }
        if (!wanted) return;
        wanted = false; closeWhenDone = closeAfter;
        feedback(false);
        if (panel != null) panel.setPaused(true);
        clearComposition();
        Run stopping = run;
        if (stopping == null || stopping.ended) {
            if (closeAfter) closeInternal();
            else ui(I18n.t("paused"), false, IDLE);
            return;
        }
        // A periodic refresh may already have stopped AudioRecord. Still await
        // that stream's final instead of dropping it or reopening the microphone.
        if (!stopping.audioStopped && stopping.audio != null) {
            if (capture != null) capture.stopping = true;
            stopping.audio.stop(true);
        }
        ui(I18n.t("processing_your_last_words"), false, PROCESSING);
        later(() -> {
            if (run == stopping && !stopping.ended) fail(I18n.t("response_timed_out_tap_to_try_again"), null);
        }, 12000);
    }

    private void resumeListening() {
        if (wanted || !showing) return;
        if (run != null && (!run.ended || !run.audioStopped)) {
            ui(I18n.t("finishing_your_last_words_hold_to_resume"), false, PROCESSING); return;
        }
        if (!editorValid()) { closeInternal(); return; }
        if (!hasPermission()) { fail(I18n.t("allow_microphone_access_in_samsung_keyboard_settings"), null); return; }
        refreshExpected();
        retryAvailable = false; closeWhenDone = false; wanted = true;
        if (run != null) { cancelOffMain(run); run = null; }
        startSession();
    }

    private void startSession() {
        if (!showing || !wanted || run != null) return;
        if (!editorValid() || !matchesExpected()) { closeInternal(); return; }
        if (!hasPermission()) { fail(I18n.t("microphone_permission_is_required"), null); return; }
        String apiKey = options.effectiveApiKey();
        if (apiKey == null || apiKey.isEmpty()) { fail(I18n.t("no_api_key_voice"), null); return; }
        S3Session.Config config = new S3Session.Config();
        config.apiKey = apiKey;
        config.language = normalizeLanguage(options.primary.isEmpty() ? languageTag : options.primary);
        config.additionalLanguages = options.additionalTags();
        config.buildDisplay = Build.DISPLAY; config.model = Build.MODEL;
        config.triggerPackage = editorPackage;
        Run next = new Run(generation);
        next.history = captureHistory();
        config.surroundingText = next.history == null ? null : next.history.original;
        run = next;
        next.session = new S3Session(config, new S3Session.Listener() {
            @Override public void onPartial(String text, boolean stable) {
                post(next, () -> {
                    if (next.ended || !wanted || next.interimFinished || next.edit != null) return;
                    // Interim recognizer is only a preview. It must not consume or
                    // unpair Jetson's pending onEdit/onFinal result. Without a shared
                    // segment ID, late interim after a Jetson final cannot be matched:
                    // disable previews until the next session rather than recompose it.
                    partial(text);
                });
            }
            @Override public void onEdit(int intent, String fullTranscript, String editedHistory) {
                post(next, () -> {
                    if (next.ended || next.finalHandled) return;
                    next.interimFinished = true;
                    flushEdit(next);
                    next.suppressFinal = false;
                    next.edit = new EditResult(intent, fullTranscript, editedHistory);
                    EditResult pending = next.edit;
                    // S3 calls onEdit and onFinal for the same alternative. Buffer the
                    // edit so they form ONE editor transaction, including empty finals.
                    if ((intent == 2 || intent == 3) && editedHistory != null) later(() -> {
                        if (current(next) && !next.ended && next.edit == pending) {
                            flushEdit(next);
                            // A later final has no segment ID. A live stream must
                            // rotate; a draining stream must ignore that paired final.
                            if (wanted) rotateAfterFinal(next);
                            else next.suppressFinal = true;
                        }
                    }, 120);
                });
            }
            @Override public void onFinal(String text) {
                post(next, () -> {
                    if (next.ended || next.finalHandled) return;
                    next.finalHandled = true;
                    next.interimFinished = true;
                    if (next.edit != null) {
                        EditResult edit = next.edit; next.edit = null;
                        applyFinal(text, edit);
                    } else if (!next.suppressFinal) applyFinal(text, null);
                    next.suppressFinal = false;
                    if (closeWhenDone && !wanted && showing) { closeInternal(); return; }
                    if (current(next) && !next.ended) rotateAfterFinal(next);
                });
            }
            @Override public void onError(Throwable error) {
                post(next, () -> { if (!next.ended) fail(I18n.t("network_or_recognition_error_tap_mic_to_retry"), error); });
            }
            @Override public void onClosed() { post(next, () -> ended(next)); }
        });
        boolean startCapture = capture == null;
        if (startCapture) capture = createCapture();
        next.audio = capture.audio;
        capture.destination = next;
        if (panel != null) panel.setPaused(false);
        ui(I18n.t("starting"), true, LISTENING);
        try {
            next.session.start();
            if (startCapture) { next.audio.start(); feedback(true); }
        } catch (Throwable error) {
            if (startCapture) { capture = null; next.audio.stop(false); }
            next.audioStopped = true;
            throw error;
        }
        restartNotBefore = SystemClock.elapsedRealtime() + 600;
        // Bound a silent/stuck connection, and rotate long dictation sessions.
        later(() -> {
            if (current(next) && !next.ended && !next.audioStopped) {
                if (capture != null) capture.stopping = true;
                next.audio.stop(true);
                ui(I18n.t("refreshing_voice_connection"), false, PROCESSING);
                later(() -> {
                    if (current(next) && !next.ended) fail(I18n.t("server_response_timed_out"), null);
                }, 12000);
            }
        }, 55000);
    }

    /** The microphone/encoder outlives each transport. Only packets after the
     * main-thread rollover boundary enter the next fresh Ogg stream; never replay
     * pre-final audio. S3Session already bounds and queues pages while connecting. */
    private void rotateAfterFinal(Run owner) {
        if (!current(owner) || owner.ended || owner.edit != null || !wanted) return;
        if (capture == null || capture.stopping) return;
        try { finishAudio(owner); }
        catch (Throwable error) { fail(I18n.t("voice_upload_was_interrupted_tap_to_try_again"), error); return; }
        owner.ended = true; owner.acceptAudio = false;
        cancelOffMain(owner);
        run = null;
        startSession();
    }

    private void ended(Run owner) {
        if (owner.ended) return;
        flushEdit(owner);
        if (!current(owner)) return;
        clearComposition();
        if (wanted && capture != null && !capture.stopping) {
            owner.ended = true; owner.acceptAudio = false;
            cancelOffMain(owner);
            run = null;
            startSession();
            return;
        }
        owner.ended = true; owner.acceptAudio = false;
        owner.audio.stop(false);
        if (owner.audioStopped) retire(owner);
        else ui(wanted ? I18n.t("starting") : I18n.t("paused"), false, wanted ? PROCESSING : IDLE);
    }

    private Capture createCapture() {
        Capture owner = new Capture();
        owner.audio = new AudioPipeline(new AudioPipeline.Listener() {
            @Override public void onPacket(byte[] packet, int preSkip) {
                // Tag on the capture thread, not when this runnable is dequeued:
                // packets produced before rollover must never leak into the new stream.
                Run destination = owner.destination;
                int bytes = owner.pendingBytes.addAndGet(packet.length);
                if (bytes > 256000) {
                    owner.pendingBytes.addAndGet(-packet.length);
                    throw new IllegalStateException("UI audio queue stalled");
                }
                enqueue(() -> {
                    try {
                        Run target = destination;
                        if (capture != owner || target == null || !current(target)
                                || target.ended || !target.acceptAudio) return;
                        if (target.ogg == null) {
                            target.ogg = new OggOpusWriter(preSkip);
                            target.session.sendAudio(target.ogg.headers());
                        }
                        if (target.lastPacket != null)
                            target.session.sendAudio(target.ogg.addPacket(target.lastPacket, false));
                        target.lastPacket = packet;
                    } finally { owner.pendingBytes.addAndGet(-packet.length); }
                });
            }
            @Override public void onLevel(float level) {
                enqueue(() -> {
                    Run target = run;
                    if (capture == owner && target != null && !target.ended && wanted && panel != null) {
                        panel.setLevel(level);
                        if (!owner.stopping && owner.silence.update(level, SystemClock.elapsedRealtime(), options.silenceSeconds)) {
                            stopListening(true); return;
                        }
                        if (!target.heardAudio && level > .025f) {
                            target.heardAudio = true;
                            ui(I18n.t("listening"), true, LISTENING);
                        }
                    }
                });
            }
            @Override public void onError(Throwable error) {
                enqueue(() -> { if (capture == owner) fail(I18n.t("mic_opus_encoder_error_tap_mic_to_retry"), error); });
            }
            @Override public void onStopped(boolean drained) {
                enqueue(() -> {
                    if (capture != owner) return;
                    capture = null;
                    Run target = run;
                    if (target == null) return;
                    target.audioStopped = true;
                    if (target.ended) retire(target);
                    else if (drained) {
                        finishAudio(target);
                        target.session.finish();
                        ui(I18n.t("waiting_for_transcription"), false, PROCESSING);
                    } else fail(I18n.t("voice_recording_stopped_unexpectedly"), null);
                });
            }
        });
        return owner;
    }

    private void finishAudio(Run owner) {
        if (owner.ogg != null && owner.lastPacket != null) {
            owner.session.sendAudio(owner.ogg.addPacket(owner.lastPacket, true));
            owner.lastPacket = null;
        }
    }

    private void retire(Run old) {
        if (!current(old)) return;
        run = null;
        cancelOffMain(old);
        if (wanted) {
            long token = generation;
            later(() -> {
                if (showing && generation == token && wanted && run == null) startSession();
            }, Math.max(100, restartNotBefore - SystemClock.elapsedRealtime()));
        } else if (closeWhenDone && !old.failed) closeInternal();
        else if (!old.failed) ui(I18n.t("paused"), false, IDLE);
    }

    private void flushEdit(Run owner) {
        EditResult edit = owner.edit; owner.edit = null;
        // Without onFinal there is no FinalAlternative transcript. full_transcript
        // is metadata, not a substitute for the transcript of other intent types.
        if (edit != null && !owner.finalHandled && (edit.intent == 2 || edit.intent == 3) && edit.history != null) {
            owner.finalHandled = true;
            applyFinal("", edit);
        }
    }

    private void partial(String text) {
        if (text == null || text.isEmpty()) return;
        if (text.length() > TEXT_LIMIT || !readyToEdit()) return;
        // Live text stays in the header strip; the editor only receives the final result.
        String shown = text.length() > 80 ? "…" + text.substring(text.length() - 80) : text;
        ui(shown, wanted, wanted ? LISTENING : PROCESSING);
    }

    private void applyFinal(String transcript, EditResult edit) {
        if (!readyToEdit()) return;
        if (edit != null && (edit.intent == 2 || edit.intent == 3) && edit.history != null) {
            HistoryRange range = run == null ? null : run.history;
            if (edit.history.length() > TEXT_LIMIT * 2 || range == null || !historyUnchanged(range)) {
                fail(I18n.t("couldn_t_verify_the_text_before_the_cursor_voice_edit_wasn"), null); return;
            }
            clearComposition();
            if (!readyToEdit()) return;
            // InputBoxInfo field 1 is BEFORE-cursor history, not the whole document.
            // Bind this result to the immutable range sent by this particular run.
            changingEditor = true;
            try {
                boolean batch = connection.beginBatchEdit();
                try {
                    if (!historyUnchanged(range)) throw new IllegalStateException("history changed before edit");
                    require(connection.setSelection(range.start, range.end));
                    // Verify the selected range again, after moving the selection.
                    CharSequence selected = connection.getSelectedText(0);
                    if (!range.original.isEmpty() && (selected == null || !range.original.contentEquals(selected))) {
                        connection.setSelection(range.end, range.end);
                        throw new IllegalStateException("selected history differs from original");
                    }
                    require(connection.commitText(edit.history, 1));
                    trackedSelectionStart = trackedSelectionEnd = -1;
                    selectedBeforeComposition = null; compositionStart = -1; compositionText = null; compositionReplacedSelection = false;
                    require(connection.finishComposingText());
                } finally { if (batch) connection.endBatchEdit(); }
                refreshExpected();
            } finally { changingEditor = false; }
        } else {
            // Other intents use FinalAlternative.transcript, not edited_history.
            if (transcript == null || transcript.isEmpty()) { clearComposition(); return; }
            if (transcript.length() > TEXT_LIMIT) { fail(I18n.t("transcription_is_too_long"), null); return; }
            changingEditor = true;
            try {
                String text = transcript;
                CharSequence previous = connection.getTextBeforeCursor(1, 0);
                if (previous != null && previous.length() == 1 && !Character.isWhitespace(previous.charAt(0))
                        && !Character.isWhitespace(text.charAt(0)) && ".,!?;:)]}".indexOf(text.charAt(0)) < 0) {
                    text = " " + text;
                }
                require(connection.commitText(text, 1));
                trackedSelectionStart = trackedSelectionEnd = -1;
                selectedBeforeComposition = null; compositionStart = -1; compositionText = null; compositionReplacedSelection = false;
                require(connection.finishComposingText());
                refreshExpected();
            } finally { changingEditor = false; }
        }
        ui(wanted ? I18n.t("speak_now") : closeWhenDone ? I18n.t("recognized") : I18n.t("paused"), wanted, wanted ? LISTENING : IDLE);
    }

    private boolean readyToEdit() {
        if (!showing || !editorValid() || !matchesExpected()) {
            // Do not finish/delete composition through an editor which changed under us.
            selectedBeforeComposition = null; compositionStart = -1; compositionText = null; compositionReplacedSelection = false;
            closeInternal(); return false;
        }
        return true;
    }

    private void clearComposition() {
        if (selectedBeforeComposition == null) return;
        String original = selectedBeforeComposition;
        int start = compositionStart;
        boolean restoreSelection = compositionReplacedSelection;
        selectedBeforeComposition = null;
        compositionStart = -1; compositionText = null; compositionReplacedSelection = false;
        if (!editorValid() || !matchesExpected()) return;
        changingEditor = true;
        try {
            require(connection.setComposingText(original, 1));
            require(connection.finishComposingText());
            if (restoreSelection && expected.complete && start >= 0)
                require(connection.setSelection(start, start + original.length()));
            refreshExpected();
        } finally { changingEditor = false; }
    }

    private void refreshExpected() {
        Snapshot updated = snapshot();
        if (updated == null) throw new IllegalStateException("editor stopped exposing its text");
        expected = updated;
    }

    private boolean matchesExpected() {
        Snapshot actual = snapshot();
        return expected != null && expected.same(actual);
    }

    private Snapshot snapshot() {
        if (!editorValid()) return null;
        ExtractedTextRequest request = new ExtractedTextRequest();
        request.hintMaxChars = TEXT_LIMIT + 1; request.hintMaxLines = 100;
        ExtractedText text = connection.getExtractedText(request, 0);
        CharSequence before = connection.getTextBeforeCursor(TEXT_LIMIT + 1, 0);
        CharSequence after = connection.getTextAfterCursor(TEXT_LIMIT + 1, 0);
        CharSequence selected = connection.getSelectedText(0);
        boolean missingContext = before == null || after == null;
        if (before == null) before = "";
        if (after == null) after = "";
        String selection = selected == null ? null : selected.toString();
        if (selection != null && selection.length() > 200) return null;
        if (!missingContext && text != null && text.text != null && text.partialStartOffset < 0 && text.startOffset == 0
                && text.text.length() <= TEXT_LIMIT && text.selectionStart >= 0 && text.selectionEnd >= 0
                && text.selectionStart <= text.text.length() && text.selectionEnd <= text.text.length()) {
            String value = text.text.toString();
            int low = Math.min(text.selectionStart, text.selectionEnd), high = Math.max(text.selectionStart, text.selectionEnd);
            String extractedSelection = value.substring(low, high);
            // A prefix extract with startOffset=0 is not necessarily a whole document.
            // A null getSelectedText is NOT proof that a nonempty selection is absent.
            if (extractedSelection.length() <= 200 && value.substring(0, low).contentEquals(before)
                    && value.substring(high).contentEquals(after)
                    && (selection == null || extractedSelection.equals(selection)))
            {
                headerOnlyPreview = false;
                return new Snapshot(value, text.selectionStart, text.selectionEnd, true);
            }
        }
        // Without a reliable extract, a null selected text is not evidence of
        // an empty selection. Never stage a composing replacement in this editor.
        headerOnlyPreview = true;
        if (selection == null) selection = "";
        // Editors without ExtractedText still support ordinary composing/commit.
        // This bounded context is NOT a range we can safely replace as full history.
        String left = before.toString(), right = after.toString();
        if (left.length() > 900) {
            int start = left.length() - 900;
            if (Character.isLowSurrogate(left.charAt(start))) start++;
            left = left.substring(start);
        }
        if (right.length() > 900) {
            int end = 900;
            if (Character.isHighSurrogate(right.charAt(end - 1))) end--;
            right = right.substring(0, end);
        }
        return new Snapshot(left + selection + right, left.length(), left.length() + selection.length(), false);
    }

    /** API 31 supplies an absolute offset even when ExtractedText is only a window.
     * Reject offset=-1, selections and stale/inconsistent editor implementations. */
    private HistoryRange captureHistory() {
        if (!editorValid()) return null;
        SurroundingText context = connection.getSurroundingText(TEXT_LIMIT + 1, 64, 0);
        if (context == null || context.getText() == null || context.getOffset() < 0) {
            // Preserve support for editors implementing only legacy ExtractedText.
            Snapshot legacy = snapshot();
            if (legacy == null || !legacy.complete || legacy.start != legacy.end) return null;
            return HistoryRange.capture(legacy.text.substring(0, legacy.start), legacy.start, true);
        }
        if (context.getSelectionStart() != context.getSelectionEnd()) return null;
        int caret = context.getSelectionStart();
        if (caret < 0 || caret > context.getText().length()) return null;
        String before = context.getText().subSequence(0, caret).toString();
        CharSequence check = connection.getTextBeforeCursor(before.length(), 0);
        if (check == null || !before.contentEquals(check)) return null;
        long absolute = (long) context.getOffset() + caret;
        if (absolute > Integer.MAX_VALUE) return null;
        return HistoryRange.capture(before, (int) absolute, true);
    }

    private boolean historyUnchanged(HistoryRange range) {
        HistoryRange actual = captureHistory();
        return actual != null && range.start == actual.start && range.end == actual.end
                && range.original.equals(actual.original);
    }

    private boolean editorValid() {
        EditorInfo now = service.getCurrentInputEditorInfo();
        return connection != null && connection == service.getCurrentInputConnection() && editor != null && now == editor
                && !password(now) && now.fieldId == editorField && now.inputType == editorType
                && Objects.equals(editorPackage, now.packageName);
    }

    private void watchEditor() {
        if (!showing) return;
        if (!root.isAttachedToWindow() || !editorValid()) { closeInternal(); return; }
        if (!changingEditor && !matchesExpected()) pauseForExternalInput();
        if (showing) main.postDelayed(editorWatch, 750);
    }

    private void pauseForExternalInput() {
        if (!showing || changingEditor) return;
        wanted = false;
        feedback(false);
        closeWhenDone = false;
        Run old = run;
        if (old != null) {
            old.ended = true; old.edit = null; old.acceptAudio = false;
            if (capture != null) capture.stopping = true;
            if (old.audio != null) old.audio.stop(false);
            cancelOffMain(old);
            if (old.audioStopped) run = null;
        }
        try {
            if (matchesExpected()) clearComposition();
            else restoreAfterSelectionChange();
        } catch (Throwable error) { log("discard interim on external edit", error); }
        selectedBeforeComposition = null; compositionStart = -1; compositionText = null; compositionReplacedSelection = false;
        Snapshot current = snapshot();
        if (current == null) { closeInternal(); return; }
        expected = current;
        if (panel != null) panel.setPaused(true);
        ui(I18n.t("paused"), false, IDLE);
    }

    // A selection-only change may arrive after the editor moved the caret. Restore
    // just our unchanged composing span, then put the user's selection back.
    private void restoreAfterSelectionChange() {
        if (selectedBeforeComposition == null || !editorValid() || expected == null) return;
        Snapshot actual = snapshot();
        if (actual == null || !actual.complete || !expected.complete
                || !expected.text.equals(actual.text) || compositionText == null
                || compositionStart < 0 || compositionStart + compositionText.length() > actual.text.length()
                || !actual.text.regionMatches(compositionStart, compositionText, 0, compositionText.length())) return;
        int oldLength = compositionText.length(), delta = selectedBeforeComposition.length() - oldLength;
        int start = actual.start, end = actual.end;
        changingEditor = true;
        try {
            require(connection.setSelection(compositionStart, compositionStart + oldLength));
            require(connection.setComposingText(selectedBeforeComposition, 1));
            require(connection.finishComposingText());
            require(connection.setSelection(adjustAfterRestore(start, compositionStart, oldLength, delta),
                    adjustAfterRestore(end, compositionStart, oldLength, delta)));
        } finally { changingEditor = false; }
    }

    private static int adjustAfterRestore(int position, int start, int oldLength, int delta) {
        if (position <= start) return position;
        if (position >= start + oldLength) return position + delta;
        return start + Math.min(position - start, oldLength + delta);
    }

    private void fail(String message, Throwable error) {
        if (error != null) log("voice operation", error);
        wanted = false; retryAvailable = true; closeWhenDone = false;
        feedback(false);
        if (panel != null) panel.setPaused(true);
        Run old = run;
        if (old != null) {
            old.ended = true; old.failed = true; old.edit = null; old.acceptAudio = false;
            if (capture != null) capture.stopping = true;
            if (old.audio != null) old.audio.stop(false);
            cancelOffMain(old);
            // Wait for resource release before permitting another AudioRecord.
            if (old.audioStopped) run = null;
        }
        try { clearComposition(); } catch (Throwable nested) { log("clear composition", nested); }
        if (showing) {
            try { ui(describe(error) + message, false, ERROR); } catch (Throwable nested) { log("error UI", nested); }
        }
    }

    private void closeInternal() {
        feedback(false);
        showing = false; wanted = false; generation++;
        try { main.removeCallbacksAndMessages(null); } catch (Throwable error) { log("remove callbacks", error); }
        Capture oldCapture = capture; capture = null;
        if (oldCapture != null) oldCapture.audio.stop(false);
        Run old = run; run = null;
        if (old != null) {
            old.acceptAudio = false;
            try { if (old.audio != null) old.audio.stop(false); } catch (Throwable error) { log("abort audio", error); }
            cancelOffMain(old);
        }
        try { clearComposition(); } catch (Throwable error) { log("finish editor", error); }
        connection = null; editor = null; expected = null;
        selectedBeforeComposition = null; compositionStart = -1; compositionText = null; compositionReplacedSelection = false;
        try { root.removeOnAttachStateChangeListener(attachment); } catch (Throwable error) { log("detach root listener", error); }
        VoicePanel oldPanel = panel; panel = null;
        if (oldPanel != null) {
            try { oldPanel.stop(); } catch (Throwable error) { log("stop panel", error); }
        }
        ModuleMain.HeaderMount oldMount = headerMount; headerMount = null;
        if (oldMount != null) {
            try { oldMount.close(); } catch (Throwable error) { log("remove panel", error); }
        }
        if (!notified) {
            notified = true;
            try { if (onClosed != null) onClosed.run(); } catch (Throwable error) { log("closed callback", error); }
        }
    }

    /** No IGNORE_* flags: View honors the user's global haptic preference. */
    private void feedback(boolean start) {
        if (feedbackActive == start) return;
        feedbackActive = start;
        try {
            if (options.haptic) root.performHapticFeedback(start
                    ? android.view.HapticFeedbackConstants.CONFIRM
                    : android.view.HapticFeedbackConstants.CONTEXT_CLICK);
        } catch (Throwable error) { log("haptic feedback", error); }
        try {
            if (!options.sound) return;
            android.media.AudioManager audio = (android.media.AudioManager) service.getSystemService(android.content.Context.AUDIO_SERVICE);
            android.app.NotificationManager notifications = (android.app.NotificationManager) service.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            if (audio == null || audio.getRingerMode() != android.media.AudioManager.RINGER_MODE_NORMAL
                    || audio.getStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION) == 0
                    || notifications == null || notifications.getCurrentInterruptionFilter() != android.app.NotificationManager.INTERRUPTION_FILTER_ALL) return;
            if (tone != null) { tone.release(); tone = null; }
            android.media.ToneGenerator player = new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 30);
            tone = player;
            player.startTone(start ? android.media.ToneGenerator.TONE_PROP_BEEP : android.media.ToneGenerator.TONE_PROP_ACK, 65);
            // Separate handler: closeInternal removes the session handler's callbacks.
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { if (tone == player) { player.release(); tone = null; } }
                catch (Throwable error) { log("release feedback sound", error); }
            }, 160);
        } catch (Throwable error) { log("sound feedback", error); }
    }

    private void cancelOffMain(Run old) {
        if (old.cancelled || old.session == null) return;
        old.cancelled = true;
        try {
            CLOSER.execute(() -> {
                try { old.session.cancel(); } catch (Throwable error) { log("cancel transport", error); }
            });
        } catch (Throwable error) { log("schedule transport cleanup", error); }
    }

    private boolean current(Run candidate) { return showing && run == candidate && generation == candidate.generation; }
    private void post(Run owner, Runnable action) { enqueue(() -> { if (current(owner)) action.run(); }); }
    private void enqueue(Runnable action) {
        try { main.post(() -> guarded(action)); } catch (Throwable error) { log("post callback", error); }
    }
    private void later(Runnable action, long delay) {
        try { main.postDelayed(() -> guarded(action), delay); } catch (Throwable error) { log("post delayed", error); }
    }
    private void dispatch(Runnable action) {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) guarded(action);
            else enqueue(action);
        } catch (Throwable error) { log("dispatch", error); }
    }
    private void guarded(Runnable action) {
        try { action.run(); }
        catch (Throwable error) {
            log("callback", error);
            try {
                if (!showing) closeInternal();
                else fail(I18n.t("voice_input_error_try_again"), null);
            }
            catch (Throwable nested) { log("failure cleanup", nested); try { closeInternal(); } catch (Throwable ignored) { log("close", ignored); } }
        }
    }
    private void ui(String status, boolean listening, int wave) {
        if (panel == null) return;
        panel.setStatus(status); panel.setListening(listening); panel.setWaveState(wave);
        if (!listening) panel.setLevel(0);
    }
    private void toast(String text) { Toast.makeText(service, text, Toast.LENGTH_LONG).show(); }
    private boolean hasPermission() { return service.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED; }
    private static void require(boolean result) { if (!result) throw new IllegalStateException("editor rejected voice edit"); }
    private static boolean password(EditorInfo info) {
        if (info == null) return true;
        int type = info.inputType & InputType.TYPE_MASK_CLASS;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        return type == InputType.TYPE_NULL
                || type == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                || type == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }
    private static String normalizeLanguage(String tag) {
        String value = tag == null || tag.trim().isEmpty() ? Locale.getDefault().toLanguageTag() : tag;
        if (value.equalsIgnoreCase("zh-CN")) return "zh-Hans";
        if (value.equalsIgnoreCase("zh-TW")) return "zh-Hant";
        if (value.equalsIgnoreCase("zh-HK")) return "yue";
        return value;
    }
    private static String describe(Throwable error) {
        if (error == null) return "";
        Throwable root = error;
        for (int i = 0; i < 8 && root.getCause() != null && root.getCause() != root; i++) root = root.getCause();
        String text = root.getClass().getSimpleName();
        String detail = root.getMessage();
        if (detail != null && !detail.isEmpty()) text += ": " + detail;
        if (text.length() > 160) text = text.substring(0, 160) + "…";
        return text + " — ";
    }
    private static void log(String operation, Throwable error) {
        try { Log.e(TAG, operation, error); } catch (Throwable ignored) { }
    }
    private static final class Capture {
        final AtomicInteger pendingBytes = new AtomicInteger();
        final SilenceDetector silence = new SilenceDetector();
        AudioPipeline audio;
        volatile Run destination;
        boolean stopping;
    }
    private static final class Run {
        final long generation;
        volatile boolean acceptAudio = true;
        S3Session session; AudioPipeline audio;
        OggOpusWriter ogg; byte[] lastPacket;
        boolean audioStopped, ended, cancelled, suppressFinal, failed, interimFinished, heardAudio, finalHandled;
        EditResult edit;
        HistoryRange history;
        Run(long generation) { this.generation = generation; }
    }
    private static final class EditResult {
        final int intent; final String full, history;
        EditResult(int intent, String full, String history) { this.intent = intent; this.full = full; this.history = history; }
    }
    private static final class Snapshot {
        final String text; final int start, end; final boolean complete;
        Snapshot(String text, int start, int end, boolean complete) {
            this.text = text; this.start = start; this.end = end; this.complete = complete;
        }
        int low() { return Math.min(start, end); }
        int high() { return Math.max(start, end); }
        boolean same(Snapshot other) {
            return other != null && complete == other.complete && start == other.start && end == other.end && text.equals(other.text);
        }
    }
}
