# AURA Dialogue Factory

Developer-only tooling for the local dialogue catalog. It is not packaged into
the Android application and uses only the Python standard library.

## Commands

```text
python factory.py validate data.json
python factory.py normalize data.json normalized.json
python factory.py deduplicate data.json deduplicated.json
python factory.py compile data.json compiled.json
python factory.py statistics data.json
python factory.py test-corpus data.json corpus.json
python factory.py export-sqlite data.json dialogue.sqlite
```

The input format is a JSON array of nodes. Each node has an `id`, `language`,
`intents`, `patterns`, `responses`, and optional `context`, `emotion`, and
`next`. The tool deliberately fails on malformed content, duplicate IDs,
empty patterns, and empty responses so catalog mistakes are caught before an
APK build.
