# Mobile / APK Readiness

Purpose: keep MoneyFlow honest before packaging the Vue PWA inside an Android APK.

Current architecture:

- Frontend: Vue PWA.
- Backend: Spring Boot API.
- Target later: Capacitor Android wrapper.

This document is a gate. It is not an implementation plan for this sprint.

## 1. Current State

- PWA shell exists.
- Web app manifest exists.
- Service worker should stay pass-through or network-first for authenticated API traffic. Do not cache private financial data.
- Mobile bottom navigation exists.
- Route lazy loading exists.
- The app is not a native APK yet.
- No Android project exists yet.
- No Capacitor runtime is configured yet.

## 2. Do Not Build APK Until

- P0 browser UAT passes.
- Login, logout, token refresh, and session expiry behavior are stable.
- Voice audio upload and playback are stable on deployed backend storage.
- Daily Closing, Planning, and Transactions flows are stable.
- Mobile layout passes 360 px, 390 px, and 414 px widths.
- No horizontal scroll appears on core routes.
- Private financial data is not cached by the service worker.
- Production API base URL is explicit and not `localhost`.
- CORS is configured for deployed frontend origins.

## 3. Capacitor Plan

Do later, after the gates above pass:

1. Install Capacitor packages.
2. Create the `android` folder from the web app.
3. Configure `appId` and app name.
4. Configure production API base URL.
5. Build the web app into the Capacitor web directory.
6. Test Android WebView on a real device.
7. Generate production icons and splash assets.
8. Verify microphone, file picker, downloads, and back button behavior.
9. Verify release signing only when APK packaging is actually needed.

Do not commit generated Android project files until the WebView proof is useful and reviewed.

## 4. Backend Concerns

- CORS origins must include only deployed app origins. Avoid wildcard origins when credentials or auth headers are used.
- Cookie/token storage strategy must be explicit for WebView. Current bearer-token behavior must not leak into logs, URLs, screenshots, or service worker cache.
- HTTPS is required for production API calls, microphone permission, and secure token transport.
- API base URL must be environment-driven. APK builds cannot call `localhost` on the user's phone.
- Upload max sizes must be documented for voice audio and avatar files.
- Cloudinary env must be present for audio/avatar storage where these features are enabled.
- Cloudinary folder roots must be environment-safe: dev data in dev folders, production data in production folders.
- Audio retention expectations must be explicit. Do not promise permanent audio retention unless backend/storage policy guarantees it.
- Health checks must remain available:
  - `GET /api/public/health/live`
  - `GET /api/public/health/ready`
- Backend error responses should stay safe: no secrets, signed URLs, API keys, or raw provider credentials.
- Playback should use authenticated backend-mediated access, not public unauthenticated audio URLs.

## 5. Manual Android Test Checklist

Run on Android WebView or a real Capacitor build candidate:

- [ ] Install/open app.
- [ ] Login succeeds.
- [ ] Logout clears usable session.
- [ ] Session refresh/expiry behavior is sane.
- [ ] Workspace list loads.
- [ ] Default workspace selection loads.
- [ ] Main routes navigate correctly.
- [ ] Deep links or refreshed routes land on the expected screen.
- [ ] Android back button behavior is predictable.
- [ ] Voice recording asks for microphone permission.
- [ ] Denied microphone permission shows a useful error state.
- [ ] Voice audio upload succeeds.
- [ ] Voice audio playback succeeds.
- [ ] Transcript-only audio state does not show a fake playable button.
- [ ] Avatar upload works from file picker.
- [ ] Avatar delete works.
- [ ] Offline behavior shows offline/error state.
- [ ] No fake sync or fake saved data appears offline.
- [ ] Service worker does not cache private API responses.
- [ ] Safe area and notch are respected.
- [ ] Bottom navigation does not overlap content or Android gesture area.
- [ ] 360 px, 390 px, and 414 px layouts have no horizontal scroll.

## 6. Risks

- Android WebView microphone permission can differ from desktop Chrome.
- Android file picker and camera permissions can differ from browser upload behavior.
- `localhost` is not the backend from a phone or emulator unless explicitly bridged.
- Service worker caching private financial data is a high-severity privacy bug.
- Auth token persistence in WebView needs a deliberate storage strategy.
- CORS that works in local browser may fail in a packaged WebView or deployed origin.
- Audio recording codecs may differ by device.
- Large audio/avatar uploads may hit API, proxy, or provider limits.
- Back button handling can accidentally exit the app or lose unsaved drafts.
- Deep links can fail if the SPA fallback is not configured in the hosting layer.

## Backend Safety Notes

- Keep production config separate from dev config.
- Do not use production DB or production Cloudinary folder for local APK/WebView tests.
- Do not add fake runtime data for offline states. Show error or empty state.
- Do not expose direct Cloudinary secrets or signed upload parameters to frontend unless intentionally designed.
- Do not widen CORS to `*` to make APK testing pass.
- Do not add permanent audio caching in the service worker.
- Treat all APK screenshots/logs as potentially containing financial data.

## Frontend Contract Notes

- Frontend must call the configured API base URL, not a hardcoded local URL.
- Authenticated API responses must be network-only or non-persistent.
- Audio upload endpoint must be called with the recorded blob as form data.
- Audio playback must handle unavailable, failed, transcript-only, and playable states.
- Avatar upload/delete must handle provider failure without corrupting the user profile.
- Routes must be reload-safe under SPA hosting before WebView packaging.
