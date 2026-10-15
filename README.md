# M832 Print

[Français](README.fr.md) · English

**Installation:** [English](INSTALL.md) · [Français](INSTALL.fr.md)

An independent Android print service for the Phomemo M832 A4 thermal printer. It prints PDFs directly over Bluetooth without the Phomemo app. Printing has been tested with a Pixel 9 and a real M832.

## Use

1. Pair the M832 with Android over Bluetooth.
2. Enable **M832 Print** in Android's print settings.
3. Open a PDF in an app that supports Android printing, choose **Print**, then select **Phomemo M832**.

Android provides the preview, page selection, and copy count. **More options** opens a native dialog with Light, Medium, and Dark density choices. The service advertises A4 monochrome printing only. It has no launcher icon; its status screen is available through the print service settings.

The service checks the cover and paper before each page. If the cover is open or a sheet is missing, the Android print job waits and resumes when the printer is ready. A two-page PDF was printed on two separate sheets, including the wait for the second sheet. A job started with the cover open resumed after the cover was closed.

## Status and limitations

The status screen shows the cover, paper, and raw hexadecimal printer response. On the tested M832, `1A 05 98` means cover closed and `1A 05 99` means cover open; `1A 06 89` means paper present and `1A 06 88` means paper absent. The meaning of `1A 04 NN` has not been confirmed for battery level on the M832, so the app shows the raw code without a percentage.

The three density settings and print counts above one copy have not yet been compared on the physical printer. Compatibility with other printer models has not been tested.

## Privacy

The app requests only **Nearby devices** (`BLUETOOTH_CONNECT`). It has no Internet permission, account, location permission, or broad file access. Android supplies the PDF to the print service; the service makes a temporary private copy for rendering and removes it after the job.

## Build

Use a JDK supported by Android Gradle Plugin 8.11.1 and install Android SDK 35. Set the SDK path through `ANDROID_HOME` or an untracked `local.properties` file, then run:

```sh
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, then enable the print service in Android settings. The app targets Android 15 and supports Android 12 or later.

This is an unofficial project and is not affiliated with Phomemo.

Licensed under [MIT](LICENSE).
