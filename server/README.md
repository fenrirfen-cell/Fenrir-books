# Fenrir Books

A standalone Node.js browser library with a versioned API for Fenrir Read. Inspired by the workflow of Tsehla's book cloner (search, download, skip duplicate jobs), but independently implemented. The original project mentions download restrictions; this project does not circumvent them or mirror its server. Supply books you have permission to distribute.

## Start

Install Node.js 20 or newer, open this folder in Terminal, and run `npm start`. No dependency installation is needed. Open http://127.0.0.1:3000.

To try from your phone on the same trusted Wi-Fi network, run `HOST=0.0.0.0 npm start` on macOS/Linux (PowerShell: `$env:HOST='0.0.0.0'; npm start`), and open the computer's LAN IP address on port 3000. Do not expose this unauthenticated demo to the internet; add HTTPS and access control before hosting publicly.

## Add books

Copy authorized EPUB/PDF files into `books/`. Add an entry in `catalog.json` with a unique lowercase hyphenated ID, title, author, description, license and a formats object containing the actual filenames. The bundled sample is a complete public-domain sonnet, not a novel.

## Android integration

Use your server's HTTPS base URL in production. On Android emulator, for local testing, the host machine is usually http://10.0.2.2:3000 (test-only cleartext configuration may be required).

- GET /api/v1/books?q=search — returns `{"books":[...]}`
- GET /api/v1/books/ozymandias — title metadata and available formats
- GET /api/v1/books/ozymandias/download/epub — streams a file with content type and length
- GET /api/v1/catalog-links?q=search — lists up to 100 eligible local book download links for the Android app's link collector. It does not crawl external websites.

In Fenrir Read, map the response to existing book models and use Android's standard download manager or existing download implementation. Resolve relative downloadUrl against the configured base URL; save EPUB/PDF through the existing import flow. No changes have been made to the Android app yet.

The web queue is session-based, retries failed downloads and avoids duplicate jobs during that session. Browser downloads buffer the file in memory; for very large files, use direct downloads or a native Android download manager.

Run `npm test` for API tests.
