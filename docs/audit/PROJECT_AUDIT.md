# KiroX — технический аудит проекта

Дата: 2026-10-24 · Ветка: `main` (`cd221e9`) + незакоммиченные изменения в `TelegramAuthScreens.kt`, `PhoneLoginScreen.kt`, `PhoneNumberEngineTest.kt` (не трогались).

Обозначения: **[П]** — подтверждено чтением кода / запуском; **[?]** — предположение, требует проверки на устройстве.
Критичность: 🔴 критично · 🟠 высокая · 🟡 средняя · ⚪ низкая.

## 1. Сборка и проверки (выполнено)

| Проверка | Результат |
|---|---|
| `gradlew` с системной Java | ❌ В `PATH` Java 8; AGP 8.13.2 требует JVM ≥ 11/17. Сборка возможна только с `JAVA_HOME` = JBR Android Studio. |
| `assembleDebug` (JBR) | ✅ `app-debug.apk` ≈ 48.8 МБ |
| `testDebugUnitTest` | ✅ 12 тестов, 0 падений (DemoAuth 2, DemoSeed 2, DemoChatRepository 2, PhoneNumberEngine 6) |
| `lintDebug` | ❌ 2 ошибки, 22 предупреждения (см. ниже) |
| `connectedAndroidTest` | не запускался (нет подключённого устройства в сессии) |
| Реальная авторизация | не выполнялась (по правилам аудита); `kirox.properties` отсутствует → приложение стартует в состоянии `Blocked` |

Lint, ошибки:
1. `res/values/styles.xml:9` — `android:windowLightNavigationBar` требует API 27, `minSdk = 23` **[П]** 🟡. Решение: перенести в `values-v27/styles.xml`.
2. `TdApi.java` слишком большой для lint (5.4 МБ) **[П]** ⚪. Решение: `lint { ignore/excluded }` для `org.drinkless` или системное свойство `idea.max.intellisense.filesize`.

Lint, важные предупреждения: `StaticFieldLeak` (`DemoRepository.kt:114`, ложноположительное — хранится `applicationContext`), `ConfigurationScreenWidthHeight` (`ChatScreen.kt:192,238`), `MissingApplicationIcon`, устаревшие зависимости (10× `GradleDependency`).

## 2. Структура и стек

- Один модуль `:app`, ~3 470 строк Kotlin + сгенерированные `org.drinkless.tdlib.Client/TdApi` (161 тыс. строк Java).
- Kotlin 2.2.10, AGP 8.13.2, Compose BOM 2025.09.01, Room 2.7.2 (KSP), DataStore 1.1.7, libphonenumber 9.0.41.
- `compileSdk/targetSdk 36`, `minSdk 23`. Single-Activity (`MainActivity`) + Jetpack Compose. Без Fragment, без Navigation Compose, без DI (Hilt/Koin).
- Нативная TDLib: только `jniLibs/arm64-v8a/libtdjni.so` (34.5 МБ), TDLib **1.8.69**, commit `ea3f69c` (локальный checkout `third_party/td`, в `.gitignore`).
- Секреты: `API_ID/API_HASH` читаются из `kirox.properties` / env → `BuildConfig` **[П]**. Файл в `.gitignore`. В репозитории значений нет.

## 3. Архитектура: что есть фактически

```
MainActivity ─ KiroXApp (ручная навигация enum Destination + AnimatedContent)
 ├─ TelegramViewModel ─ TdlibClientManager (TDLib Client, состояние, маппинг) ─ TelegramSessionManager (пути)
 │    ├─ TelegramAuthRepository / TelegramChatRepository (тонкие прокси StateFlow)
 │    └─ UI: PhoneLoginScreen → TelegramAuthStepScreen → TelegramMainScreen (только список чатов)
 └─ DEMO: DemoAuth → DemoCodeScreen → DemoMainScreen(DemoViewModel ─ DemoRepository ─ Room kirox_demo.db + DataStore)
                                        └─ DemoChatScreen (ChatScreen.kt)
```

### 3.1 Реально работающие компоненты [П по коду]
- Ввод номера: `PhoneNumberEngine`, `CountryResolver`, `PhoneEntryState`, `CountryPickerScreen` (libphonenumber, покрыто тестами).
- Конечный автомат авторизации TDLib (`TdlibClientManager.applyAuthorizationState`): телефон, код, 2FA-пароль, email, email-код, регистрация, подтверждение на другом устройстве (только текст ссылки, без QR).
- Получение списка чатов Main/Archive через `UpdateNewChat`/`UpdateChatPosition`/`UpdateChatLastMessage`, отображение в `TelegramMainScreen`.
- Профиль `GetMe` загружается, но **нигде не отображается** в реальном UI.
- Сохранение сессии между перезапусками — средствами TDLib (`useFileDatabase/useMessageDatabase`), путь `filesDir/tdlib/session-primary` **[П код; ? устройство]**.
- Весь DEMO-режим (Room + DataStore) — функционален локально.

### 3.2 Заглушки
- В реальном режиме: вкладки «Контакты / Настройки / Профиль», поиск, «прочитать всё», «создать» — показывают тост «будет подключено» (`TelegramMainScreen.kt`).
- Открытие реального чата отсутствует — `RealChatRow` не кликабелен.
- `TdLibChatAdapter` (`ChatModels.kt`) — интерфейс без реализаций и без использования.
- В DEMO: истории, создание группы/канала, поиск, все пункты настроек кроме «Кастомизации», редактирование профиля, медиа профиля — тосты «в разработке».

### 3.3 Фиктивные данные
`DemoSeed` (2 аккаунта, 7 контактов, 9 чатов, 18 сообщений, 4 звонка), захардкоженные истории в `DemoMainScreen.Stories`, жёстко прописанные «Кирилл», «@username», «Создаю KiroX» в `CenterIdentity`/`ProfileScreen` (не из БД!). Подробно — `DEMO_REMOVAL_PLAN.md`.

### 3.4 Дублирование / мёртвый код [П]
| Файл | Статус |
|---|---|
| `ui/start/StartScreen.kt` | не используется (старый экран входа) |
| `ui/demo/DemoHomeScreen.kt` | не используется (заменён `DemoMainScreen`) |
| `ui/splash/AnimatedSplashScreen.kt` | не используется (используется `AnimatedSplashScreen2`) |
| `core/demo/DemoContent.kt` | пустой файл-комментарий |
| `DemoMainScreen.Tile` | приватная функция без вызовов |
| `TelegramMainScreen` vs `DemoMainScreen.ChatsScreen` | две независимые реализации одного экрана (шапка, вкладки, строка чата, нижняя панель) с расходящимися размерами |
| Цвета `White/Secondary/Divider` | переобъявлены в 5 файлах с разными значениями |

## 4. Проблемы (подробно)

### A1 🔴 [П] Жизненный цикл TDLib-клиента привязан к ViewModel
- **Где:** `telegram/TelegramViewModel.kt` (`TdlibClientManager(application)` в конструкторе), `TdlibClientManager.kt`.
- **Причина:** нет `onCleared()` → `client.close()`/`scope.cancel()`. `CoroutineScope` менеджера никогда не отменяется. При пересоздании ViewModel (выход из Activity кнопкой «Назад» и повторный вход в том же процессе) создаётся второй `Client` на ту же `databaseDirectory`.
- **Влияние:** TDLib не допускает два инстанса на одну БД → ошибка/зависание авторизации **[? проверить сценарий Back → повторный запуск]**, утечка нативного потока и памяти.
- **Решение:** единый application-scoped `TelegramClient` (singleton через `Application`/DI), ViewModel лишь подписывается.
- **Риск изменения:** средний. **Тесты:** инструментальный тест повторного старта Activity.

### A2 🔴 [П] Сессия Telegram доступна для бэкапа и не шифруется
- **Где:** `AndroidManifest.xml` (`android:allowBackup="true"`), `TdlibClientManager.sendParameters` (`databaseEncryptionKey = byteArrayOf()`).
- **Влияние:** `filesDir/tdlib` (ключ авторизации, кеш сообщений) попадает в Auto Backup / `adb backup` → возможен перенос сессии на другое устройство.
- **Решение:** `allowBackup=false` или `dataExtractionRules`/`fullBackupContent` с исключением `tdlib/`; ключ шифрования БД генерировать и хранить через Android Keystore (обёрнутый ключ в DataStore/файле).
- **Риск:** средний (смена ключа на существующей БД требует `SetDatabaseEncryptionKey`). **Тесты:** проверка перезапуска с зашифрованной БД.

### A3 🟠 [П] После выхода из аккаунта клиент не пересоздаётся
- **Где:** `applyAuthorizationState` → `AuthorizationStateClosed`: `client = null`, состояние `WaitPhoneNumber`, но `start()` не вызывается.
- **Влияние:** после logout (когда он появится) ввод номера даст «TDLib ещё не готова».
- **Решение:** при `Closed` создавать новый `Client` (или перейти в `Initializing` и вызвать `start()`).

### A4 🟠 [П] `busy`-флаг и гонки авторизации
- `sendAuth` молча отбрасывает запрос, если `busy`; `applyAuthorizationState` сбрасывает `busy=false` при любом обновлении, пока ответ на запрос ещё не пришёл. `SetTdlibParameters` идёт через тот же `busy`.
- **Влияние:** редкие «нажал — ничего не произошло». **Решение:** очередь запросов/отдельный флаг на запрос; параметры отправлять напрямую.

### A5 🟠 [П] Папки Telegram невозможны в текущей модели
- `sameList()` сравнивает только `javaClass` → все `ChatListFolder(id)` считаются одним списком; `UpdateChatFolders` не обрабатывается.

### A6 🟠 [П] Неполная обработка обновлений чатов
Не обрабатываются: `UpdateChatReadOutbox`, `UpdateChatDraftMessage`, `UpdateChatNotificationSettings`, `UpdateChatIsMarkedAsUnread`, `UpdateChatUnreadMentionCount`, `UpdateUser`, `UpdateUserStatus`, `UpdateFile`, `UpdateChatAddedToList/RemovedFromList`, `UpdateConnectionState`, `UpdateChatAction`. Аватары берутся только если файл уже скачан (`local.path`), `DownloadFile` не вызывается → **в реальном режиме аватаров не будет** [П].

### A7 🟠 [П] Нет пагинации списка чатов
`LoadChats(…, 100)` вызывается один раз. Пользователи с >100 чатами увидят не всё.

### A8 🟡 [П] Публикация всего списка на каждое обновление
`publishChats()` при каждом событии сортирует все чаты дважды (main+archive) и создаёт новые списки. Изменяемые `TdApi.Chat` хранятся в `ConcurrentHashMap` и мутируются. При активных чатах — лишние рекомпозиции всего `LazyColumn`. **Решение:** debounce/conflate (~16–50 мс), иммутабельные модели, `distinctUntilChanged`.

### A9 🟡 [П] Навигация: ручной enum, состояние не переживает пересоздание
`KiroXApp.destination` — `remember`, не `rememberSaveable`; нет back stack. Системная кнопка «Назад» на `TelegramMainScreen`/`TelegramAuth` закрывает Activity (что вместе с A1 даёт повторное создание клиента). DEMO-переходы блокируют реакцию на смену `telegramAuth` (`if (destination == DemoCode || DemoHome || Countries) return`).

### A10 🟡 [П] `DemoChatScreen` пересоздаёт Flow на каждой рекомпозиции
`demoViewModel.messages(chatId).collectAsStateWithLifecycle(...)` — `messages()` вызывается в теле composable без `remember` → новая подписка Room при каждой рекомпозиции. Плюс `storedMessages.firstOrNull{…}` внутри `itemsIndexed` → O(n²).

### A11 🟡 [П] Потеря позиции прокрутки при открытии чата (DEMO)
`DemoMainScreen`: `openChatId?.let { DemoChatScreen(...); return }` удаляет весь Scaffold из композиции; `LazyListState` списка не поднят → после «Назад» список в начале.

### A12 🟡 [П] Только ABI arm64-v8a
Устройства armeabi-v7a и эмуляторы x86_64 → `UnsatisfiedLinkError`, перехватывается и показывает `Blocked`. Для разработки на эмуляторе нужна x86_64-сборка.

### A13 🟡 [П] Release-конфигурация не готова
`isMinifyEnabled=false`, `proguard-rules.pro` пуст (при включении R8 нужны keep-правила для `org.drinkless.tdlib.**` из-за JNI), нет иконки приложения, нет подписи.

### A14 ⚪ [П] Логи TDLib отключены, но не устанавливается `SetLogVerbosityLevel`
`Client.setLogMessageHandler(1, …)` подавляет только сообщения уровня ≤1 в обработчике; вербозность по умолчанию пишет в logcat **[? проверить logcat]**. Рекомендуется `Client.execute(SetLogVerbosityLevel(1))` до `create`.

### A15 ⚪ [П] Тексты ошибок
`humanError` превращает коды вроде `FLOOD_WAIT_x` в «Flood wait 30» — нужно локализованное сопоставление (FLOOD_WAIT, PHONE_NUMBER_BANNED, SESSION_PASSWORD_NEEDED и т.п.).

## 5. Локальное хранилище
- Room `kirox_demo.db` и DataStore `kirox_demo_settings` — **только DEMO**. Реальных настроек KiroX (тема, вкладка, анимации) в отдельном хранилище нет.
- `fallbackToDestructiveMigrationOnDowngrade()`, `exportSchema=false` — допустимо для DEMO, не годится для будущих production-таблиц.
- Секретов (паролей, кодов) в хранилищах нет [П]; код/пароль живут только в Compose-state (`remember`, не `rememberSaveable`) — не попадают в Bundle. ✅

## 6. Тема, анимации, жизненный цикл
- Нет `MaterialTheme`/своей темы, нет токенов: цвета и размеры захардкожены в каждом файле. AMOLED Black — де-факто единственная тема; **Graphite Dark отсутствует** [П].
- Анимации: `AnimatedContent` навигации 280 мс, fade вкладок 160/120 мс, бесконечные частицы в настройках (только при видимости экрана — ок), анимированный сплэш.
- `enableEdgeToEdge()` включён; `windowSoftInputMode=adjustResize`. Insets обрабатываются вручную и непоследовательно (см. `BOTTOM_NAVIGATION_AUDIT.md`).
- Жизненный цикл: используется `collectAsStateWithLifecycle` ✅, но TDLib не реагирует на lifecycle/сеть (нет `SetNetworkType`, нет foreground-сервиса/push).

## 7. Тесты
- 4 unit-класса (12 тестов), 2 инструментальных (`TdlibNativeLoadTest`, `DemoDatabaseTest`).
- 6 из 12 unit-тестов покрывают DEMO. Логика `TdlibClientManager` (маппинг, сортировка, автомат авторизации) тестами не покрыта и не тестируема в текущем виде (жёсткая зависимость от `Client`).

## 8. Зависимости и лицензии
- Все зависимости — Apache 2.0 (AndroidX, libphonenumber), TDLib — BSL-1.0 (`licenses/TDLib-BSL-1.0.txt`), шрифт Pacifico — OFL (`assets/licenses`). `THIRD_PARTY_NOTICES.md` корректно разделяет лицензии.
- ⚠ Нужно проверить, с чем слинкована `libtdjni.so` (OpenSSL, zlib) и добавить их уведомления — уже отмечено в `THIRD_PARTY_NOTICES.md` **[?]**.
- Лицензии не изменялись.

## 9. Что можно сохранить без переработки
`phone/*` полностью; `CountryPickerScreen`; `TelegramAuthState` и экран шагов авторизации (с правками A3/A4); `KiroXIcons`; `AnimatedSplashScreen2`; UI-модели чата `ChatModels.kt` (`MessageUi`, `ChatPeerUi`, `ChatAppearance`) и визуальные компоненты `ChatScreen.kt` (после отвязки от `DemoViewModel`); визуальные компоненты `DemoMainScreen` (ScreenHeader, FlatTabs, ChatRow, SearchBox, Chip, SettingRow, Profile-блоки) — как основа дизайн-системы.

## 10. Что изменить до полноценного подключения TDLib
1. Application-scoped `TelegramClient` + `onCleared`-безопасность (A1).
2. Безопасность хранения (A2).
3. Разделить `TdlibClientManager` на: `TdClient` (send/updates как Flow), `AuthRepository`, `ChatListRepository`, `UserRepository`, `FileRepository`, `MessageRepository`.
4. Навигация с back stack (Navigation Compose или собственный стек) с отдельным графом auth/main.
5. Общие UI-компоненты и токены (вынести из `DemoMainScreen`), отвязка экранов от `Demo*`-типов через UI-модели.
