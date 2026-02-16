# Nowtice

Stochastic mindfulness pings for Android. Nudges you at random intervals to check in with yourself.

Inspired by [TagTime](https://github.com/tagtime/TagTime), but stripped down to just the notification — no logging or tagging.

## Settings

- **Average interval** — mean time between pings (default: 45 min). Uses an exponential distribution so pings are memoryless/unpredictable.
- **Quiet hours** — no pings during this window (default: 10pm–8am).
- **Message** — what the notification says (default: "notice the vividness of reality").

## Building

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Requires Android SDK and JDK 17.

## Package

`com.malcolmocean.nowtice`
