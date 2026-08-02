# AURA — Visual System 2.1

## Character

AURA uses the supplied product references as its visual source of truth: a compact cinematic-black music interface, violet/magenta energy accents, a friendly central AURA character and dense but calm content cards. Oversized marketing-style hero typography is deliberately avoided.

## Color tokens

- Background: `#050A11`
- Deep surface: `#0D131B`
- Elevated surface: `#171D27`
- Primary text: `#F7F6FC`
- Secondary text: `#A9AFBB`
- Primary accent: `#7C3CFF`
- Voice/highlight accent: `#C13DFF`
- Ambient blue: `#2467FF`
- Positive/context accent: `#6FE3D1`
- Border: white at 9% opacity

Functional color must never be the only indication of state. Active controls combine color with icons or labels.

## Shape and spacing

- 4/8 dp spacing rhythm.
- Cards: 14–22 dp corner radius depending on hierarchy.
- Touch targets: at least 48×48 dp.
- Adjacent actions: at least 8 dp apart.
- One visually dominant action per screen.

## Motion

- Meaningful transitions only, normally 150–300 ms.
- Reordering follows the finger and preserves the playing track by identity.
- Lists use stable track IDs and placement animation.
- Animations must remain interruptible and must not block input.

## Accessibility

- Normal text contrast target: WCAG AA 4.5:1.
- Every icon-only action has a content description.
- Drag-and-drop also exposes accessibility actions for moving an item up or down.
- Destructive queue clearing requires confirmation.
- Back navigation and Android system gesture areas remain unobstructed.

## Queue UX

The queue is a music session rather than a utility list. It exposes current order and automatic history as separate tabs. Track actions are available both visually and through local voice commands. Media3 is the live playback authority; Room stores durable metadata and session snapshots without temporary stream URLs.

## Playlist discoverability

Every reusable track row can expose a visible overflow action named `Добавить в плейлист`. The full player also shows a dedicated playlist action and contextual AURA prompt. The picker always offers `Создать новый плейлист` before existing playlists; the Library/Playlists header keeps a visible `+` action.

## Country radio

Country radio uses the free Radio Browser directory. Countries are horizontally browsable, station loading has explicit progress/error/retry states, and only HTTPS streams are presented because AURA keeps Android cleartext traffic disabled. Radio streams retain a durable descriptor so saved playlists and restored sessions remain playable.
