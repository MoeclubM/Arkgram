package org.telegram.messenger;

import android.net.Uri;
import android.text.TextUtils;
import com.google.common.base.Charsets;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Locale;

public class FlexTranslationHelper {

    public static final String[] userAgents = new String[] {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:94.0) Gecko/20100101 Firefox/94.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:95.0) Gecko/20100101 Firefox/95.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.93 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.55 Safari/537.36"
    };
    private static int lastIndexOfSafe(String text, String target, int start, int end) {
        int idx = text.lastIndexOf(target, end - 1);
        return (idx >= start) ? idx : -1;
    }

    public static ArrayList<String> cut(String text, int maxLength) {
        ArrayList<String> result = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        int encodedLength = 0;
        int lastBreak = -1;
        for (int i = 0; i < text.length(); ) {
            final int codePoint = text.codePointAt(i);
            final int next = i + Character.charCount(codePoint);
            String piece = text.substring(i, next);
            int pieceLength = Uri.encode(piece).length();
            if (encodedLength + pieceLength > maxLength && part.length() > 0) {
                if (lastBreak > 0) {
                    result.add(Uri.encode(part.substring(0, lastBreak)));
                    part.delete(0, lastBreak);
                    encodedLength = Uri.encode(part.toString()).length();
                } else {
                    result.add(Uri.encode(part.toString()));
                    part.setLength(0);
                    encodedLength = 0;
                }
                lastBreak = lastIndexOfSafe(part.toString(), "\n", 0, part.length());
                if (lastBreak == -1) {
                    lastBreak = lastIndexOfSafe(part.toString(), " ", 0, part.length());
                }
                if (lastBreak >= 0) {
                    lastBreak += 1;
                }
                continue;
            }
            part.append(piece);
            encodedLength += pieceLength;
            if (codePoint == '\n' || codePoint == ' ') {
                lastBreak = part.length();
            }
            i = next;
        }
        if (part.length() > 0) {
            result.add(Uri.encode(part.toString()));
        }
        return result;
    }

    public static void translate(int provider, String text, String fromLng, String toLng, Utilities.Callback3<String, Boolean, String> done) {
        if (done == null) return;
        if (provider == FlexConfig.TRANSLATION_PROVIDER_TELEGRAM) {
            AndroidUtilities.runOnUIThread(() -> done.run(null, false, LocaleController.getString(R.string.TranslationFailedAlert2)));
            return;
        }
        if (provider == FlexConfig.TRANSLATION_PROVIDER_GOOGLE || provider == FlexConfig.TRANSLATION_PROVIDER_GOOGLE_CN) {
            final String etext = Uri.encode(text);
            if (etext.length() > 5000) {
                ArrayList<String> parts = cut(text, 5000);
                ArrayList<String> results = new ArrayList<>();
                for (int i = 0; i < parts.size(); ++i) {
                    results.add(null);
                }

                final boolean[] fullyDone = new boolean[1];
                for (int i = 0; i < parts.size(); ++i) {
                    final int index = i;
                    alternativeTranslateInternal(provider, parts.get(i), fromLng, toLng, (res, rateLimit, error) -> {
                        if (fullyDone[0]) return;
                        if (res != null) {
                            results.set(index, res);
                            boolean allDone = true;
                            for (int j = 0; j < results.size(); ++j) {
                                if (results.get(j) == null) {
                                    allDone = false;
                                    break;
                                }
                            }
                            if (allDone) {
                                fullyDone[0] = true;
                                done.run(TextUtils.join("", results), false, null);
                            }
                        } else {
                            fullyDone[0] = true;
                            done.run(null, rateLimit, error);
                        }
                    });
                }
                return;
            }
            alternativeTranslateInternal(provider, etext, fromLng, toLng, done);
            return;
        }
        alternativeTranslateInternal(provider, text, fromLng, toLng, done);
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

    private static String getProviderErrorMessage(int provider, HttpURLConnection connection, String responseText) {
        try {
            if (!TextUtils.isEmpty(responseText)) {
                JSONObject object = new JSONObject(responseText);
                if (provider == FlexConfig.TRANSLATION_PROVIDER_DEEPL) {
                    String message = object.optString("message", null);
                    if (!TextUtils.isEmpty(message)) {
                        return message;
                    }
                } else if (provider == FlexConfig.TRANSLATION_PROVIDER_LLM) {
                    JSONObject error = object.optJSONObject("error");
                    if (error != null) {
                        String message = error.optString("message", null);
                        if (!TextUtils.isEmpty(message)) {
                            return message;
                        }
                    }
                    String message = object.optString("message", null);
                    if (!TextUtils.isEmpty(message)) {
                        return message;
                    }
                }
            }
        } catch (Exception ignore) {
        }
        try {
            int responseCode = connection != null ? connection.getResponseCode() : 0;
            if (responseCode >= 400) {
                return LocaleController.getString(R.string.FlexTranslationRequestFailed) + " (" + responseCode + ")";
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private static String getDeepLLanguage(String language, boolean target) {
        if (TextUtils.isEmpty(language)) {
            return null;
        }
        String normalized = language.toLowerCase(Locale.US).replace('_', '-');
        if (TranslateController.UNKNOWN_LANGUAGE.equals(normalized)) {
            return null;
        }
        if ("nb".equals(normalized)) {
            normalized = "no";
        }
        if ("zh-hant".equals(normalized) || "zh-tw".equals(normalized) || "zh-hk".equals(normalized) || "zh-mo".equals(normalized)) {
            return target ? "ZH-HANT" : "ZH";
        }
        if ("zh".equals(normalized) || "zh-hans".equals(normalized) || "zh-cn".equals(normalized) || "zh-sg".equals(normalized)) {
            return target ? "ZH-HANS" : "ZH";
        }
        if ("es-419".equals(normalized)) {
            return target ? "ES-419" : "ES";
        }
        if ("pt-br".equals(normalized)) {
            return target ? "PT-BR" : "PT";
        }
        if ("pt".equals(normalized) || "pt-pt".equals(normalized)) {
            return target ? "PT-PT" : "PT";
        }
        if ("en-gb".equals(normalized)) {
            return target ? "EN-GB" : "EN";
        }
        if ("en".equals(normalized) || "en-us".equals(normalized)) {
            return target ? "EN-US" : "EN";
        }
        if ("no".equals(normalized)) {
            return "NB";
        }
        int divider = normalized.indexOf('-');
        if (divider >= 0) {
            normalized = normalized.substring(0, divider);
        }
        return normalized.toUpperCase(Locale.US);
    }

    private static void alternativeTranslateInternal(int provider, String text, String fromLng, String toLng, Utilities.Callback3<String, Boolean, String> done) {
        if (done == null) return;
        if (provider == FlexConfig.TRANSLATION_PROVIDER_TELEGRAM) {
            AndroidUtilities.runOnUIThread(() -> done.run(null, false, LocaleController.getString(R.string.TranslationFailedAlert2)));
            return;
        }
        final int finalProvider = provider;
        new Thread() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                String responseText = null;
                try {
                    if (finalProvider == FlexConfig.TRANSLATION_PROVIDER_DEEPL) {
                        String apiKey = FlexConfig.getDeepLApiKey();
                        if (TextUtils.isEmpty(apiKey)) {
                            AndroidUtilities.runOnUIThread(() -> done.run(null, false, LocaleController.getString(R.string.FlexTranslationDeepLApiKeyMissing)));
                            return;
                        }
                        String apiUrl = FlexConfig.getDeepLApiUrl();
                        String targetLanguage = getDeepLLanguage(toLng, true);
                        String sourceLanguage = getDeepLLanguage(fromLng, false);
                        connection = (HttpURLConnection) new URI(apiUrl).toURL().openConnection();
                        connection.setConnectTimeout(15000);
                        connection.setReadTimeout(30000);
                        connection.setRequestMethod("POST");
                        connection.setDoOutput(true);
                        connection.setRequestProperty("Authorization", "DeepL-Auth-Key " + apiKey);
                        connection.setRequestProperty("User-Agent", userAgents[(int) Math.round(Math.random() * (userAgents.length - 1))]);
                        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

                        String body = "text=" + Uri.encode(text) + "&target_lang=" + Uri.encode(targetLanguage) + "&preserve_formatting=1";
                        if (!TextUtils.isEmpty(sourceLanguage)) {
                            body += "&source_lang=" + Uri.encode(sourceLanguage);
                        }
                        try (OutputStream outputStream = connection.getOutputStream()) {
                            outputStream.write(body.getBytes(Charsets.UTF_8));
                        }

                        responseText = readConnectionText(connection, false);
                        JSONArray translations = new JSONObject(responseText).optJSONArray("translations");
                        if (translations == null || translations.length() <= 0) {
                            throw new IllegalStateException(LocaleController.getString(R.string.FlexTranslationInvalidResponse));
                        }
                        String result = translations.optJSONObject(0) != null ? translations.optJSONObject(0).optString("text", null) : null;
                        if (TextUtils.isEmpty(result)) {
                            throw new IllegalStateException(LocaleController.getString(R.string.FlexTranslationInvalidResponse));
                        }
                        AndroidUtilities.runOnUIThread(() -> done.run(result, false, null));
                        return;
                    }
                    String sourceLanguage = TextUtils.isEmpty(fromLng) || TranslateController.UNKNOWN_LANGUAGE.equals(fromLng) ? "auto" : fromLng;
                    String uri = "https://";
                    uri += finalProvider == FlexConfig.TRANSLATION_PROVIDER_GOOGLE_CN ? "translate.google.cn" : "translate.googleapis.com";
                    uri += "/translate_a/single?client=gtx&sl=" + Uri.encode(sourceLanguage) + "&tl=" + Uri.encode(toLng) + "&dt=t" + "&ie=UTF-8&oe=UTF-8&otf=1&ssel=0&tsel=0&kc=7&dt=at&dt=bd&dt=ex&dt=ld&dt=md&dt=qca&dt=rw&dt=rm&dt=ss&q=";
                    uri += text;
                    connection = (HttpURLConnection) new URI(uri).toURL().openConnection();
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("User-Agent", userAgents[(int) Math.round(Math.random() * (userAgents.length - 1))]);
                    connection.setRequestProperty("Content-Type", "application/json");

                    responseText = readConnectionText(connection, false);
                    JSONTokener tokener = new JSONTokener(responseText);
                    JSONArray array = new JSONArray(tokener);
                    JSONArray array1 = array.getJSONArray(0);
                    String result = "";
                    for (int i = 0; i < array1.length(); ++i) {
                        String blockText = array1.getJSONArray(i).getString(0);
                        if (blockText != null && !blockText.equals("null"))
                            result += /*(i > 0 ? "\n" : "") +*/ blockText;
                    }
                    if (Uri.decode(text).startsWith("\n") && !result.startsWith("\n"))
                        result = "\n" + result;
                    final String finalResult = Uri.decode(text).endsWith("\n") && !result.endsWith("\n") ? result + "\n" : result;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (done != null)
                            done.run(finalResult, false, null);
                    });
                } catch (Exception e) {
                    try {
                        String errorResponseText = readConnectionText(connection, true);
                        if (!TextUtils.isEmpty(errorResponseText)) {
                            responseText = errorResponseText;
                        }
                    } catch (Exception readError) {
                        FileLog.e(readError);
                    }
                    String errorMessage = getProviderErrorMessage(finalProvider, connection, responseText);
                    if (TextUtils.isEmpty(errorMessage)) {
                        errorMessage = e instanceof JSONException ? LocaleController.getString(R.string.FlexTranslationInvalidResponse) : e.getMessage();
                    }
                    if (TextUtils.isEmpty(errorMessage)) {
                        errorMessage = LocaleController.getString(R.string.FlexTranslationRequestFailed);
                    }
                    FileLog.e(e);
                    try {
                        final boolean rateLimit = connection != null && connection.getResponseCode() == 429;
                        final String finalErrorMessage = errorMessage;
                        AndroidUtilities.runOnUIThread(() -> {
                            done.run(null, rateLimit, finalErrorMessage);
                        });
                    } catch (Exception e2) {
                        final String finalErrorMessage = errorMessage;
                        AndroidUtilities.runOnUIThread(() -> {
                            done.run(null, false, finalErrorMessage);
                        });
                    }
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        }.start();
    }

}
