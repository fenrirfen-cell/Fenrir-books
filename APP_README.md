# Fenrir Books Android

Standalone book downloader with a native dark-and-gold interface. Search public-domain Project Gutenberg books through Gutendex, or configure an authorized HTTPS Fenrir Books API. Add authorized EPUB/PDF direct links, track downloads and open downloaded files in an installed reader. The app does not implement a website access-control bypass.

## APK

Open the repository Actions tab, select **Build Fenrir Books APK**, then download the **Fenrir-Books** artifact after a successful run. Unzip and install Fenrir-Books.apk. The release build is signed using the runner's development key; updates need a stable release signing key configured before distribution.

Use Android Studio or Gradle 8.9 with Android SDK 35 to build locally: `gradle :app:assembleRelease`.
