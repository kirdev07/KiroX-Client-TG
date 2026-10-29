# KiroX Fusion UI v2 — status after `8a17166`

**Branch:** `design/kirox-fusion-expressive-glass` · `main` not changed, no merge.

## Reference check

`python3 design-reference/fusion-v2/check_approved_reference.py` → **MISSING_APPROVED_PNG**.
The approved original `1000084555.png` (1536×620, SHA-256 `0092657…d4f3`) is not in the repository.
No visual match is claimed. Per `APPROVED_CORE_VISUAL_EXACT.md` further Compose redesign is stopped until the PNG is added.

## What is in the code now (kept, works with TDLib)

- Palettes `Expressive` (default for new installs) and `Glass`; saved palettes kept; `glass_blur` setting.
- Dock Чаты · Звонки · Истории · Настройки; Contacts and own profile as sub-screens; Stories tab; local Личные / Группы / Каналы filters from TDLib chat type; archive tabs unchanged.
- Profile without fake status/badge; «Переключить аккаунт» opens the real accounts sheet.
- Settings in grouped cards; in-app mini call pill; themed auth/call backgrounds.
- `KiroXFeatures.ANIMATIONS = false` — global off switch, asked by the owner earlier; the new docs ask to replace it with controlled animations + Reduce Motion / Power Saver fallback (pending).

## Checks on `8a17166`

| Check | Result |
|---|---|
| `testDebugUnitTest` | PASS |
| `assembleDebug` | PASS |
| `lintDebug` | PASS, 0 warnings |
| `assembleReleaseCheck` (R8) | NOT TESTED on this commit (manual runs were cancelled by later pushes) |
| Screenshot test, 9 screens × 2 styles | PASS (renders), **not approved by the owner** |
| Device | NOT TESTED |
| Visual match with the approved PNG | NOT TESTED — `MISSING_APPROVED_PNG` |

## Six-screen gap matrix (from the text of `APPROVED_CORE_VISUAL_EXACT.md`)

| Screen | Reference (text) | Current implementation | Differences (P0 first) | Files to change |
|---|---|---|---|---|
| Expressive · Чаты | KiroX centred in header, menu left, search + action right; big rounded search; stories row right under it; chips Все/Личные/Каналы/Группы; dense list; pill dock | Large left title «Чаты» with 3 icon actions; no search bar (search is an icon); stories row; Архив/Сообщения tabs; chips; list; floating dock | P0: header layout (centred KiroX + menu), always-visible search bar above stories. Archive tabs stay (owner rule) — placement to agree | `TelegramMainScreen.kt` (ChatsTab), `AccountScreens.kt` (ScreenTitle), `FusionMaterial.kt` (search) |
| Expressive · Переписка | Header avatar + name + status; textured violet abstract wallpaper over the whole area; dark translucent incoming, saturated lilac outgoing; media card; lilac voice card with wave; rounded composer | Plain back title with name only; flat violet gradient; colours roughly match; composer rounded | P0: real wallpaper image instead of gradient; header with avatar/status. P1: media and voice card styling | `TelegramChatScreen.kt`, `ChatScreen.kt`, `ChatWallpaper.kt`, new wallpaper asset |
| Expressive · Профиль/настройки | Deep black base, centred round avatar, name/status, two compact groups of card buttons: «Изменить профиль», Premium (real), then notifications, privacy, data, appearance, language, KiroX, help | Settings: avatar, name, edit pill + accounts button, four grouped cards (Аккаунт/Оформление/Telegram/Приложение) on violet gradient | P0: base colour and compact two-group layout, item order (no Language/Help rows yet) | `AccountScreens.kt` (SettingsTab, ProfileHeader), `FusionTheme.kt` |
| Glass · Чаты | Same layout; blue-graphite glass search; transparent list with thin dividers; blue story rings; blue-violet active chips; floating glass dock | Same as Expressive with blue tint; dock translucent; list opaque-looking | P0: a real themed background image behind content (licensed), visible glass layers; inherits header/search P0 | `FusionBackdrop.kt`, `FusionBottomBar.kt`, `TelegramMainScreen.kt`, asset |
| Glass · Переписка | Mountain/lake evening wallpaper behind messages with soft blue scrim; glass bubbles, blue-lilac outgoing; strokes and glow | Navy gradient, solid blue outgoing, translucent incoming without stroke | P0: wallpaper image + scrim; glass bubbles with stroke | `ChatWallpaper.kt`, `ChatScreen.kt` (MessageRow), asset |
| Glass · Профиль/настройки | Mountains with haze on top, then dark navy; translucent rounded items with thin blue stroke; same order as Expressive | Translucent cards with light stroke on navy gradient | P0: top image area; stroke colour; inherits Expressive layout P0 | `AccountScreens.kt`, `FusionBackdrop.kt`, asset |

Cross-cutting P0: background images need licensed assets (none in the repo); typography and icon style cannot be checked without the PNG; animations to return with a fallback.

## Next step (waiting for the owner)

1. Add the original PNG to `design-reference/fusion-v2/assets/KiroX_Fusion_Expressive_Glass_APPROVED_ORIGINAL.png`.
2. Provide (or approve the source of) the background images: Expressive abstract wallpaper, Glass mountains/lake.
3. Then one small sub-stage at a time (first: Expressive · Чаты header/search), screenshots after each, waiting for feedback.
