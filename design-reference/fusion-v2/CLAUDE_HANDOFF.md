# Claude Code — KiroX Fusion Expressive / Fusion Glass handoff

**Read this before touching Android code.**

Repository: https://github.com/kirdev07/KiroX-Client-TG_Kirdev.07
Branch: `design/kirox-fusion-expressive-glass` (do not write to main).
Status: owner **approved visual designs**, not a blanket approval to rewrite routing/Telegram integration.

## Preparation

1. Check out the named branch and examine its current HEAD; confirm the checked-out branch before writing.
2. Read `design-reference/fusion-v2/README.md`, `DESIGN_SPEC.md`, `theme-tokens.json`, and `preview.html`.
3. **Also load the exact composite reference image approved by the owner in ChatGPT on 2026-10-29.** It contains two rows of seven Android screenshots: purple Expressive first, blue Glass second. This image is **not stored in this branch**; request it if missing. Do NOT use a new generated concept to override it.
4. Inspect actual source and route logic before editing: `app/src/main/java/com/kirox/app/ui/theme/FusionTheme.kt`, `ui/components/FusionBottomBar.kt`, `ui/home/TelegramMainScreen.kt`, `ui/chat/TelegramChatScreen.kt`, `ui/home/SettingsScreens.kt`, `ui/KiroXApp.kt`, settings persistence and the TDLib managers. Inspect other sources as needed. Old docs in `design-reference/` are a historical baseline, not current owner-approved visual composition.
5. Make an **inventory of what is already implemented**, noting present vs missing capabilities. Current GitHub code already includes theme palettes and a pill dock — avoid reinventing these.

## Critical functional question before changing routing

- Owner's visual target has bottom bar: Chats / Calls / Stories / Settings.
- Existing source had Contacts / Chats / Settings / Profile.
- Show the owner an explicit route mapping which retains **contacts**, **own profile**, **account switching** and user settings; do not silently make old features unreachable.
- Calls / Stories must never be populated with fictional data. If real TDLib feature isn't ready, use genuine empty / unsupported states.
- Visual "KiroX Premium" labels in generated imagery are not permission to claim premium. Render official state only from TDLib; do not invent subscriptions, badges, calls or active sessions.

## Desired code approach

- Keep existing Kotlin Compose and Material 3 ecosystem, adopt expressive shapes/typography/motion only where compatible with installed Compose Material3 version.
- One reusable screen/state layer for all themes; don't duplicate feature state, repositories, navigation or TDLib bindings.
- Extend existing `FusionTheme` with **Fusion Expressive** + **Fusion Glass** style configuration; minimize regressions to any existing palettes.
- Read the proposed theme token JSON as a design guide, not as a runtime parser dependency.
- Provide opaque fallback to Glass blur on unsupported/low-tier phones and when accessibility/energy settings reduce effects. Full-viewport blur should not duplicate and reprocess all image content every frame.
- Use stable keys / lazy lists, preserve chat scroll positions, account scoping, drafts, Android Back, keyboard and insets.
- Don't copy Yougram code or artwork. User uses Yougram **only as design quality reference**; implementation, assets, text and architecture must be original and license-clean.
- Screenshot reference is the approved composition, **do not rearrange key widgets**.

## Work in small, reviewable checkpoints

1. **Stage A:** screenshot/code audit + route mapping + changed-file plan. Report differences from each approved screen. If the owner wants to proceed, implement the next stage.
2. **Stage B:** theme foundations and persistence, optional glass layer with fallback.
3. **Stage C:** approved chat list + dock (after route mapping approval).
4. **Stage D:** approved conversation and composer.
5. **Stage E:** approved profile and settings.
6. **Stage F:** approved call list and active call styling where actual VoIP state exists.
7. **Stage G:** approved appearance customization and previews.
8. **Stage H:** QA, screen capture comparison, build verification and doc updates.

**After each stage:** provide commit SHA, changed files, APK location when built, visual screenshots from real device/emulator, tests PASSED/FAILED/NOT TESTED, performance caveats and user-visible gaps. Stop after a stage for user approval. No merge to main without explicit approval.

## Useful commands

~~~shell
git fetch origin
git switch design/kirox-fusion-expressive-glass
git status
./gradlew testDebugUnitTest assembleDebug lintDebug
~~~

Run Gradle only when Android SDK/NDK and JDK are installed; report NOT TESTED otherwise. Never fabricate successful builds or fake Telegram network tests.

## Preview disclaimer

`preview.html` is a small **standalone visual schematic** for discussing the two skin systems; it is **not a production Compose implementation** and does not prove TDLib feature availability. Compare against actual owner-approved raster for pixel decisions.

## Future design scope

Once these seven core screens in two themes are reviewed, separately design/approve incoming-call and real video-call states, stories viewer/editor, media gallery, contacts, search, auth and empty/error states. Their final layouts are not pre-approved by the owner here.
