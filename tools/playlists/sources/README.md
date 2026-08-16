# ChartBrain source snapshots

Each adapter writes one small JSON snapshot here with the shape
`{"source":"spotify","items":[{"rank":1,"artist":"…","title":"…"}]}`.
The build-time brain merges matching artist/title pairs using reciprocal-rank
fusion and writes one compact APK asset. The Android app never scrapes chart
pages and never downloads audio.

Supported source IDs are `official-charts`, `apple-music`, `spotify`,
`youtube-most-popular`, `listenbrainz`, and `new-releases`. Empty or malformed
snapshots are ignored, so one unavailable site cannot break the catalog.
