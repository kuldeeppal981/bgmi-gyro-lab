# BGMI Gyro Lab

Standalone Android app to measure gyroscope delay, capture a still-point, and practice gyro aim.

This does **not** inject into BGMI, overlay the game, or make the sensor faster. BGMI reads the gyro itself. Use this lab to see how laggy the phone is, calibrate a zero-point, then warm up.

## What you get

- **Delay test** — hold still, then flick. Reports sample rate (Hz), still bias, jitter, dropouts, motion-to-display latency, and a Good / Playable / High delay grade for ADS feel.
- **Calibrate** — 3 second still capture, deadzone, landscape vs portrait mapping, invert yaw/pitch. Saved on the phone for the trainer.
- **Aim trainer** — hold still, track, and snap drills using calibrated integration (`angle += omega * dt`).
- **BGMI tips** — refresh rate, battery optimization, smoothing, and how to interpret a high-delay grade.

## Open and build (Android Studio)

1. Install [Android Studio](https://developer.android.com/studio) (Koala or newer) with the Android SDK (API 35) and a JDK 17.
2. **File → Open** this folder: `C:\Users\kulde\bgmi-gyro-lab`
3. Let Gradle sync. If asked for a Gradle wrapper JAR, use **File → New → New Project** wrapper or Android Studio’s “generate wrapper” prompt. Wrapper properties already pin **Gradle 8.9**.
4. Connect your phone with **USB debugging** on (`Settings → Developer options → USB debugging`).
5. Select the device and click **Run**.

### Install the debug APK without the Run button

After a successful build:

```
app\build\outputs\apk\debug\app-debug.apk
```

Copy that APK to the phone and open it (allow install from this source), or from a PC with platform-tools:

```
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

On the phone, allow **Install unknown apps** for Files or for your computer, depending how you copy it.

## In-app order

1. Run **Delay test** (still 5s, flick when prompted).
2. **Calibrate** still-point and axis check, hold the phone like BGMI (landscape).
3. Use **Trainer** before a session. If snap overshoots, lower ADS gyro in BGMI — this app cannot cut hardware delay.

## Project layout

- `app/src/main/java/com/bgmigyrolab/app/gyro/` — `GyroRepository` (`SENSOR_DELAY_FASTEST` + Choreographer vsync), delay math, aim integrator
- `app/src/main/java/com/bgmigyrolab/app/data/` — DataStore calibration
- `app/src/main/java/com/bgmigyrolab/app/ui/screens/` — home, delay, calibrate, trainer, tips
