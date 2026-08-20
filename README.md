# Carioca Android MVP

An ad-free Kotlin + Jetpack Compose implementation of Carioca for 2–4 players.

## Current build

- Regular (8 rounds) and Special (11 rounds) selection
- Practice setup for 2–4 players with Easy, Medium, and Hard AI choices
- Interactive table demo with legal-steal shine and audio cue
- 108-card double deck; printed Jokers are the only wildcards
- One Joker maximum per meld; red aces are ordinary cards
- Leg, straight, Crazy Straight, Colour Straight, and Royal Straight validation
- Scoring primitives: +2 per steal, −10 exact cut, no −30 going-out bonus
- Unit tests and GitHub Actions APK build

Private multiplayer and the complete turn engine remain the next milestone.

## Build

Use JDK 17, Android SDK 35, and Gradle 8.11.1:

```bash
gradle testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
