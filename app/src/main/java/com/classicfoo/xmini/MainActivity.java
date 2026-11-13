package com.classicfoo.xmini;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PAGE_POSTS = 0;
    private static final int PAGE_COMPOSE = 1;
    private static final int PAGE_NOTIFICATIONS = 2;
    private static final int PICK_IMAGE_REQUEST = 41;
    private static final String PREFS = "xmini_settings";
    private static final String OAUTH_PREFS = "oauth_flow";
    private static final String API_ACCESS = "https://x.com/i/oauth2/authorize";
    private static final String API_CONSOLE = "https://developer.x.com/";
    private static final int INK = Color.rgb(25, 25, 25);
    private static final int MUTED = Color.rgb(111, 113, 116);
    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int PAPER = Color.rgb(247, 247, 245);
    private static final int LINE = Color.rgb(232, 232, 229);
    private static final int IMAGE_LIMIT_BYTES = 5 * 1024 * 1024;

    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private XApi api;
    private LinearLayout root;
    private int selectedPage = PAGE_POSTS;
    private boolean inConversation;
    private boolean accountLoading;
    private boolean postsLoading;
    private boolean mentionsLoading;
    private boolean conversationLoading;
    private boolean publishing;
    private TextView composeCount;
    private boolean postsLoaded;
    private boolean mentionsLoaded;
    private boolean sessionReady;
    private String accountId;
    private String accountName;
    private String accountUsername;
    private String accountImageUrl;
    private String accountError;
    private String postsError;
    private String mentionsError;
    private String conversationError;
    private String composeError;
    private String nextPostsToken;
    private String nextMentionsToken;
    private String nextConversationToken;
    private String conversationId;
    private String draft = "";
    private String replyToId;
    private String replyToHandle;
    private Uri selectedImage;
    private String selectedImageMime;
    private final ArrayList<JSONObject> posts = new ArrayList<>();
    private final ArrayList<JSONObject> mentions = new ArrayList<>();
    private final ArrayList<JSONObject> conversationPosts = new ArrayList<>();
    private final Map<String, JSONObject> postsUsers = new HashMap<>();
    private final Map<String, JSONObject> postsMedia = new HashMap<>();
    private final Map<String, JSONObject> mentionsUsers = new HashMap<>();
    private final Map<String, JSONObject> mentionsMedia = new HashMap<>();
    private final Map<String, JSONObject> conversationUsers = new HashMap<>();
    private final Map<String, JSONObject> conversationMedia = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        configureSystemBars();
        if (savedInstanceState != null) {
            selectedPage = savedInstanceState.getInt("page", PAGE_POSTS);
            draft = savedInstanceState.getString("draft", "");
            replyToId = savedInstanceState.getString("reply_id");
            replyToHandle = savedInstanceState.getString("reply_handle");
            inConversation = savedInstanceState.getBoolean("in_conversation");
            conversationId = savedInstanceState.getString("conversation_id");
            String image = savedInstanceState.getString("image_uri");
            if (image != null) selectedImage = Uri.parse(image);
            selectedImageMime = savedInstanceState.getString("image_mime");
        }
        refreshApi();
        renderRoot();
        handleOAuthIntent(getIntent());
        if (!accountLoading && api.hasSavedSession()) loadAccount();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleOAuthIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("page", selectedPage);
        state.putString("draft", draft);
        state.putString("reply_id", replyToId);
        state.putString("reply_handle", replyToHandle);
        state.putBoolean("in_conversation", inConversation);
        state.putString("conversation_id", conversationId);
        state.putString("image_uri", selectedImage == null ? null : selectedImage.toString());
        state.putString("image_mime", selectedImageMime);
        super.onSaveInstanceState(state);
    }

    @Override
    public void onBackPressed() {
        if (inConversation) {
            inConversation = false;
            renderRoot();
        } else if (selectedPage != PAGE_POSTS) {
            selectedPage = PAGE_POSTS;
            renderRoot();
            loadPageIfNeeded();
        } else {
            super.onBackPressed();
        }
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(PAPER);
        window.setNavigationBarColor(Color.WHITE);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (Build.VERSION.SDK_INT >= 35) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
    }

    private void refreshApi() {
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString("client_id", "");
        if (saved.isEmpty()) saved = getString(R.string.x_client_id);
        api = new XApi(this, saved, getString(R.string.oauth_redirect_uri));
    }

    private void renderRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAPER);
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        setContentView(root);

        LinearLayout body = vertical();
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        renderHeader(body);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout page = vertical();
        page.setPadding(dp(20), dp(10), dp(20), dp(28));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        if (inConversation) {
            renderConversationPage(page);
        } else if (selectedPage == PAGE_POSTS) {
            renderPostsPage(page);
        } else if (selectedPage == PAGE_COMPOSE) {
            renderComposePage(page);
        } else {
            renderNotificationsPage(page);
        }
        root.addView(renderBottomNavigation(), new LinearLayout.LayoutParams(-1, dp(70)));
        root.requestApplyInsets();
    }

    private void renderHeader(LinearLayout parent) {
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(12), dp(16), dp(12));

        if (inConversation) {
            TextView back = text("‹", 32, INK, false);
            back.setGravity(Gravity.CENTER);
            back.setContentDescription("Back to list");
            back.setOnClickListener(view -> {
                inConversation = false;
                renderRoot();
            });
            header.addView(back, new LinearLayout.LayoutParams(dp(36), dp(40)));
        }

        String title = inConversation ? "Conversation" : pageTitle();
        TextView titleView = text(title, 24, INK, true);
        header.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        if (sessionReady && !inConversation && selectedPage != PAGE_COMPOSE) {
            TextView refresh = text("Refresh", 14, INK, true);
            refresh.setGravity(Gravity.CENTER);
            refresh.setPadding(dp(10), dp(8), dp(10), dp(8));
            refresh.setBackground(background(Color.WHITE, LINE, 12));
            refresh.setOnClickListener(view -> refreshCurrentPage());
            header.addView(refresh, new LinearLayout.LayoutParams(-2, dp(38)));
        } else if (sessionReady && inConversation) {
            TextView refresh = text("Refresh", 14, INK, true);
            refresh.setGravity(Gravity.CENTER);
            refresh.setPadding(dp(10), dp(8), dp(10), dp(8));
            refresh.setBackground(background(Color.WHITE, LINE, 12));
            refresh.setOnClickListener(view -> loadConversation(false));
            header.addView(refresh, new LinearLayout.LayoutParams(-2, dp(38)));
        }
        parent.addView(header, new LinearLayout.LayoutParams(-1, -2));

        if (sessionReady) {
            LinearLayout accountLine = horizontal();
            accountLine.setGravity(Gravity.CENTER_VERTICAL);
            accountLine.setPadding(dp(22), 0, dp(18), dp(10));
            TextView handle = text("@" + safe(accountUsername), 13, MUTED, false);
            accountLine.addView(handle, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView disconnect = text("Disconnect", 13, MUTED, false);
            disconnect.setGravity(Gravity.CENTER);
            disconnect.setOnClickListener(view -> confirmDisconnect());
            accountLine.addView(disconnect, new LinearLayout.LayoutParams(-2, dp(28)));
            parent.addView(accountLine, new LinearLayout.LayoutParams(-1, -2));
        }

        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        parent.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private String pageTitle() {
        if (selectedPage == PAGE_COMPOSE) return "Compose";
        if (selectedPage == PAGE_NOTIFICATIONS) return "Notifications";
        return "My Posts";
    }

    private View renderBottomNavigation() {
        LinearLayout nav = horizontal();
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Color.WHITE);
        View topBorder = new View(this);
        topBorder.setBackgroundColor(LINE);
        LinearLayout wrapper = vertical();
        wrapper.addView(topBorder, new LinearLayout.LayoutParams(-1, dp(1)));
        wrapper.addView(nav, new LinearLayout.LayoutParams(-1, 0, 1f));
        String[] labels = {"My Posts", "Compose", "Notifications"};
        for (int i = 0; i < labels.length; i++) {
            final int page = i;
            LinearLayout item = vertical();
            item.setGravity(Gravity.CENTER);
            item.setClickable(true);
            item.setFocusable(true);
            item.setBackgroundResource(android.R.drawable.list_selector_background);
            NavGlyph glyph = new NavGlyph(this, i, selectedPage == i);
            item.addView(glyph, new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView label = text(labels[i], 11, selectedPage == i ? INK : MUTED, selectedPage == i);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
            labelParams.topMargin = dp(3);
            item.addView(label, labelParams);
            item.setOnClickListener(view -> {
                if (inConversation) inConversation = false;
                selectedPage = page;
                renderRoot();
                loadPageIfNeeded();
            });
            nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        return wrapper;
    }

    private void renderPostsPage(LinearLayout page) {
        if (!sessionReady) {
            renderConnectionGate(page);
            return;
        }
        TextView hint = text("Your posts, newest first. Older posts load only when you ask.", 14, MUTED, false);
        hint.setPadding(0, dp(4), 0, dp(14));
        page.addView(hint);
        if (postsError != null) page.addView(errorCard(postsError, () -> loadPosts(false)));
        if (postsLoading && posts.isEmpty()) page.addView(loadingCard("Loading your posts…"));
        if (!postsLoading && posts.isEmpty() && postsError == null) {
            page.addView(emptyCard("No posts returned", "X has no posts to show for this account."));
        }
        for (JSONObject post : posts) {
            page.addView(renderPostCard(post, postsUsers, postsMedia, false));
        }
        if (nextPostsToken != null) {
            page.addView(actionButton(postsLoading ? "Loading…" : "Load earlier posts", () -> loadPosts(true), postsLoading));
        }
    }

    private void renderNotificationsPage(LinearLayout page) {
        if (!sessionReady) {
            renderConnectionGate(page);
            return;
        }
        TextView hint = text("Mentions and replies that mention you. Refresh when you want to check.", 14, MUTED, false);
        hint.setPadding(0, dp(4), 0, dp(6));
        page.addView(hint);
        TextView limitation = text("X’s API does not provide a general notifications feed. Likes and follows are not shown.", 12, MUTED, false);
        limitation.setPadding(0, 0, 0, dp(14));
        page.addView(limitation);
        if (mentionsError != null) page.addView(errorCard(mentionsError, () -> loadMentions(false)));
        if (mentionsLoading && mentions.isEmpty()) page.addView(loadingCard("Checking mentions…"));
        if (!mentionsLoading && mentions.isEmpty() && mentionsError == null) {
            page.addView(emptyCard("Nothing here yet", "New mentions and replies will appear when X returns them."));
        }
        for (JSONObject post : mentions) {
            page.addView(renderMentionCard(post));
        }
        if (nextMentionsToken != null) {
            page.addView(actionButton(mentionsLoading ? "Loading…" : "Load older mentions", () -> loadMentions(true), mentionsLoading));
        }
    }

    private void renderComposePage(LinearLayout page) {
        if (!sessionReady) {
            renderConnectionGate(page);
            return;
        }
        if (replyToId != null) {
            LinearLayout replyContext = card();
            replyContext.setPadding(dp(14), dp(12), dp(14), dp(12));
            replyContext.addView(text("Replying to @" + safe(replyToHandle), 14, MUTED, true));
            if (draft.length() == 0) {
                TextView policy = text("X may reject replies when the other author has not mentioned or quoted your account.", 12, MUTED, false);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
                p.topMargin = dp(5);
                replyContext.addView(policy, p);
            }
            page.addView(replyContext, cardParams(0));
        }

        EditText editor = new EditText(this);
        editor.setText(draft);
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        editor.setTextColor(INK);
        editor.setHintTextColor(Color.rgb(155, 157, 160));
        editor.setHint("Write something worth sharing…");
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setMinLines(7);
        editor.setMaxLines(12);
        editor.setPadding(dp(16), dp(16), dp(16), dp(16));
        editor.setBackground(background(Color.WHITE, LINE, 16));
        editor.setSelection(editor.getText().length());
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                draft = s.toString();
                if (composeCount != null) composeCount.setText(approximateCount(draft) + " / 280");
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        page.addView(editor, new LinearLayout.LayoutParams(-1, -2));

        if (selectedImage != null) page.addView(renderSelectedImage());

        LinearLayout tools = horizontal();
        tools.setGravity(Gravity.CENTER_VERTICAL);
        TextView addImage = text(selectedImage == null ? "＋  Image" : "＋  Change image", 14, BLUE, true);
        addImage.setGravity(Gravity.CENTER);
        addImage.setPadding(dp(14), 0, dp(14), 0);
        addImage.setBackground(background(Color.WHITE, LINE, 12));
        addImage.setOnClickListener(view -> chooseImage());
        tools.addView(addImage, new LinearLayout.LayoutParams(-2, dp(42)));
        composeCount = text(approximateCount(draft) + " / 280", 13, MUTED, false);
        composeCount.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        tools.addView(composeCount, new LinearLayout.LayoutParams(0, dp(42), 1f));
        LinearLayout.LayoutParams toolsParams = new LinearLayout.LayoutParams(-1, -2);
        toolsParams.topMargin = dp(12);
        page.addView(tools, toolsParams);

        TextView linkHint = text("Links stay in your post; X generates previews when available.", 12, MUTED, false);
        linkHint.setPadding(0, dp(10), 0, dp(4));
        page.addView(linkHint);
        if (composeError != null) page.addView(errorCard(composeError, null));
        TextView publish = actionButton(publishing ? "Publishing…" : (replyToId == null ? "Post" : "Reply"), this::publishDraft, publishing);
        LinearLayout.LayoutParams publishParams = new LinearLayout.LayoutParams(-1, dp(50));
        publishParams.topMargin = dp(12);
        page.addView(publish, publishParams);
    }

    private View renderPostCard(JSONObject post, Map<String, JSONObject> users,
                                Map<String, JSONObject> media, boolean conversation) {
        LinearLayout outer = card();
        outer.setPadding(dp(15), dp(14), dp(15), dp(12));

        String username = authorUsername(post, users);
        String authorName = authorName(post, users);
        LinearLayout heading = horizontal();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(avatar(username, 38));
        LinearLayout authorBlock = vertical();
        authorBlock.setPadding(dp(10), 0, 0, 0);
        LinearLayout.LayoutParams authorParams = new LinearLayout.LayoutParams(0, -2, 1f);
        TextView author = text("@" + safe(username), 14, INK, true);
        authorBlock.addView(author);
        TextView detail = text((authorName.isEmpty() ? "" : authorName + "  ·  ") + relativeTime(post.optString("created_at", "")), 12, MUTED, false);
        authorBlock.addView(detail);
        heading.addView(authorBlock, authorParams);
        outer.addView(heading);

        TextView body = text(post.optString("text", ""), 16, INK, false);
        body.setAutoLinkMask(Linkify.WEB_URLS);
        body.setLinkTextColor(BLUE);
        body.setMovementMethod(LinkMovementMethod.getInstance());
        body.setTextIsSelectable(true);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(12);
        outer.addView(body, bodyParams);

        View image = renderPostImage(post, media);
        if (image != null) {
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, dp(205));
            imageParams.topMargin = dp(12);
            outer.addView(image, imageParams);
        }

        LinearLayout actions = horizontal();
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView replies = text("↩  " + replyCount(post) + (replyCount(post) == 1 ? " reply" : " replies"), 13, MUTED, false);
        actions.addView(replies, new LinearLayout.LayoutParams(0, dp(36), 1f));
        TextView open = text(conversation ? "Open on X ↗" : "Open conversation  →", 13, BLUE, true);
        open.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        open.setOnClickListener(view -> {
            if (conversation) openExternal("https://x.com/i/status/" + post.optString("id", ""));
            else openConversation(post);
        });
        actions.addView(open, new LinearLayout.LayoutParams(-2, dp(36)));
        if (conversation && canReplyTo(post)) {
            TextView reply = text("Reply", 13, INK, true);
            reply.setGravity(Gravity.CENTER);
            reply.setPadding(dp(12), 0, dp(12), 0);
            reply.setOnClickListener(view -> startReply(post));
            actions.addView(reply, new LinearLayout.LayoutParams(-2, dp(36)));
        }
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
        actionParams.topMargin = dp(6);
        outer.addView(actions, actionParams);
        return withCardSpacing(outer);
    }

    private View renderMentionCard(JSONObject post) {
        LinearLayout outer = card();
        outer.setPadding(dp(15), dp(14), dp(15), dp(12));
        String username = authorUsername(post, mentionsUsers);
        String authorName = authorName(post, mentionsUsers);
        boolean reply = accountId != null && accountId.equals(post.optString("in_reply_to_user_id", ""));

        LinearLayout heading = horizontal();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(avatar(username, 38));
        LinearLayout authorBlock = vertical();
        authorBlock.setPadding(dp(10), 0, 0, 0);
        authorBlock.addView(text("@" + safe(username) + (reply ? " replied to you" : " mentioned you"), 14, INK, true));
        authorBlock.addView(text((authorName.isEmpty() ? "" : authorName + "  ·  ") + relativeTime(post.optString("created_at", "")), 12, MUTED, false));
        heading.addView(authorBlock, new LinearLayout.LayoutParams(0, -2, 1f));
        outer.addView(heading);

        TextView body = text(post.optString("text", ""), 16, INK, false);
        body.setAutoLinkMask(Linkify.WEB_URLS);
        body.setLinkTextColor(BLUE);
        body.setMovementMethod(LinkMovementMethod.getInstance());
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(12);
        outer.addView(body, bodyParams);
        View image = renderPostImage(post, mentionsMedia);
        if (image != null) {
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, dp(190));
            imageParams.topMargin = dp(12);
            outer.addView(image, imageParams);
        }

        LinearLayout actions = horizontal();
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView open = text("Open conversation  →", 13, BLUE, true);
        open.setOnClickListener(view -> openConversation(post));
        actions.addView(open, new LinearLayout.LayoutParams(0, dp(36), 1f));
        if (canReplyTo(post)) {
            TextView answer = text("Reply", 13, INK, true);
            answer.setGravity(Gravity.CENTER);
            answer.setPadding(dp(12), 0, dp(12), 0);
            answer.setOnClickListener(view -> startReply(post));
            actions.addView(answer, new LinearLayout.LayoutParams(-2, dp(36)));
        }
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
        actionParams.topMargin = dp(6);
        outer.addView(actions, actionParams);
        return withCardSpacing(outer);
    }

    private View renderPostImage(JSONObject post, Map<String, JSONObject> media) {
        JSONObject attachments = post.optJSONObject("attachments");
        JSONArray keys = attachments == null ? null : attachments.optJSONArray("media_keys");
        if (keys == null || keys.length() == 0) return null;
        JSONObject image = media.get(keys.optString(0));
        if (image == null) return null;
        String url = XApi.emptyToNull(image.optString("url", null));
        if (url == null) url = XApi.emptyToNull(image.optString("preview_image_url", null));
        if (url == null) return null;
        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setBackground(background(Color.rgb(239, 240, 241), LINE, 12));
        view.setClipToOutline(true);
        view.setContentDescription(image.optString("alt_text", "Post image"));
        loadRemoteImage(url, view);
        return view;
    }

    private View renderSelectedImage() {
        LinearLayout preview = horizontal();
        preview.setGravity(Gravity.CENTER_VERTICAL);
        preview.setPadding(dp(10), dp(10), dp(10), dp(10));
        preview.setBackground(background(Color.WHITE, LINE, 14));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(background(Color.rgb(239, 240, 241), LINE, 10));
        preview.addView(image, new LinearLayout.LayoutParams(dp(88), dp(72)));
        TextView name = text("Image attached", 14, INK, true);
        name.setPadding(dp(12), 0, dp(8), 0);
        preview.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView remove = text("Remove", 13, BLUE, true);
        remove.setGravity(Gravity.CENTER);
        remove.setOnClickListener(view -> {
            selectedImage = null;
            selectedImageMime = null;
            renderRoot();
        });
        preview.addView(remove, new LinearLayout.LayoutParams(-2, dp(42)));
        loadLocalImage(selectedImage, image);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        return wrapInLayout(preview, params);
    }

    private View wrapInLayout(View view, LinearLayout.LayoutParams params) {
        LinearLayout wrapper = vertical();
        wrapper.addView(view, params);
        return wrapper;
    }

    private boolean canReplyTo(JSONObject post) {
        if (accountId == null) return false;
        String authorId = post.optString("author_id", "");
        if (!accountId.equals(authorId) && accountId.equals(post.optString("in_reply_to_user_id", ""))) return true;
        JSONObject entities = post.optJSONObject("entities");
        JSONArray entityMentions = entities == null ? null : entities.optJSONArray("mentions");
        if (entityMentions != null) {
            for (int i = 0; i < entityMentions.length(); i++) {
                JSONObject mention = entityMentions.optJSONObject(i);
                if (mention != null && accountId.equals(mention.optString("id", ""))) return true;
            }
        }
        JSONArray references = post.optJSONArray("referenced_tweets");
        if (references != null) {
            for (int i = 0; i < references.length(); i++) {
                JSONObject reference = references.optJSONObject(i);
                if (reference == null || !"quoted".equals(reference.optString("type"))) continue;
                String referencedId = reference.optString("id", "");
                if (referencedId.equals(conversationId)) return true;
                for (JSONObject ownPost : posts) {
                    if (referencedId.equals(ownPost.optString("id", ""))) return true;
                }
            }
        }
        return false;
    }

    private int replyCount(JSONObject post) {
        JSONObject metrics = post.optJSONObject("public_metrics");
        return metrics == null ? 0 : Math.max(0, metrics.optInt("reply_count", 0));
    }

    private String authorUsername(JSONObject post, Map<String, JSONObject> users) {
        String authorId = post.optString("author_id", "");
        if (authorId.equals(accountId) && accountUsername != null) return accountUsername;
        JSONObject user = users.get(authorId);
        if (user == null) user = mentionsUsers.get(authorId);
        if (user == null) user = postsUsers.get(authorId);
        if (user == null) user = conversationUsers.get(authorId);
        if (user != null && !user.optString("username", "").isEmpty()) return user.optString("username");
        return post.optString("username", "account");
    }

    private String authorName(JSONObject post, Map<String, JSONObject> users) {
        String authorId = post.optString("author_id", "");
        if (authorId.equals(accountId)) return accountName == null ? "" : accountName;
        JSONObject user = users.get(authorId);
        if (user == null) user = mentionsUsers.get(authorId);
        if (user == null) user = postsUsers.get(authorId);
        if (user == null) user = conversationUsers.get(authorId);
        return user == null ? "" : user.optString("name", "");
    }

    private TextView avatar(String identity, int sizeDp) {
        String value = identity == null ? "?" : identity;
        TextView avatar = text(value.isEmpty() ? "?" : value.substring(0, 1).toUpperCase(Locale.getDefault()), 15, Color.WHITE, true);
        avatar.setGravity(Gravity.CENTER);
        int color = avatarColor(value);
        avatar.setBackground(background(color, color, sizeDp));
        return (TextView) sizeView(avatar, sizeDp, sizeDp);
    }

    private int avatarColor(String value) {
        int[] colors = {0xFF5978A8, 0xFF5A8E83, 0xFF9A7659, 0xFF806A9B, 0xFF68869B};
        return colors[Math.abs(value.hashCode() % colors.length)];
    }

    private void loadRemoteImage(String url, ImageView target) {
        io.execute(() -> {
            try {
                URL imageUrl = new URL(url);
                if (!"https".equalsIgnoreCase(imageUrl.getProtocol())) return;
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) imageUrl.openConnection();
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(15_000);
                Bitmap bitmap;
                try (InputStream input = connection.getInputStream()) {
                    bitmap = BitmapFactory.decodeStream(input);
                } finally {
                    connection.disconnect();
                }
                if (bitmap != null) main.post(() -> {
                    if (!isFinishing()) target.setImageBitmap(bitmap);
                });
            } catch (Exception ignored) { }
        });
    }

    private void loadLocalImage(Uri uri, ImageView target) {
        if (uri == null) return;
        io.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                Bitmap bitmap = input == null ? null : BitmapFactory.decodeStream(input);
                if (bitmap != null) main.post(() -> {
                    if (!isFinishing()) target.setImageBitmap(bitmap);
                });
            } catch (Exception ignored) { }
        });
    }

    private void mergeIncludes(JSONObject response, Map<String, JSONObject> users, Map<String, JSONObject> media) {
        JSONObject includes = response.optJSONObject("includes");
        if (includes == null) return;
        JSONArray includedUsers = includes.optJSONArray("users");
        if (includedUsers != null) {
            for (int i = 0; i < includedUsers.length(); i++) {
                JSONObject user = includedUsers.optJSONObject(i);
                if (user != null) users.put(user.optString("id", ""), user);
            }
        }
        JSONArray includedMedia = includes.optJSONArray("media");
        if (includedMedia != null) {
            for (int i = 0; i < includedMedia.length(); i++) {
                JSONObject item = includedMedia.optJSONObject(i);
                if (item != null) media.put(item.optString("media_key", ""), item);
            }
        }
    }

    private LinearLayout card() {
        LinearLayout result = vertical();
        result.setBackground(background(Color.WHITE, LINE, 16));
        return result;
    }

    private LinearLayout.LayoutParams cardParams(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(topMarginDp);
        params.bottomMargin = dp(10);
        return params;
    }

    private View withCardSpacing(View view) {
        view.setLayoutParams(cardParams(0));
        return view;
    }

    private View emptyCard(String title, String message) {
        LinearLayout empty = card();
        empty.setPadding(dp(18), dp(18), dp(18), dp(18));
        empty.addView(text(title, 16, INK, true));
        TextView detail = text(message, 14, MUTED, false);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(6);
        empty.addView(detail, detailParams);
        empty.setLayoutParams(cardParams(4));
        return empty;
    }

    private View loadingCard(String message) {
        LinearLayout loading = card();
        loading.setPadding(dp(18), dp(20), dp(18), dp(20));
        TextView label = text(message, 14, MUTED, false);
        label.setGravity(Gravity.CENTER);
        loading.addView(label);
        loading.setLayoutParams(cardParams(4));
        return loading;
    }

    private View errorCard(String message, Runnable retry) {
        LinearLayout error = card();
        error.setPadding(dp(16), dp(15), dp(16), dp(14));
        error.addView(text("Could not load from X", 15, Color.rgb(155, 44, 44), true));
        TextView detail = text(message, 13, MUTED, false);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(5);
        error.addView(detail, detailParams);
        if (retry != null) {
            TextView button = text("Try again", 13, BLUE, true);
            button.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            button.setOnClickListener(view -> retry.run());
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(34));
            buttonParams.topMargin = dp(4);
            error.addView(button, buttonParams);
        }
        error.setLayoutParams(cardParams(4));
        return error;
    }

    private TextView actionButton(String label, Runnable action, boolean disabled) {
        TextView button = text(label, 15, Color.WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(background(disabled ? 0xFF8B8D90 : INK, disabled ? 0xFF8B8D90 : INK, 14));
        button.setEnabled(!disabled);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(true);
        return view;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private View sizeView(View view, int widthDp, int heightDp) {
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)));
        return view;
    }

    private GradientDrawable background(int fill, int stroke, int cornerRadiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(cornerRadiusDp));
        if (stroke != fill) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private void addSpace(LinearLayout parent, int heightPx) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(-1, heightPx));
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception error) {
            toast("No browser is available to open this link.");
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "your account" : value;
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty() ? "Unexpected error. Please try again." : message.trim();
    }

    private String relativeTime(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) return "";
        try {
            long created = java.time.Instant.parse(timestamp).toEpochMilli();
            long age = Math.max(0, System.currentTimeMillis() - created);
            long minutes = age / 60_000L;
            if (minutes < 1) return "now";
            if (minutes < 60) return minutes + "m";
            long hours = minutes / 60;
            if (hours < 24) return hours + "h";
            long days = hours / 24;
            if (days < 7) return days + "d";
            return new java.text.SimpleDateFormat("MMM d", Locale.getDefault()).format(new java.util.Date(created));
        } catch (Exception ignored) {
            return "";
        }
    }

    private static int approximateCount(String value) {
        if (value == null || value.isEmpty()) return 0;
        java.util.regex.Matcher urls = java.util.regex.Pattern.compile("https?://\\S+", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value);
        int count = 0;
        int cursor = 0;
        while (urls.find()) {
            count += value.substring(cursor, urls.start()).codePointCount(0, urls.start() - cursor);
            count += 23;
            cursor = urls.end();
        }
        count += value.substring(cursor).codePointCount(0, value.length() - cursor);
        return count;
    }

    private static String randomBase64Url(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static boolean constantTimeEquals(String expected, String received) {
        if (expected == null || received == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), received.getBytes(StandardCharsets.UTF_8));
    }

    private final class NavGlyph extends View {
        private final int kind;
        private final boolean selected;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        NavGlyph(Context context, int kind, boolean selected) {
            super(context);
            this.kind = kind;
            this.selected = selected;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = dp(24) / 24f;
            canvas.save();
            canvas.translate((getWidth() - dp(24)) / 2f, (getHeight() - dp(24)) / 2f);
            canvas.scale(scale, scale);
            paint.setColor(selected ? INK : MUTED);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.9f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);

            if (kind == PAGE_POSTS) {
                Path bubble = new Path();
                bubble.moveTo(5, 4);
                bubble.lineTo(19, 4);
                bubble.quadTo(20, 4, 20, 5);
                bubble.lineTo(20, 16);
                bubble.quadTo(20, 17, 19, 17);
                bubble.lineTo(10, 17);
                bubble.lineTo(6, 20);
                bubble.lineTo(6, 17);
                bubble.lineTo(5, 17);
                bubble.quadTo(4, 17, 4, 16);
                bubble.lineTo(4, 5);
                bubble.quadTo(4, 4, 5, 4);
                canvas.drawPath(bubble, paint);
                canvas.drawLine(8, 9, 16, 9, paint);
                canvas.drawLine(8, 13, 14, 13, paint);
            } else if (kind == PAGE_COMPOSE) {
                Path pencil = new Path();
                pencil.moveTo(5, 18.5f);
                pencil.lineTo(6.2f, 14.2f);
                pencil.lineTo(16.8f, 3.8f);
                pencil.quadTo(17.5f, 3, 18.2f, 3.8f);
                pencil.lineTo(20.2f, 5.8f);
                pencil.quadTo(21, 6.5f, 20.2f, 7.2f);
                pencil.lineTo(9.6f, 17.7f);
                pencil.lineTo(5, 18.5f);
                canvas.drawPath(pencil, paint);
                canvas.drawLine(15.8f, 5, 18.6f, 7.8f, paint);
            } else {
                Path bell = new Path();
                bell.moveTo(5, 17);
                bell.quadTo(7, 14.7f, 7, 10);
                bell.quadTo(7, 5, 12, 5);
                bell.quadTo(17, 5, 17, 10);
                bell.quadTo(17, 14.7f, 19, 17);
                bell.lineTo(5, 17);
                canvas.drawPath(bell, paint);
                canvas.drawLine(9.5f, 19, 14.5f, 19, paint);
                canvas.drawCircle(12, 3, 1.1f, paint);
            }
            canvas.restore();
        }
    }


    private void renderConversationPage(LinearLayout page) {
        if (!sessionReady) {
            renderConnectionGate(page);
            return;
        }
        TextView hint = text("Only this conversation is opened. X Recent Search can return replies from the last 7 days.", 13, MUTED, false);
        hint.setPadding(0, dp(4), 0, dp(14));
        page.addView(hint);
        if (conversationError != null) page.addView(errorCard(conversationError, () -> loadConversation(false)));
        if (conversationLoading && conversationPosts.isEmpty()) page.addView(loadingCard("Loading this conversation…"));
        if (!conversationLoading && conversationPosts.isEmpty() && conversationError == null) {
            page.addView(emptyCard("No recent replies returned", "This conversation may be older than X’s recent search window, or have no replies."));
        }
        for (JSONObject post : conversationPosts) {
            page.addView(renderPostCard(post, conversationUsers, conversationMedia, true));
        }
        if (nextConversationToken != null) {
            page.addView(actionButton(conversationLoading ? "Loading…" : "Load earlier replies", () -> loadConversation(true), conversationLoading));
        }
    }

    private void renderConnectionGate(LinearLayout page) {
        addSpace(page, dp(18));
        LinearLayout welcome = card();
        welcome.setPadding(dp(18), dp(20), dp(18), dp(18));
        welcome.addView(text("A quieter way to use X.", 21, INK, true));
        TextView message = text("Publish, keep up with your own posts, and answer conversations. No home feed, recommendations, ads, or suggested posts.", 15, MUTED, false);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-1, -2);
        messageParams.topMargin = dp(9);
        welcome.addView(message, messageParams);
        TextView access = text("An approved X Developer app and active API access are required. Current published rates include $0.005 per Post read, $0.010 per User read, $0.015 per Post create, and $0.20 per post with a URL. Summoned replies or quotes are listed at $0.010 per request. X may change prices; API use is billed to the developer account you configure.", 13, MUTED, false);
        LinearLayout.LayoutParams accessParams = new LinearLayout.LayoutParams(-1, -2);
        accessParams.topMargin = dp(14);
        welcome.addView(access, accessParams);
        if (accountError != null) {
            TextView error = text(accountError, 13, Color.rgb(170, 48, 48), false);
            LinearLayout.LayoutParams errorParams = new LinearLayout.LayoutParams(-1, -2);
            errorParams.topMargin = dp(12);
            welcome.addView(error, errorParams);
        }
        page.addView(welcome, cardParams(0));

        TextView configure = actionButton(api.isConfigured() ? "Connect X" : "Add X client ID", () -> {
            if (api.isConfigured()) beginAuthorization(); else showClientIdDialog();
        }, accountLoading);
        page.addView(configure, new LinearLayout.LayoutParams(-1, dp(50)));

        TextView developerLink = text("X Developer Console  ↗", 14, BLUE, true);
        developerLink.setGravity(Gravity.CENTER);
        developerLink.setOnClickListener(view -> openExternal(API_CONSOLE));
        LinearLayout.LayoutParams linkParams = new LinearLayout.LayoutParams(-1, dp(46));
        linkParams.topMargin = dp(8);
        page.addView(developerLink, linkParams);

        TextView permissions = text("The app asks only for tweet.read, tweet.write, users.read, media.write, and offline.access. It uses X’s official API and OAuth PKCE.", 12, MUTED, false);
        permissions.setPadding(dp(4), dp(10), dp(4), 0);
        page.addView(permissions);
    }

    private void showClientIdDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("OAuth 2.0 Client ID");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString("client_id", ""));
        LinearLayout holder = vertical();
        holder.setPadding(dp(22), dp(8), dp(22), 0);
        holder.addView(input, new LinearLayout.LayoutParams(-1, dp(52)));
        new AlertDialog.Builder(this)
                .setTitle("Set up X API")
                .setMessage("Create an approved Native App in the X Developer Console. Enable OAuth 2.0 with Read and Write, register this callback exactly:\n\n" + getString(R.string.oauth_redirect_uri) + "\n\nThe client ID is public. Do not enter a client secret.")
                .setView(holder)
                .setNeutralButton("Developer Console", (dialog, which) -> openExternal(API_CONSOLE))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String clientId = input.getText().toString().trim();
                    if (clientId.isEmpty()) {
                        toast("Enter the OAuth client ID from your X app.");
                        return;
                    }
                    if (api != null) api.disconnect();
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("client_id", clientId).apply();
                    accountId = null;
                    accountUsername = null;
                    sessionReady = false;
                    posts.clear();
                    mentions.clear();
                    postsLoaded = false;
                    mentionsLoaded = false;
                    accountError = null;
                    refreshApi();
                    renderRoot();
                })
                .show();
    }

    private void beginAuthorization() {
        if (!api.isConfigured()) {
            showClientIdDialog();
            return;
        }
        try {
            String verifier = randomBase64Url(32);
            String state = randomBase64Url(24);
            String challenge = Base64.encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)),
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            getSharedPreferences(OAUTH_PREFS, MODE_PRIVATE).edit()
                    .putString("verifier", verifier)
                    .putString("state", state)
                    .apply();
            Uri authorization = Uri.parse(API_ACCESS).buildUpon()
                    .appendQueryParameter("response_type", "code")
                    .appendQueryParameter("client_id", currentClientId())
                    .appendQueryParameter("redirect_uri", getString(R.string.oauth_redirect_uri))
                    .appendQueryParameter("scope", api.scopes())
                    .appendQueryParameter("state", state)
                    .appendQueryParameter("code_challenge", challenge)
                    .appendQueryParameter("code_challenge_method", "S256")
                    .build();
            startActivity(new Intent(Intent.ACTION_VIEW, authorization));
        } catch (Exception error) {
            accountError = "Could not start X sign-in: " + safeMessage(error);
            renderRoot();
        }
    }

    private String currentClientId() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getString("client_id", getString(R.string.x_client_id));
    }

    private void handleOAuthIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri callback = intent.getData();
        if (!"xmini".equals(callback.getScheme()) || !"oauth".equals(callback.getHost()) || !"/callback".equals(callback.getPath())) return;
        String code = callback.getQueryParameter("code");
        String returnedState = callback.getQueryParameter("state");
        String providerError = callback.getQueryParameter("error");
        android.content.SharedPreferences prefs = getSharedPreferences(OAUTH_PREFS, MODE_PRIVATE);
        String expectedState = prefs.getString("state", null);
        String verifier = prefs.getString("verifier", null);
        prefs.edit().clear().apply();
        if (providerError != null) {
            accountError = "X sign-in was not completed: " + providerError.replace('_', ' ');
            renderRoot();
            return;
        }
        if (code == null || expectedState == null || verifier == null || returnedState == null || !constantTimeEquals(expectedState, returnedState)) {
            accountError = "The X sign-in response could not be verified. Please connect again.";
            renderRoot();
            return;
        }
        accountLoading = true;
        accountError = null;
        renderRoot();
        io.execute(() -> {
            try {
                api.exchangeAuthorizationCode(code, verifier);
                JSONObject me = api.getMe().optJSONObject("data");
                if (me == null) throw new IllegalStateException("X did not return account details.");
                main.post(() -> onAccountLoaded(me));
            } catch (Exception error) {
                main.post(() -> {
                    accountLoading = false;
                    accountError = safeMessage(error);
                    renderRoot();
                });
            }
        });
    }

    private void loadAccount() {
        if (accountLoading) return;
        accountLoading = true;
        accountError = null;
        renderRoot();
        io.execute(() -> {
            try {
                JSONObject me = api.getMe().optJSONObject("data");
                if (me == null) throw new IllegalStateException("X did not return account details.");
                main.post(() -> onAccountLoaded(me));
            } catch (Exception error) {
                main.post(() -> {
                    accountLoading = false;
                    sessionReady = false;
                    accountError = safeMessage(error);
                    renderRoot();
                });
            }
        });
    }

    private void onAccountLoaded(JSONObject me) {
        accountLoading = false;
        sessionReady = true;
        accountId = me.optString("id", null);
        accountName = me.optString("name", "");
        accountUsername = me.optString("username", "");
        accountImageUrl = me.optString("profile_image_url", "");
        accountError = null;
        renderRoot();
        loadPageIfNeeded();
    }

    private void loadPageIfNeeded() {
        if (!sessionReady || inConversation) return;
        if (selectedPage == PAGE_POSTS && !postsLoaded && !postsLoading) loadPosts(false);
        if (selectedPage == PAGE_NOTIFICATIONS && !mentionsLoaded && !mentionsLoading) loadMentions(false);
    }

    private void refreshCurrentPage() {
        if (selectedPage == PAGE_POSTS) loadPosts(false);
        else if (selectedPage == PAGE_NOTIFICATIONS) loadMentions(false);
    }

    private void loadPosts(boolean loadMore) {
        if (!sessionReady || postsLoading) return;
        String token = loadMore ? nextPostsToken : null;
        if (loadMore && token == null) return;
        if (!loadMore) {
            posts.clear();
            postsUsers.clear();
            postsMedia.clear();
            nextPostsToken = null;
            postsError = null;
            postsLoaded = false;
        }
        postsLoading = true;
        renderRoot();
        io.execute(() -> {
            try {
                JSONObject response = api.getMyPosts(token);
                JSONArray data = XApi.data(response);
                String next = XApi.nextToken(response);
                main.post(() -> {
                    mergeIncludes(response, postsUsers, postsMedia);
                    append(posts, data);
                    nextPostsToken = next;
                    postsLoading = false;
                    postsLoaded = true;
                    postsError = null;
                    renderRoot();
                });
            } catch (Exception error) {
                main.post(() -> {
                    postsLoading = false;
                    postsLoaded = true;
                    postsError = safeMessage(error);
                    renderRoot();
                });
            }
        });
    }

    private void loadMentions(boolean loadMore) {
        if (!sessionReady || mentionsLoading) return;
        String token = loadMore ? nextMentionsToken : null;
        if (loadMore && token == null) return;
        if (!loadMore) {
            mentions.clear();
            mentionsUsers.clear();
            mentionsMedia.clear();
            nextMentionsToken = null;
            mentionsError = null;
            mentionsLoaded = false;
        }
        mentionsLoading = true;
        renderRoot();
        io.execute(() -> {
            try {
                JSONObject response = api.getMentions(token);
                JSONArray data = XApi.data(response);
                String next = XApi.nextToken(response);
                main.post(() -> {
                    mergeIncludes(response, mentionsUsers, mentionsMedia);
                    append(mentions, data);
                    nextMentionsToken = next;
                    mentionsLoading = false;
                    mentionsLoaded = true;
                    mentionsError = null;
                    renderRoot();
                });
            } catch (Exception error) {
                main.post(() -> {
                    mentionsLoading = false;
                    mentionsLoaded = true;
                    mentionsError = safeMessage(error);
                    renderRoot();
                });
            }
        });
    }

    private void loadConversation(boolean loadMore) {
        if (!sessionReady || conversationLoading || conversationId == null) return;
        String token = loadMore ? nextConversationToken : null;
        if (loadMore && token == null) return;
        if (!loadMore) {
            conversationPosts.clear();
            conversationUsers.clear();
            conversationMedia.clear();
            nextConversationToken = null;
            conversationError = null;
        }
        conversationLoading = true;
        renderRoot();
        io.execute(() -> {
            try {
                JSONObject response = api.getConversation(conversationId, token);
                JSONArray data = XApi.data(response);
                String next = XApi.nextToken(response);
                main.post(() -> {
                    mergeIncludes(response, conversationUsers, conversationMedia);
                    append(conversationPosts, data);
                    nextConversationToken = next;
                    conversationLoading = false;
                    conversationError = null;
                    renderRoot();
                });
            } catch (Exception error) {
                main.post(() -> {
                    conversationLoading = false;
                    conversationError = safeMessage(error);
                    renderRoot();
                });
            }
        });
    }

    private void openConversation(JSONObject post) {
        String id = post.optString("conversation_id", post.optString("id", ""));
        if (id.isEmpty()) return;
        conversationId = id;
        inConversation = true;
        conversationPosts.clear();
        nextConversationToken = null;
        conversationError = null;
        renderRoot();
        loadConversation(false);
    }

    private void startReply(JSONObject post) {
        String id = post.optString("id", "");
        if (id.isEmpty()) return;
        replyToId = id;
        replyToHandle = authorUsername(post, mentionsUsers);
        draft = "";
        composeError = null;
        inConversation = false;
        selectedPage = PAGE_COMPOSE;
        renderRoot();
    }

    private void publishDraft() {
        String body = draft.trim();
        if (body.isEmpty() && selectedImage == null) {
            composeError = "Write a post or attach an image first.";
            renderRoot();
            return;
        }
        if (publishing) return;
        Uri imageUri = selectedImage;
        String mime = selectedImageMime;
        String target = replyToId;
        publishing = true;
        composeError = null;
        hideKeyboard();
        renderRoot();
        io.execute(() -> {
            try {
                byte[] imageBytes = imageUri == null ? null : readSelectedImage(imageUri, mime);
                api.publish(body, target, imageBytes);
                main.post(() -> {
                    publishing = false;
                    draft = "";
                    selectedImage = null;
                    selectedImageMime = null;
                    replyToId = null;
                    replyToHandle = null;
                    composeError = null;
                    selectedPage = PAGE_POSTS;
                    inConversation = false;
                    postsLoaded = false;
                    renderRoot();
                    loadPosts(false);
                    toast("Published to X.");
                });
            } catch (Exception error) {
                main.post(() -> {
                    publishing = false;
                    composeError = safeMessage(error);
                    renderRoot();
                });
            }
        });
    }

    private byte[] readSelectedImage(Uri uri, String mimeType) throws Exception {
        String mime = mimeType == null ? getContentResolver().getType(uri) : mimeType;
        if (mime == null || !(mime.equals("image/jpeg") || mime.equals("image/jpg") || mime.equals("image/png") || mime.equals("image/gif") || mime.equals("image/webp"))) {
            throw new IllegalArgumentException("X accepts JPG, PNG, GIF, or WEBP images for posts.");
        }
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalArgumentException("Could not open the selected image.");
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > IMAGE_LIMIT_BYTES) throw new IllegalArgumentException("X’s image upload limit is 5 MB. Choose a smaller image.");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private void chooseImage() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("image/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(picker, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGE_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        selectedImage = data.getData();
        selectedImageMime = getContentResolver().getType(selectedImage);
        try {
            getContentResolver().takePersistableUriPermission(selectedImage, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
        composeError = null;
        renderRoot();
    }

    private void confirmDisconnect() {
        new AlertDialog.Builder(this)
                .setTitle("Disconnect X?")
                .setMessage("This removes the saved session from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Disconnect", (dialog, which) -> {
                    api.disconnect();
                    sessionReady = false;
                    accountId = null;
                    accountUsername = null;
                    accountName = null;
                    accountImageUrl = null;
                    accountError = null;
                    posts.clear();
                    mentions.clear();
                    conversationPosts.clear();
                    postsLoaded = false;
                    mentionsLoaded = false;
                    inConversation = false;
                    replyToId = null;
                    replyToHandle = null;
                    renderRoot();
                })
                .show();
    }

    private static void append(ArrayList<JSONObject> list, JSONArray array) {
        for (int i = 0; i < array.length(); i++) {
            JSONObject post = array.optJSONObject(i);
            if (post == null) continue;
            String id = post.optString("id", "");
            boolean duplicate = false;
            for (JSONObject existing : list) {
                if (id.equals(existing.optString("id", ""))) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) list.add(post);
        }
    }

    private void hideKeyboard() {
        try {
            InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.hideSoftInputFromWindow(root.getWindowToken(), 0);
        } catch (Exception ignored) { }
    }
}
