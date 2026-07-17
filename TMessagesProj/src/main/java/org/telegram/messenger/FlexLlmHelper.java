package org.telegram.messenger;

import android.net.Uri;
import android.text.TextUtils;

import com.google.common.base.Charsets;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;

public class FlexLlmHelper {

    private static final int CLAUDE_MAX_TOKENS = 4096;
    private static final String CLAUDE_API_VERSION = "2023-06-01";

    public static void requestText(String apiUrl, String apiKey, String model, String systemPrompt, String userPrompt, double temperature, Utilities.Callback2<String, String> done) {
        requestText(FlexConfig.LLM_API_TYPE_CHAT_COMPLETIONS, apiUrl, apiKey, model, systemPrompt, userPrompt, temperature, done);
    }

    public static void requestText(int apiType, String apiUrl, String apiKey, String model, String systemPrompt, String userPrompt, double temperature, Utilities.Callback2<String, String> done) {
        if (done == null) {
            return;
        }
        if (TextUtils.isEmpty(apiUrl)) {
            AndroidUtilities.runOnUIThread(() -> done.run(null, LocaleController.getString(R.string.FlexLlmApiUrlMissing)));
            return;
        }
        if (TextUtils.isEmpty(model)) {
            AndroidUtilities.runOnUIThread(() -> done.run(null, LocaleController.getString(R.string.FlexLlmModelMissing)));
            return;
        }
        new Thread(() -> {
            HttpURLConnection connection = null;
            String responseText = null;
            try {
                connection = (HttpURLConnection) new URI(apiUrl).toURL().openConnection();
                connection.setConnectTimeout(15000);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                setAuthHeaders(connection, apiType, apiKey);

                JSONObject request = new JSONObject();
                request.put("model", model);
                request.put("temperature", temperature);
                if (apiType == FlexConfig.LLM_API_TYPE_RESPONSES) {
                    if (!TextUtils.isEmpty(systemPrompt)) {
                        request.put("instructions", systemPrompt);
                    }
                    request.put("input", userPrompt);
                } else if (apiType == FlexConfig.LLM_API_TYPE_CLAUDE_MESSAGES) {
                    request.put("max_tokens", CLAUDE_MAX_TOKENS);
                    if (!TextUtils.isEmpty(systemPrompt)) {
                        request.put("system", systemPrompt);
                    }
                    request.put("messages", new JSONArray()
                        .put(new JSONObject()
                            .put("role", "user")
                            .put("content", userPrompt)));
                } else {
                    JSONArray messages = new JSONArray();
                    if (!TextUtils.isEmpty(systemPrompt)) {
                        messages.put(new JSONObject()
                            .put("role", "system")
                            .put("content", systemPrompt));
                    }
                    messages.put(new JSONObject()
                        .put("role", "user")
                        .put("content", userPrompt));
                    request.put("messages", messages);
                }

                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(request.toString().getBytes(Charsets.UTF_8));
                }

                responseText = readConnectionText(connection, false);
                JSONObject response = new JSONObject(responseText);
                String result;
                if (apiType == FlexConfig.LLM_API_TYPE_RESPONSES) {
                    String status = response.optString("status", "");
                    if ("failed".equals(status)) {
                        JSONObject error = response.optJSONObject("error");
                        String message = error != null ? error.optString("message", null) : null;
                        throw new IllegalStateException(TextUtils.isEmpty(message) ? LocaleController.getString(R.string.FlexLlmRequestFailed) : message);
                    }
                    if ("incomplete".equals(status)) {
                        JSONObject details = response.optJSONObject("incomplete_details");
                        String reason = details != null ? details.optString("reason", null) : null;
                        throw new IllegalStateException(LocaleController.getString(R.string.FlexLlmResponseIncomplete) + (TextUtils.isEmpty(reason) ? "" : " (" + reason + ")"));
                    }
                    result = extractResponseOutput(response.optJSONArray("output"));
                } else if (apiType == FlexConfig.LLM_API_TYPE_CLAUDE_MESSAGES) {
                    String stopReason = response.optString("stop_reason", "");
                    if (!TextUtils.isEmpty(stopReason) && !"end_turn".equals(stopReason)) {
                        throw new IllegalStateException(LocaleController.getString(R.string.FlexLlmResponseIncomplete) + " (" + stopReason + ")");
                    }
                    result = extractTextContent(response.optJSONArray("content"));
                } else {
                    result = extractChatCompletion(response);
                }
                if (TextUtils.isEmpty(result)) {
                    throw new IllegalStateException(LocaleController.getString(R.string.FlexLlmInvalidResponse));
                }
                String finalResult = result.trim();
                AndroidUtilities.runOnUIThread(() -> done.run(finalResult, null));
            } catch (Exception e) {
                try {
                    String errorResponseText = readConnectionText(connection, true);
                    if (!TextUtils.isEmpty(errorResponseText)) {
                        responseText = errorResponseText;
                    }
                } catch (Exception readError) {
                    FileLog.e(readError);
                }
                String errorMessage = getErrorMessage(connection, responseText, e);
                FileLog.e(e);
                String finalErrorMessage = errorMessage;
                AndroidUtilities.runOnUIThread(() -> done.run(null, finalErrorMessage));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "FlexLlmRequest").start();
    }

    public static void requestModels(String apiUrl, String apiKey, Utilities.Callback2<ArrayList<String>, String> done) {
        requestModels(FlexConfig.LLM_API_TYPE_CHAT_COMPLETIONS, apiUrl, apiKey, done);
    }

    public static void requestModels(int apiType, String apiUrl, String apiKey, Utilities.Callback2<ArrayList<String>, String> done) {
        if (done == null) {
            return;
        }
        if (TextUtils.isEmpty(apiUrl)) {
            AndroidUtilities.runOnUIThread(() -> done.run(null, LocaleController.getString(R.string.FlexLlmApiUrlMissing)));
            return;
        }
        new Thread(() -> {
            HttpURLConnection connection = null;
            String responseText = null;
            try {
                ArrayList<String> models = new ArrayList<>();
                String modelsApiUrl = getModelsApiUrl(apiUrl);
                String afterId = null;
                while (true) {
                    String requestUrl = modelsApiUrl;
                    if (!TextUtils.isEmpty(afterId)) {
                        requestUrl = Uri.parse(modelsApiUrl).buildUpon().appendQueryParameter("after_id", afterId).build().toString();
                    }
                    connection = (HttpURLConnection) new URI(requestUrl).toURL().openConnection();
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Accept", "application/json");
                    setAuthHeaders(connection, apiType, apiKey);

                    responseText = readConnectionText(connection, false);
                    JSONObject response = new JSONObject(responseText);
                    JSONArray data = response.optJSONArray("data");
                    if (data == null) {
                        throw new IllegalStateException(LocaleController.getString(R.string.FlexLlmInvalidResponse));
                    }
                    for (int i = 0; i < data.length(); ++i) {
                        JSONObject model = data.optJSONObject(i);
                        String id = model != null ? model.optString("id", "").trim() : "";
                        if (!TextUtils.isEmpty(id) && !models.contains(id)) {
                            models.add(id);
                        }
                    }
                    if (apiType != FlexConfig.LLM_API_TYPE_CLAUDE_MESSAGES || !response.optBoolean("has_more")) {
                        break;
                    }
                    afterId = response.optString("last_id", null);
                    if (TextUtils.isEmpty(afterId)) {
                        throw new IllegalStateException(LocaleController.getString(R.string.FlexLlmInvalidResponse));
                    }
                    connection.disconnect();
                    connection = null;
                }
                if (models.isEmpty()) {
                    throw new IllegalStateException(LocaleController.getString(R.string.FlexLlmInvalidResponse));
                }
                AndroidUtilities.runOnUIThread(() -> done.run(models, null));
            } catch (Exception e) {
                try {
                    String errorResponseText = readConnectionText(connection, true);
                    if (!TextUtils.isEmpty(errorResponseText)) {
                        responseText = errorResponseText;
                    }
                } catch (Exception readError) {
                    FileLog.e(readError);
                }
                String errorMessage = getErrorMessage(connection, responseText, e);
                FileLog.e(e);
                String finalErrorMessage = errorMessage;
                AndroidUtilities.runOnUIThread(() -> done.run(null, finalErrorMessage));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "FlexLlmModelsRequest").start();
    }

    private static void setAuthHeaders(HttpURLConnection connection, int apiType, String apiKey) {
        if (apiType == FlexConfig.LLM_API_TYPE_CLAUDE_MESSAGES) {
            connection.setRequestProperty("anthropic-version", CLAUDE_API_VERSION);
            if (!TextUtils.isEmpty(apiKey)) {
                connection.setRequestProperty("x-api-key", apiKey);
            }
            return;
        }
        if (!TextUtils.isEmpty(apiKey)) {
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
    }

    private static String readConnectionText(HttpURLConnection connection, boolean errorStream) throws IOException {
        if (connection == null) {
            return null;
        }
        InputStream stream = errorStream ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return null;
        }
        Reader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(stream, Charsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            int c;
            while ((c = reader.read()) != -1) {
                builder.append((char) c);
            }
            return builder.toString();
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private static String getErrorMessage(HttpURLConnection connection, String responseText, Exception exception) {
        try {
            if (!TextUtils.isEmpty(responseText)) {
                JSONObject object = new JSONObject(responseText);
                JSONObject error = object.optJSONObject("error");
                if (error != null) {
                    String message = error.optString("message", null);
                    if (!TextUtils.isEmpty(message)) {
                        return message;
                    }
                    String type = error.optString("type", null);
                    if (!TextUtils.isEmpty(type)) {
                        return type;
                    }
                }
                String message = object.optString("message", null);
                if (!TextUtils.isEmpty(message)) {
                    return message;
                }
            }
        } catch (Exception ignore) {
        }
        try {
            int responseCode = connection != null ? connection.getResponseCode() : 0;
            if (responseCode >= 400) {
                return LocaleController.getString(R.string.FlexLlmRequestFailed) + " (" + responseCode + ")";
            }
        } catch (Exception ignore) {
        }
        if (exception instanceof JSONException) {
            return LocaleController.getString(R.string.FlexLlmInvalidResponse);
        }
        if (!TextUtils.isEmpty(exception.getMessage())) {
            return exception.getMessage();
        }
        return LocaleController.getString(R.string.FlexLlmRequestFailed);
    }

    private static String getModelsApiUrl(String apiUrl) {
        String value = apiUrl.trim();
        int suffixIndex = value.indexOf('?');
        int fragmentIndex = value.indexOf('#');
        if (suffixIndex < 0 || fragmentIndex >= 0 && fragmentIndex < suffixIndex) {
            suffixIndex = fragmentIndex;
        }
        String suffix = suffixIndex >= 0 ? value.substring(suffixIndex) : "";
        value = suffixIndex >= 0 ? value.substring(0, suffixIndex) : value;
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/chat/completions")) {
            return value.substring(0, value.length() - "/chat/completions".length()) + "/models" + suffix;
        }
        if (value.endsWith("/responses")) {
            return value.substring(0, value.length() - "/responses".length()) + "/models" + suffix;
        }
        if (value.endsWith("/messages")) {
            return value.substring(0, value.length() - "/messages".length()) + "/models" + suffix;
        }
        return value + "/models" + suffix;
    }

    private static String extractChatCompletion(JSONObject response) {
        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() <= 0) {
            return null;
        }
        JSONObject choice = choices.optJSONObject(0);
        if (choice == null) {
            return null;
        }
        String finishReason = choice.optString("finish_reason", "");
        if (!TextUtils.isEmpty(finishReason) && !"stop".equals(finishReason)) {
            throw new IllegalStateException(LocaleController.getString(R.string.FlexLlmResponseIncomplete) + " (" + finishReason + ")");
        }
        return extractMessageContent(choice.optJSONObject("message"));
    }

    private static String extractMessageContent(JSONObject message) {
        if (message == null) {
            return null;
        }
        Object content = message.opt("content");
        if (content instanceof String) {
            return ((String) content).trim();
        }
        if (content instanceof JSONArray) {
            return extractTextContent((JSONArray) content);
        }
        return null;
    }

    private static String extractResponseOutput(JSONArray output) {
        if (output == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < output.length(); ++i) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String text = extractTextContent(item.optJSONArray("content"));
            if (!TextUtils.isEmpty(text)) {
                builder.append(text);
            }
        }
        String result = builder.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private static String extractTextContent(JSONArray contentArray) {
        if (contentArray == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < contentArray.length(); ++i) {
            Object block = contentArray.opt(i);
            if (block instanceof String) {
                builder.append((String) block);
                continue;
            }
            if (!(block instanceof JSONObject)) {
                continue;
            }
            JSONObject object = (JSONObject) block;
            Object text = object.opt("text");
            if (text instanceof String) {
                builder.append((String) text);
            }
        }
        String result = builder.toString().trim();
        return result.isEmpty() ? null : result;
    }
}
