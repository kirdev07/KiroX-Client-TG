# KiroX Fusion UI — approved design branch

**Status:** visual direction approved by project owner on 2026-10-29. **Implementation status:** design handoff only; no Android UI or TDLib code changed here.

## Approved visual directions

1. **Fusion Expressive** — purple/violet highlights, rich dark Material 3 Expressive surfaces, compact rounded modules, rounded purple outgoing bubbles, playful but measured animation.
2. **Fusion Glass** — midnight-blue atmospheric backgrounds, translucent glass surfaces with optional real blur, blue/violet highlights, the **same** screen layouts and actions.

The intended visual composition is the user-approved **seven-screen reference board** discussed in ChatGPT on 2026-10-29, with **7 screens in each of 2 themes**:

| # | Screen | Both themes |
|---|---|---|
| 1 | Chat list, stories row, search, categories, bottom navigation | Approved |
| 2 | Conversation, media, audio, composer | Approved |
| 3 | Profile/account + grouped settings | Approved |
| 4 | Calls history/list | Approved |
| 5 | Active audio/video call screen layout | Approved visually; live functionality must be verified |
| 6 | Settings overview | Approved |
| 7 | Theme customization and appearance controls | Approved |

**Preserve the layout** from the approved board: do not rearrange header actions, stories row, filter pills, chat rows, message composer, bottom navigation or settings cards without a separate review.

### Source-of-truth and limitations

The owner-approved raster image lives in the ChatGPT conversation; it is **not yet committed as a binary asset here**. Ask the owner to attach that exact approved image when running Claude Code, rather than substituting another AI-generated screenshot. This folder's preview is a developer-friendly schematic, **not** a replacement for the approved artwork.

- [Design contract](DESIGN_SPEC.md)
- [Claude Code task brief](CLAUDE_HANDOFF.md)
- [Theme tokens](theme-tokens.json)
- [Additional call screens — visually approved](CALLS_EXTRA_DRAFT.md)
- [Stories screens — draft for owner review](STORIES_DESIGN_DRAFT.md)
- [Local interactive preview](preview.html)

Existing materials in parent directory are **older references** and conflict with the newly approved primary-navigation layout. This branch does not silently rewrite old specs; new design decisions are documented below.

## Scope and rollout

- This branch is a separate visual redesign track. **main remains unchanged.**
- The current working client uses Kotlin + Compose + Material 3 + TDLib.
- Actual production UI work belongs in **small staged commits** on this branch after reviewing present-day source. **Never** replace working TDLib code with fake/demo data.
- Keep missing functionality visibly unavailable rather than pretending success.
- Later design discussion: incoming calls, full video calls, detailed Stories flows, contact details, media viewers, auth states, empty/error/offline. The approved board already covers the general **calls list** and **active call** appearance, not every call state.

## Reviewer checklist

- [ ] Reference screenshot attached for pixel-level checks
- [ ] 14 approved theme×screen combinations visually compared on device
- [ ] Navigation conflicts with older KiroX specs explicitly mapped
- [ ] Theme switching preserves navigation and account/Telegram state
- [ ] Blur disabled gracefully on low-end devices or when reduced motion is active
- [ ] Accessibility: touch >= 48dp, labels, contrast, font scaling
- [ ] TDLib actions/data: real, or clearly unavailable
- [ ] Compilation + screenshot checks + test report included
- [ ] Owner approves each implementation stage before merge

## Согласованные дополнительные экраны — 29 октября 2026

- [Авторизация и управление аккаунтами — 16 визуально утверждённых макетов](AUTH_ACCOUNTS_DRAFT.md). Splash, приветствие, телефон, код, 2FA, список аккаунтов, добавление и переключение в Expressive и Glass. Реализация ещё не выполнена.
