# Сторонние компоненты и лицензии

Этот документ поясняет происхождение зависимостей, **не изменяя** их оригинальных лицензий. Лицензия [Apache 2.0](LICENSE) распространяется на оригинальные компоненты kiroX, права на которые принадлежат соответствующим авторам/участникам проекта. Это **не переоформляет** TDLib или другие сторонние компоненты под Apache 2.0.

## TDLib — Boost Software License 1.0

**Источник:** [tdlib/td](https://github.com/tdlib/td)  
**Лицензия:** [Boost Software License 1.0](licenses/TDLib-BSL-1.0.txt)  
**Включённые компоненты:**
- app/src/main/java/org/drinkless/tdlib/Client.java
- app/src/main/java/org/drinkless/tdlib/TdApi.java
- app/src/main/jniLibs/arm64-v8a/libtdjni.so

TDLib обеспечивает взаимодействие с Telegram. Авторские уведомления её разработчиков сохраняются и не заменяются именем автора kiroX. Сама библиотека независима от оригинального кода приложения. При подготовке релизного APK нужно отдельно проверить **все** библиотеки, с которыми слинкована конкретная нативная сборка TDLib, и их условия распространения.

## Pacifico — SIL Open Font License 1.1

**Источник:** [Google Fonts / Pacifico](https://github.com/googlefonts/Pacifico)  
**Лицензия:** [SIL OFL 1.1, полный текст в проекте](app/src/main/assets/licenses/Pacifico-OFL.txt)  
**Файл:** app/src/main/res/font/pacifico_regular.ttf

Авторские права на шрифт сохраняются за авторами Pacifico. Применение шрифта не означает передачу прав на него проекту kiroX.

## ZXing — Apache License 2.0

**Источник:** [zxing/zxing](https://github.com/zxing/zxing)  
**Лицензия:** [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)  
**Зависимость:** `com.google.zxing:core:3.5.4` (Gradle)

Используется для отображения QR-кода при входе в Telegram. Авторские права принадлежат авторам ZXing (ZXing authors).

## Движок звонков (папка `voip/`)

Голосовые и видеозвонки работают через библиотеку `libkiroxvoip.so`, которая собирается из исходников в папке `voip/`. Компоненты сохраняют свои оригинальные лицензии; на них **не распространяется** Apache 2.0. Сборочный и JNI-код Telegram для Android (GPL) в проект не переносился.

**Оригинальный код kiroX (Apache 2.0):** `voip/kirox/` (JNI-мост), `voip/CMakeLists.txt`, `voip/tgnet/FileLog.h` (заглушка логгера), `app/src/main/java/com/kirox/app/voip/`.

| Компонент | Путь | Лицензия | Текст лицензии |
|---|---|---|---|
| tgcalls (Telegram) | `voip/tgcalls/tgcalls` | GNU LGPL 3.0 | `voip/tgcalls/LICENSE` |
| WebRTC (C++ и Java `org.webrtc`) | `voip/voip/webrtc`, `app/src/main/java/org/webrtc` | BSD 3-Clause + патентный грант | `voip/voip/webrtc/LICENSE`, `voip/voip/webrtc/PATENTS` |
| Abseil | `voip/third_party/absl` | Apache 2.0 | `voip/third_party/absl/LICENSE` |
| libyuv | `voip/third_party/libyuv` | BSD 3-Clause | `voip/third_party/libyuv/LICENSE` |
| libsrtp | `voip/third_party/libsrtp` | BSD 3-Clause | `voip/third_party/libsrtp/LICENSE` |
| usrsctp | `voip/third_party/usrsctplib` | BSD 3-Clause | `voip/third_party/usrsctplib/LICENSE.md` |
| PFFFT | `voip/third_party/pffft` | BSD-подобная (FFTPACK) | `voip/third_party/pffft/LICENSE` |
| RNNoise | `voip/third_party/rnnoise`, `voip/voip/rnnoise` | BSD 3-Clause | `COPYING` в этих папках |
| Opus (заголовки и сборка `libopus.a`) | `voip/third_party/xiph` | BSD 3-Clause | `voip/third_party/xiph/opus/COPYING` |
| CRC32C | `voip/voip/third_party/crc32c` | BSD 3-Clause | `voip/voip/third_party/crc32c/LICENSE` |
| libevent | `voip/voip/third_party/libevent` | BSD 3-Clause | `voip/voip/third_party/libevent/LICENSE` |
| jni_zero (Chromium) | `voip/voip/third_party/jni_zero` | BSD 3-Clause | `voip/voip/third_party/jni_zero/LICENSE` |
| BoringSSL (заголовки и `libcrypto.a`, `libssl.a`) | `voip/third_party/boringssl` | OpenSSL / ISC | `voip/third_party/boringssl/LICENSE` |
| FFmpeg (заголовки и `libav*.a`, `libsw*.a`, собраны без `--enable-gpl`) | `voip/third_party/ffmpeg` | GNU LGPL 2.1+ | `voip/third_party/ffmpeg/COPYING.LGPLv2.1`, `LICENSE.md` |
| libvpx (заголовки и `libvpx.a`) | `voip/third_party/libvpx` | BSD 3-Clause | `voip/third_party/libvpx/LICENSE` |
| dav1d (заголовки и `libdav1d.a`) | `voip/third_party/dav1d` | BSD 2-Clause | `voip/third_party/dav1d/COPYING` |
| OpenH264 (заголовки и `libopenh264.a`) | `voip/third_party/openh264` | BSD 2-Clause | `voip/third_party/openh264/LICENSE` |
| tde2e / tdutils (часть TDLib, `libtde2e.a`, `libtdutils.a`) | `voip/td/tde2e` | Boost Software License 1.0 | [licenses/TDLib-BSL-1.0.txt](licenses/TDLib-BSL-1.0.txt) |

Готовые статические библиотеки в `voip/prebuild/lib/arm64-v8a` взяты из сборки этих же проектов (скрипты сборки Telegram для Android). tgcalls и FFmpeg распространяются по LGPL: исходники tgcalls и наш JNI-мост лежат в репозитории, поэтому `libkiroxvoip.so` можно пересобрать с изменённой версией этих библиотек. Перед публикацией релиза нужно отдельно проверить условия OpenH264 (патентная лицензия Cisco действует только для бинарников, скачанных у Cisco) и сверить версии всех компонентов.

## Зависимости Android и тестов

Через Gradle используются AndroidX, Jetpack Compose, Kotlin, Room, DataStore, Google libphonenumber, JUnit и компоненты тестирования. Они подключаются как самостоятельные сторонние зависимости и регулируются условиями их правообладателей.

Перед распространением сборок необходимо создать проверенный отчёт по **конкретным версиям** всех прямых, транзитивных и нативных зависимостей и включить требуемые уведомления. Данный файл — исходный перечень, **не окончательный лицензионный аудит**.

## Telegram и обозначения

kiroX — независимый сторонний клиент. Telegram, его название, знаки и сервисы принадлежат соответствующим правообладателям; проект не является официальным продуктом Telegram и не аффилирован с ним.

Если происхождение отдельного исходного файла вызывает сомнения, нельзя автоматически считать его оригинальным кодом kiroX: прежде следует установить источник и лицензию.
