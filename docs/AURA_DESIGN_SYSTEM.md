# AURA — Visual System 2.0

## Character

AURA uses a calm cinematic-dark interface with a restrained aurora accent. The product should feel personal, warm and premium rather than technical or aggressively futuristic.

## Color tokens

- Background: `#080911`
- Deep surface: `#111321`
- Elevated surface: `#171A2A`
- Primary text: `#F7F5FF`
- Secondary text: `#AEB2C8`
- Primary accent: `#8B7CFF`
- Soft accent: `#B9AFFF`
- Positive/context accent: `#58E1C1`
- Border: white at 9% opacity

Functional color must never be the only indication of state. Active controls combine color with icons or labels.

## Shape and spacing

- 4/8 dp spacing rhythm.
- Cards: 20–34 dp corner radius depending on hierarchy.
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
