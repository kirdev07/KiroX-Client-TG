# KiroX Fusion UI — Stage A audit

**Date:** 29.10.2026
**Branch:** `design/kirox-fusion-expressive-glass` (local merge `2914e2a`)
**Base:** the design branch (`a26ba44`, documents only) + `feature/stage8-extras` (`fc58177`, the current tested code).
`main` is not changed.

## 0. Starting point

| Check | Result |
|---|---|
| `git status` before work | clean, no user changes |
| Design branch content | only `design-reference/` documents; based on old `main` `e7d6510` |
| Code base used | `feature/stage8-extras` @ `fc58177` merged in (TDLib 1.8.69 `4c6377aa`, AGP 9.5, Kotlin 2.4, Compose BOM 2026.09, Material 3 already in use) |
| Merge conflicts | none (the design branch touches only `design-reference/`) |
| Approved PNG references | **not available** (not in GitHub, not in this session). Work follows the text specs and `theme-tokens.json`; pixel-perfect match **cannot be claimed** |
| Version | `versionName = 1.0.0-rc.1`, `versionCode = 8` (Gradle) |

Note: the master task was written against `main` `e7d6510`. Much of what it lists as missing already exists on `feature/stage8-extras` (fullscreen photo/video, GIF/sticker panel, forum topics, video chats with screen sharing, multiple accounts, notifications, background audio). The audit below is against the real current code.

## 1. Baseline checks

| Command (GitHub Actions, ubuntu, JDK 17) | Result on `fc58177` |
|---|---|
| `testDebugUnitTest` (34 test classes) | PASSED |
| `assembleDebug` | PASSED |
| `assembleReleaseCheck` (R8) | PASSED |
| `lintDebug` | PASSED, 0 errors, 0 warnings |
| Screenshot test (Robolectric, 7 screens) | PASSED, PNGs on branch `screenshots` |
| `connectedDebugAndroidTest` | NOT TESTED (no device/emulator) |
| Real device: login, chats, calls, push, stories | NOT TESTED |

## 2. Navigation — before / after

### Before (current code)

```
Auth graph: Phone → Countries → AuthStep (code / 2FA / email / QR / registration)
Main graph: Chats(root, 4 tabs) → Conversation → Thread (comments / forum topic)
  Tabs: Контакты · Чаты · Настройки · Профиль
  Контакты tab: [Контакты | Звонки] section tabs, add contact, contact card, call
  Чаты tab: stories row, [Архив | Сообщения], folder chips, list, search, FAB → New message
  Настройки tab: profile card, accounts sheet, sections → sub-screens
     (Customization, Themes, ChatStyle, Animations, Updates, Devices, ProfileEdit,
      Notifications, Privacy, Storage, Premium, Advanced)
  Профиль tab: own profile, QR, edit, photo, Saved, logout
Overlays: CallScreen (1:1), GroupCallScreen, StoryViewer
```

### After (approved dock `Чаты · Звонки · Истории · Настройки`)

| New tab | Opens | Old functions kept reachable from |
|---|---|---|
| Чаты | the same chat list: stories row, **Архив / Сообщения (unchanged)**, folder chips, list, search, FAB | — |
| Звонки | the existing `CallsList` (real `SearchCallMessages` history, filters, call back) | was the «Звонки» section of the Контакты tab |
| Истории | new tab over the existing `TelegramStoryRepository` + `StoryViewer` + `StoryPost` | the stories row in Чаты opens the same viewer |
| Настройки | the existing settings | — |
| Контакты (no tab) | the existing `ContactsList` as a sub-screen | Чаты header action, New message screen, Settings → Контакты |
| Профиль (no tab) | the existing `ProfileTab` as a sub-screen | Settings profile card (tap), Settings → «Мой профиль» |
| Аккаунты | the existing `AccountsSheet` | Settings profile card button; the Profile menu (placeholder replaced) |

Nothing is removed; four destinations move, none disappear. The tab index is saved with `rememberSaveable`, as before.

## 3. Reuse / adapt / create (46 compositions per theme)

| Block | Composition | Current code | Action |
|---|---|---|---|
| Base | 01 Chat list | `TelegramMainScreen.ChatsTab` | adapt (header, theme colours; archive tabs untouched) |
| | 02 Conversation | `TelegramChatScreen`, `ChatScreen.MessageRow`, `ChatComposer` | adapt (theme bubbles, wallpaper) |
| | 03 Profile | `ProfileTab` | adapt (fix fake «в сети», local badge, account placeholder) |
| | 04 Calls list | `CallsList` | reuse as a tab |
| | 05 Active call | `CallScreen` | adapt (theme background) |
| | 06 Settings | `SettingsTab` | adapt (grouped cards) |
| | 07 Appearance studio | `ThemesScreen`, `CustomizationScreen` | adapt (Expressive / Glass / AMOLED / custom palettes, blur toggle) |
| Calls | Incoming | `CallScreen.IncomingControls` | adapt |
| | Video call | `CallScreen.CallVideo` | adapt |
| | Mini call window | — | **create** (in-app chip; system PiP not done) |
| Stories | Feed | stories row only | **create** Stories tab |
| | Viewer | `StoryViewer` | reuse |
| | Editor | `StoryPost` (picker + caption) | adapt; drawing/stickers on a story not implemented |
| | Publish / privacy | `StoryPost` (contacts / everyone) | reuse |
| Contacts | Contacts | `ContactsList` | reuse as sub-screen |
| | Global search | `ChatSearchResults` (chats + messages) | reuse |
| | User card | `ContactCard` sheet | reuse |
| | Contact actions, add contact, new group, new channel | `AddContactDialog`, `NewMessageScreen` | reuse |
| | User media | shared media in chat | partially exists (search by type not built) |
| Media | Photo viewer, video player, attachment sheet, stickers/emoji, GIF, files, inline attachments | `MessageMedia`, `StickerPanel`, `AttachmentSheet`, `MediaFiles` | reuse + theme |
| | Media gallery / shared media tabs | — | not implemented (honest gap) |
| Auth | Splash, welcome, phone, code, 2FA, QR | `AnimatedSplashScreen2`, `PhoneLoginScreen`, `TelegramAuthScreens` | reuse + theme |
| | Accounts list / add / switch | `AccountsSheet`, `TelegramAccounts` | reuse |
| Groups | Chat list with filters | folder chips | adapt (+ local type filters Личные / Группы / Каналы) |
| | Group info, members, channel view | chat info menu, channel posts in conversation | partial |
| | Create group / channel | `NewMessageScreen` | reuse |
| | Forum topics | `ForumTopicsList`, `TelegramMessageRepository.openTopic` | reuse |
| | Folders, edit folder | `FolderDialogs` (create / delete) | reuse; folder editing (add/remove chats later) not implemented |

## 4. Problems found in the current code

1. **Fake data in the profile:** the Профиль tab always shows «в сети · Пользователь KiroX» and a local check badge next to the name (`KiroXBadge`), which reads like verification. The spec forbids both. → Real fields only (Premium / Telegram verification when TDLib says so), badge removed.
2. **Placeholder in the Profile menu:** «Переключить аккаунт» shows «в разработке» although `AccountsSheet` works. → Opens the real sheet.
3. **Dock mismatch** (section 2).
4. **Theme:** only the four monochrome palettes; no colour, no glass surfaces.
5. **Animations:** the owner asked to switch them off for now → global off switch (section 6).

## 5. Data migration

- The palette is stored in DataStore as the enum name (`palette`). New values `Expressive` and `Glass` are added to `FusionPalette`; old names (`AmoledBlack`, `GraphiteDark`, `Midnight`, `Espresso`) stay valid, so saved choices survive.
- Default for a new install: **Fusion Expressive**. Existing users keep their saved palette until they change it.
- New preference `glass_blur` (Boolean, default true; blur only on Android 12+, a static translucent fill otherwise).
- No TDLib, account or session data is touched by theme changes; switching theme only recomposes.

## 6. Animations off

All decorative motion goes through `LocalMotionFactor` and `LocalSeasonalEffects`. A single switch `KiroXFeatures.ANIMATIONS = false` forces both off app-wide (motion factor 0, no seasonal particles, no list/swipe/FAB animations). Turning it back on is a one-line change.

## 7. Risks

- No pixel reference: colours come from `theme-tokens.json` (marked as not measured).
- Glass blur: `Modifier.blur` works on Android 12+ only and blurs the element itself, so it is used for backgrounds only; panels use translucency (no backdrop blur available in Compose without heavy custom code).
- Root navigation change may surprise users of the old layout; nothing is removed (section 2).
- Device-only behaviour (calls, push, background, PiP) stays NOT TESTED.

## 8. Plan (B–H), approved by the owner in advance

B theme + tokens + persistence + blur fallback → C dock, chats, contacts/profile routes → D conversation look → E profile/settings → F Stories tab → G calls look + mini call chip → H auth/accounts look, appearance studio, regression, screenshots of both themes.
