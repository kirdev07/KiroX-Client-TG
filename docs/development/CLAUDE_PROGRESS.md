# Прогресс Claude Code

**При возобновлении:** прочитать этот файл, затем `git status` и `git log --oneline -5` на ветке `audit/beta-stabilization`.

## Текущий этап
**B — отчёт готов, жду разрешения владельца на исправления.**

## Сделано
- Этап A: аудит TDLib, звонков, push, безопасности, обновлений, UI (2026-10-25).
- Этап B: `docs/audit/FULL_AUDIT.md` (0 Critical, 3 High, 9 Medium, 6 Low).
- Список 21 пункта восстановлен полностью: `docs/development/REMAINING_21_TASKS.md`.
- Автотесты на ветке: unit 35/35 PASS, assembleDebug PASS, lintDebug 0 ошибок / 40 предупреждений.
- `assembleReleaseCheck` — FAIL из-за google-services для `com.kirox.app.check` (M9).

## Блокеры
- H2: Telegram не доставляет push неофициальному клиенту — нужен выбор подхода (повторная проверка Web Push или постоянное соединение в фоне).
- Пункт 7 «Возможности KiroX» — нужно описание от владельца.

## Следующий шаг
После разрешения: M9 → M1 → M2 → H1 (уведомления о сообщениях) → H3 (энергопотребление анимации) → P1-пункты 5, 17, 18.

## Изменённые файлы на этом этапе
- `docs/audit/FULL_AUDIT.md` (новый)
- `docs/development/REMAINING_21_TASKS.md` (новый)
- `docs/development/CLAUDE_PROGRESS.md` (новый)
