# KiroX Fusion UI v2 — implementation-ready design contract

Status: **VISUAL DESIGN APPROVED**, engineering implementation **NOT YET COMPLETED**.
Visual source: exact owner-approved 2026-10-29 composite with purple row (Expressive) and blue row (Glass), seven screen types per theme. The image is separate from this repo; request the exact attachment from the owner for pixel-accurate validation.

## 1. Design principles

- Two **complete style variants**, not just color-swapped Material themes. Screen order, primary actions and information architecture stay consistent between variants. Shapes, backdrop, depth, border treatment, press/transition visuals may change.
- Material 3 Expressive in **purple**: black/near-black foundation, vivid purple primary action, bright violet outgoing bubbles, layered dark cards, pill-shaped selectors, intentional glow.
- Fusion Glass in **blue**: deep navy/blue foundation, optional scenic/background wallpaper, translucent panels, frosted dock, blue-purple highlights, glow constrained to active controls and progress.
- The approved reference is intentionally **colorful**. Do NOT convert it back to monochrome by default.
- Prefer content legibility above effect strength: body text high contrast; dim layers behind text when wallpaper is bright.
- Every visual target uses the same real user/chats/session state regardless of selected theme.

## 2. Navigation — IMPORTANT compatibility gate

The newly approved reference board visually shows the bottom bar as:
**Чаты · Звонки · Истории · Настройки** (active item illuminated in an expressive/glass pill).

Existing main-screen code (checked 2026-10-29) uses **Контакты · Чаты · Настройки · Профиль** and older UI_SPEC.md describes this as the approved contract.

**New owner-approved composition takes visual precedence**, but navigation cannot be changed by guessing: before coding, inspect each current route and propose a non-destructive migration mapping:
- Chats → current real chat list.
- Calls → current real calls history/functionality or visibly unsupported state.
- Stories → actual TDLib stories where implemented; otherwise safe, informative empty/unsupported state.
- Settings → current settings with reachable account/profile and contacts.
- Profile → reached from the profile/avatar/account element without deleting existing profile features.
- Contacts → reachable clearly from Chats/new-message/search or Settings if bottom item is replaced; do not orphan existing contacts.
- If a call/story feature is not functional yet, do not populate it with pretend data or simulate a successful action.

Include a before/after navigation graph and ask the owner to approve this functional migration **before** modifying current bottom-tab routes. Merely aligning appearance with reference does not authorize breaking existing routing.

## 3. Approved screen-level contracts (each in Expressive + Glass)

### 01 / Chat list

Layout top-to-bottom:
1. Status bar / inset.
2. Compact header: nav/menu icon on left, **KiroX** wordmark central, search and secondary action on right.
3. Rounded search field spanning almost full width.
4. Horizontal Stories quick row: My Story, Saved Messages, channels/favorites and recent peers; avatar borders show unread state; account/avatar comes from real state.
5. Compact filter row with pill chips (e.g. Все / Личные / Каналы / Группы). Real Telegram folders remain accessible and their identity is preserved, never conflated with Archive.
6. Dense conversation list: 48–54dp avatars, bold sender, preview, time, unread/muted icons, verified actual badges only. Distinct UI for Saved Messages and Archive.
7. Bottom dock: 4 icon/label slots and illuminated active item, under system gesture area with proper insets.

Preserve scrolling and stable IDs. Chat list is not a grid of cards. Use minimal horizontal dividers and gentle surface separation. Target list row 64–76dp.

### 02 / Conversation

Layout:
1. Header back, peer avatar/name/online status, call/video and overflow as available.
2. Chat wallpaper. Expressive: violet bloom/textured purple; Glass: mountains/nature or custom wallpaper. Wall is a **theme preview asset** until actual user wallpaper is selected.
3. Incoming bubbles: rounded graphite/glass. Outgoing: purple-blue high contrast with readable timestamp. Respect actual message timestamps, status, replies, mentions, media.
4. File/media cards aligned with message bubbles; audio playback row with play, waveform, duration (real data). Maintain formats and message semantics already provided by TDLib.
5. Bottom composer with rounded translucent/graphite input, attachment and send/voice action.
6. Keyboard/insets, replies, editing/forwarding and selected context state handled without jumping or hiding input.

Apply user's chosen bubble radius and font scale. No guaranteed "free premium". Typing/online only if available.

### 03 / Profile + grouped account settings

Centered avatar, name, genuine presence/status; optional photo banner. Group rows immediately below include Edit Profile, official Telegram Premium state (only when TDLib reports it; never invent a Premium entitlement), Notifications, Privacy, Storage, Appearance, Language, KiroX features, Help, About. Account switch stays reachable as implemented. Selected glass panels are frosted over wallpaper; Expressive uses opaque layered containers.

Distinct profile content (bio/links/media) remains accessible; do not throw away existing features to match a small screenshot.

### 04 / Calls list

Header Calls, search, filter segmented pill: All / Incoming / Outgoing / Missed. Vertical participants with avatar, status icon, timestamp and call-back action. Missed call states are clearly differentiated without relying solely on color. Only use actual call history and verified calling capability; no fake entries.

### 05 / Active call (visual target only, functionality contingent on VoIP)

Background adapts to theme. Peer avatar and display name centered with actual call state/time. Large round control grid for Speaker, Video, Mute, Add participant (only if supported), Hold (only if supported), Keypad (only if supported) and a prominent red End button. Active toggles must have label/state and a visible focus treatment. Show permission/device/offline states. For unimplemented features, disabled state or explanatory sheet, never fictitious success. Later agree separate incoming-call and live video layouts.

### 06 / Settings home

Top header and searchable settings if search exists. Grouped rounded cards: Account, Notifications, Privacy, Data/Storage, Appearance, Chats, Stickers/Emoji, Folders, Devices, Language, About. Stable information hierarchy with icons at consistent size; avoid endless separators and duplicate settings destinations. Search is functional only if index exists, otherwise no misleading clickable button.

### 07 / Appearance studio

Screen with four preview presets: Expressive, Glass, AMOLED, Custom. Only the **first two** are visually approved in current phase. AMOLED and Custom are legacy/placeholder configurations, do not claim that their full new designs are approved.
- Accent selection (Purple, Blue, Violet etc.) applies to visual accents **within selected style**, not arbitrary substitution of entire layout.
- Blur on/off and strength for Glass; reduced-effects automatic mode.
- Motion/animated backgrounds and seasonal decorations separate toggles.
- Shape selector for chat bubbles/containers with live sample preview and reset.
- Persist choice through existing settings layer, not through a duplicated storage model.
- Show a clear preview before apply and allow undo/reset.

## 4. Palette, typography, motion

See theme-tokens.json for exact **proposed starting tokens** (NOT measured from screenshot). Tune to pixel reference in review.

- Expressive base #080710, surface #141121, surfaceHigh #211B31, primary #A276FF, accent #8264FF, foreground #F8F5FF.
- Glass base #071424, glassSurface rgba(34,59,92,0.52), blue primary #6B91FF, violet secondary #9A83FF, foreground #F4F8FF.
- Both support custom wallpapers, protective text scrim and proper contrast.
- Headers: ~20–24sp Semibold, body chat labels ~14–16sp, metadata 11–13sp (never below 11sp for crucial text).
- Standard touch target >=48dp. Avoid font fixed sizes that clip 130–200% scaling. Use real ellipsis where needed.
- Expressive surfaces rounded 16–28dp; pills fully round; Glass containers 16–24dp with subtle strokes and depth.
- Micro motion 120–220ms; major route transitions 220–320ms with interruptions and system animator scale support.
- Low-end: no expensive full-screen blur; avoid recomposing backdrop on every TDLib update. Static translucent fill is allowed.

## 5. Performance, accessibility, API behavior

- Kotlin/Compose with existing FusionTheme and current repositories; no wholesale rewrite.
- Respect system gesture bars/edge-back, cutouts, IME; preserve chat scroll and drafts.
- The two styles are rendering strategies sharing common screen state and actions, not duplicated Telegram screens.
- Guard against TDLib session leaks, lost state on theme switch, image GPU churn, heavy blur over animated media.
- Reduce Motion/Power Saver disables nonessential gradients/particle loops; Glass uses non-blur fallback.
- Screen reader names and selected state; min contrast, readable text even over wallpapers; content descriptions for unlabeled icons.
- No server-side Premium/badges claims; only actual Telegram fields. Keep KiroX's local visual branding distinct from Telegram verification.

## 6. QA acceptance matrix

Each of the 7 screens × 2 themes:
- [ ] Screenshot versus approved original
- [ ] State populated with real/empty/error variants
- [ ] 360×640dp, 412×915dp, >=600dp responsive behavior
- [ ] FontScale 1.0 / 1.3 / 2.0, RTL-safe where supported
- [ ] Gesture navigation & 3-button system navigation
- [ ] IME opened/closed where relevant
- [ ] Reduce Motion and disabled blur
- [ ] Persistent theme change and process recreation
- [ ] No dead taps or fabricated success
- [ ] Live TDLib functionality unaffected

For active calls, verify with actual VoIP integration or mark explicitly **NOT TESTED / NOT IMPLEMENTED**.

## 7. Staged engineering implementation (owner confirms each stage)

**A — inspection and UX mapping:** screenshots of real APK and route-state inventory, compare approved board, plan changed files and collisions. No feature rewrites.

**B — design foundations:** two style variants, tokens, overlays, quality/fallback toggles, theme persistence, instrumentation. Keep visual behaviors behind existing settings.

**C — chats + bottom nav:** implement screen 01 in both styles, owner approves. Review navigation mapping before swapping tabs.

**D — conversation:** screen 02 in both styles, owner approves.

**E — profile + settings:** screens 03 and 06, owner approves.

**F — calls:** screens 04 and 05, strictly preserving real VoIP contract; owner approves.

**G — appearance studio:** screen 07 + live preview; owner approves.

**H — integration and regression:** screenshots, tests, accessibility, cold launch, performance, compatibility and owner sign-off. Only merge after review.

## 8. Not yet approved as full mockups

Incoming call ring screen, immersive video call, full story viewer/editor, media gallery, rich contact profile, login/onboarding variants, advanced call controls. Plan these after the 7×2 baseline. Do not imply that the current visuals establish their exact layout.
