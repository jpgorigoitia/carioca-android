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

## Current build — 0.5.0

### Visual and touch table
- Responsive portrait and landscape layouts; landscape is the full table presentation
- Orientation changes preserve the live game instead of restarting it
- Navy / deep-blue / teal / gold visual system with a felt table
- Original compact chibi-style player avatars and active-turn glow
- Proper card faces and patterned card backs
- Visible contract rail, table melds, scores, card counts and turn messages
- Tap cards to build a selection
- Grab and drag the selected group onto the table to lay it
- Drag a single hand card onto the discard pile to finish a turn
- During draw phase, drag the deck or glowing discard into the hand
- Legal drop targets highlight during a drag
- Floating dragged-card feedback and steal sound/SFX toggle
- Hand sorting by suit or rank in AI practice

### Play vs AI
- Playable 2–4 player Carioca practice game
- Easy, Medium, and Hard AI
- Regular (8 rounds) and Special (11 rounds)
- 108-card double deck; printed Jokers are the only wildcards
- One Joker maximum per meld; red aces are ordinary cards
- Leg, Straight, Crazy Straight, Colour Straight, and Royal Straight validation
- Legal discard-steal shine and sound cue
- Scoring: +2 per steal, −10 exact cut, no −30 going-out bonus

### Online play
- Live Supabase-backed room creation and joining
- Private rooms with 6-character invitation codes
- Public waiting-room discovery
- 2–4 player table capacity
- Host starts the game after at least two players join
- Shared deal, draw/steal, meld, discard, score and round state across devices
- Server-side turn ownership and optimistic state-version checks reject out-of-turn or stale updates
- Opponent cards remain face-down in the Android UI
- Automatic game-state refresh keeps connected phones synchronized
- Random local player tokens; raw room tables are not exposed to the client

The current multiplayer transport uses short-interval state polling rather than a final Realtime/Broadcast implementation. It is suitable for functional testing of multiplayer game flow; lower-latency realtime transport is a later optimization.

## Build

Use JDK 17, Android SDK 35, and Gradle 8.11.1:

```bash
gradle testDebugUnitTest assembleDebug
```

The debug APK is written to:

`app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions also uploads the APK as a workflow artifact.
