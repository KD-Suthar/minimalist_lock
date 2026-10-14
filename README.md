# MinimalLock

A minimalist Android launcher that limits your own phone use. Black home screen with a round clock,
daily time limits per app and for the whole phone, a pause before distracting apps, Shorts/Reels
blocking, and notification digests.

Made for sideloading on your own phone. It is not built for the Play Store (it uses an accessibility
service, which Play restricts).

## Privacy: no data stored or sent

- **No internet permission.** The app does not declare network access at all, so it technically
  cannot upload anything. The GitHub build fails if the APK ever requests it
  (see `.github/workflows/build.yml`, step "Privacy check").
- **No accounts, analytics, ads, crash reporters or third-party libraries.** The app has zero dependencies.
- **Backup is off** (`allowBackup=false`), so settings don't go to cloud backup either.
- **What is kept on the phone** (private app storage): your limits, your app lists, how many times you
  opened a limited app today, and whether you used today's emergency unlock.
- **What is never kept:** notification text, screen text, what you typed in the pause screen, or any
  history of what you did.
- The accessibility service reads only the package name of the app in front and, inside YouTube and
  Instagram, the view ids on screen to spot Shorts/Reels. The notification service reads only the
  app's package name and a few system flags, then uses Android's built-in snooze.
- The code is small. Read it before you install it.

## Features

- Black home screen, round clock, date, "phone time left" counter, favorites, swipe up for all apps
  (search, Enter opens the first match), hold anywhere for settings.
- Daily limit per app and for the whole phone. At the limit the app (or every non-necessity) is locked
  until midnight. A small counter shows time left while you use an app.
- Necessities (phone, messages, camera, maps, clock, settings, payments, authenticator ...) are never
  blocked and don't count. The picker only offers necessity-type apps.
- Pause before limited apps: 10s, growing +10s per open that day (max 60s); from the 4th open
  you must also type a reason.
- Blocks YouTube Shorts and Instagram Reels (sends you back when it detects them).
- Notification filter: apps not on your list are delayed to digest times (default 9, 13, 18, 21).
- Emergency unlock: once a day, 60s wait, type a phrase, 5 minutes of freedom.
- Settings take 15s to open, so you can't casually loosen your own limits.
- Battery: no polling while the screen is off or no tracked app is open; content events are only
  listened to inside YouTube/Instagram.

## Get the APK (no Android Studio needed)

1. Create a free GitHub account and a new repository (public or private).
2. Upload everything in this folder to it (drag the files in on the web page).
   If `.github/workflows/build.yml` did not upload (hidden folder), use *Add file > Create new file*,
   type `.github/workflows/build.yml` as the name and paste the file's contents.
3. Open the **Actions** tab, pick **Build APK**, and wait a few minutes. (If it did not start, press
   **Run workflow**.)
4. Open the finished run and download **MinimalLock-debug-apk** at the bottom. Unzip it to get
   `app-debug.apk`, copy it to the phone, and install it (allow "install unknown apps" when asked).

If the build fails, open the failed step, copy the red error text, and send it to whoever maintains this.

## Setup on the phone

Open MinimalLock and follow the Setup screen:

1. Usage access
2. Accessibility service
3. Display over other apps
4. Notification access
5. Default home app
6. Battery: unrestricted

If Accessibility or Notification access is greyed out (Android blocks this for apps installed from
outside the Play Store): App info > three dots > **Allow restricted settings**, then try again.

OnePlus: also allow Auto-launch and set Battery to Unrestricted, or the phone may stop the blocker.

## Tuning Shorts/Reels detection

Detection works by matching view ids inside the YouTube and Instagram apps. Those ids can change
when the apps update. If it stops working or never worked on your version:

Settings > **Capture screen ids**, open Reels or Shorts and keep it on screen for 25 seconds, then
Settings > **Show captured ids** and copy them. Add the right ones to `ytIds` / `igIds` in
`BlockerService.kt`.

## Known limits

- No Android blocker is unbypassable: you can always switch the accessibility service off in Settings.
  This is "annoying enough", not a vault.
- Changes to limits take effect immediately; the 15s wait is the only guard.

## License

MIT. See `LICENSE`.
