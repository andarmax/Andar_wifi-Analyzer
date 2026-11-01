# Changelog

## v1.0.1

- Hardened the WiFi scan receiver lifecycle, including cancellation, timeout, and callback cleanup.
- Removed the destructive Room migration fallback.
- Persisted the Room schema for database version 1.
- Aligned network endpoints and privacy information with the implementation.
- Improved Android build and signing configuration, CI, unit-test, lint, and `check` verification.
- Residual risk: `RECEIVER_EXPORTED` has not been fully tested across all supported API levels and devices.
