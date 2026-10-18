# Releasing Sparely

Releases are built and signed by GitHub Actions (`.github/workflows/release.yml`), so the signing
key never has to be on a laptop or in the repository.

## One-time setup: the release key

1. Create the key (keep it forever; without it no update can install over a previous release).
   **In Android Studio:**
   1. **Build → Generate Signed App Bundle or APK…**, pick **APK**, then **Next**.
   2. Under *Key store path*, click **Create new…**
   3. *Key store path*: somewhere outside the project, e.g. `Documents/sparely-release.jks`.
      Choose a password and confirm it.
   4. *Key*: alias `sparely`, a password (can be the same), validity `25` years or more, and at
      least your name under *Certificate*. Click **OK**.
   5. The key file now exists. Click **Cancel** to close the wizard: GitHub does the signing.

   **Or in a terminal** (Android Studio's *Terminal* tab works):
   ```
   keytool -genkeypair -v -keystore sparely-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias sparely
   ```
2. **Back it up offline** with its passwords (a password manager, a USB key).
3. Turn it into text (in Android Studio's *Terminal* tab, from the folder holding the key):
   - macOS: `base64 -i sparely-release.jks | pbcopy`
   - Linux: `base64 -w0 sparely-release.jks`
   - Windows (PowerShell): `[Convert]::ToBase64String([IO.File]::ReadAllBytes("sparely-release.jks"))`
4. In GitHub: **Settings → Secrets and variables → Actions → New repository secret**, add
   `KEYSTORE_BASE64` (the text), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`sparely`) and `KEY_PASSWORD`.

Avoid `\`, `:` and `=` in the passwords; they're written to a `.properties` file.

## Each release

1. In `app/build.gradle.kts`, raise `versionCode` by one and set `versionName` (e.g. `3.1.0`).
2. Write the notes in `docs/releases/v3.1.0.md` (the tag's name).
3. Commit and push to master, then tag and push the tag:
   ```
   git tag v3.1.0
   git push origin v3.1.0
   ```
   No PC? Ask Claude Code to push the tag, or publish the release yourself on github.com
   (**Releases → Draft a new release**, new tag `v3.1.0`, attach nothing): publishing creates the
   tag, and the workflow then builds the signed APK and adds it to that release.
4. The workflow checks the tag matches `versionName`, builds and signs `Sparely-3.1.0.apk` and opens
   a **draft** release with the notes. Look it over under Releases and press **Publish**.

To try the release build without releasing, open **Actions → Release → Run workflow**. It keeps
the APK as an artifact (debug-signed if the secrets aren't set yet).
