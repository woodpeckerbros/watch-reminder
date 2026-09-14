# Zmanio — Billing and trial

## Product and entitlement

- Product ID: `zmanio_lifetime`; Google Play `INAPP`, one-time **Buy** purchase, non-consumable Lifetime unlock. No ads, subscriptions, recurring charges, or consumption calls.
- Each artifact owns one `EntitlementManager`. `zmanio_entitlement` SharedPreferences stores first activation, highest observed wall time, and last Google Play-verified lifetime ownership.
- Production trial is exactly 14 days. The highest observed wall time makes simple system-clock rollback unable to extend it. `LIFETIME_PURCHASED` always overrides trial expiry.
- The public states are `TRIAL_ACTIVE`, `TRIAL_EXPIRED`, `LIFETIME_PURCHASED`, `CHECKING`, and `BILLING_UNAVAILABLE`.

## Billing and restore

Billing Library 9.1.0 keeps one client per process, enables pending one-time products and automatic service reconnection, queries `ProductDetails` and `queryPurchasesAsync(INAPP)` on connection and each Activity foreground. The displayed price is Play's localized `getFormattedPrice()`; it is never hard-coded. Only `PURCHASED` grants Lifetime; `PENDING` does not. Completed purchases are acknowledged and never consumed. A successful ownership query restores access; temporary Billing unavailability preserves the last verified Lifetime cache.

Watch and phone use the same application ID and query Play directly, so a Google Play account has one entitlement on both form factors. The watch refreshes ownership when foregrounded; no parallel entitlement sync protocol was created.

## Expiry delivery behavior

User data is retained. When locked, reminder/snooze/watchdog, Smart Alarm, water, fasting and Jewish-calendar schedules are cancelled and `ReminderMonitoringService` stops. Existing receivers independently recheck entitlement, preventing a stale PendingIntent from delivering an alert. Purchase/restore invokes the same centralized enforcer to rebuild schedules and restart user-enabled monitoring.

## Backup encryption and trial metadata

New `.zmbu` backups are `ZMBU3` AES-GCM envelopes with a random IV and authenticated header. They contain `trialMetadata.trialStartedAt` from the watch. The phone retains this metadata when editing and returning a backup. Authentication rejects manual edits/corruption. Legacy plaintext backups are accepted only for migration and encrypted when saved to the phone.

The key is shared by watch and phone so offline transfer works without a backend or password. It prevents casual viewing and manual JSON editing, but does not defend against a determined attacker who extracts the application binary. A passphrase or backend-managed key would be required for that. Without a backend, clearing data/uninstalling may also reset a client-only trial depending on Android backup behavior.

## Legacy paid users

The older paid build has no reliable, non-forgeable paid-order marker. The app intentionally does not grant Lifetime merely because it was updated: that would unlock all future free users. Existing paid testers need a Play Console/admin process such as communication, refund, or promotion; no insecure automatic grandfathering is present.

## Play Console setup

1. In **Monetize with Play > Products > App pricing**, change the app from paid to free. This is one-way for the package.
2. Ensure the payments profile and merchant eligibility are complete.
3. In **Monetize with Play > Products > One-time products**, create `zmanio_lifetime`, with Hebrew/English metadata.
4. Add one purchase option: **Buy**, digital service, quantity limit one; do not add subscription or rental options.
5. Set regional availability/prices; activate both product and purchase option.
6. Upload/publish the AAB on an internal or closed track. The active product and test release must be available to the same account before ProductDetails is returned.
7. Add testers in **Settings > License testing**.

## End-to-end test

1. Install the internal-test release through Play with a license-test account (not sideloaded).
2. Verify fresh trial, encrypted backup transfer, and normal reminders/Smart Alarm.
3. In an expired test state, verify paywall text, localized price, Restore Purchase, stopped FGS, and blocked alerts.
4. Complete purchase; verify immediate unlock, acknowledgement, scheduling rebuild and monitoring restart only when it was enabled.
5. Foreground the other form factor and verify ownership restore. Test cancellation, pending payment and temporary offline Billing: none unlock except a completed/restored purchase, and last verified Lifetime remains usable offline.
