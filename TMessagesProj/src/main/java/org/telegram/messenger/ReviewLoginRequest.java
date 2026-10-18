package org.telegram.messenger;

import android.util.Base64;

import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Exchanges a reviewer access code for a real Telegram device authorization. */
public class ReviewLoginRequest {
    private final ConnectionsManager connections;
    private final int guid;
    private final Utilities.Callback2<TLRPC.TL_auth_authorization, TLRPC.TL_error> callback;
    private String accessCode;
    private int requestId;
    private volatile boolean cancelled;
    private volatile HttpURLConnection connection;

    public ReviewLoginRequest(int account, int guid, String accessCode,
            Utilities.Callback2<TLRPC.TL_auth_authorization, TLRPC.TL_error> callback) {
        connections = ConnectionsManager.getInstance(account);
        this.guid = guid;
        this.accessCode = accessCode;
        this.callback = callback;
    }

    public void start() {
        request(exportToken(), true);
    }

    private TLRPC.TL_auth_exportLoginToken exportToken() {
        TLRPC.TL_auth_exportLoginToken request = new TLRPC.TL_auth_exportLoginToken();
        request.api_id = BuildVars.APP_ID;
        request.api_hash = BuildVars.APP_HASH;
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).isClientActivated()) {
                request.except_ids.add(UserConfig.getInstance(account).getClientUserId());
            }
        }
        return request;
    }

    private void request(TLObject request, boolean approveToken) {
        requestId = connections.sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (cancelled) return;
            if (error != null) {
                finish(null, error);
            } else if (response instanceof TLRPC.TL_auth_loginTokenSuccess) {
                finish((TLRPC.TL_auth_authorization) ((TLRPC.TL_auth_loginTokenSuccess) response).authorization, null);
            } else if (response instanceof TLRPC.TL_auth_loginTokenMigrateTo) {
                TLRPC.TL_auth_loginTokenMigrateTo migration = (TLRPC.TL_auth_loginTokenMigrateTo) response;
                connections.setDefaultDatacenterId(migration.dc_id);
                TLRPC.TL_auth_importLoginToken imported = new TLRPC.TL_auth_importLoginToken();
                imported.token = migration.token;
                request(imported, false);
            } else if (response instanceof TLRPC.TL_auth_loginToken && approveToken) {
                approve(((TLRPC.TL_auth_loginToken) response).token);
            } else {
                fail("Telegram did not complete the approved login: " + response.getClass().getSimpleName());
            }
        }), ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
        connections.bindRequestToGuid(requestId, guid);
    }

    private void approve(byte[] token) {
        new Thread(() -> {
            try {
                connection = (HttpURLConnection) new URL(ApplicationLoader.applicationContext.getString(
                        R.string.ReviewLoginServiceUrl)).openConnection();
                if (cancelled) return;
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Authorization", "Bearer " + accessCode);
                connection.setDoOutput(true);
                JSONObject body = new JSONObject();
                body.put("token", Base64.encodeToString(token, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP));
                try (java.io.OutputStream output = connection.getOutputStream()) {
                    output.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int status = connection.getResponseCode();
                InputStream stream = status == 200 ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }
                if (status != 200) throw new IllegalStateException("HTTP " + status + ": " + response);
                if (!new JSONObject(response.toString()).getBoolean("authorized")) {
                    throw new IllegalStateException(response.toString());
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (!cancelled) request(exportToken(), false);
                });
            } catch (Exception error) {
                if (!cancelled) {
                    FileLog.e(error);
                    AndroidUtilities.runOnUIThread(() -> fail(error.toString()));
                }
            } finally {
                accessCode = null;
                if (connection != null) connection.disconnect();
            }
        }, "ArkGramReviewLogin").start();
    }

    private void fail(String message) {
        TLRPC.TL_error error = new TLRPC.TL_error();
        error.code = -1;
        error.text = message;
        finish(null, error);
    }

    private void finish(TLRPC.TL_auth_authorization authorization, TLRPC.TL_error error) {
        accessCode = null;
        if (!cancelled) callback.run(authorization, error);
    }

    public void cancel() {
        cancelled = true;
        accessCode = null;
        if (requestId != 0) connections.cancelRequest(requestId, true);
        if (connection != null) connection.disconnect();
    }
}
