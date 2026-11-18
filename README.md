# life1-class-collector

Collects **future** group-class timetable entries from Life1 Budapest booking sites and partner órarend pages into one soft-delete CSV, then publishes a searchable static list on GitHub Pages.

Target Pages URL: [https://mkortvelyess.github.io/life1-class-collector/](https://mkortvelyess.github.io/life1-class-collector/)

Timezone for every stored instant: **`Europe/Budapest`** (`TimeZones.EUROPE_BUDAPEST`).

## Sources

| Branch | URL | Approach |
| --- | --- | --- |
| Life1 Allee | https://booking.life1.hu/allee/orarend/ | Server-rendered Mindbody/Healcode HTML (current + next week, all dayparts) |
| Life1 Corvin | https://booking.life1.hu/corvin/orarend/ | same |
| Life1 Etele | https://booking.life1.hu/etele/orarend/ | same |
| Life1 Nyugati | https://booking.life1.hu/nyugati/orarend/ | same |
| Life1 Váci35 | https://booking.life1.hu/vaci35/orarend/ | same |
| Prestige Fitness | https://prestigefitness.hu/orarend/ | WellnessGate JSON API (`action=get_timetable`, ~14 days) |
| Lite Wellness | https://www.litewellness.hu/csoportos-orak-es-orarend/orarend/ | Typo3 HTML tables per room × current/next ISO week |

Identity key: **branch name + start DateTime + class name + instructor**.

## CSV schema (`classes.csv`)

| Column | Meaning |
| --- | --- |
| `branch` | Branch / partner display name |
| `start` | Class start `yyyy-MM-dd'T'HH:mm` in Europe/Budapest wall time |
| `end` | Class end, same format |
| `class_name` | Class title |
| `instructor` | Instructor name (as published) |
| `source_url` | Timetable page URL (linked from the Pages UI) |
| `deleted` | `0` active, `1` soft-deleted (was present, missing from a later successful scrape of that branch) |
| `first_seen` | First collection timestamp |
| `last_seen` | Most recent collection timestamp when the class was observed |

Soft-delete rules:

- New identity → insert with `deleted=0`
- Seen again → keep/revive `deleted=0`, refresh `last_seen` / `end` / `source_url`
- Previously non-deleted identity missing from a successful scrape of that branch → set `deleted=1` (row kept)
- Rows are never removed; CSV need not be sorted

## Local run

Requires **JDK 25** (Gradle toolchain can download it) and network access to the timetable hosts.

```bash
./gradlew test
./gradlew run --args="--out=classes.csv"
# or
./gradlew jar
java -jar build/libs/life1-class-collector-1.0.0.jar --out=classes.csv
```

Preview the static site (after a CSV exists):

```bash
cp classes.csv src/main/resources/viz/
python3 -m http.server 8080 --directory src/main/resources/viz
# open http://127.0.0.1:8080/
```

## Push to GitHub (manual — no Cursor GitHub connect required)

1. Create empty repo `MKORTVELYESS/life1-class-collector` on GitHub.
2. From this project root:

```bash
git init
git add .
git commit -m "Initial life1-class-collector"
git branch -M main
git remote add origin git@github.com:MKORTVELYESS/life1-class-collector.git
git push -u origin main
```

3. **Actions write permission**: Repo → Settings → Actions → General → Workflow permissions → **Read and write permissions** (needed so the daily job can commit `classes.csv` and trigger Pages redeploy).
4. **Pages**: Settings → Pages → Source = **GitHub Actions**. Run workflow **Deploy GitHub Pages** once (or push a CSV/viz change).
5. Enable the **Update classes.csv** workflow (Actions tab). It runs daily at `23:05` UTC and on `workflow_dispatch`. When the CSV changes it commits and explicitly runs `deploy-pages.yml` (GitHub does not chain `on: push` from `GITHUB_TOKEN` pushes).

## Workflows

- `.github/workflows/update-classes.yml` — schedule + manual; `./gradlew run`; commit `classes.csv`; redeploy Pages when changed
- `.github/workflows/deploy-pages.yml` — copies `src/main/resources/viz/*` + `classes.csv` to GitHub Pages

## Pages UI

- Text search filter (class / branch / instructor)
- List of non-deleted classes sorted by start time
- Branch name links to the scraped timetable URL

## Scrape notes / limitations

- **Life1 booking**: One HTML GET per branch includes both published weeks (`tbody.actualWeek` / `tbody.nextWeek`) and all dayparts. Only desktop `table.table-timetable.hidden-xs` is parsed (mobile markup duplicates the same cells). Horizon is whatever the site publishes (typically current + next week).
- **Prestige**: Uses the public WellnessGate API key embedded in the page. Live checks returned ~14 days even with larger `timetable_days`. Cancelled rows (`elmarad`) are skipped.
- **Lite**: Scrapes three rooms × discovered week links on the landing page. Horizon is current + next ISO week.
- If a branch request fails, that branch is logged and skipped; soft-delete does **not** mark its rows deleted on a failed empty scrape.
- Cloud/CI egress or bot blocking may empty a source; verify with a local `./gradlew run` if Actions produce sparse CSVs. Fixtures under `src/test/resources/fixtures/` keep parsers testable offline.
