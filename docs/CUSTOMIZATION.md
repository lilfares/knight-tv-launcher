# Customize

Edit `src/me/efesser/flauncher/arabic/MainActivity.java`:

- `TwilightBackground` defines the static gradient.
- `createShell` defines the header and first devices/settings category.
- `appRows` defines artwork, labels and row spacing.
- `focusRow` softens only previous categories; rows share a category tag.
- `wireHorizontal` and `wireVertical` define remote focus navigation.
- Favorites and app-launch counters are stored in the app's private preferences.

Keep work light: resize and cache artwork once, avoid continuous background effects, preserve readable labels and obvious focus outlines, and retain an option to reduce motion.

## Device integration

The inspected firmware uses `com.cultraview.livetv/.MainActivity` with VIEW and integer `sourceId` 9, 10, 11 for HDMI 1, 2, 3. Familiar TV settings use `android.settings.SETTINGS`; full Android settings use `com.android.settings/.homepage.SettingsHomepageActivity`.

These are firmware-specific, not portable Android interfaces. Check and adapt them for other models. Manufacturer binaries are not included.

After changes, increment the manifest version, build with the same private signing key, and test remote navigation, app opening, category scrolling, HDMI return and Home selection. Preserve a source/APK snapshot before installing a new version.
