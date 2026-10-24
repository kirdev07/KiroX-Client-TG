# План безопасного удаления DEMO

> **Статус (2026-10-24): выполнено (этап F).** Реальный режим проверен владельцем на устройстве. DEMO-код, DEMO-тесты, мёртвые экраны, Room и KSP удалены; общие компоненты чата оставлены в `ui/chat/ChatScreen.kt`; при запуске удаляются `kirox_demo.db` и `kirox_demo_settings.preferences_pb`. Во flavor `demo` DEMO не переносился — он был нужен только как прототип. В APK нет классов `com.kirox.app.core.demo` и `androidx.room`.

Ничего не удалено. План выполняется только после того, как реальные сценарии из этапов B–D (`MIGRATION_ROADMAP.md`) подтверждены на устройстве.

## 1. Инвентаризация DEMO-зависимостей [П]

**Production-код: 13 файлов, ~1 290 строк, плюс 2 точки входа в общем коде.**

| # | Файл | Что содержит | Зависит от |
|---|---|---|---|
| 1 | `core/demo/DemoEntities.kt` | 7 Room-сущностей + `DemoSettings` | Room |
| 2 | `core/demo/DemoDao.kt` | DAO, `initializeOnce()` сидирование | 1, 5 |
| 3 | `core/demo/DemoDatabase.kt` | `kirox_demo.db`, singleton | 1, 2 |
| 4 | `core/demo/DemoRepository.kt` | репозиторий + DataStore `kirox_demo_settings` | 1–3, 5 |
| 5 | `core/demo/DemoSeed.kt` | DEMO-аккаунты, контакты, чаты, сообщения, звонки | 1 |
| 6 | `core/demo/DemoViewModel.kt` | VM | 4 |
| 7 | `core/demo/DemoContent.kt` | пустой (комментарий) | — |
| 8 | `demo/DemoAuth.kt` | DEMO-код `12345` | — |
| 9 | `ui/demo/DemoCodeScreen.kt` | экран ввода DEMO-кода, `DemoBadge`, `ExitDemoButton` | `ui/start` |
| 10 | `ui/demo/DemoHomeScreen.kt` | **мёртвый код** | 9 |
| 11 | `ui/home/DemoMainScreen.kt` | весь DEMO-интерфейс: чаты, истории, контакты, звонки, настройки, профиль, аккаунты, нижняя панель | 1, 6, 12 |
| 12 | `ui/chat/ChatScreen.kt` → `DemoChatScreen` | экран переписки на `DemoViewModel`/`DemoMessage` | 1, 6, `ChatModels.kt` |
| 13 | `ui/start/StartScreen.kt` | **мёртвый код** (старый вход) | — |

Точки входа в общем коде:
- `ui/KiroXApp.kt` — `Destination.DemoCode`, `Destination.DemoHome`, импорты `DemoAuth`, `DemoCodeScreen`, `DemoMainScreen`, условие в `LaunchedEffect(telegramAuth)`.
- `ui/start/PhoneLoginScreen.kt` — параметр `onDemoMode`, `DemoButton`.

Захардкоженные фиктивные данные вне `DemoSeed` (`DemoMainScreen.kt`): `Stories` (6 фиктивных историй), `CenterIdentity` («Кирилл», «@username», emoji-статус), `ProfileScreen` («Создаю KiroX», «Дата рождения: Не указана»), `AccountsSheet` («DEMO · изолированные данные»), бейдж «ДЕМО» в `ScreenHeader`.

Хранилище на устройстве: `databases/kirox_demo.db*`, `datastore/kirox_demo_settings.preferences_pb`.

Тесты: `test/chat/DemoChatRepositoryTest.kt`, `test/core/demo/DemoSeedTest.kt`, `test/demo/DemoAuthTest.kt`, `androidTest/core/demo/DemoDatabaseTest.kt`.

Зависимости Gradle, нужные только DEMO: Room (runtime, ktx, compiler, testing) и KSP. DataStore понадобится для реальных настроек — оставить.

## 2. Карта зависимостей

```
PhoneLoginScreen.onDemoMode ─► KiroXApp.Destination.DemoCode ─► DemoCodeScreen ─► DemoAuth
                                         └► Destination.DemoHome ─► DemoMainScreen ─► DemoViewModel ─► DemoRepository ─► DemoDatabase/DemoDao ─► DemoSeed/DemoEntities
                                                                         └► DemoChatScreen (ChatScreen.kt) ─► DemoViewModel, DemoMessage
Мёртвые: DemoHomeScreen, StartScreen, DemoContent, AnimatedSplashScreen(v1)
```

## 3. Последовательность

**Шаг 0 (можно сразу, безопасно):** удалить мёртвый код — `DemoHomeScreen.kt`, `StartScreen.kt`, `DemoContent.kt`, `AnimatedSplashScreen.kt`, `Tile`. Проверка: сборка + тесты.

**Шаг 1 — изоляция (до удаления):**
1. Вынести визуальные компоненты из `DemoMainScreen.kt` и `ChatScreen.kt` в общий `ui/components` (без `Demo*`-типов; параметры — UI-модели).
2. `DemoChatScreen` → разделить на stateless `ChatScreen(state: ChatUiState, actions)` + DEMO-адаптер. Реальный `MessageRepository` подключается к тому же stateless-экрану.
3. Перенести весь DEMO-код в source set `src/demo/` (product flavor `demo`) или `src/debug/`. В `main` оставить интерфейс-точку входа (`DemoEntry` no-op). Production (`release`/flavor `prod`) не содержит DEMO-классов и Room.

**Шаг 2 — гейт:** кнопка DEMO скрыта в production-сборке (`BuildConfig.DEMO_ENABLED`). Проверить, что при `Ready` из любого экрана выполняется переход к реальным чатам.

**Шаг 3 — критерии готовности реального режима** (все выполнены):
- вход/выход/повторный запуск на реальном аккаунте;
- список чатов, открытие чата, отправка/получение;
- контакты, профиль, настройки реального аккаунта на общих компонентах;
- нет ни одной ссылки `com.kirox.app.core.demo` из `src/main`.

**Шаг 4 — удаление из production:**
1. Удалить `onDemoMode`/`DemoButton`, `Destination.Demo*`.
2. Удалить (или оставить только во flavor `demo`) файлы 1–12, тесты DEMO.
3. Удалить Room/KSP из production-зависимостей, если не используются иначе.
4. Однократная очистка на устройстве при обновлении: `context.deleteDatabase("kirox_demo.db")` и удаление `datastore/kirox_demo_settings.preferences_pb` (неопасно: только локальные фиктивные данные; Telegram-данные не затрагиваются).

**Шаг 5 — проверка:**
- `grep -r "Demo\|ДЕМО\|DEMO" app/src/main` → пусто (кроме, возможно, миграции очистки);
- чистая установка: неавторизованный → экран входа, авторизованный → свои чаты;
- обновление поверх старой версии: DEMO-файлы удалены, сессия Telegram сохранена;
- в APK (`apkanalyzer dex packages`) нет `com.kirox.app.core.demo`.

## 4. Что оставить для автотестов
`DemoSeed`-подобные фикстуры переносятся в `src/test`/`src/androidTest` как фейковые реализации репозиториев (`FakeChatRepository` и т.п.) для UI-тестов и превью. Они не попадают в production.
