# M832 Print

Français · [English](README.md)

**Installation :** [Français](INSTALL.fr.md) · [English](INSTALL.md)

Service d'impression Android indépendant pour l'imprimante thermique A4 Phomemo M832. Il imprime les PDF directement en Bluetooth, sans l'application Phomemo. L'impression a été testée sur un Pixel 9 avec une M832 réelle.

## Utilisation

1. Associer la M832 à Android en Bluetooth.
2. Activer **M832 Print** dans les réglages d'impression Android.
3. Ouvrir un PDF dans une application compatible avec l'impression Android, choisir **Imprimer**, puis **Phomemo M832**.

Android fournit l'aperçu, le choix des pages et le nombre de copies. **Plus d'options** ouvre une boîte de dialogue native avec les densités Claire, Modérée et Épaisse. Le service annonce uniquement l'impression A4 en noir et blanc. Il n'a pas d'icône dans le menu des applications ; son écran d'état est accessible depuis les réglages du service d'impression.

Le service vérifie le capot et le papier avant chaque page. Si le capot est ouvert ou si une feuille manque, le travail Android attend et reprend lorsque l'imprimante est prête. Un PDF de deux pages a été imprimé sur deux feuilles distinctes, avec attente de la seconde feuille. Un travail lancé capot ouvert a repris après la fermeture du capot.

## État et limites

L'écran d'état affiche le capot, le papier et la réponse hexadécimale brute. Sur la M832 testée, `1A 05 98` signifie capot fermé et `1A 05 99` capot ouvert ; `1A 06 89` signifie papier présent et `1A 06 88` papier absent. La signification de `1A 04 NN` pour le niveau de batterie n'est pas confirmée sur la M832 : l'application affiche ce code sans pourcentage.

Les trois densités et les tirages de plusieurs copies n'ont pas encore été comparés sur l'imprimante réelle. La compatibilité avec d'autres modèles n'a pas été testée.

## Vie privée

L'application demande uniquement la permission **Appareils à proximité** (`BLUETOOTH_CONNECT`). Elle ne demande ni accès Internet, ni compte, ni localisation, ni accès global aux fichiers. Android transmet le PDF au service d'impression, qui en fait une copie temporaire privée pour le rendu et la supprime après le travail.

## Compilation

Utiliser un JDK compatible avec Android Gradle Plugin 8.11.1 et installer le SDK Android 35. Configurer le chemin du SDK avec `ANDROID_HOME` ou un fichier `local.properties` non suivi, puis lancer :

```sh
./gradlew :app:assembleDebug
```

L'APK de développement est créé dans `app/build/outputs/apk/debug/app-debug.apk`. L'installer avec `adb install -r app/build/outputs/apk/debug/app-debug.apk`, puis activer le service d'impression dans les réglages Android. L'application cible Android 15 et fonctionne à partir d'Android 12.

Pour créer une version signée, conserver la clé privée et ses identifiants ensemble hors du dépôt, puis lancer `python3 scripts/build_release.py /chemin/vers/credentials.txt`. L'APK signé se trouve dans `app/build/outputs/apk/release/app-release.apk`. Sauvegarder les deux fichiers de signature : les prochaines mises à jour devront utiliser la même clé. Une version signée avec une nouvelle clé ne peut pas remplacer directement une installation de développement ; il faut d'abord désinstaller cette dernière.

Ce projet est indépendant et n'est pas affilié à Phomemo.

Projet sous [licence MIT](LICENSE).
