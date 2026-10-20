# Build on Windows

Requirements: Python 3, JDK 17+, Android SDK platform 35, and build-tools 35.0.0. Obtain Android SDK tools from Google's official Android developer site and install the stated SDK components first.

From the repository directory, run in PowerShell (replace paths):

```powershell
python build.py --sdk "C:\path\to\Android\Sdk" --jdk "C:\path\to\jdk-17" --keystore "C:\private\arabic-tv-signing.jks" --out "dist\arabic-tv-1.2.apk" --work "build"
```

The script compiles resources and Java, creates DEX, aligns the APK, signs it, and verifies its signature. It creates a local development signing identity if the specified keystore does not exist. See build.py for its development signing configuration; this is not a production key-management system.

Keep the keystore outside the repository and back it up privately. Future updates to an installed app must use the same signing identity. A build using a new key cannot update the existing installation directly.

Do not commit keystores, passwords, SDK directories, or generated build files. The original installation's signing key is deliberately not included.
