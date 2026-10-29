# KiroX Fusion UI v2 — status after `8a17166`

**Branch:** `design/kirox-fusion-expressive-glass` · `main` not changed, no merge.

## Reference check

`python3 design-reference/fusion-v2/check_approved_reference.py` → **APPROVED_REFERENCE_OK** (1536×620, SHA-256 `0092657…d4f3`, added in `6f438bf`).
Current screenshots were compared with it below. No visual match is claimed yet.

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

## Six-screen gap matrix (approved PNG vs. screenshots of `8a17166`)

| Screen | Approved PNG | Current implementation | Differences (P0 first) | Files to change |
|---|---|---|---|---|
| Expressive · Чаты | Near-black base; header: menu left, **KiroX centred**, two icons right; always-visible rounded search; stories row (Моя история, Избранное, channels…); chips Все/Личные/Каналы/Группы; list; **Архив as a list row** («24 чата»); opaque dock with labels and violet pill | Violet gradient base; large left title «Чаты» + 3 icons; search only as icon; Архив/Сообщения tabs; chips Все/Личные/Группы/Каналы + folders; FAB; floating dock | P0: base colour; centred header + menu; search bar; chip order. **Archive tabs vs. archive row — owner decision** (owner rule: do not touch archive) | `TelegramMainScreen.kt`, `AccountScreens.kt` (ScreenTitle), `FusionMaterial.kt`, `FusionTheme.kt`, `FusionBottomBar.kt` |
| Expressive · Переписка | Header: back, avatar, name, «в сети», call + more; **violet floral wallpaper** full screen; incoming dark translucent; outgoing violet gradient; file card; violet voice card with wave; composer: emoji, field, attach, mic in violet circle | Back + name only; flat gradient; solid bubbles; mic plain icon | P0: wallpaper image; header avatar/status/actions; outgoing gradient; mic button style | `TelegramChatScreen.kt`, `ChatScreen.kt`, `ChatWallpaper.kt`, wallpaper asset |
| Expressive · Профиль/настройки | No dock; back + more; ringed avatar, name, status; cards: [Изменить профиль, Premium] · [Уведомления и звуки, Конфиденциальность, Данные и память, Оформление, Язык] · [KiroX Настройки, Помощь, О приложении] | Settings tab: search icon, avatar with camera, edit pill + accounts button, four groups with other items | P0: three groups in this order; Язык / Помощь / О приложении rows; Premium row only with real status text | `AccountScreens.kt` (SettingsTab, ProfileHeader) |
| Glass · Чаты | Same layout; navy blurred backdrop; glass search; thin dividers; blue rings; **floating glass dock** | Navy gradient; same structure as current Expressive | Inherits Expressive P0; glass search/dock strokes | as above + `FusionBackdrop.kt` |
| Glass · Переписка | **Mountains/lake evening photo** behind messages; glass bubbles with stroke; blue-violet outgoing; glass composer | Navy gradient, solid bubbles | P0: wallpaper photo + scrim; bubble stroke/translucency | `ChatWallpaper.kt`, `ChatScreen.kt`, asset |
| Glass · Профиль/настройки | Mountain photo header fading to navy; translucent groups with thin stroke | Translucent cards on gradient | P0: photo header; same group layout as Expressive | `AccountScreens.kt`, asset |

Cross-cutting: the PNG's wallpapers and avatars are illustrations inside a 1536×620 collage — too small to crop; full-size licensed images are needed. Animations: the PNG subtitle says «Плавные анимации»; current code has them globally off (owner's earlier request).

## Next step (waiting for the owner)

1. ~~Add the original PNG~~ — done (`6f438bf`).
2. Provide (or approve the source of) the background images: Expressive abstract wallpaper, Glass mountains/lake.
3. Then one small sub-stage at a time (first: Expressive · Чаты header/search), screenshots after each, waiting for feedback.
