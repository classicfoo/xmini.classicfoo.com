# X API capability check

XMini uses the official X API v2 and OAuth 2.0 Authorization Code with PKCE. The app does not scrape X pages, use private endpoints, or bypass API access controls.

## Implemented

- `GET /2/users/me` and `GET /2/users/:id/tweets` for the connected user's account and posts.
- `GET /2/users/:id/mentions` for mentions, including replies/quote posts that X returns in the mention timeline.
- `GET /2/tweets/search/recent` with a `conversation_id:` query for the selected conversation only.
- `POST /2/tweets` for new posts and replies.
- `POST /2/media/upload` with `media_category=tweet_image` for a single image attachment.
- OAuth scopes: `tweet.read`, `tweet.write`, `users.read`, `media.write`, `offline.access`.

## Unavailable or constrained

- X API v2's documented timelines include user posts, mentions, and home timeline, but no general notifications endpoint. Notifications therefore means the mention timeline here. Likes, follows, and other notification types are not available in this app.
- Recent Search returns posts from only the previous 7 days. It cannot provide a complete archive of old replies through this path.
- The self-serve create-post documentation restricts replies: the original post's author must have mentioned the replying account or quoted one of its posts. X can reject replies that don't meet this rule.
- This APK checks notifications on demand; it does not run a service or claim to deliver push notifications. X Activity events are delivered through webhooks and have separate per-event billing.
- Photo upload is limited here to one JPG, PNG, GIF, or WEBP image per post and 5 MB. Video, the separate `tweet_gif` animated-GIF category, multiple images, and alt-text editing are not implemented.

## Official references

- [OAuth 2.0 Authorization Code with PKCE](https://docs.x.com/fundamentals/authentication/oauth-2-0/authorization-code)
- [X API v2 timelines](https://docs.x.com/x-api/posts/timelines/introduction)
- [Get Posts](https://docs.x.com/x-api/users/get-posts)
- [Get Mentions](https://docs.x.com/x-api/users/get-mentions)
- [Manage Posts and reply rules](https://docs.x.com/x-api/posts/manage-tweets/introduction)
- [Search Posts and time range](https://docs.x.com/x-api/posts/search/introduction)
- [Upload Media](https://docs.x.com/x-api/media/upload-media)
- [X Activity event pricing](https://docs.x.com/x-api/activity/event-pricing)
- [Developer Platform pricing](https://developer.x.com/)
