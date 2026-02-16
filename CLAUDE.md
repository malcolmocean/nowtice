# CLAUDE.md

## Project

Nowtice — Android app for stochastic mindfulness pings. Kotlin, Gradle build. Package: `com.malcolmocean.nowtice`.

Supports multiple independent pings, each with its own config, icon, and color. Uses AlarmManager for scheduling stochastic notification timing.

Key source files in `app/src/main/java/com/malcolmocean/nowtice/`:
- `MainActivity.kt` — UI with tabbed multi-ping management
- `PingScheduler.kt` — stochastic scheduling logic
- `PingReceiver.kt` — handles alarm broadcasts
- `Settings.kt` — per-ping settings
- `BootReceiver.kt` — re-schedules pings on device boot

Maestro UI test flows live in `flows/`. TODO list lives in `TODO.md`.

## Working with Malcolm

- Don't ask unnecessary questions. Make reasonable decisions and move. If the answer is obvious or a matter of taste that Malcolm hasn't opinionated on, just pick.
- Specifically for the maestro dev loop: always run in background, pick the appropriate flow file yourself.
- Malcolm values agency and directness — do the thing rather than asking permission to do the thing.
