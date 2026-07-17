package org.telegram.messenger;

import android.content.ContentResolver;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TranslateAlert2;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

public class FlexFileManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void exportSettings(Uri uri, int currentAccount) throws Exception {
        ContentResolver resolver = ApplicationLoader.applicationContext.getContentResolver();
        OutputStream outputStream = resolver.openOutputStream(uri, "wt");
        if (outputStream == null) {
            throw new IllegalStateException(LocaleController.getString(R.string.FlexFileUnavailable));
        }

        TranslateController translateController = MessagesController.getInstance(currentAccount).getTranslateController();
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        LinkedHashMap<String, Object> global = new LinkedHashMap<>();
        LinkedHashMap<String, Object> account = new LinkedHashMap<>();

        root.put("version", 6);
        root.put("exported_at", System.currentTimeMillis());
        root.put("current_account", currentAccount);
        root.put("global", global);
        root.put("account", account);

        global.put("download_speed_boost", FlexConfig.getDownloadSpeedBoost());
        global.put("default_video_quality", FlexConfig.getDefaultVideoQuality());
        global.put("default_photo_high_quality", SharedConfig.photoHighQualityDefault);
        global.put("auto_retry_failed_media_downloads", SharedConfig.autoRetryFailedMediaDownloads);
        global.put("disable_channel_swipe_next", SharedConfig.disableChannelSwipeNext);
        global.put("lazy_attach_camera", SharedConfig.lazyAttachCamera);
        global.put("disable_webrtc", FlexConfig.isWebRtcDisabled());
        global.put("disable_no_forwards_restrictions", FlexConfig.isNoForwardsRestrictionsDisabled());
        global.put("forwarding_hide_source_default", FlexConfig.isForwardingSourceHiddenByDefault());
        global.put("forwarding_hide_caption_default", FlexConfig.isForwardingCaptionHiddenByDefault());
        global.put("show_dc_info", FlexConfig.isDcInfoEnabled());
        global.put("hide_main_tabs", FlexConfig.isMainTabsHidden());
        global.put("disable_ui_transparency", FlexConfig.isUiTransparencyDisabled());
        global.put("disable_ui_blur", FlexConfig.isUiBlurDisabled());
        global.put("disable_markdown", FlexConfig.isMarkdownDisabled());
        global.put("new_markdown_parser", FlexConfig.isNewMarkdownParserEnabled());
        global.put("markdown_parse_links", FlexConfig.isMarkdownParseLinksEnabled());
        global.put("ad_block_enabled", FlexConfig.isAdBlockEnabled());
        global.put("ad_block_keywords", FlexConfig.getAdBlockKeywordsText());
        global.put("ad_blocked_message_ids", FlexConfig.getAdBlockedMessageIdsText());
        global.put("ad_blocked_message_rules", FlexConfig.getAdBlockedMessageRulesText());
        global.put("ad_blocked_user_ids", FlexConfig.getAdBlockedUsersText());
        global.put("translation_provider", FlexConfig.getTranslationProvider());
        global.put("translation_target_language", TranslateAlert2.getToLanguage());
        global.put("translation_deepl_api_url", FlexConfig.getDeepLApiUrl());
        global.put("translation_deepl_api_key", FlexConfig.getDeepLApiKey());
        global.put("translation_llm_model_ref", FlexConfig.getTranslationLlmModelRef());
        global.put("translation_llm_prompt", FlexConfig.getLlmPrompt());
        global.put("ai_summary_llm_model_ref", FlexConfig.getAiSummaryLlmModelRef());
        global.put("ai_summary_llm_prompt", FlexConfig.getAiSummaryLlmPrompt());
        LinkedHashMap<String, Object> llmProviders = new LinkedHashMap<>();
        for (int provider = FlexConfig.LLM_PROVIDER_CUSTOM; provider <= FlexConfig.LLM_PROVIDER_ANTHROPIC; ++provider) {
            LinkedHashMap<String, Object> config = new LinkedHashMap<>();
            config.put("api_type", FlexConfig.getProviderApiType(provider));
            config.put("api_url", FlexConfig.getStoredProviderApiUrl(provider));
            config.put("api_key", FlexConfig.getProviderApiKey(provider));
            config.put("models", FlexConfig.getProviderModelsText(provider));
            llmProviders.put(String.valueOf(provider), config);
        }
        global.put("llm_provider_configs", llmProviders);

        account.put("translate_button", translateController.isContextTranslateEnabled());
        account.put("translate_chat_button", translateController.isChatTranslateEnabled());

        try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    public static void importSettings(Uri uri, int currentAccount) throws Exception {
        ContentResolver resolver = ApplicationLoader.applicationContext.getContentResolver();
        InputStream inputStream = resolver.openInputStream(uri);
        if (inputStream == null) {
            throw new IllegalStateException(LocaleController.getString(R.string.FlexFileUnavailable));
        }

        JsonObject root;
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }

        JsonObject global = root.has("global") && root.get("global").isJsonObject() ? root.getAsJsonObject("global") : root;
        if (global.has("download_speed_boost")) {
            FlexConfig.setDownloadSpeedBoost(global.get("download_speed_boost").getAsInt());
        }
        if (global.has("default_video_quality")) {
            FlexConfig.setDefaultVideoQuality(global.get("default_video_quality").getAsInt());
        }
        if (global.has("default_photo_high_quality")) {
            SharedConfig.photoHighQualityDefault = global.get("default_photo_high_quality").getAsBoolean();
            SharedConfig.saveConfig();
        }
        if (global.has("auto_retry_failed_media_downloads")) {
            SharedConfig.autoRetryFailedMediaDownloads = global.get("auto_retry_failed_media_downloads").getAsBoolean();
            SharedConfig.saveConfig();
        }
        if (global.has("disable_channel_swipe_next")) {
            SharedConfig.disableChannelSwipeNext = global.get("disable_channel_swipe_next").getAsBoolean();
            SharedConfig.saveConfig();
        }
        if (global.has("lazy_attach_camera")) {
            SharedConfig.lazyAttachCamera = global.get("lazy_attach_camera").getAsBoolean();
            SharedConfig.saveConfig();
        }
        if (global.has("disable_webrtc")) {
            FlexConfig.setWebRtcDisabled(global.get("disable_webrtc").getAsBoolean());
        }
        if (global.has("disable_no_forwards_restrictions")) {
            FlexConfig.setNoForwardsRestrictionsDisabled(global.get("disable_no_forwards_restrictions").getAsBoolean());
        }
        if (global.has("forwarding_hide_source_default")) {
            FlexConfig.setForwardingSourceHiddenByDefault(global.get("forwarding_hide_source_default").getAsBoolean());
        }
        if (global.has("forwarding_hide_caption_default")) {
            FlexConfig.setForwardingCaptionHiddenByDefault(global.get("forwarding_hide_caption_default").getAsBoolean());
        }
        if (global.has("show_dc_info")) {
            FlexConfig.setDcInfoEnabled(global.get("show_dc_info").getAsBoolean());
        }
        if (global.has("hide_main_tabs")) {
            FlexConfig.setMainTabsHidden(global.get("hide_main_tabs").getAsBoolean());
        }
        if (global.has("disable_ui_transparency")) {
            FlexConfig.setUiTransparencyDisabled(global.get("disable_ui_transparency").getAsBoolean());
        }
        if (global.has("disable_ui_blur")) {
            FlexConfig.setUiBlurDisabled(global.get("disable_ui_blur").getAsBoolean());
        }
        if (global.has("disable_markdown")) {
            FlexConfig.setMarkdownDisabled(global.get("disable_markdown").getAsBoolean());
        }
        if (global.has("new_markdown_parser")) {
            FlexConfig.setNewMarkdownParserEnabled(global.get("new_markdown_parser").getAsBoolean());
        }
        if (global.has("markdown_parse_links")) {
            FlexConfig.setMarkdownParseLinksEnabled(global.get("markdown_parse_links").getAsBoolean());
        }
        if (global.has("ad_block_enabled")) {
            FlexConfig.setAdBlockEnabled(global.get("ad_block_enabled").getAsBoolean());
        }
        if (global.has("ad_block_keywords")) {
            FlexConfig.setAdBlockKeywordsText(global.get("ad_block_keywords").getAsString());
        }
        if (global.has("ad_blocked_message_ids")) {
            FlexConfig.setAdBlockedMessageIdsText(global.get("ad_blocked_message_ids").getAsString());
        }
        if (global.has("ad_blocked_message_rules")) {
            FlexConfig.setAdBlockedMessageRulesText(global.get("ad_blocked_message_rules").getAsString());
        }
        if (global.has("ad_blocked_user_ids")) {
            FlexConfig.setAdBlockedUsersText(global.get("ad_blocked_user_ids").getAsString());
        }
        if (global.has("translation_provider")) {
            FlexConfig.setTranslationProvider(global.get("translation_provider").getAsInt());
        }
        if (global.has("translation_target_language")) {
            TranslateAlert2.setToLanguage(global.get("translation_target_language").getAsString());
        }
        if (global.has("translation_deepl_api_url")) {
            FlexConfig.setDeepLApiUrl(global.get("translation_deepl_api_url").getAsString());
        }
        if (global.has("translation_deepl_api_key")) {
            FlexConfig.setDeepLApiKey(global.get("translation_deepl_api_key").getAsString());
        }
        if (global.has("translation_llm_prompt")) {
            FlexConfig.setLlmPrompt(global.get("translation_llm_prompt").getAsString());
        }
        if (global.has("ai_summary_llm_prompt")) {
            FlexConfig.setAiSummaryLlmPrompt(global.get("ai_summary_llm_prompt").getAsString());
        }
        if (global.has("llm_provider_configs") && global.get("llm_provider_configs").isJsonObject()) {
            JsonObject llmProviders = global.getAsJsonObject("llm_provider_configs");
            for (int provider = FlexConfig.LLM_PROVIDER_CUSTOM; provider <= FlexConfig.LLM_PROVIDER_ANTHROPIC; ++provider) {
                String key = String.valueOf(provider);
                if (!llmProviders.has(key) || !llmProviders.get(key).isJsonObject()) {
                    continue;
                }
                JsonObject config = llmProviders.getAsJsonObject(key);
                if (config.has("api_type")) {
                    FlexConfig.setProviderApiType(provider, config.get("api_type").getAsInt());
                }
                if (config.has("api_url")) {
                    FlexConfig.setProviderApiUrl(provider, config.get("api_url").getAsString());
                }
                if (config.has("api_key")) {
                    FlexConfig.setProviderApiKey(provider, config.get("api_key").getAsString());
                }
                if (config.has("models")) {
                    FlexConfig.setProviderModelsText(provider, config.get("models").getAsString());
                }
            }
        }
        if (global.has("translation_llm_model_ref")) {
            FlexConfig.setTranslationLlmModelRef(global.get("translation_llm_model_ref").getAsString());
        }
        if (global.has("ai_summary_llm_model_ref")) {
            FlexConfig.setAiSummaryLlmModelRef(global.get("ai_summary_llm_model_ref").getAsString());
        }

        JsonObject account = root.has("account") && root.get("account").isJsonObject() ? root.getAsJsonObject("account") : root;
        TranslateController translateController = MessagesController.getInstance(currentAccount).getTranslateController();
        if (account.has("translate_button")) {
            translateController.setContextTranslateEnabled(account.get("translate_button").getAsBoolean());
        }
        if (account.has("translate_chat_button")) {
            translateController.setChatTranslateEnabled(account.get("translate_chat_button").getAsBoolean());
        }

        Theme.refreshThemeColors();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mainTabsVisibilityToggled);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.flexAdBlockSettingsChanged);
    }
}
