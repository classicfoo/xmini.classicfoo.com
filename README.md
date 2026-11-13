# XMini

A small Android client for publishing to X and keeping up with replies to your own posts. The app has three tabs: **My Posts**, **Compose**, and **Notifications**. It does not request or display a home timeline, recommendations, Explore, trending topics, ads, or suggested posts.

## Connect an X account

X API access is not included with the APK. Each user connects an X Developer app with access to the required endpoints, and X may bill API use to that app's developer account.

1. Create an approved Project and **Native App** in the [X Developer Console](https://developer.x.com/).
2. Enable OAuth 2.0 with Read and Write permission.
3. Register this callback URI exactly: `xmini://oauth/callback`.
4. In XMini, choose **Add X client ID** and enter the app's OAuth 2.0 client ID. A native app is a public OAuth client; do not enter a client secret.
5. Connect and approve the scopes `tweet.read`, `tweet.write`, `users.read`, `media.write`, and `offline.access`.

XMini uses OAuth 2.0 Authorization Code with PKCE. Access and refresh tokens are encrypted with a device Keystore key. The client ID is public app configuration and is stored locally.

## What the X API supports here

| Feature | Official API path | Current limit or behavior |
| --- | --- | --- |
| My Posts | `GET /2/users/:id/tweets` | Chronological, paged only after tapping **Load earlier posts**. X documents up to 3,200 recent posts for this timeline. |
| Mentions and replies | `GET /2/users/:id/mentions` | Shows posts that mention the connected account, including replies and quote posts returned by X. This is not a full X notifications feed; likes and follows are not included. |
| Conversation replies | `GET /2/tweets/search/recent` with `conversation_id:` | Search covers only the previous 7 days. The app loads a bounded page and offers an explicit **Load earlier replies** action. It never opens another account's timeline. |
| Publish and reply | `POST /2/tweets` | X's self-serve API may reject a reply unless the post author mentioned the connected account or quoted one of its posts. X decides whether a reply is permitted. |
| Photo and link posts | `POST /2/media/upload`, then `POST /2/tweets` | This build attaches one JPG, PNG, GIF, or WEBP image up to 5 MB. Links are posted as text; X generates a preview when it chooses to. |

The Notifications tab refreshes when opened and when **Refresh** is tapped. It does not provide background or push alerts. X's separate Activity API delivers webhook events, so using it for push alerts would require a server endpoint and separate event billing; this APK does not pretend that polling is push delivery.

## Pricing and access

X's public [Developer Platform pricing page](https://developer.x.com/) currently describes pay-per-use access and lists unit prices including **$0.005 per Post read**, **$0.010 per User read**, **$0.015 per Post create request**, **$0.200 per post containing a URL**, and **$0.010 per summoned reply/quote create request**. Expansions may add read resources. Prices, endpoint access, rate limits, and billing requirements can change, so check the X Developer Console before connecting or using the app. X also requires an approved developer app and applicable endpoint access.

## Build

```bash
./gradlew :app:assembleDebug
```

The public GitHub releases contain signed, debuggable APKs that can be installed and updated through Obtainium. The debug signing key is kept on the build machine; don't delete it if you need to preserve upgrade compatibility.
