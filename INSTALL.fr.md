# Installer M832 Print

Français · [English](INSTALL.md)

M832 Print est un service d'impression Android pour la Phomemo M832. Il n'apparaît pas dans le menu des applications. Android le présente dans les réglages d'impression et dans le menu d'impression des lecteurs PDF.

## Prérequis

- Android 12 ou plus récent et une Phomemo M832 associée en Bluetooth.
- Du papier thermique A4.
- Une application capable d'ouvrir les PDF et d'utiliser l'action **Imprimer** d'Android.

## Obtenir et installer l'APK

Si un APK est disponible dans les [Releases](https://github.com/Mohtimad/m832-android-print/releases) du dépôt, télécharge-le sur le téléphone et ouvre-le pour l'installer. Android peut demander d'autoriser l'installation depuis le navigateur ou le gestionnaire de fichiers.

Si aucune version n'est publiée, compile l'APK depuis les sources avec le SDK Android 35 et un JDK compatible avec Android Gradle Plugin 8.11.1 :

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

La compilation utilise `ANDROID_HOME` ou un fichier `local.properties` local pour trouver le SDK Android.

## Activer l'impression

1. Allume la M832 et associe-la dans les réglages Bluetooth d'Android. L'appareil associé doit s'appeler **M832**.
2. Ouvre les **Paramètres** Android, recherche **Impression**, puis ouvre **M832 Print** et active le service.
3. Accorde la permission **Appareils à proximité** si Android la demande.
4. Ouvre un PDF, choisis **Imprimer**, puis **Phomemo M832**. Vérifie l'aperçu et choisis les pages, les copies ou la densité dans **Plus d'options**.
5. Charge une feuille thermique A4 pour chaque page. Le travail attend si le papier manque ou si le capot est ouvert, puis reprend lorsque la M832 est prête.

La page de réglages du service affiche l'état du capot, du papier et la réponse hexadécimale brute. Le code de batterie apparaît sans pourcentage, car sa signification n'a pas été confirmée sur la M832.

Si **Phomemo M832** n'apparaît pas, vérifie que l'imprimante est associée et que le service d'impression est activé. Si un travail reste en attente, vérifie le capot, charge une feuille et assure-toi que la M832 est allumée.
