# KiroX — аудит Telegram-функций

**Дата:** 27.10.2026 · **KiroX:** `main` @ `e7d6510` (1.0.0-rc.1) · **Ориентир:** Yougram @ `7b89cfaa` (versionName 1.3.1)
**Метод:** статус определён по исходному коду (вызовы `TdApi.*`, маппинг, UI), а не по README. Сборка и проверки на устройстве в этой сессии не выполнялись (в облачной среде нет Android SDK).
План работ: [`TELEGRAM_INTEGRATION_PLAN.md`](TELEGRAM_INTEGRATION_PLAN.md).

> **Yougram использован только как функциональный ориентир** — чтобы понять, какие Telegram-функции бывают в независимом TDLib-клиенте и как они ведут себя для пользователя. В документе нет кода Yougram, и реализация KiroX его не использует (см. §5).

Обозначения: ✅ полностью · 🟡 частично · 🔸 заглушка/только отображение · ❌ нет. Сложность доработки: S / M / L / XL.

---

## 1. Технический фон

| Параметр | KiroX | Yougram (для сравнения) |
|---|---|---|
| Язык / UI | Kotlin 2.2.10, Compose BOM 2025.09.01, Material 3 | Kotlin 2.3, Compose, Material 3 |
| Доступ к TDLib | собственная обёртка `TelegramClient` над `org.drinkless.tdlib.Client` (JNI) | `dev.g000sha256:tdl-coroutines` 13.0.0 (td_json) + ASM-патч байткода |
| Версия TDLib | **1.8.69**, commit `ea3f69ce` (`TdApi.GIT_COMMIT_HASH`), `libtdjni.so` | в репозитории не зафиксирована (берётся из библиотеки) |
| ABI | `arm64-v8a` | `arm64-v8a`, `armeabi-v7a` |
| minSdk / targetSdk | 29 / 37 | 31 / 36 |
| Звонки | собственный JNI-мост `voip/kirox/kirox_voip_jni.cpp` + tgcalls (LGPL-3.0) + WebRTC | tgcalls + WebRTC + Java-код из Telegram-Android |
| Push | Firebase (если есть `google-services.json`) + Web Push через ntfy (`push/KiroxPush.kt`) | push нет |
| Лицензия собственного кода | Apache-2.0 (`LICENSE`, `NOTICE`) | **отсутствует** |

**Совместимость API.** Все TDLib-методы, нужные по плану, есть в TDLib 1.8.69 KiroX (проверено по `TdApi.java`): `SetNetworkType`, `SearchChatMessages`, `SearchMessages`, `SearchPublicChats`, `SearchChatsOnServer`, `SearchContacts`, `PinChatMessage`/`UnpinChatMessage`/`GetChatPinnedMessage`, `GetMessageThread`/`GetMessageThreadHistory`, `GetForumTopics`/`GetForumTopicHistory`/`CreateForumTopic`, `GetInstalledStickerSets`/`GetStickerSet`/`GetRecentStickers`/`GetSavedAnimations`, `InputMessageSticker`/`InputMessageAnimation`/`InputMessageVideoNote`, `SendMessageAlbum`, `ParseTextEntities`, `SetChatDraftMessage`, `SendChatAction`, `GetPasswordState`/`SetPassword`, `GetBlockedMessageSenders`, `GetAutoDownloadSettingsPresets`, `AddProxy`, `GetStoryInteractions`, `SetStoryReaction`, `JoinVideoChat`. **Обновлять TDLib и нативные библиотеки для плана не требуется.** `SendMessage` в 1.8.69 принимает `MessageTopic` (`MessageTopicThread` / `MessageTopicForum`) вторым параметром — это учтено в плане.

---

## 2. Сводная таблица

Колонка «Yougram» — что есть в ориентире (поведение, а не реализация). Колонка «Нужно в KiroX» — что сделать собственными средствами.

| # | Функция | KiroX | Yougram | Нужно в KiroX | Сложн. |
|---|---|---|---|---|---|
| 1 | Голосовые: запись | ✅ OGG/Opus, волна 5 бит, отмена | 🟡 запись + блокировка свайпом вверх; волну **не отправляет** | блокировка записи, пауза | S |
| 2 | Голосовые: воспроизведение | 🟡 play/stop, без волны на пузыре, без перемотки и скорости; глобальный `MediaPlayer` | 🟡 волна с прогрессом, без скорости/перемотки | волна, прогресс, перемотка, 1×/1.5×/2×, lifecycle плеера | M |
| 3 | Кружки: воспроизведение | 🔸 заглушка (`MediaPlaceholder`) | ✅ в круге, кольцо прогресса | плеер в круге | M |
| 4 | Кружки: запись | ❌ | ✅ CameraX, ≤60 с, смена камеры, зум | запись CameraX + MediaCodec | L |
| 5 | GIF | 🔸 заглушка; отправка нет | ✅ панель GIF, автовоспроизведение | плеер + отправка `InputMessageAnimation` | M |
| 6 | Стикеры статичные | 🔸 текст «Стикер 😀» | ✅ панель наборов, недавние, избранные | панель + отображение WEBP | M |
| 7 | Анимированные стикеры TGS | ❌ | ✅ Lottie | Lottie (Apache-2.0) | M |
| 8 | Видеостикеры WEBM | ❌ | 🟡 MediaPlayer без альфы | решение по декодеру | M–L |
| 9 | Эмодзи-панель / custom emoji | ❌ (системная клавиатура) | 🟡 фиксированный список; custom emoji только отображение | панель Unicode + отображение custom emoji | M |
| 10 | Отправка фото | ✅ одиночное с подписью | ✅ + альбом до 10, редактор | альбомы | S–M |
| 11 | Отправка видео | ✅ `InputMessageVideo` | 🟡 шлёт как документ | — (в KiroX лучше) | — |
| 12 | Отправка документов | ✅ | ✅ | — | — |
| 13 | Просмотр фото | ✅ превью в пузыре | ✅ полноэкранный pager, зум | полноэкранный просмотр с зумом | M |
| 14 | Просмотр видео | 🔸 заглушка | ✅ плеер со скоростями | Media3 | M |
| 15 | Скачивание документов/музыки | ❌ только имя файла | ✅ загрузка, отмена, сохранение | `TelegramMediaRepository`, FileProvider, MediaStore | M |
| 16 | Автозагрузка медиа | ❌ | 🔸 настройки есть, не применяются | `GetAutoDownloadSettingsPresets` | S |
| 17 | Реакции | ✅ эмодзи add/remove | ✅ + `GetMessageAvailableReactions` | список доступных реакций чата | S |
| 18 | Ответы | ✅ | ✅ + свайп для ответа | свайп-ответ, переход к оригиналу | S |
| 19 | Пересылка | ✅ | 🟡 одно сообщение | — | — |
| 20 | Закрепление | ❌ пункт скрыт | ✅ плашка, закрепить/открепить | `PinChatMessage` и др. | M |
| 21 | Редактирование / удаление | ✅ у себя / у всех | 🟡 удаляет только у всех | — | — |
| 22 | Мультивыбор | ❌ пункт скрыт | ❌ | собственная реализация | S–M |
| 23 | Форматирование текста | ❌ entities не читаются и не отправляются | 🔸 только ссылки/упоминания | рендер entities, `ParseTextEntities` | M |
| 24 | Превью ссылок, `t.me`-ссылки | ❌ | 🟡 deep links есть, превью нет | `linkPreview`, `GetInternalLinkType` | M |
| 25 | Черновики | 🔸 только чтение `UpdateChatDraftMessage` | 🔸 только локально | `SetChatDraftMessage` | S |
| 26 | «Печатает…» | ❌ | ✅ | `SendChatAction`, `UpdateChatAction` | S |
| 27 | Поиск в чате | 🟡 только по загруженным сообщениям | ✅ серверный, навигация ↑↓ | `SearchChatMessages` + прыжок к сообщению | M |
| 28 | Глобальный поиск | 🟡 `SearchChats` по известным чатам | ✅ сообщения, контакты, публичные чаты | `SearchMessages`, `SearchPublicChats`, `SearchChatsOnServer`, `SearchContacts` | M |
| 29 | Комментарии каналов | ❌ | 🟡 открытие треда, отправка | `GetMessageThread(History)` | M |
| 30 | Форумные темы | ❌ | 🟡 список тем, чтение/отправка; без создания | `GetForumTopics`, `GetForumTopicHistory`, `CreateForumTopic` | M–L |
| 31 | Stories: просмотр | 🟡 фото; видео «показываются обложкой» | ✅ видео, пауза удержанием, реакции, ответ, просмотры | видео, реакции, ответ, просмотры, удаление | M |
| 32 | Stories: публикация | ✅ фото/видео, подпись, аудитория | 🔸 есть в коде, недостижимо из UI | — (в KiroX лучше) | — |
| 33 | Мультиаккаунт | 🟡 до 3 аккаунтов, переключение; вход во второй не проверен; уведомления только активного | ✅ переключение | проверка на устройстве, уведомления неактивных | M |
| 34 | Вход: QR, email | ✅ (`RequestQrCodeAuthentication`, email-код) | ❌ | — (в KiroX лучше) | — |
| 35 | Приватность | ✅ 5 правил с сохранением исключений | ✅ 12 правил | добавить правила (дата рождения, «о себе», пересылка, голосовые, сообщения) | S |
| 36 | Устройства / сеансы | ✅ список, завершение | ✅ + подтверждение входа по QR, подключённые сайты | `ConfirmQrCodeAuthentication`, сайты | S |
| 37 | Облачный пароль (2FA) | 🟡 ввод и восстановление при входе; управление нет | 🟡 установка без email | `GetPasswordState`, `SetPassword`, email восстановления | M |
| 38 | Чёрный список | ❌ | ✅ | `GetBlockedMessageSenders`, `SetMessageSenderBlockList` | S |
| 39 | Данные и память | ✅ размер, очистка | ✅ + прокси | прокси, автозагрузка | M |
| 40 | Настройки уведомлений | ✅ по типам чатов, превью; в чате — без звука | 🟡 многое сохраняется, но не применяется | исключения (`GetChatNotificationSettingsExceptions`) | S |
| 41 | Уведомления: действия | 🟡 только «Прочитано» | ✅ ответ из шторки | `RemoteInput`-ответ | S |
| 42 | Push при выгруженном приложении | ✅ FCM/ntfy, проверено 25.10 | ❌ | — (в KiroX лучше) | — |
| 43 | Фоновое соединение | ✅ `KeepConnectedService` | ✅ | — | — |
| 44 | Состояние соединения в UI | ✅ `UpdateConnectionState` | ❌ | — | — |
| 45 | Тип сети / восстановление | ❌ нет `SetNetworkType` | ✅ | `ConnectivityManager` → `SetNetworkType` | S |
| 46 | Звонки 1-на-1 аудио/видео | ✅ tgcalls, камера, смена камеры, датчик приближения | ✅ | — | — |
| 47 | Маршрут звука / Bluetooth | 🟡 только телефон/динамик через устаревший `isSpeakerphoneOn`; нет `BLUETOOTH_CONNECT` | ✅ телефон/динамик/Bluetooth, автопереключение | `setCommunicationDevice` (API 31+) | S–M |
| 48 | Журнал звонков | ✅ `SearchCallMessages`, группировка | ✅ + фильтр «пропущенные» | фильтр пропущенных | S |
| 49 | Групповые звонки, видеочаты | ❌ (отложено владельцем 26.10) | ❌ | этап P3 | XL |
| 50 | Секретные чаты | ✅ создание, автоудаление | ❌ | — (в KiroX лучше) | — |
| 51 | Опросы | 🔸 текстом | 🟡 голосование, создание | отображение и голосование, затем создание | M |
| 52 | Боты: клавиатуры | ❌ `ReplyMarkup` не используется | 🟡 inline/reply-клавиатуры, команды | отображение и нажатие кнопок | M |
| 53 | Папки | ✅ создание, фильтр, удаление | 🔸 только чтение | — (в KiroX лучше) | — |
| 54 | Архив, закреплённые чаты | ✅ | ✅ | — | — |
| 55 | Контакты | ✅ список, добавление по номеру | 🟡 список, поиск | поиск по контактам | S |
| 56 | Группы / каналы: создание | ✅ (проверка на аккаунте не выполнялась) | ✅ + управление правами, ссылки-приглашения | права участников, ссылки | M |
| 57 | Перевод, Web Apps, платежи, отложенные | ❌ | ❌ | P3 | — |

Итого по ядру мессенджера (пп. 1–30): в KiroX ✅ 9, 🟡 3, 🔸/❌ 18. Основной разрыв — **медиа (видео, GIF, кружки, стикеры, файлы)**, **форматирование**, **закрепление и серверный поиск**, **комментарии и темы**. В инфраструктуре (push, вход, секретные чаты, публикация историй, папки) KiroX уже впереди ориентира.

---

## 3. Подробности по областям

Для каждой области: исходные файлы KiroX → TDLib API → зависимости → лицензионные ограничения → критерий готовности. Пути — от `app/src/main/java/com/kirox/app/`.

### 3.1 Голосовые и кружки
- **Файлы:** `ui/chat/VoiceNotes.kt` (`VoiceRecorder`, `VoicePlayer`), `ui/chat/ChatScreen.kt` (`VoiceMessage`, `MediaPlaceholder`), `ui/chat/TelegramChatScreen.kt`, `telegram/TelegramMessageRepository.kt` (`sendVoice`, `toModel`).
- **Найдено:** `VoicePlayer` — синглтон с `MediaPlayer` без `AudioFocus`, без остановки при уходе из чата; `MessageVideoNote` маппится в общий `Video` и рисуется заглушкой; запись кружков отсутствует.
- **TDLib:** `InputMessageVoiceNote`, `InputMessageVideoNote`, `MessageVoiceNote.voiceNote.waveform`, `OpenMessageContent`, `DownloadFile`, `SendChatAction(ChatActionRecordingVoiceNote/VideoNote)`.
- **Зависимости:** Media3 ExoPlayer, CameraX (обе Apache-2.0).
- **Готово, когда:** голосовое и кружок записываются, отправляются и воспроизводятся в официальном клиенте; входящие воспроизводятся с прогрессом; плеер освобождается при выходе из чата.

### 3.2 GIF, стикеры, эмодзи
- **Файлы:** `telegram/TelegramMessageRepository.kt` (стикер → `Other`), `ui/chat/ChatScreen.kt`; новых панелей нет.
- **TDLib:** `GetInstalledStickerSets`, `GetStickerSet`, `GetRecentStickers`, `GetSavedAnimations`, `GetStickers`, `SearchEmojis`, `InputMessageSticker`, `InputMessageAnimation`, `UpdateInstalledStickerSets`, `UpdateRecentStickers`.
- **Зависимости:** Lottie (Apache-2.0) для TGS; для WEBM — Media3 или собственный JNI на `libvpx.a`, уже лежащем в `voip/prebuild` (BSD-3).
- **Готово, когда:** панель Эмодзи/Стикеры/GIF в Fusion UI; TGS и WEBM анимируются без роста памяти; анимации останавливаются вне экрана.

### 3.3 Медиа: отправка, загрузка, просмотр
- **Файлы:** `telegram/TelegramFileRepository.kt` (только `DownloadFile` для превью), `ui/components/TelegramAvatar.kt` (`rememberTelegramBitmap`), `ui/chat/ChatScreen.kt` (`DocumentMessage` — только имя), `ui/chat/TelegramChatScreen.kt` (`PickVisualMedia`, `GetContent`).
- **TDLib:** `SendMessageAlbum`, `DownloadFile(offset, limit)`, `CancelDownloadFile`, `UpdateFile`, `GetAutoDownloadSettingsPresets`, `SetAutoDownloadSettings`.
- **Зависимости:** Media3. Android: `FileProvider`, `MediaStore`.
- **Готово, когда:** альбомы отправляются и показываются сеткой; видео и музыка играют встроенно; документы скачиваются с прогрессом, открываются и сохраняются; автозагрузка следует настройкам.

### 3.4 Действия с сообщениями, текст
- **Файлы:** `telegram/TelegramMessageRepository.kt` (`toggleReaction`, `delete`, `forward`, `edit`, `inputText` — entities всегда пустые), `ui/chat/TelegramChatScreen.kt` (`hidden = { "Закрепить", "Выбрать" }`).
- **TDLib:** `PinChatMessage`, `UnpinChatMessage`, `GetChatPinnedMessage`, `GetMessageAvailableReactions`, `ParseTextEntities`, `TextEntity*`, `SetChatDraftMessage`, `SendChatAction`, `UpdateChatAction`, `GetInternalLinkType`, `ReplyMarkup*`, `GetCallbackQueryAnswer`.
- **Готово, когда:** закрепление и мультивыбор работают; разметка и ссылки отображаются и отправляются; черновики синхронизируются; «печатает…» видно.

### 3.5 Поиск
- **Файлы:** `telegram/TelegramChatRepository.kt` (`search` → `SearchChats`), `ui/chat/TelegramChatScreen.kt` (фильтр по загруженным `shownMessages`), `ui/home/TelegramMainScreen.kt`.
- **TDLib:** `SearchChatMessages`, `SearchMessages`, `SearchPublicChats`, `SearchPublicChat`, `SearchChatsOnServer`, `SearchContacts`, `GetChatHistory(fromMessageId, offset<0)`.
- **Готово, когда:** находит сообщения и чаты, отсутствующие в локальном кеше, и переходит к найденному сообщению.

### 3.6 Комментарии и форумы
- **Файлы:** нет. Будут: `telegram/TelegramThreadRepository.kt`, экран треда на базе существующего `ChatScreen`.
- **TDLib:** `GetMessageThread`, `GetMessageThreadHistory`, `SendMessage(..., MessageTopicThread/MessageTopicForum, ...)`, `GetForumTopics`, `GetForumTopic`, `GetForumTopicHistory`, `CreateForumTopic`, `UpdateForumTopicInfo`.
- **Готово, когда:** комментарии под постом открываются и отправляются; форум открывается списком тем с пагинацией.

### 3.7 Stories
- **Файлы:** `telegram/TelegramStoryRepository.kt`, `ui/home/CallsAndStories.kt` (`StoryViewer`), `ui/home/StoryPost.kt`.
- **TDLib:** `GetStoryInteractions`, `SetStoryReaction`, `InputMessageReplyToStory`, `DeleteStory`, `GetChatPostedToChatPageStories`, `GetChatArchivedStories`.
- **Готово, когда:** видео-истории проигрываются; доступны пауза удержанием, реакция, ответ, список просмотров своей истории, удаление.

### 3.8 Аккаунты и настройки
- **Файлы:** `telegram/TelegramAccounts.kt`, `telegram/TelegramRepositories.kt`, `telegram/TelegramSettingsRepository.kt`, `telegram/TelegramSessionsRepository.kt`, `ui/home/SettingsScreens.kt`, `ui/home/AccountScreens.kt`.
- **TDLib:** `GetPasswordState`, `SetPassword`, `SetRecoveryEmailAddress`, `GetBlockedMessageSenders`, `SetMessageSenderBlockList`, `ConfirmQrCodeAuthentication`, `GetConnectedWebsites`, `GetChatNotificationSettingsExceptions`, `AddProxy`, `GetProxies`, `EnableProxy`; дополнительные `UserPrivacySetting*`.
- **Готово, когда:** каждое изменение сохраняется на сервере и видно в официальном клиенте; второй аккаунт входит, переключается и получает уведомления.

### 3.9 Уведомления и соединение
- **Файлы:** `notify/MessageNotifications.kt`, `notify/KeepConnectedService.kt`, `push/KiroxPush.kt`, `telegram/TelegramClient.kt`.
- **Найдено:** нет `SetNetworkType` — TDLib не узнаёт о смене сети; в уведомлении нет ответа.
- **TDLib:** `SetNetworkType`, `ProcessPushNotification` (уже есть), `SendMessage` из `RemoteInput`.
- **Готово, когда:** после смены сети соединение восстанавливается без перезапуска; ответ из шторки доставляется.

### 3.10 Звонки
- **Файлы:** `telegram/TelegramVoipRepository.kt`, `telegram/TelegramCallRepository.kt`, `voip/CallService.kt`, `voip/NativeCall.kt`, `ui/call/CallScreen.kt` (`CallAudioRoute`), `voip/` (C++).
- **Найдено:** аудио- и видеозвонки 1-на-1 работают; маршрут звука — только `isSpeakerphoneOn` (устарело с API 31), Bluetooth не поддержан.
- **Android:** `AudioManager.setCommunicationDevice`, `BLUETOOTH_CONNECT`.
- **Готово, когда:** звук автоматически уходит в Bluetooth-гарнитуру; переключение Телефон/Динамик/Bluetooth во время звонка; отключение гарнитуры не обрывает звук.

---

## 4. Найденные проблемы в существующем коде KiroX

| ID | Проблема | Где | Риск |
|---|---|---|---|
| K1 | Нет `SetNetworkType` | `telegram/TelegramClient.kt` | медленное восстановление после смены сети |
| K2 | `VoicePlayer` — глобальный синглтон без lifecycle и `AudioFocus` | `ui/chat/VoiceNotes.kt` | звук продолжает играть после выхода, конфликт с музыкой |
| K3 | Видео, GIF, кружки, стикеры — одна заглушка | `TelegramMessageRepository.toModel`, `ChatScreen.MediaPlaceholder` | пользователь не видит медиа |
| K4 | Поиск в чате только по загруженной части | `TelegramChatScreen.shownMessages` | старые сообщения не находятся |
| K5 | `FormattedText` всегда без entities | `TelegramMessageRepository.inputText` / `toModel` | теряется разметка и ссылки |
| K6 | Маршрут звука через устаревший `isSpeakerphoneOn` | `ui/call/CallScreen.kt` | на API 31+ возможен неверный маршрут, нет Bluetooth |
| K7 | APK не собирается воспроизводимо в CI; TDLib `.so` без скрипта сборки (`TDLIB_STATUS.md` §1) | — | невозможно автоматически проверять регрессии |
| K8 | Тесты: 10 unit-файлов, 2 инструментальных; нет тестов репозиториев сообщений | `app/src/test` | регрессии при расширении модели сообщений |

---

## 5. Лицензии и границы использования Yougram

| Объект | Лицензия | Вывод для KiroX |
|---|---|---|
| Собственный код Yougram | **нет лицензии** (нет `LICENSE`; README прямо сообщает, что файла лицензии пока нет) | все права у автора; **копировать нельзя** ни код, ни ресурсы, ни UI. Используются только наблюдения о поведении |
| Java-код и JNI звонков в Yougram (`org/telegram/*`, связка tgcalls) | по происхождению — Telegram-Android (GPL-2.0) / tgcalls (LGPL-3.0) | не переносить: в KiroX уже есть свой JNI-мост, перенос мог бы потянуть GPL-обязательства |
| `yougram.jks` (ключ подписи в git) | — | не использовать, не трогать |
| Сторонние библиотеки, которые есть и у Yougram (Media3, CameraX, Lottie, ZXing) | Apache-2.0 | можно подключать **напрямую из Maven** по их документации; это не перенос кода Yougram |
| TDLib | Boost 1.0 | уже в KiroX (`licenses/TDLib-BSL-1.0.txt`) |
| Движок звонков KiroX (`voip/`) | tgcalls LGPL-3.0, WebRTC BSD-3, FFmpeg LGPL-2.1+, Opus/libvpx BSD, dav1d BSD-2 | уже описано в `THIRD_PARTY_NOTICES.md`; перед релизом проверить условия OpenH264 |

**Всё, что указано как «нужно в KiroX», реализуемо через уже подключённый TDLib 1.8.69 и стандартные Android/Jetpack API без переноса чужого кода.**
