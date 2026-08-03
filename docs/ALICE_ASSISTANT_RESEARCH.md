# Local dialogue research notes

The practical non-generative design is a bounded state machine:

- intent patterns are language-specific and word-boundary aware;
- negated or quoted commands are never executed;
- a dialogue node has weighted response variants and cooldown keys;
- recent turns update a compact Room conversation state;
- unknown utterances are counted in normalized form without retaining audio;
- explicit confirmation is required before a phrase becomes a learned mapping.

This gives predictable behavior on a weak phone, fast responses, offline
operation, and testable failure modes. It also makes it possible to grow the
catalog to tens of megabytes without putting a generative model in the APK.
