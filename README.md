# SyncBeat — Android prototype (v0.1)

This is a first prototype source project. It is **not a tested or signed release APK**.

## What it currently does
- Admin selects an audio file stored on the admin phone.
- Admin starts an HTTP audio server on port 8080.
- Guest phones connect to the admin phone's hotspot and enter the admin URL.
- Guest phones buffer the audio and poll the room state.
- Admin schedules a common start about five seconds in the future.

## Important limitations
- This prototype is for testing on a small number of Android phones first.
- Hotspot IP addresses vary by phone. The admin app lists detected IPv4 addresses; use the address reachable from guest phones.
- Some Android hotspot implementations isolate clients or block local traffic. If guests cannot connect, the hotspot/router may not allow device-to-device communication.
- Start times are best-effort, not sample-accurate. Different phones, Wi-Fi conditions, and audio hardware introduce delay.
- Keep the app open on both admin and guest phones during testing.
- Use audio you own or are licensed to distribute. This prototype does not extract or rebroadcast YouTube audio.
- The first version has no automatic device discovery, room password, background playback service, or synced pause/seek controls.
- HTTP is unencrypted and intended only for a trusted local hotspot, not public networks.

## Build an APK using GitHub Actions (phone-only workflow)
1. On your Android phone, open GitHub in your browser and sign in (create an account if needed).
2. Create a new **public or private repository** named `SyncBeat`.
3. Upload the files and folders from this ZIP to the repository, preserving their paths.
4. Open the repository's **Actions** tab and enable workflows if GitHub asks.
5. Choose **Build SyncBeat APK** and tap **Run workflow**. You can also push a commit to `main` to trigger it.
6. Wait for the workflow to finish successfully.
7. Open the completed run, scroll to **Artifacts**, and download `SyncBeat-debug-apk`.
8. Extract the ZIP artifact and install `app-debug.apk`. Android may ask you to allow your browser or file manager to install unknown apps.

## Test
1. On the admin phone, enable its Wi-Fi hotspot.
2. Open SyncBeat, choose an MP3/audio file, and tap **Start room**.
3. Note the listed IP address ending in `:8080`.
4. On each guest phone, connect to the admin hotspot.
5. Open SyncBeat, enter the admin URL (for example `http://192.168.43.1:8080`) and tap **Join room**.
6. Wait until guests say they are connected and ready.
7. On the admin phone, tap **Broadcast Play**.

If it does not connect, try each listed admin IP. Some phone hotspots do not permit local client-to-client connections.

## Project structure
- `app/` Android application source
- `.github/workflows/build-apk.yml` cloud APK build workflow
