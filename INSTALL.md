# Install M832 Print

[Français](INSTALL.fr.md) · English

M832 Print is an Android print service for the Phomemo M832. It does not appear in the app drawer. Android exposes it in print settings and in the print menu of PDF readers.

## Requirements

- Android 12 or later and a Phomemo M832 paired over Bluetooth.
- A4 thermal paper.
- An app that can open PDFs and use Android's **Print** action.

## Get and install the APK

If an APK is available in this repository's [Releases](https://github.com/Mohtimad/m832-android-print/releases), download it on the phone and open it to install. Android may ask you to allow installation from your browser or file manager.

If no release is available, build the APK from source with Android SDK 35 and a JDK supported by Android Gradle Plugin 8.11.1:

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The build uses `ANDROID_HOME` or a local `local.properties` file to find the Android SDK.

## Enable printing

1. Turn on the M832 and pair it in Android's Bluetooth settings. The paired device should be named **M832**.
2. Open Android **Settings**, search for **Printing**, then open **M832 Print** and enable the service.
3. Grant the **Nearby devices** permission if prompted.
4. Open a PDF, choose **Print**, and select **Phomemo M832**. Review the preview and choose pages, copies, or density in **More options**.
5. Load one A4 thermal sheet for each page. A job waits if the paper is missing or the cover is open, then resumes when the M832 is ready.

The service settings page shows cover and paper state plus the raw hexadecimal printer response. The battery code is shown without a percentage because its meaning has not been confirmed on the M832.

If **Phomemo M832** does not appear, check that the printer is paired and the print service is enabled. If a job waits, check the cover, load a sheet, and confirm that the M832 is powered on.
