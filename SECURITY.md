# Security

If you find a security problem, for example a way for someone on the network to control a TV without pairing, please don't open a public issue. Use GitHub's [private vulnerability reporting](https://github.com/anand34577/lumen-remote/security/advisories/new) instead.

I'll reply within a week and credit you in the release notes unless you'd rather stay anonymous.

## How pairing works

The phone creates its own RSA key and certificate the first time it runs. During pairing, the TV shows a code that proves both sides are talking to each other directly; the TV then trusts that certificate for future connections. The private key never leaves the phone and is excluded from backups.
