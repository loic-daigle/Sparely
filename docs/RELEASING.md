# Releasing Sparely

Releases are built and signed by GitHub Actions (`.github/workflows/release.yml`), so the signing
key never has to be on a laptop or in the repository.

## One-time setup: the release key

1. Create the key (keep it forever; without it no update can install over a previous release):
   ```
   keytool -genkeypair -v -keystore sparely-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias sparely
   ```
2. **Back it up offline** with its passwords (a password manager, a USB key).
3. Turn it into text:
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
   No PC? Create the tag on github.com: **Releases → Draft a new release → Choose a tag → type
   v3.1.0 → Create new tag**, then close that draft without publishing; the workflow makes its own.
4. The workflow checks the tag matches `versionName`, builds and signs `Sparely-3.1.0.apk` and opens
   a **draft** release with the notes. Look it over under Releases and press **Publish**.

To try the release build without releasing, open **Actions → Release → Run workflow**. It keeps
the APK as an artifact (debug-signed if the secrets aren't set yet).
