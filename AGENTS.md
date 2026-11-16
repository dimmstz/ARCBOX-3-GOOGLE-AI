# Custom Agent Instructions for ArcBox

## Push to GitHub Automation

Whenever the user requests **"Push to github"** (or variations like "envia pro github", "faz o push", "publica a release no github"):

1. **Version Bump**:
   - Increment `versionCode` and `versionName` in `app/build.gradle.kts`.
   - Update `README.md` with the new version badge, a new section `## 🚀 Novidades da Versão vX.Y.Z` containing the changelog, and archive previous versions in the history section.

2. **Compilation and APK Generation**:
   - Build both production release APK and debug APK:
     - `gradle :app:assembleRelease :app:assembleDebug`
   - Release APK output: `app/build/outputs/apk/release/app-release.apk` (named `Arcbox-vX.Y.Z-release.apk`).
   - Debug APK output: `app/build/outputs/apk/debug/app-debug.apk` (named `app-debug.apk`).

3. **Source Code Version Control**:
   - Commit all modified source code, assets, configuration, and documentation with a clear commit message.
   - Create an annotated Git tag matching the version (e.g., `vX.Y.Z`).
   - Push the commits to `main` and push the new tag to GitHub.

4. **GitHub Release Publication**:
   - Create a GitHub Release via GitHub API matching the tag `vX.Y.Z`.
   - Include the release notes / novidades in the release description.
   - Upload both APK assets to the release:
     - `Arcbox-vX.Y.Z-release.apk` (Release build)
     - `app-debug.apk` (Debug build)
