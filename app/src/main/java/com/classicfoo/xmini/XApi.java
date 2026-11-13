package com.classicfoo.xmini;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small direct client for X API v2. OAuth tokens remain in the encrypted TokenVault. */
final class XApi {
    private static final String API = "https://api.x.com";
    private static final String TOKEN_URL = "https://api.x.com/2/oauth2/token";
    private static final String SCOPES = "tweet.read tweet.write users.read media.write offline.access";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private final String clientId;
    private final String redirectUri;
    private final TokenVault vault;
    private String userId;

    XApi(Context context, String clientId, String redirectUri) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.redirectUri = redirectUri;
        this.vault = new TokenVault(context.getApplicationContext());
    }

    String scopes() {
        return SCOPES;
    }

    boolean isConfigured() {
        return !clientId.isEmpty();
    }

    boolean hasSavedSession() {
        try {
            return vault.load() != null;
        } catch (Exception ignored) {
            vault.clear();
            return false;
        }
    }

    void disconnect() {
        vault.clear();
    }

    JSONObject exchangeAuthorizationCode(String code, String verifier) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("code_verifier", verifier);
        form.put("client_id", clientId);
        JSONObject token = requestForm(TOKEN_URL, form);
        saveTokenResponse(token, null);
        return token;
    }

    JSONObject getMe() throws Exception {
        JSONObject response = get("/2/users/me", params(
                "user.fields", "id,name,username,profile_image_url"));
        JSONObject data = response.optJSONObject("data");
        userId = data == null ? null : emptyToNull(data.optString("id", null));
        if (userId == null) throw new ApiException("X did not return your account ID.");
        return response;
    }

    JSONObject getMyPosts(String paginationToken) throws Exception {
        requireUserId();
        Map<String, String> query = postsQuery("20");
        if (paginationToken != null && !paginationToken.isEmpty()) query.put("pagination_token", paginationToken);
        return get("/2/users/" + Uri.encode(userId) + "/tweets", query);
    }

    JSONObject getMentions(String paginationToken) throws Exception {
        requireUserId();
        Map<String, String> query = postsQuery("20");
        if (paginationToken != null && !paginationToken.isEmpty()) query.put("pagination_token", paginationToken);
        return get("/2/users/" + Uri.encode(userId) + "/mentions", query);
    }

    JSONObject getConversation(String conversationId, String paginationToken) throws Exception {
        Map<String, String> query = postsQuery("25");
        query.put("query", "conversation_id:" + conversationId);
        query.put("sort_order", "recency");
        if (paginationToken != null && !paginationToken.isEmpty()) query.put("pagination_token", paginationToken);
        return get("/2/tweets/search/recent", query);
    }

    JSONObject publish(String text, String replyToId, byte[] imageBytes) throws Exception {
        String access = ensureAccessToken();
        JSONObject payload = new JSONObject();
        payload.put("text", text == null ? "" : text);
        if (replyToId != null && !replyToId.isEmpty()) {
            payload.put("reply", new JSONObject().put("in_reply_to_tweet_id", replyToId));
        }
        if (imageBytes != null) {
            String mediaId = uploadImage(access, imageBytes);
            payload.put("media", new JSONObject().put("media_ids", new JSONArray().put(mediaId)));
        }
        return requestJson("POST", API + "/2/tweets", access, payload);
    }

    private String uploadImage(String accessToken, byte[] imageBytes) throws Exception {
        JSONObject payload = new JSONObject()
                .put("media", Base64.encodeToString(imageBytes, Base64.NO_WRAP))
                .put("media_category", "tweet_image");
        JSONObject result = requestJson("POST", API + "/2/media/upload", accessToken, payload);
        JSONObject data = result.optJSONObject("data");
        String mediaId = data == null ? "" : data.optString("id", "");
        if (mediaId.isEmpty()) throw new ApiException("X did not return an image upload ID.");
        return mediaId;
    }

    private Map<String, String> postsQuery(String maxResults) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("max_results", maxResults);
        query.put("tweet.fields", "author_id,created_at,conversation_id,public_metrics,attachments,in_reply_to_user_id,entities,referenced_tweets");
        query.put("expansions", "author_id,attachments.media_keys");
        query.put("user.fields", "id,name,username,profile_image_url");
        query.put("media.fields", "media_key,type,url,preview_image_url,alt_text");
        return query;
    }

    private JSONObject get(String path, Map<String, String> query) throws Exception {
        Uri.Builder builder = Uri.parse(API + path).buildUpon();
        for (Map.Entry<String, String> entry : query.entrySet()) {
            builder.appendQueryParameter(entry.getKey(), entry.getValue());
        }
        return requestJson("GET", builder.build().toString(), ensureAccessToken(), null);
    }

    private String ensureAccessToken() throws Exception {
        TokenVault.Bundle tokens = vault.load();
        if (tokens == null) throw new ApiException("Connect your X account to continue.");
        if (tokens.expiresAtMillis > System.currentTimeMillis() + 60_000L) return tokens.accessToken;
        if (tokens.refreshToken == null || tokens.refreshToken.isEmpty()) {
            vault.clear();
            throw new ApiException("Your X session expired. Connect again to continue.");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", tokens.refreshToken);
        form.put("client_id", clientId);
        JSONObject refreshed = requestForm(TOKEN_URL, form);
        saveTokenResponse(refreshed, tokens.refreshToken);
        TokenVault.Bundle updated = vault.load();
        if (updated == null) throw new ApiException("X did not return a valid session.");
        return updated.accessToken;
    }

    private void requireUserId() throws ApiException {
        if (userId == null || userId.isEmpty()) {
            throw new ApiException("Your account is still loading. Refresh and try again.");
        }
    }

    private void saveTokenResponse(JSONObject response, String previousRefreshToken) throws Exception {
        String access = response.optString("access_token", "");
        if (access.isEmpty()) throw new ApiException("X did not return an access token.");
        String refresh = response.optString("refresh_token", previousRefreshToken);
        long expiresIn = response.optLong("expires_in", 7200L);
        vault.save(access, refresh, System.currentTimeMillis() + Math.max(60L, expiresIn) * 1000L);
    }

    private JSONObject requestForm(String url, Map<String, String> fields) throws Exception {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            body.append('=');
            body.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream stream = connection.getOutputStream()) {
                stream.write(bytes);
            }
            return readResponse(connection);
        } finally {
            connection.disconnect();
        }
    }

    private JSONObject requestJson(String method, String url, String accessToken, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setRequestProperty("Accept", "application/json");
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream stream = connection.getOutputStream()) {
                    stream.write(bytes);
                }
            }
            return readResponse(connection);
        } finally {
            connection.disconnect();
        }
    }

    private JSONObject readResponse(HttpURLConnection connection) throws Exception {
        int status = connection.getResponseCode();
        InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response = input == null ? "{}" : readUtf8(input);
        JSONObject json;
        try {
            json = new JSONObject(response);
        } catch (JSONException error) {
            if (status >= 400) throw new ApiException("X API returned HTTP " + status + ".");
            throw new ApiException("X returned a response the app could not read.");
        }
        if (status >= 400) throw new ApiException(explainError(status, json));
        return json;
    }

    private static String explainError(int status, JSONObject json) {
        String detail = json.optString("detail", "").trim();
        if (detail.isEmpty()) detail = json.optString("message", "").trim();
        if (detail.isEmpty()) {
            JSONArray errors = json.optJSONArray("errors");
            if (errors != null && errors.length() > 0) {
                JSONObject first = errors.optJSONObject(0);
                if (first != null) {
                    detail = first.optString("detail", first.optString("message", first.optString("title", ""))).trim();
                }
            }
        }
        if (detail.isEmpty()) detail = "Request failed (HTTP " + status + "). Check X API access and permissions.";
        return detail;
    }

    private static String readUtf8(InputStream input) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static Map<String, String> params(String key, String value) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    static String nextToken(JSONObject response) {
        JSONObject meta = response.optJSONObject("meta");
        return meta == null ? null : emptyToNull(meta.optString("next_token", null));
    }

    static JSONArray data(JSONObject response) {
        JSONArray data = response.optJSONArray("data");
        return data == null ? new JSONArray() : data;
    }

    static String emptyToNull(String value) {
        return value == null || value.isEmpty() || "null".equals(value) ? null : value;
    }

    static final class ApiException extends Exception {
        ApiException(String message) {
            super(message);
        }
    }
}
