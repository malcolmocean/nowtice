# Test Notification Button

## Problem

Users can't preview what their ping notification will look and feel like without waiting for a stochastic alarm to fire. They also can't trigger the notification permission prompt on demand.

## Design

Add a "Test Notification" button to the per-ping settings screen that fires a real notification using the current ping's configuration (icon, color, title, message, vibration pattern).

### Approach

Extract `showNotification` and `triggerVibration` from `PingReceiver` into a `NotificationHelper` object. Both `PingReceiver` and the test button call this shared code. The test button does NOT reschedule the next ping.

### Behavior

1. User taps "Test Notification" in a ping's settings.
2. If `POST_NOTIFICATIONS` permission is not granted (Android 13+), request it. Do not fire the notification until permission is granted.
3. Show the notification using the current in-memory config (not the persisted one), so the user sees exactly what they've configured even before saving.
4. Vibration pattern plays as configured.

### UI Placement

After the vibration pattern picker, before the delete button. Styled as an `OutlinedButton` consistent with the existing settings UI.

### Files Changed

- `NotificationHelper.kt` (new) — extracted notification + vibration logic
- `PingReceiver.kt` — delegates to `NotificationHelper`
- `MainActivity.kt` — adds test button, handles permission for test flow
