# 🐴 Poulinage

**A foaling alarm for horse breeders, running on an old Android phone.**

Foaling usually happens at night and goes fast, and a mare in difficulty needs someone there within minutes. Poulinage turns a phone attached to the mare's halter into a monitor: when her movements match the restlessness and rolling that come with foaling, the breeder gets an SMS straight away.

It was built for a breeder and is used in production. The client wanted to reuse phones they already owned, many of them old and low-end, so the app is written in **native Kotlin** to stay light and reliable on that hardware (Android 8.1 and up).

<!-- Screenshots: add 2–3 images here, e.g. home, history and settings screens. -->

## How it works

The app runs as a foreground service with a wake lock, so it keeps monitoring with the screen off and is restarted by the system if it gets killed.

Two detectors run side by side on a sliding time window:

| Sensor | Watches for | Default trigger |
|---|---|---|
| Accelerometer | Sudden, repeated jolts (agitation, pawing, going down) | 3 peaks above 1.8 g within 10 s |
| Gyroscope | Sustained rotation (the mare rolling) | 6 samples above 2 rad/s within 10 s |

When either one fires:

1. The event is saved to the in-app alert history.
2. An SMS is sent to the configured number with the alert type, time and sensor reading.
3. Cooldowns prevent a flood of messages: alerts are spaced at least 5 s apart, SMS at least 30 s apart.

The breeder also receives an SMS when monitoring is stopped or the app is closed, so a silent phone never passes for a calm night.

Every threshold can be tuned from the settings screen, and live sensor values are shown to help calibrate on a given mare.

## Reliability

- **SMS delivery tracking**: each send is confirmed through Android's `SMS_SENT` callback, and failures (no signal, radio off) are reported.
- **Remote logging**: errors and delivery results are posted to a small logging API, so problems in the field can be diagnosed without touching the phone.
- **Offline queue**: logs captured without network coverage (common in stables) are queued on the device and flushed automatically when the connection comes back.
- **Hardware fallbacks**: if the phone has no gyroscope, rotation detection is turned off and the accelerometer keeps working.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Android foreground service, `SensorManager`, `SmsManager`
- Min SDK 27 (Android 8.1), target SDK 36

## Build

Requirements: Android Studio (or JDK 17+ and the Android SDK).

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. The phone needs a SIM card that can send SMS. The app asks for SMS and notification permissions on first launch.

> The app's interface is in French.
