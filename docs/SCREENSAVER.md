# Screensaver (version 1.3)

Open خيارات الواجهة → شاشة الاستراحة.

- Enabled by default, after 5 minutes idle on Home.
- Wait options: 1, 3, 5, or 10 minutes.
- Image changes: 30, 60, 120 (default), or 300 seconds.
- معاينة الآن previews immediately; the enable setting can turn automatic activation off.
- Clock uses 24-hour English numerals, updated at each minute boundary.
- Arabic Gregorian and Umm al-Qura Hijri date lines also use English numerals. Both follow the TV date and time zone. Hijri dates can differ from local moon-sighting announcements.
- The first received key press, touch gesture, or pointer movement dismisses the screen and restores the previous Home focus. The dismissing key's repeat and key-up events are consumed too.
- No overlay over HDMI or other apps. Activity pause/window focus loss cancels idle and slide timers, hides the screensaver, and releases its displayed image. Returning to Home starts a fresh idle countdown. Settings dialogs also suspend it.

## Lightweight operation

Six AI-generated scenes are packed into one 2-column × 3-row PNG atlas. Android decodes one region at a time on a single worker; the atlas is never decoded as a full bitmap. Images use RGB565. The display has a short fade-in unless Reduce motion is enabled. There is no continuous pan/zoom, network access, service, or startup receiver. Keep-screen-on applies only while the ambient screen is visible; system display timeout settings are not changed.

Scenes: alpine lake, waterfront city, Mediterranean coast, Arabian dunes, forest, and autumn mountain lake. These are generated illustrations of scenery, not documentary location photos.

## Code

AmbientScreen.java owns timer, clock/date rendering, image decoding, and dismissal. MainActivity.java forwards activity focus/lifecycle and input events. To replace scenes, preserve the exact equal 2 × 3 atlas grid with 16:9 cells; add no gutters, labels, or baked-in clock text.

Version 1.2 APK/source remain backed up locally. Keep the original private signing key for updates; it is never uploaded.
