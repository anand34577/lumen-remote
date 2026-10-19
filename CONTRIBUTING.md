# Contributing

Thanks for wanting to help. Bug reports from real TVs are the most useful thing you can send, because every manufacturer bends the protocol a little differently.

## Reporting a problem

Open an [issue](https://github.com/anand34577/lumen-remote/issues/new/choose) and include your TV model, phone, Android version and the app version from Settings > About.

## Working on the code

You need JDK 17 and the Android SDK (Android Studio sets both up).

```bash
./gradlew testDebugUnitTest lintDebug   # what CI runs
./gradlew installDebug                  # put it on a connected phone
```

How the code is laid out:

| Folder | What lives there |
|---|---|
| `protocol/` | The Android TV Remote v2 messages, encoded by hand (no protobuf plugin) |
| `connection/` | Pairing, the control session, reconnects and Wake-on-LAN. `TvController` is the one place the UI talks to |
| `discovery/` | Finding TVs with mDNS |
| `data/` | Saved TVs, shortcuts, macros and settings (DataStore) |
| `service/` | Notification remote, widget and Quick Settings tile |
| `ui/` | Jetpack Compose screens and the glass design components |

### Testing against a real TV

`LiveTvTest` talks to an actual TV and is skipped unless you point it at one:

```bash
TV_HOST=192.168.1.20 TV_STAGE=probe   ./gradlew testDebugUnitTest --tests '*LiveTvTest*' --rerun
TV_HOST=192.168.1.20 TV_STAGE=pair TV_CODE_FILE=code.txt ./gradlew testDebugUnitTest --tests '*LiveTvTest*' --rerun
TV_HOST=192.168.1.20 TV_STAGE=control ./gradlew testDebugUnitTest --tests '*LiveTvTest*' --rerun
```

During the `pair` stage, write the code from the TV screen into `code.txt`. The `control` stage toggles mute twice.

## Pull requests

- Keep each PR to one change, and say how you tested it.
- Match the style of the code around you. No new dependencies without a good reason; the APK is small on purpose.
- UI changes: include a before/after screenshot in both dark and light themes.

## Releasing (maintainers)

1. Bump `lumen.versionName` and `lumen.versionCode` in `gradle.properties` and add a section to `CHANGELOG.md`.
2. Merge to `main`, then push a tag: `git tag -a v1.2.0 -m "1.2.0: ..." && git push origin v1.2.0`.
3. The Release workflow builds the APK, signs it, checks the signature and publishes it with the changelog section as notes.

The workflow needs these repository secrets:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Your keystore file, base64-encoded (`base64 -w0 release.jks`, or `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))` in PowerShell) |
| `ANDROID_KEYSTORE_PASSWORD` | The keystore password |
| `ANDROID_KEY_ALIAS` | The key alias |
| `ANDROID_KEY_PASSWORD` | The key password |

To sign locally instead, create an untracked `keystore.properties` in the project root with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`.
