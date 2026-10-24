# Критические проблемы и приоритеты

[П] подтверждено · [?] требует проверки. Ссылки на подробности: PA = PROJECT_AUDIT, TS = TDLIB_STATUS, BN = BOTTOM_NAVIGATION_AUDIT, UX = UI_UX_AUDIT.

## P0 — исправить до любой работы с реальными аккаунтами
| # | Проблема | Файл / класс | Влияние | Решение | Риск изм. | Тесты |
|---|---|---|---|---|---|---|
| 1 | [П] Сессия TDLib попадает в бэкап, БД не зашифрована | `AndroidManifest.xml`, `TdlibClientManager.sendParameters` | кража сессии через backup | `allowBackup=false`/исключения, ключ через Keystore | средний | перезапуск с шифрованной БД |
| 2 | [П] Клиент TDLib принадлежит ViewModel, не закрывается; [?] второй клиент на ту же БД | `TelegramViewModel`, `TdlibClientManager` | зависание/ошибка авторизации, утечка | application-scoped `TelegramClient` | средний | Back → повторный запуск |
| 3 | [П] После `Closed` клиент не пересоздаётся | `TdlibClientManager.applyAuthorizationState` | невозможно войти после выхода | recreate | низкий | logout → login |
| 4 | [П] Сборка не работает с системной Java 8 | окружение / `gradle.properties` | невоспроизводимая сборка | `org.gradle.java.home` или документация JDK 17 | низкий | `gradlew assembleDebug` |

## P1 — блокирует рабочую версию
| # | Проблема | Где | Решение |
|---|---|---|---|
| 5 | [П] Нет сообщений, контактов, профиля, файлов в реальном режиме | TS §3 | этапы C–D |
| 6 | [П] Папки сливаются в один список | `TdlibClientManager.sameList` | сравнение `ChatListFolder.chatFolderId` |
| 7 | [П] Нет пагинации чатов (100) | `loadAccount` | `LoadChats` до 404 по мере прокрутки |
| 8 | [П] Аватары не загружаются | `mappedChats` | `FileRepository.DownloadFile` |
| 9 | [П] Пустой список = вечная «Загрузка» | `TelegramMainScreen` | отдельные состояния loading/empty/error |
| 10 | [П] Навигация без back stack, `remember` вместо saveable | `KiroXApp` | Navigation с графами |
| 11 | [П] DEMO доступен в production-входе, блокирует переход в реальный режим | `PhoneLoginScreen`, `KiroXApp` | флаг сборки / flavor |
| 12 | [П] Terms of Service не показываются при регистрации | `TelegramAuthStepScreen` | показать `termsOfService.text` |
| 13 | [П] QR-вход недостижим | `TdlibClientManager` | `RequestQrCodeAuthentication` + QR-рендер |
| 14 | [П] lint: `windowLightNavigationBar` на API 23–26 | `styles.xml` | `values-v27` |

## P2 — качество и UX
| # | Проблема | Где |
|---|---|---|
| 15 | [П] 4-сторонняя рамка, разная высота/иконки двух нижних панелей, нет press-state | BN |
| 16 | [П] Захардкоженные «Кирилл/@username/Создаю KiroX» | `DemoMainScreen.CenterIdentity/ProfileScreen` |
| 17 | [П] Нет токенов, ~30 разных литералов цветов, нет Graphite Dark, M3-компоненты с дефолтной палитрой | UX §7 |
| 18 | [П] Long-press архивирует без подтверждения/undo | `DemoMainScreen.ChatRow` |
| 19 | [П] Глобальный свайп вкладок конфликтует с back-жестом и горизонтальными списками | `DemoMainScreen` |
| 20 | [П] Пересортировка всего списка на каждый update | `publishChats` |
| 21 | [П] Flow без `remember`, O(n²) в чате | `ChatScreen.kt` |
| 22 | [П] Потеря позиции списка после выхода из чата | `DemoMainScreen` |
| 23 | [П] Только arm64 `.so` | `jniLibs` |
| 24 | [П] Время звонков без даты | `CallRow` |
| 25 | [?] Клавиатура перекрывает поля на Контактах | `ContactsScreen` |

## P3 — техдолг
Мёртвый код (`StartScreen`, `DemoHomeScreen`, `AnimatedSplashScreen`, `DemoContent`, `Tile`); `isMinifyEnabled=false` без keep-правил; нет иконки приложения; нет Baseline Profile; устаревшие зависимости (lint `GradleDependency`); `SimpleDateFormat` на строку; 10 sp подписи; проверить уведомления лицензий для библиотек, слинкованных в `libtdjni.so`.

## Оценки готовности (экспертные, не измерения)
| Область | % | Критерий |
|---|---|---|
| Ввод номера / выбор страны | 90 | работает, покрыто тестами; нет только мелкой полировки |
| Авторизация TDLib | 60 | 7 из 9 состояний обрабатываются; нет QR, ToS, logout, recreate |
| TDLib-данные (чаты/сообщения/контакты/файлы/звонки) | 10 | только список чатов без пагинации/папок/аватаров |
| Архитектура под TDLib | 30 | есть изоляция `TdApi` от UI и репозиторий-прокси; нет владельца клиента, DI, навигации |
| UI-прототип (DEMO) | 65 | все разделы свёрстаны, чат проработан; большинство действий — заглушки, нет темы/токенов |
| Нижняя панель | 40 | см. BN, 4/10 |
| Безопасность хранения | 35 | секреты вне git, нет логов TDLib; но backup + незашифрованная БД |
| Тесты | 20 | 12 unit (половина — DEMO), 0 тестов TDLib-логики |
| **Проект в целом к рабочей версии** | **≈ 25** | средневзвешенно; основная работа — этапы A–D |
