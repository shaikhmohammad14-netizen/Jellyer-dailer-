# Jelly Dialer

Liquid-glass phone app for Android (Kotlin + Jetpack Compose). Built for the vivo V70 Elite (OriginOS 6), works on Android 10+.

- Default dialer role, InCallService, call screening service
- Incoming and in-call screens in iOS, ColorOS and One UI styles
- Calls post a standard call notification (CallStyle), so OriginOS shows them in its own call banner/island
- Tabs at the bottom: Favorites, Recents, Keypad, AI, Settings
- Themes, accent colors, backgrounds, glass transparency and jelly sliders
- AI: on-device spam rules, optional online model (add your API key in Settings)
- Deep links to system settings (sound, ringtone, blocked numbers, SIM, voicemail, full-screen alerts and more)

## Build on GitHub
1. Upload everything in this folder to the root of a new repo (keep the `.github` folder).
2. Open the **Actions** tab, run **Build APK** (it also runs on every push).
3. When it finishes, download **JellyGlassDialer-debug-apk** from the run page, unzip and install the APK.

## First run on the phone
1. Open the app and tap **Grant access**.
2. Settings → **Set as default dialer**.
3. Settings → **Enable spam call screening** (optional).
4. Settings → **Full-screen call alerts**: allow it, so incoming calls show over the lock screen.
