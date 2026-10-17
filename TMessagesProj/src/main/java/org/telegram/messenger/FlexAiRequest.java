package org.telegram.messenger;

import android.text.TextUtils;

import org.json.JSONArray;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_aicompose;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.Components.TranslateAlert2;
import org.telegram.ui.iv.RichMessageConvert;

import java.util.ArrayList;

/** Adapts the existing Telegram text/AI requests to the selected external service. */
public class FlexAiRequest {

    public int guid;
    private final int account;
    private final TLObject method;
    private final RequestDelegate completion;
    private final String modelRef;
    private final int translationProvider;
    private final String apiUrl;
    private final String apiKey;
    private final int apiType;
    private volatile boolean cancelled;
    private volatile Runnable cancellation;

    public static boolean supports(TLObject method) {
        return ((method instanceof TLRPC.TL_messages_translateText || method instanceof TLRPC.TL_messages_translateRichMessage) && FlexConfig.usesExternalTranslationProvider()) ||
            ((method instanceof TLRPC.TL_messages_summarizeText || method instanceof TLRPC.TL_messages_composeMessageWithAI || method instanceof TLRPC.TL_messages_composeRichMessageWithAI) && FlexConfig.usesExternalOfficialAi());
    }

    public FlexAiRequest(int account, TLObject method, RequestDelegate completion) {
        this.account = account;
        this.method = method;
        this.completion = completion;
        boolean translation = method instanceof TLRPC.TL_messages_translateText || method instanceof TLRPC.TL_messages_translateRichMessage;
        translationProvider = translation ? FlexConfig.getTranslationProvider() : FlexConfig.TRANSLATION_PROVIDER_LLM;
        modelRef = translation ? FlexConfig.getTranslationLlmModelRef() : FlexConfig.getOfficialAiModelRef();
        int provider = FlexConfig.getLlmProviderFromRef(modelRef);
        apiUrl = FlexConfig.getProviderApiUrl(provider);
        apiKey = FlexConfig.getProviderApiKey(provider);
        apiType = FlexConfig.getProviderApiType(provider);
    }

    public void start() {
        Utilities.globalQueue.postRunnable(() -> {
            if (cancelled) return;
            try {
                if (method instanceof TLRPC.TL_messages_translateText) {
                    TLRPC.TL_messages_translateText req = (TLRPC.TL_messages_translateText) method;
                    ArrayList<TLRPC.TL_textWithEntities> texts = new ArrayList<>(req.text);
                    for (int id : req.id) texts.add(messageText(loadMessage(req.peer, id)));
                    translate(texts, req.to_lang, 0, new TLRPC.TL_messages_translateResult());
                } else if (method instanceof TLRPC.TL_messages_translateRichMessage) {
                    TLRPC.TL_messages_translateRichMessage req = (TLRPC.TL_messages_translateRichMessage) method;
                    ArrayList<TL_iv.RichMessage> texts = new ArrayList<>();
                    for (TL_iv.TL_inputRichMessage input : req.text) texts.add(input.localSource);
                    for (int id : req.id) texts.add(loadMessage(req.peer, id).rich_message);
                    translateRich(texts, req.to_lang, req.tone, 0, new TLRPC.TL_messages_translatedRichMessage());
                } else if (method instanceof TLRPC.TL_messages_summarizeText) {
                    TLRPC.TL_messages_summarizeText req = (TLRPC.TL_messages_summarizeText) method;
                    String instruction = "Summarize the supplied message accurately and concisely. Preserve its important facts. Return only the summary.";
                    if (!TextUtils.isEmpty(req.to_lang)) instruction += " Write in " + req.to_lang + ".";
                    if (!TextUtils.isEmpty(req.tone)) instruction += " Tone: " + req.tone + ".";
                    requestText(instruction, messageText(loadMessage(req.peer, req.id)).text, (text, error) -> finish(error == null ? entities(text, true) : null, error));
                } else if (method instanceof TLRPC.TL_messages_composeMessageWithAI) {
                    TLRPC.TL_messages_composeMessageWithAI req = (TLRPC.TL_messages_composeMessageWithAI) method;
                    String instruction = composePrompt(req.proofread, req.emojify, req.translate_to_lang, req.tone);
                    Utilities.Callback2<TLRPC.TL_textWithEntities, String> done = (text, error) -> {
                        TLRPC.TL_composedMessageWithAI result = new TLRPC.TL_composedMessageWithAI();
                        result.result_text = text;
                        finish(error == null ? result : null, error);
                    };
                    if (req.text.entities.isEmpty()) {
                        requestText(instruction, req.text.text, (text, error) -> done.run(error == null ? entities(text, true) : null, error));
                    } else {
                        transformRich(RichMessageConvert.fromCharSequence(MessageObject.formatTextWithEntities(req.text)), instruction, null, (rich, error) ->
                            done.run(error == null ? entities(RichMessageConvert.toCharSequence(rich), false) : null, error));
                    }
                } else {
                    TLRPC.TL_messages_composeRichMessageWithAI req = (TLRPC.TL_messages_composeRichMessageWithAI) method;
                    String instruction = composePrompt(req.proofread, req.emojify, req.translate_to_lang, req.tone);
                    if (req.text == null) {
                        requestText("Generate the article requested by the user using Markdown. Return only the article content.", ((TL_aicompose.inputAiComposeToneSingleUse) req.tone).custom_prompt, (text, error) -> {
                            TLRPC.TL_composedRichMessageWithAI result = new TLRPC.TL_composedRichMessageWithAI();
                            if (error == null) result.result = RichMessageConvert.fromCharSequence(MessageObject.formatTextWithEntities(entities(text, true)));
                            finish(error == null ? result : null, error);
                        });
                    } else {
                        transformRich(req.text.localSource, instruction, null, (rich, error) -> {
                            TLRPC.TL_composedRichMessageWithAI result = new TLRPC.TL_composedRichMessageWithAI();
                            result.result = rich;
                            finish(error == null ? result : null, error);
                        });
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
                finish(null, e.getMessage());
            }
        });
    }

    private TLRPC.Message loadMessage(TLRPC.InputPeer peer, int id) {
        TLRPC.Message message = MessagesStorage.getInstance(account).getMessage(DialogObject.getPeerDialogId(peer), id);
        if (message == null) throw new IllegalStateException(LocaleController.getString(R.string.FlexAiSourceMissing));
        return message;
    }

    private TLRPC.TL_textWithEntities messageText(TLRPC.Message message) {
        TLRPC.TL_textWithEntities text = new TLRPC.TL_textWithEntities();
        text.text = message.rich_message != null ? RichMessageConvert.toCharSequence(message.rich_message).toString() : message.message;
        text.entities = message.entities;
        return text;
    }

    private TLRPC.TL_textWithEntities entities(CharSequence text, boolean markdown) {
        CharSequence[] value = {text};
        TLRPC.TL_textWithEntities result = new TLRPC.TL_textWithEntities();
        result.entities = MediaDataController.getInstance(account).getEntities(value, true, markdown);
        result.text = value[0].toString();
        return result;
    }

    private void translate(ArrayList<TLRPC.TL_textWithEntities> texts, String language, int index, TLRPC.TL_messages_translateResult result) {
        if (cancelled) return;
        if (index == texts.size()) {
            finish(result, null);
            return;
        }
        TLRPC.TL_textWithEntities source = texts.get(index);
        Utilities.Callback2<String, String> done = (text, error) -> {
            if (error != null) {
                finish(null, error);
            } else {
                TLRPC.TL_textWithEntities translated = new TLRPC.TL_textWithEntities();
                translated.text = text;
                result.result.add(TranslateAlert2.preprocess(source, translated));
                translate(texts, language, index + 1, result);
            }
        };
        if (TextUtils.isEmpty(source.text)) {
            done.run("", null);
        } else if (translationProvider == FlexConfig.TRANSLATION_PROVIDER_LLM) {
            if (source.entities.isEmpty()) {
                requestText(FlexConfig.getLlmPrompt(), "Target language: " + language + "\nText:\n" + source.text, done);
            } else {
                TL_iv.RichMessage rich = RichMessageConvert.fromCharSequence(MessageObject.formatTextWithEntities(source));
                transformRich(rich, FlexConfig.getLlmPrompt() + " Target language: " + language + ".", language, (translated, error) -> {
                    if (error != null) finish(null, error);
                    else {
                        TLRPC.TL_textWithEntities value = entities(RichMessageConvert.toCharSequence(translated), false);
                        result.result.add(TranslateAlert2.preprocess(source, value));
                        translate(texts, language, index + 1, result);
                    }
                });
            }
        } else {
            FlexTranslationHelper.translate(translationProvider, source.text, null, language, (text, limit, error) -> done.run(text, error));
        }
    }

    private void translateRich(ArrayList<TL_iv.RichMessage> texts, String language, String tone, int index, TLRPC.TL_messages_translatedRichMessage result) {
        if (cancelled) return;
        if (index == texts.size()) {
            finish(result, null);
            return;
        }
        transformRich(texts.get(index), "Translate into " + language + "." + (TextUtils.isEmpty(tone) ? "" : " Tone: " + tone + "."), language, (rich, error) -> {
            if (error != null) finish(null, error);
            else {
                result.result.add(rich);
                translateRich(texts, language, tone, index + 1, result);
            }
        });
    }

    private void transformRich(TL_iv.RichMessage source, String instruction, String language, Utilities.Callback2<TL_iv.RichMessage, String> done) {
        TL_iv.RichMessage result = FlexRichTextHelper.copy(source);
        ArrayList<TL_iv.textPlain> texts = FlexRichTextHelper.texts(result);
        JSONArray input = new JSONArray();
        for (TL_iv.textPlain text : texts) input.put(text.text);
        if (translationProvider != FlexConfig.TRANSLATION_PROVIDER_LLM) {
            // Non-LLM services translate each text run without changing document metadata.
            translateRichRuns(texts, language, 0, () -> done.run(result, null), error -> done.run(null, error));
            return;
        }
        requestText(instruction + " The input is a JSON array of consecutive document text runs. Return ONLY a JSON array of strings with exactly the same length and order. Preserve URLs, names, code and mathematical notation; do not add or remove runs.", input.toString(), (output, error) -> {
            if (error != null) {
                done.run(null, error);
                return;
            }
            try {
                JSONArray values = new JSONArray(output);
                if (values.length() != texts.size()) throw new IllegalStateException(LocaleController.getString(R.string.FlexTranslationInvalidResponse));
                for (int i = 0; i < texts.size(); i++) texts.get(i).text = values.getString(i);
                done.run(result, null);
            } catch (Exception e) {
                FileLog.e(e);
                done.run(null, e.getMessage());
            }
        });
    }

    private void translateRichRuns(ArrayList<TL_iv.textPlain> texts, String language, int index, Runnable done, Utilities.Callback<String> error) {
        if (cancelled) return;
        if (index == texts.size()) {
            done.run();
            return;
        }
        FlexTranslationHelper.translate(translationProvider, texts.get(index).text, null, language, (text, limit, failure) -> {
            if (failure != null) error.run(failure);
            else {
                texts.get(index).text = text;
                translateRichRuns(texts, language, index + 1, done, error);
            }
        });
    }

    private String composePrompt(boolean proofread, boolean emojify, String language, TL_aicompose.InputAiComposeTone tone) {
        StringBuilder prompt = new StringBuilder("Edit the supplied text. Preserve its meaning, formatting, links, names and factual content. Return only the edited text.\n");
        if (proofread) prompt.append("Correct spelling and grammar.\n");
        if (!TextUtils.isEmpty(language)) prompt.append("Translate into ").append(language).append(".\n");
        if (emojify) prompt.append("Add appropriate emoji.\n");
        if (tone instanceof TL_aicompose.inputAiComposeToneSingleUse) {
            prompt.append(((TL_aicompose.inputAiComposeToneSingleUse) tone).custom_prompt);
        } else if (tone instanceof TL_aicompose.inputAiComposeToneDefault) {
            prompt.append("Writing style: ").append(((TL_aicompose.inputAiComposeToneDefault) tone).tone);
        } else if (tone instanceof TL_aicompose.inputAiComposeToneID) {
            long id = ((TL_aicompose.inputAiComposeToneID) tone).id;
            TL_aicompose.TL_aiComposeTone selected = null;
            for (TL_aicompose.AiComposeTone candidate : MessagesController.getInstance(account).getTonesController().tones) {
                if (candidate instanceof TL_aicompose.TL_aiComposeTone && ((TL_aicompose.TL_aiComposeTone) candidate).id == id) selected = (TL_aicompose.TL_aiComposeTone) candidate;
            }
            if (selected == null || TextUtils.isEmpty(selected.prompt)) throw new IllegalStateException(LocaleController.getString(R.string.FlexAiToneMissing));
            prompt.append(selected.prompt);
        }
        return prompt.toString();
    }

    private void requestText(String instruction, String text, Utilities.Callback2<String, String> done) {
        if (cancelled) return;
        cancellation = FlexLlmHelper.requestText(apiType, apiUrl, apiKey, FlexConfig.getLlmModelFromRef(modelRef), instruction + (method instanceof TLRPC.TL_messages_translateText || method instanceof TLRPC.TL_messages_translateRichMessage ? "" : "\n" + FlexConfig.getOfficialAiPrompt()), text, (result, error) -> {
            if (cancelled) return;
            try {
                done.run(result, error);
            } catch (Exception e) {
                FileLog.e(e);
                finish(null, e.getMessage());
            }
        });
        if (cancelled && cancellation != null) cancellation.run();
    }

    private void finish(TLObject result, String error) {
        Utilities.stageQueue.postRunnable(() -> {
            if (cancelled) return;
            TLRPC.TL_error failure = null;
            if (error != null || result == null) {
                failure = new TLRPC.TL_error();
                failure.code = -1;
                failure.text = TextUtils.isEmpty(error) ? LocaleController.getString(R.string.FlexLlmRequestFailed) : error;
            }
            completion.run(result, failure);
        });
    }

    public void cancel() {
        cancelled = true;
        Runnable action = cancellation;
        if (action != null) action.run();
    }
}
