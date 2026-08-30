# Carioca Android

A free, ad-free Kotlin + Jetpack Compose implementation of Carioca for Android.

## Product direction

This app is intentionally **just the game**:

- No coins
- No gems
- No betting
- No advertising walls or ad SDKs
- No pay-to-play mechanics
- No account required for a multiplayer table

The interaction model takes inspiration from polished social card-game apps, while the code, artwork, branding, rules implementation, and UI are original to this project.

## Current build — 0.3.0

### Home
- Clean Navy / Deep Blue / Teal / Gold visual system
- Two primary paths: **Play Online** and **Play vs AI**

### Play vs AI
- Playable 2–4 player Carioca practice game
- Easy, Medium, and Hard AI
- Regular (8 rounds) and Special (11 rounds)
- 108-card double deck; printed Jokers are the only wildcards
- One Joker maximum per meld; red aces are ordinary cards
- Leg, Straight, Crazy Straight, Colour Straight, and Royal Straight validation
- Legal discard-steal shine and sound cue
- Scoring: +2 per steal, −10 exact cut, no −30 going-out bonus

### Online rooms
- Live Supabase-backed room creation and joining
- Private rooms with 6-character invitation codes
- Public waiting-room discovery
- 2–4 player table capacity
- Automatic lobby refresh so joins appear on all connected devices
- Random local player tokens; raw room tables are not exposed to the client

### Online gameplay status
The room/lobby transport is live and tested across the backend API. The **next implementation layer is synchronized card-turn state** (dealing, draw/steal, meld actions, discard, scoring and round progression across multiple phones). Until that reducer is wired to the room transport, online rooms are real but online card turns are not yet playable.

## Build

Use JDK 17, Android SDK 35, and Gradle 8.11.1:

```bash
gradle testDebugUnitTest assembleDebug
```

The debug APK is written to:

`app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions also uploads the APK as a workflow artifact.
