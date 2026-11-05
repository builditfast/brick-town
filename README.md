# Brick Town

**See where your money goes, one brick at a time.** Brick Town is a playful spending tracker for one person or a couple. Every €25 you spend is one brick, every spending category is a tower, and Penny, a gold coin, cheers you on as your town grows.

<p align="center">
  <a href="media/brick-town-showreel.mp4"><img src="media/brick-town-preview.gif" alt="Brick Town showreel" width="720"></a>
  <br>
  <sub>15-second showreel. Click it for the full-quality video.</sub>
</p>

## What it does

- **Add a brick in seconds.** Type the amount on the numpad, pick a tower, done. The Month screen shows what you spent and what is left for today and for the month.
- **Towers per category.** Groceries, eating out, home, shopping and the rest each grow their own tower. You can rename, merge or remove towers.
- **Gold days and streaks.** A day with no spending is a gold €0 day, and logging every day keeps your streak alive. A gentle evening reminder at 20:00 only comes on a day you have not logged yet.
- **One town for two.** Invite your partner with a link and you share the same towers, bills and budget. Mark a brick as shared and **Settle up** shows who owes whom, split 50/50.
- **Weekly recap and month done.** Every Sunday at 18:00 a recap shows your week against the last one. When a pay month ends, the month done screen hands you its trophy.
- **Import a bank statement.** Bring in a CSV or PDF statement and review the rows before they become bricks. A PDF is read on the server in memory and never stored.
- **Home-screen widgets (Android).** Today's number, the month's towers and your streak without opening the app, plus a one-tap "add a brick".
- **Works offline.** Bricks added without a connection wait on the phone and sync when you are back online.
- **Ask it by voice.** The `bricktown` command-line tool and its MCP server let a voice assistant answer "how much did I spend this month" or "add 12 euros groceries".

## How it is built

One Kotlin Multiplatform codebase: the screens and the logic are shared between Android and iPhone, and a small server keeps a town in sync between two phones.

| Module | What it holds |
|---|---|
| `:domain` | Entities (also the wire format) and every rule: money, bricks, months, streaks, bank statements. Shared by the app and the server. |
| `:shared` | The town repository, the local SQLDelight cache, the Ktor client, the offline outbox, reminders and accounts. Android and iOS. |
| `:composeApp` | Every screen and the design system in Compose Multiplatform (`commonMain`), plus the Android widgets. `iosApp/` is the Xcode shell. |
| `:server` | Ktor 3 with Postgres, Exposed, Flyway and Koin: accounts, the town API, statement import, the invite page and feature flags. |
| `cli/` | `bricktown.py` (summary, add, recent, undo; `--json`) and `mcp_server.py` over it. |

## Running it

You need JDK 17, Android Studio (Ladybug or newer) and, for iPhone, Xcode 16 or newer.

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

### Server

The server needs a Postgres at `jdbc:postgresql://localhost:5432/app` (user and password `app`). The quickest way:

```bash
docker run --rm -d --name pg \
  -e POSTGRES_USER=app -e POSTGRES_PASSWORD=app -e POSTGRES_DB=app \
  -p 5432:5432 postgres:17-alpine

./gradlew :server:run
# http://localhost:8080/health
```

Override the database with `DATABASE_URL`, `DATABASE_USER` and `DATABASE_PASSWORD`. Server tests use an embedded Postgres and need no Docker: `./gradlew :server:test`.

### Android

```bash
./gradlew :composeApp:installDebug
```

A debug build talks to `http://10.0.2.2:8080`, the emulator's address for your computer. Point it at another server with `-PapiBaseUrl=http://<host>:8080`. Plain HTTP is allowed in debug builds only, to the hosts listed in [network_security_config_debug.xml](composeApp/src/androidDebug/res/xml/network_security_config_debug.xml).

A Play Store bundle needs an `https://` server address and four signing variables (`ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`):

```bash
./gradlew :composeApp:bundleRelease -PapiBaseUrl=https://api.example.com
```

### iPhone

Open `iosApp/iosApp.xcodeproj` in Xcode, pick a simulator and press Run. The build phase compiles the shared Kotlin framework for you.

### Tests

```bash
./gradlew :domain:allTests :shared:allTests :composeApp:testDebugUnitTest :server:test
```

## Deploying the server

[server/Dockerfile](server/Dockerfile) is the deploy unit. [docker-compose.yml](docker-compose.yml) runs the server, Postgres 17 and Caddy with automatic TLS on any VPS:

```bash
cp .env.example .env && $EDITOR .env    # DOMAIN, ACME_EMAIL, database password, JWT_SECRET
docker compose up -d --build
```

Any host that builds a Dockerfile (Fly.io, Railway, Render, Coolify) works too. Set `DATABASE_URL`, `APP_ENV=production`, a random `JWT_SECRET` (`openssl rand -base64 48`), and `APP_NAME`, `CONTACT_EMAIL` and `COMPANY_NAME` for the privacy, terms and account deletion pages.

## License

Apache 2.0, see [LICENSE](LICENSE).

---

Made with [freeapp.ai](https://freeapp.ai)
