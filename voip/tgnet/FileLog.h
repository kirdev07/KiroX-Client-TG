// KiroX stand-in for Telegram's tgnet logger, which the bundled WebRTC sources include.
// Debug traces are dropped.
#pragma once

#define DEBUG_D(...) do {} while (0)
#define DEBUG_E(...) do {} while (0)
#define DEBUG_FATAL(...) do {} while (0)
#define DEBUG_REF(...) do {} while (0)
#define DEBUG_DELREF(...) do {} while (0)
