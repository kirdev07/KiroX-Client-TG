# Состояние интеграции TDLib

**[П]** — подтверждено кодом/сборкой, **[?]** — требует проверки на устройстве.

## 1. Библиотека
| Пункт | Состояние |
|---|---|
| Подключение | Java-обёртка `org.drinkless.tdlib.Client` + `TdApi` в `app/src/main/java`, нативная `libtdjni.so` в `jniLibs/arm64-v8a` [П] |
| Версия | TDLib **1.8.69**, commit `ea3f69ce` (совпадает в `TdApi.GIT_COMMIT_HASH` и `third_party/td`) [П] |
| ABI | только arm64-v8a (34.5 МБ, несжатая) [П] |
| Совместимость API | классы `TdApi` соответствуют версии; используемые методы присутствуют [П] |
| Тест загрузки | `TdlibNativeLoadTest` (инструментальный) — не запускался в этой сессии |
| Сборка из исходников | процесс не задокументирован в репозитории (как получен `.so`) — нужен скрипт/README для воспроизводимости |

## 2. Конфигурация
- `API_ID/API_HASH` → `BuildConfig` из `kirox.properties` или env [П]. Значения в отчётах не раскрываются; в репозитории их нет. Файл отсутствует локально → приложение уходит в `Blocked("API_ID и API_HASH не настроены…")`.
- Неизбежное ограничение: значения в `BuildConfig` извлекаемы из APK (так у всех клиентов); это допустимо, но ключ не должен попадать в git/CI-логи.
- `useTestDc=false` захардкожено; нет debug-переключателя на тестовые DC.

## 3. Матрица функциональности

| Функция | Статус | Где / комментарий |
|---|---|---|
| Инициализация клиента | 🟡 частично | `TdlibClientManager.start()`; клиент принадлежит ViewModel, не закрывается (см. PROJECT_AUDIT A1) |
| `SetTdlibParameters` | ✅ | без шифрования БД (A2) |
| Авторизация по телефону + код | ✅ | `submitPhone/submitCode` |
| Повторная отправка кода | ✅ | `ResendAuthenticationCode` + незакоммиченный таймер в UI |
| 2FA (облачный пароль) | ✅ ввод | нет восстановления пароля (`RequestAuthenticationPasswordRecovery`) |
| Email / email-код | ✅ | нет входа через Google/Apple ID для email |
| Регистрация | ✅ | без показа Terms of Service (`termsOfService` из `WaitRegistration` игнорируется) — юридически нужно показать |
| QR-вход (другое устройство) | 🟡 | отображается только ссылка текстом; запрос `RequestQrCodeAuthentication` не вызывается — вход недостижим из UI |
| Хранение авторизации | ✅ средствами TDLib | `filesDir/tdlib/session-primary` [? проверить перезапуск] |
| Logout / завершение сессии | ❌ | нет `LogOut`; после `Closed` клиент не пересоздаётся (A3) |
| Несколько аккаунтов | ❌ | одна фиксированная директория `session-primary`; мультиаккаунт есть только в DEMO |
| Состояние соединения | ❌ | `UpdateConnectionState` не обрабатывается; нет индикатора «Соединение…» |
| Тип сети / lifecycle | ❌ | нет `SetNetworkType`, нет реакции на фон |
| Ошибки сети | 🟡 | только `humanError` для 3 кодов и code≥500 |
| Обновления | 🟡 | 7 типов update обработано (auth, newChat, title, photo, lastMessage, position, readInbox) |
| Список чатов Main/Archive | 🟡 | первые 100, без пагинации |
| Папки | ❌ | `ChatListFolder` не различаются (A5), `UpdateChatFolders` нет |
| Закреплённые чаты | ✅ флаг `isPinned` из позиции |
| Профиль (GetMe) | 🟡 | загружается в `profile`, но не показывается; фото не скачивается |
| Контакты / пользователи | ❌ | нет `GetContacts`, `UpdateUser` |
| История звонков | ❌ | В TDLib нет отдельного «call log»: история строится через `SearchCallMessages` / `MessageCall`. Не реализовано |
| Звонки (VoIP) | ❌ | `CreateCall` в API есть, но реальный звонок требует libtgvoip/tgcalls + WebRTC — отдельный крупный компонент. Групповые/видеозвонки **не обещать** |
| Истории | ❌ | API (`GetStory`, `UpdateChatActiveStories`) есть в 1.8.69, не используется |
| Сообщения: история | ❌ | нет `GetChatHistory`/`OpenChat` |
| Отправка / получение | ❌ | нет `SendMessage`, `UpdateNewMessage` |
| Редактирование / удаление / ответы / пересылка | ❌ | |
| Файлы и медиа | ❌ | нет `DownloadFile`/`UpdateFile`; нет загрузчика изображений |
| Локальное хранение | ✅ TDLib DB | message/chat/file DB включены |
| Закрытие/повторный запуск | 🟡 | при убийстве процесса — норма; при пересоздании ViewModel в живом процессе — риск двух клиентов [?] |

Итог: **≈ 15 %** готовности TDLib-слоя (критерий: из 16 ключевых областей матрицы выше ~2 полностью и ~5 частично реализованы, без учёта звонков/VoIP).

## 4. Смешение DEMO и реального аккаунта
- Хранилища разделены физически: DEMO = Room `kirox_demo.db` + DataStore `kirox_demo_settings`; Telegram = `filesDir/tdlib/` [П]. Перекрёстных чтений нет [П].
- **Риск UI-смешения [П]:** кнопка DEMO доступна на экране входа всегда; `KiroXApp` не переключается на реальный аккаунт, пока пользователь в DEMO (ранний `return` в `LaunchedEffect`). Фиктивные данные в реальном UI сейчас не показываются, т.к. `TelegramMainScreen` использует только `TelegramChatItem`.
- **Риск при развитии:** если реальные экраны контактов/звонков/профиля будут собраны путём переиспользования `DemoMainScreen`, `CenterIdentity` покажет захардкоженные «Кирилл/@username» — эти строки не из БД. Удалить до переиспользования.

## 5. Предлагаемая архитектура

```
KiroXApplication
 └─ TelegramClient (singleton, один на аккаунт)           ← единственный владелец org.drinkless Client
     ├─ updates: SharedFlow<TdApi.Update> (буфер, один поток-диспетчер)
     ├─ suspend fun <T> send(fn): T  (suspendCancellableCoroutine, маппинг TdApi.Error → TdException)
     └─ lifecycle: create / close / recreate после Closed
 Репозитории (каждый подписан на updates, хранит иммутабельный StateFlow):
     AuthRepository        — AuthorizationState → TelegramAuthState, logout, QR
     ChatListRepository    — Main/Archive/Folder(id), пагинация LoadChats, conflate
     UserRepository        — кеш User/UserFullInfo, статусы
     MessageRepository     — по chatId: OpenChat/CloseChat, GetChatHistory, Send/Edit/Delete, UpdateNewMessage…
     FileRepository        — DownloadFile с приоритетами, UpdateFile → Flow<path>
     CallsRepository       — SearchCallMessages (история); VoIP — отдельный этап
     ConnectionRepository  — UpdateConnectionState, SetNetworkType
 AccountManager            — список аккаунтов: каталог tdlib/<accountId>/, активный аккаунт в DataStore
 KiroXSettingsRepository   — DataStore: тема, вкладка, анимации (НЕ Telegram-данные)
 SecureKeyStore            — Android Keystore: ключ шифрования БД TDLib на аккаунт
 ViewModel'и экранов       — комбинируют репозитории в UiState; Compose видит только UI-модели
```

Принципы:
1. Все `TdApi.*` остаются в data-слое; UI получает `ChatUi`, `MessageUi`, `ContactUi`.
2. Один диспетчер обновлений (single-thread), репозитории не мутируют общие `TdApi`-объекты — копируют в свои модели.
3. Экраны не создают клиентов и не держат ссылки на `Client`.
4. Секреты: коды и пароли не логируются, не сохраняются, не попадают в `rememberSaveable`; логи TDLib на уровне ≤1 и только в debug.
5. Мультиаккаунт: каждый аккаунт = отдельный `TelegramClient` с отдельной директорией; одновременно активен один (остальные — по требованию для уведомлений, позже).
