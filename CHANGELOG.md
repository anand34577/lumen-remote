# Changelog

All notable changes to Lumen Remote are listed here. Versions follow [semantic versioning](https://semver.org).

## [1.0.3] - 2026-10-25

### Fixed
- After allowing local network access in system settings, the app now notices when you return and connects right away, instead of staying on the permission prompt.

## [1.0.2] - 2026-10-25

### Changed
- Updated the libraries the app is built on (AndroidX, Navigation, Coroutines, BouncyCastle) to their latest releases. Nothing changes in how the app looks or behaves.

## [1.0.1] - 2026-10-25

### Fixed
- A button held down when you left the screen, or a macro cancelled during a long press, could leave that key stuck on the TV. Keys are now always released.
- A malformed message from a device on the network can no longer crash the app.
- Adding a TV by an IP address that is already saved now updates that TV instead of creating a duplicate.
- Changing a TV's port in the editor now reconnects, not just changing its address.
- Closing the TV list while a scan was resolving a device could crash on older Android versions.

## [1.0.0] - 2026-10-19

The first public release.

### Control
- Pair with any Android TV or Google TV using the code shown on the TV. No setup on the TV side, no app to install there.
- D-pad with hold-to-repeat and real long-press, a touchpad mode for swiping, a number pad and media controls, all one tap apart.
- A volume slider that sets the exact level instead of tapping up twenty times. Your phone's own volume buttons control the TV too.
- Type on the TV with your phone keyboard, including paste. The keyboard opens by itself when the TV shows a text field.
- Voice search: speak into your phone and the words are typed on the TV.
- Power button that can also wake a TV from standby (add its MAC address when editing the TV in Lumen Remote).

### Everyday comfort
- Finds TVs on your network automatically, and reconnects by itself if the TV's IP address changes or Wi-Fi drops.
- A "now on TV" card shows the app that's open, with one tap to pin it to your shortcuts.
- App shortcuts for YouTube, Netflix, Prime Video, Disney+ and others; add any app or link and reorder them.
- Macros that run several buttons in a row, such as "pause and mute" or "sleep TV".
- Remote controls in the notification shade, a home screen widget and a Quick Settings tile.

### Make it yours
- Dark, light or system theme, five accent colors, Material You colors and a pure black mode for OLED screens.
- Left-handed layout, adjustable haptics, key repeat speed and touchpad sensitivity.
- Choose which sections appear on the remote screen.

### Privacy
- Everything stays on your home network. No account, no analytics, no ads.
