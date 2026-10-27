# KiroX — план интеграции недостающих Telegram-функций

**Дата:** 27.10.2026 · **База:** `main` @ `e7d6510` (KiroX 1.0.0-rc.1) · **TDLib:** 1.8.69 (`ea3f69ce`)
**Статус документа:** проект плана. **Изменения в исходниках не начаты и не начнутся до утверждения владельцем.**
Связанный документ: [`TELEGRAM_FEATURE_AUDIT.md`](TELEGRAM_FEATURE_AUDIT.md).

---

## 0. Правила независимой реализации (обязательные)

1. Весь новый код KiroX пишется самостоятельно на Kotlin + Jetpack Compose поверх **существующего** TDLib-слоя KiroX (`org.drinkless.tdlib.Client` + `TelegramClient`).
2. Yougram — **только функциональный ориентир**: какие функции есть и как они ведут себя для пользователя. Аудит Yougram закончен этим документом; при реализации его исходники **не открываются** и как образец не используются.
3. Не переносятся: классы, функции, алгоритмы, архитектурные фрагменты, UI-компоненты, ресурсы, иконки, названия, зависимости, нативные модули и сборочные патчи Yougram.
4. Источники для реализации: официальная документация TDLib (`td_api.tl` / `TdApi` из репозитория KiroX), Android SDK / Jetpack, документация выбранных библиотек с совместимой лицензией.
5. У Yougram **нет лицензии** (нет файла `LICENSE`, раздел «Лицензия» в README пуст) → по умолчанию все права защищены, перенос любого кода юридически невозможен без письменного разрешения автора.
6. Работающие компоненты KiroX не переписываются без необходимости; изменения — минимальными шагами внутри текущей структуры пакетов.
7. Сохраняется KiroX Fusion UI: AMOLED Black, чёрно-белый минимализм, Pill Indicator, текущая нижняя навигация, профиль/настройки, существующие анимации и жесты.
8. Никаких «бесплатного Premium», обходов серверных ограничений и скрытых функций против правил Telegram (см. ROADMAP, принцип 3).

---

## 1. Архитектурные решения (собственные, для всех этапов)

Текущий слой KiroX уже соответствует модели «один `TelegramClient` на активный аккаунт + репозитории на диспетчере TDLib → `StateFlow` → Compose». План её **расширяет**, а не заменяет.

| Решение | Суть | Почему |
|---|---|---|
| A1. Один клиент на аккаунт | Новые функции получают `TelegramClient` из существующего `TelegramRepositories`; новых экземпляров `Client`, сервисов TDLib и хранилищ сессий не создаётся | Требование «без дублирующих клиентов и параллельных сессий» |
| A2. Модель сообщения → `MessageContentUi` | Заменить плоский `TelegramMessageKind` + `text` на sealed-модель контента (`Text(entities)`, `Photo`, `Video`, `Animation`, `VideoNote`, `Voice`, `Audio`, `Document`, `Sticker`, `Poll`, `Location`, `Contact`, `Call`, `Service`, `Unsupported`). Маппинг остаётся в `TelegramMessageRepository`, UI получает только UI-модели | Сейчас видео/GIF/кружки/стикеры сливаются в заглушку; без этого ни одна медиа-функция не встраивается чисто |
| A3. `TelegramMediaRepository` | Скачивание/отмена/прогресс файлов по `fileId` поверх уже существующего `TelegramFileRepository` (`DownloadFile` + `UpdateFile`) с приоритетами и лимитом параллельных загрузок; автозагрузка по `GetAutoDownloadSettingsPresets` | Документы, видео, музыка сейчас не скачиваются вообще |
| A4. Единый `MediaPlaybackController` | Один плеер на процесс (Media3 ExoPlayer) для голосовых, музыки, видео, GIF, кружков; освобождается по lifecycle экрана/процесса; `AudioFocus` | Сейчас `VoicePlayer` — глобальный `object` с `MediaPlayer` без lifecycle и без скорости/перемотки |
| A5. Репозиторий на функцию | `TelegramSearchRepository`, `TelegramThreadRepository` (комментарии/темы), `TelegramStickerRepository`, `TelegramPinRepository` (или расширение `MessageRepository`), `TelegramPrivacyRepository`-расширение существующего `TelegramSettingsRepository`, `ConnectionMonitor` | Отдельные Repository/ViewModel там, где это оправдано |
| A6. Ошибки TDLib | Все новые вызовы — через `client.query`/`act` с маппингом в `humanError` (`TelegramErrors.kt`); добавить коды для `FLOOD_WAIT_*`, `CHAT_WRITE_FORBIDDEN`, `MESSAGE_TOO_LONG`, файловых ошибок | Единая обработка ошибок, тестируемая unit-тестами |
| A7. Тесты | Для каждого репозитория — unit-тесты чистых функций маппинга (как `ChatItemMappingTest`); для UI — Compose-тесты в `FusionUiTest`-стиле | Существующая практика проекта |

Новые сторонние зависимости (все с лицензией, совместимой с Apache-2.0 KiroX; добавлять только после утверждения):

| Библиотека | Лицензия | Для чего | Этап |
|---|---|---|---|
| `androidx.media3:media3-exoplayer`, `media3-ui-compose` (или собственная `PlayerSurface`) | Apache-2.0 | видео, GIF (MP4), кружки, музыка, голосовые, видео-истории | 2 |
| `androidx.camera:camera-*` (CameraX) | Apache-2.0 | запись кружков | 3 |
| `com.airbnb.android:lottie-compose` | Apache-2.0 | анимированные стикеры TGS (gzip → Lottie JSON) | 3 |
| WEBM-стикеры | — | вариант 1: Media3 + платформенный VP9 (без альфа-канала на части устройств); вариант 2: собственный JNI-декодер на **уже имеющемся в KiroX** `libvpx.a` (BSD-3) — решить на этапе 3 | 3 |

Звонки, VoIP-движок (`voip/`, tgcalls LGPL-3.0 + WebRTC) уже собственные в KiroX — **не заменяются** нативной частью Yougram.

---

## 2. Приоритеты

- **P0** — исправить неисправное/опасное в уже существующем.
- **P1** — базовые функции мессенджера, без которых клиент нельзя назвать полноценным.
- **P2** — важные функции Telegram.
- **P3** — расширенные функции, после стабильной 1.0.

| Этап | Приоритет | Содержание | Оценка |
|---|---|---|---|
| 1 | P0 | Стабилизация: `SetNetworkType` и восстановление соединения, lifecycle плеера, черновики, Bluetooth-маршрут звонка | S–M |
| 2 | P1 | Модель контента (A2) + медиа: просмотр/скачивание документов, видео, GIF, музыки; альбомы; форматирование текста | L |
| 3 | P1 | Стикеры/GIF-панель (статичные → TGS → WEBM), запись и воспроизведение кружков, голосовые 2.0 | L |
| 4 | P1 | Закрепление, мультивыбор, поиск (в чате на сервере, глобальный, публичный), ссылки t.me | M |
| 5 | P2 | Комментарии каналов и форумные темы | M–L |
| 6 | P2 | Настройки: 2FA, чёрный список, исключения уведомлений, автозагрузка, прокси; ответ из уведомления | M |
| 7 | P2 | Stories: видео-истории, просмотры/реакции, архив; мультиаккаунт: уведомления неактивных аккаунтов | M |
| 8 | P3 | Групповые звонки/видеочаты, демонстрация экрана, опросы (создание), боты/inline-клавиатуры, перевод | XL |

S ≈ до 1 дня, M ≈ 2–4 дня, L ≈ 1–2 недели, XL ≈ > 2 недель (оценки ориентировочные).

---

## 3. Этапы и критерии готовности

Для каждого пункта: файлы KiroX → TDLib API → зависимости → лицензионные ограничения → критерий готовности. «Новые» файлы — предложение, имена можно поменять при утверждении.

### Этап 1 (P0). Стабилизация существующего

**1.1 Тип сети и восстановление соединения**
- Файлы: `telegram/TelegramClient.kt`, `notify/KeepConnectedService.kt`, новый `telegram/ConnectionMonitor.kt`.
- TDLib: `SetNetworkType` (`NetworkTypeWiFi/Mobile/MobileRoaming/None/Other`), `UpdateConnectionState` (уже обрабатывается в UI), `SetOption("online")`.
- Android: `ConnectivityManager.registerDefaultNetworkCallback`.
- Лицензии: нет ограничений.
- Готово, когда: смена Wi-Fi ↔ мобильная сеть и режим «в самолёте» → индикатор «Ожидание сети/Соединение…» и автоматическое восстановление ≤ 5 с после появления сети; без дублей обновлений.

**1.2 Плеер голосовых: lifecycle**
- Файлы: `ui/chat/VoiceNotes.kt` (`VoicePlayer`), `ui/chat/TelegramChatScreen.kt`.
- Готово, когда: выход из чата/смена аккаунта/фон останавливает и освобождает плеер; LeakCanary (debug) не находит утечек; `AudioFocus` запрашивается и отдаётся.

**1.3 Черновики**
- Файлы: `telegram/TelegramChatRepository.kt` (`UpdateChatDraftMessage` уже читается), `ui/chat/TelegramChatScreen.kt`.
- TDLib: `SetChatDraftMessage`, `DraftMessage`/`InputMessageText`.
- Готово, когда: недописанный текст сохраняется при выходе из чата, виден в списке чатов «Черновик:» и синхронизируется с официальным клиентом.

**1.4 Маршрут звука в звонке и Bluetooth**
- Файлы: `ui/call/CallScreen.kt` (`CallAudioRoute`), `voip/CallService.kt`, `AndroidManifest.xml`.
- Android: API 31+ `AudioManager.getAvailableCommunicationDevices/setCommunicationDevice`, ниже — `startBluetoothSco`; разрешение `BLUETOOTH_CONNECT` (runtime, API 31+).
- Готово, когда: при подключённой гарнитуре звук идёт в неё автоматически; переключатель Динамик/Телефон/Bluetooth; отключение гарнитуры во время звонка не роняет звук.

**1.5 Индикатор набора текста**
- TDLib: `SendChatAction` (Typing/RecordingVoiceNote/RecordingVideoNote/UploadingPhoto…), `UpdateChatAction`.
- Готово, когда: «печатает…»/«записывает голосовое…» отображаются в шапке чата и списке, свой статус отправляется не чаще раза в 5 с.

### Этап 2 (P1). Модель контента и медиа

**2.1 `MessageContentUi` (A2)**
- Файлы: `telegram/TelegramModels.kt`, `telegram/TelegramMessageRepository.kt` (`toModel`), `ui/chat/ChatModels.kt`, `ui/chat/ChatScreen.kt`, тесты `test/.../telegram/MessageContentMappingTest.kt` (новый).
- Готово, когда: каждый тип `MessageContent` из TDLib 1.8.69 получает свой UI-вариант или явный `Unsupported` с текстом «Не поддерживается в KiroX»; unit-тест покрывает все ветки маппинга.

**2.2 Форматированный текст и ссылки**
- TDLib: `MessageText.text.entities` (Bold/Italic/Code/Pre/TextUrl/Mention/Spoiler…), `ParseTextEntities`/`ParseMarkdown` при отправке, `GetInternalLinkType` для `t.me`, `MessageText.linkPreview`.
- Готово, когда: жирный/курсив/код/ссылки/упоминания отображаются как в Telegram; нажатие на `t.me/username` открывает чат внутри KiroX; спойлер скрыт до нажатия.

**2.3 Документы и музыка**
- Файлы: `TelegramFileRepository.kt` → `TelegramMediaRepository.kt`, `ChatScreen.kt` (`DocumentMessage`).
- TDLib: `DownloadFile(priority, synchronous=false)`, `CancelDownloadFile`, `UpdateFile`, `GetAutoDownloadSettingsPresets`.
- Android: `FileProvider` + `ACTION_VIEW`, сохранение через `MediaStore`.
- Готово, когда: у документа видны размер и прогресс, загрузку можно отменить, файл открывается системным приложением и сохраняется в «Загрузки»; MP3/аудио играет во встроенном плеере с перемоткой.

**2.4 Видео и GIF**
- Зависимость: Media3 ExoPlayer (Apache-2.0).
- TDLib: `MessageVideo`/`MessageAnimation` (`video.video`, `thumbnail`, `minithumbnail`), `DownloadFile` с потоковой загрузкой (`offset/limit`).
- Готово, когда: превью с длительностью, полноэкранный просмотр с перемоткой; GIF автоигрываются без звука только в видимой области; при прокрутке одновременно декодируется не больше 1–2 роликов; память стабильна на длинной ленте.

**2.5 Альбомы и отправка медиа**
- TDLib: `SendMessageAlbum`, `InputMessagePhoto/Video` с `caption`, `mediaAlbumId` при отображении.
- Готово, когда: до 10 фото/видео отправляются одним альбомом, входящие альбомы показываются сеткой; сжатие/«без сжатия» (как документ) выбирается перед отправкой.

### Этап 3 (P1). Стикеры, GIF-панель, кружки, голосовые

**3.1 Панель стикеров, эмодзи и GIF**
- Новые: `telegram/TelegramStickerRepository.kt`, `ui/chat/StickerPanel.kt`.
- TDLib: `GetInstalledStickerSets`, `GetStickerSet`, `GetRecentStickers`, `GetSavedAnimations`, `SearchEmojis`/`GetStickers(query)`, `InputMessageSticker`, `InputMessageAnimation`, `UpdateInstalledStickerSets`, `UpdateRecentStickers`.
- Готово, когда: панель с вкладками Эмодзи/Стикеры/GIF в стиле Fusion (чёрный фон, pill-вкладки); отправка стикера и GIF; недавние обновляются.

**3.2 Анимированные стикеры TGS**
- Зависимость: `lottie-compose` (Apache-2.0); распаковка gzip — стандартный `GZIPInputStream`.
- Готово, когда: TGS проигрываются в чате и панели; в сетке панели анимация ставится на паузу вне экрана; 60 fps на среднем устройстве, без роста памяти.

**3.3 Видеостикеры WEBM**
- Решение на этапе: Media3 (если достаточно) либо собственный JNI-декодер на `libvpx.a` из `voip/prebuild` (BSD-3, уже в репозитории).
- Готово, когда: WEBM с прозрачностью отображается корректно на тестовых устройствах.

**3.4 Кружки: воспроизведение и запись**
- Новые: `ui/chat/VideoNoteRecorder.kt`, `ui/chat/VideoNoteBubble.kt`.
- Зависимости: CameraX (Apache-2.0), `MediaCodec`/`MediaMuxer` (H.264 + AAC, квадрат, ≤ 60 с).
- TDLib: `InputMessageVideoNote(InputFileLocal, thumbnail, duration, length)`, `MessageVideoNote`, `OpenMessageContent` (отметка просмотра).
- Готово, когда: удержание кнопки переключает голос ↔ кружок, запись с фронтальной камеры, отмена свайпом, кружок доходит и проигрывается в официальном клиенте; входящие кружки играют по нажатию.

**3.5 Голосовые 2.0**
- Файлы: `ui/chat/VoiceNotes.kt`.
- Готово, когда: волна из `voiceNote.waveform` на пузыре, прогресс и перемотка, скорость 1×/1.5×/2×, блокировка записи свайпом вверх, пауза записи, `OpenMessageContent` отмечает прослушанным.

### Этап 4 (P1). Закрепление, выбор, поиск

**4.1 Закрепление**
- TDLib: `PinChatMessage`, `UnpinChatMessage`, `UnpinAllChatMessages`, `GetChatPinnedMessage`, `SearchChatMessages(filter=Pinned)`.
- Файлы: `TelegramMessageRepository.kt`, `TelegramChatScreen.kt` (пункт «Закрепить» сейчас скрыт).
- Готово, когда: плашка закреплённого сообщения над историей, переход к сообщению, открепление; права проверяются по `ChatMemberStatus`/`canPinMessages`.

**4.2 Мультивыбор**
- Готово, когда: «Выбрать» (сейчас скрыт) → массовые удаление/пересылка/копирование.

**4.3 Поиск**
- Новый `telegram/TelegramSearchRepository.kt`.
- TDLib: `SearchChatMessages` (в чате, на сервере, с фильтрами медиа), `SearchMessages` (глобально), `SearchPublicChats`/`SearchPublicChat`, `SearchChatsOnServer`, `SearchContacts`; переход к найденному — `GetChatHistory(fromMessageId, offset<0)`.
- Готово, когда: поиск в чате находит сообщения вне загруженной части и прокручивает к ним; глобальный поиск на главном экране показывает чаты/контакты/публичные каналы/сообщения отдельными секциями.

### Этап 5 (P2). Комментарии и форумы

- Новый `telegram/TelegramThreadRepository.kt`, экран `ui/chat/ThreadScreen.kt` (переиспользует существующий пузырь `ChatScreen`).
- TDLib: `GetMessageThread`, `GetMessageThreadHistory`, отправка через второй параметр `SendMessage` — `MessageTopicThread` (комментарии) / `MessageTopicForum` (темы), `MessageInteractionInfo.replyInfo`; форумы: `GetForumTopics`, `GetForumTopic`, `GetForumTopicHistory`, `CreateForumTopic`, `UpdateForumTopicInfo`.
- Готово, когда: под постом канала есть «N комментариев» → открывается обсуждение с отправкой; форумная супергруппа открывается списком тем, тема — как обычный чат.

### Этап 6 (P2). Настройки Telegram

- Файлы: `telegram/TelegramSettingsRepository.kt`, `ui/home/SettingsScreens.kt`, `ui/home/AccountScreens.kt`, `notify/MessageNotifications.kt`.
- TDLib: `GetPasswordState`/`SetPassword`/`SetRecoveryEmailAddress` (2FA); `GetBlockedMessageSenders`/`SetMessageSenderBlockList`; `GetChatNotificationSettingsExceptions`; `SetAutoDownloadSettings`; `AddProxy`/`GetProxies`/`EnableProxy`; правила приватности с исключениями (пользователи/чаты).
- Android: `RemoteInput` для ответа из уведомления (сейчас есть только «Прочитано»).
- Готово, когда: каждое изменение сохраняется в Telegram и видно в официальном клиенте; ответ из шторки отправляет сообщение без открытия приложения.

### Этап 7 (P2). Stories и мультиаккаунт

- Stories: видео-историй в просмотрщике сейчас нет («показываются обложкой») → Media3; `GetStoryInteractions` (просмотры своих), `SetStoryReaction`, `GetChatPostedToChatPageStories`, `GetChatArchivedStories`, `DeleteStory`.
- Мультиаккаунт (`TelegramAccounts.kt`): сейчас онлайн только активный аккаунт → уведомления неактивных через push-пробуждение `ProcessPushNotification` в отдельном кратковременном клиенте **того же** каталога сессии (без параллельного онлайна двух клиентов на один каталог).
- Готово, когда: видео-история проигрывается со звуком и прогрессом; у своей истории видно число просмотров; уведомление второго аккаунта приходит и открывает нужный аккаунт.

### Этап 8 (P3). Расширенное (после стабильной 1.0, решение владельца)

Групповые звонки/видеочаты (`CreateVideoChat`, `JoinVideoChat`, групповой режим tgcalls уже в `voip/`), демонстрация экрана, создание опросов (`InputMessagePoll`), inline-клавиатуры ботов (`ReplyMarkup`, `GetCallbackQueryAnswer`), перевод (`TranslateText`), календарь сообщений (`GetChatMessageCalendar`).

---

## 4. Проверки после каждого модуля

1. `./gradlew :app:assembleDebug` и `:app:assembleReleaseCheck` (R8).
2. `./gradlew :app:testDebugUnitTest`; на устройстве `connectedDebugAndroidTest` (`TdlibNativeLoadTest`, `FusionUiTest`).
3. Ошибки TDLib: каждый новый вызов проверяется на отказ сети и на `TdApi.Error` (сообщение через `humanError`, без падения).
4. Lifecycle: поворот экрана, фон/возврат, смена аккаунта, `am kill`; LeakCanary в debug-сборке (добавить как `debugImplementation`, Apache-2.0).
5. Регрессия по `docs/testing/STABLE_TEST_PLAN.md`: вход, список чатов, отправка текста/фото/голосового, звонок, уведомления.
6. Коммит в отдельной ветке `feature/<этап>`, PR с результатами сборки и тестов; запись в `docs/development/CLAUDE_PROGRESS.md`.

**Ограничение среды:** в облачной среде этой сессии нет Android SDK, поэтому APK здесь не собирался. Сборку и инструментальные тесты нужно выполнить локально (Android Studio) либо настроить GitHub Actions — это предлагается сделать первым шагом этапа 1.

---

## 5. Что нужно от владельца для старта

1. Утвердить/изменить порядок этапов и приоритеты.
2. Разрешить новые зависимости: Media3, CameraX, Lottie, LeakCanary (debug).
3. Решить: CI-сборка в GitHub Actions — да/нет.
4. Работа ведётся в ветках `feature/*` с PR в `main` — подтвердить.
