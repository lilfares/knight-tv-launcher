# Knight TV Launcher — واجهة التلفزيون

A small, Arabic-first Android TV home screen tailored to the Dansat DTD58BU running Android 11. Version 1.2.

This is a separate native companion inspired by FLauncher, not the official FLauncher or an update to its Flutter application.

## Features

- Right-to-left Arabic layout and remote arrow/ENTER navigation.
- Static violet twilight background and optional focus animation.
- Favorites with manual ordering and locally tracked frequently opened apps.
- HDMI 1–3, familiar manufacturer settings, and full Android settings shortcuts.
- Devices/settings are the first category and scroll with the page.
- Earlier categories soften; neighboring rows within the active category stay clear.
- Complete artwork fitted inside tiles, with app labels kept readable.
- No network permission, analytics, third-party libraries, background service, or boot receiver.

## Documentation

- [Windows build instructions](docs/BUILD.md)
- [TV installation, debugging and Home setup](docs/INSTALL.md)
- [Controls and customization](docs/CUSTOMIZATION.md)
- [Changes and verification](CHANGELOG.md)
- [License and attribution](NOTICE.md)

The HDMI and manufacturer-settings integration is specific to the inspected Dansat/Cultraview firmware. Other televisions may require different components or source IDs.

## Controls

Arrows navigate; ENTER opens. MENU or holding ENTER opens an app's favorites/options menu. EXIT returns upward through the interface. The app does not remap Source, volume, channel, Netflix or YouTube buttons. Home opens this interface only when it has been selected as the system Home app.

## Source layout

`src/` contains the native Java activity; `res/` contains themes and launcher artwork. `AndroidManifest.xml` declares the app and Home handler. `build.py` creates and verifies a signed APK using the local Android SDK and JDK, without Gradle or Flutter.

Signing keys, personal device details, manufacturer binaries, and local build outputs are excluded from this repository.
