# Privacy

Short version: Lumen Remote talks to your TV and nothing else.

- **No account, no analytics, no ads, no crash reporting.** The app has no server to send anything to.
- **Network use.** The app finds TVs on your local network (mDNS) and connects to them directly over an encrypted connection on ports 6466 and 6467. Wake-on-LAN sends a packet to your local network. Opening an app or link on the TV is a request to the TV, not to the internet.
- **What's stored on your phone.** The TVs you've added, your shortcuts, macros and settings, and the certificate your phone uses to identify itself to your TVs. This data is excluded from Android cloud backup so the pairing certificate never leaves the phone.
- **Voice input** uses your phone's built-in speech recognizer. Lumen Remote only receives the resulting text.
- **Permissions.** Local network access (to find and reach your TV), notifications (only for the optional notification remote) and vibration (for haptics).

Questions: open an issue on [GitHub](https://github.com/anand34577/lumen-remote/issues).
