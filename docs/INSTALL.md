# Install and configure

1. Download Google's official Android SDK Platform Tools for Windows and extract them.
2. Connect the PC and TV to the same trusted local network. Find the TV's current IP in its network settings.
3. Enable Developer options, usually by selecting Build number seven times in Android About. On this Dansat firmware the manufacturer menu hides some Android settings; the actual Android Settings About and Developer options screens were needed. Menu names vary by firmware.
4. Enable USB debugging. Enable network debugging only if the TV offers it. USB debugging alone does not guarantee that ADB listens over Wi-Fi. Do not use factory/service menus or change unrelated options.
5. If network ADB is available, use the TV's current address in place of TV_IP:

```powershell
.\adb.exe connect TV_IP:5555
.\adb.exe devices -l
```

Approve the debugging authorization on the TV. Continue only when its state is `device`, not `unauthorized` or `offline`. Android wireless pairing, if provided, uses the pairing and connection addresses shown on the TV rather than necessarily port 5555. Do not expose ADB through the router to the internet.

6. Build the APK, then install and open it:

```powershell
.\adb.exe -s TV_IP:5555 install -r "dist\arabic-tv-1.2.apk"
.\adb.exe -s TV_IP:5555 shell am start -n me.efesser.flauncher.arabic/.MainActivity
```

7. Check arrow navigation, app opening, settings, HDMI ports and EXIT. Only after deciding to make this your Home app, run:

```powershell
.\adb.exe -s TV_IP:5555 shell cmd package set-home-activity --user 0 me.efesser.flauncher.arabic/.MainActivity
.\adb.exe -s TV_IP:5555 shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
.\adb.exe -s TV_IP:5555 shell input keyevent 3
```

8. Test HDMI → EXIT → confirm exit, then turn the TV off/on. On the inspected firmware HDMI exit explicitly starts the selected Home app, so the Home selection is necessary for consistent return behavior.

## Restore the previous Home app

Record the previous Home component before changing it. Select it again using the TV's default-app chooser, or use `cmd package set-home-activity --user 0 PREVIOUS_COMPONENT`. On the original setup official FLauncher was `me.efesser.flauncher/.MainActivity`; verify that it is still installed before selecting it.

No disabling or uninstalling launchers or system packages is required. Updating this app with its original signing key preserves its preferences. A downgrade to an older version code may be rejected; do not uninstall casually, because uninstalling deletes favorites and local usage counts.
