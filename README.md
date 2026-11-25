# CR Issue Tracker

An enterprise web application for tracking Change Requests (CRs) through QA delivery,
managing bugs raised during testing, and reporting delivery readiness.

Built with Spring Boot 3.4, Spring MVC, Thymeleaf, Spring Security, Spring Data JPA,
Flyway, and Bootstrap 5. The default dev profile runs on an in-memory H2 database in
PostgreSQL compatibility mode, so no external database is required for local development.

## Requirements

- Java 17 (JDK 17 is required; later JDKs are not the build target)
- Maven 3.9+

## Run locally

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn spring-boot:run
```

The dev profile starts on port 8080 by default. If 8080 is already in use:

```bash
SERVER_PORT=8090 mvn spring-boot:run
```

Open http://localhost:8080 (or the chosen port).

## Default dev users

| Username     | Password   | Role          |
|--------------|------------|---------------|
| admin        | admin123   | ADMIN         |
| cr.manager   | cr123      | CR_MANAGER    |
| qa.lead      | qa123      | QA_LEAD       |
| qa.tester    | qa123      | QA_TESTER     |
| developer    | dev123     | DEVELOPER     |
| viewer       | view123    | QA_VIEWER     |
| sa.lead      | qa123      | SA_LEAD       |

Seed data is loaded only for the `dev` profile.

## Roles & permissions

| Role | Change Requests | Bugs | Test Cycles | Test Cases | Users | Admin area |
|------|-----------------|------|-------------|------------|-------|------------|
| ADMIN | add/edit | add/edit | add/edit | add/edit | add/edit | full |
| CR_MANAGER | add/edit | view | view | view | – | hidden |
| SA_LEAD | add/edit | add/edit | view | add/edit | – | hidden |
| QA_LEAD | add/edit | add/edit | add/edit | add/edit | add/edit | Users only |
| QA_TESTER | view | add/edit | add/edit | add/edit | – | hidden |
| DEVELOPER | view | add/edit | add/edit | add/edit | – | hidden |
| QA_VIEWER | view | view | view | view | – | hidden |

All roles can view the dashboard and reports, except the **Test Case & Bug Statistics**
report, which is limited to ADMIN, CR_MANAGER, QA_LEAD and QA_TESTER. Add/edit actions
are enforced server-side (`@PreAuthorize`) and the corresponding buttons/links are hidden
for view-only roles.

## Profiles

- `dev` (default): H2 in-memory, seed data, H2 console at `/h2-console`
- `prod`: PostgreSQL via environment variables

```bash
DB_URL=jdbc:postgresql://localhost:5432/crqa \
DB_USERNAME=crqa \
DB_PASSWORD=crqa \
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

## Key features

- Form login with role-based access control
- CR management with a server-side workflow
- Bug management with comments, attachments, and full history
- Centralized bug workflow with role authorization
- Dashboard with KPI cards and Chart.js visualizations
- Sprint report grouping change requests, test execution and defects by sprint
- Test Case & Bug Statistics report: test execution and defect stats per QA Lead / QA Tester (ADMIN, CR_MANAGER, QA_LEAD, QA_TESTER)
- Server-side search, filtering, and pagination
- Test cycles and test cases (with precondition, test steps, priority, labels, issue type and story/epic/issue links)
- Change requests carry an epic (the CR number) and an optional story number
- Test-case review cycle on change requests: QA Lead → SA Lead → DDM → Business → Closed, reopenable, with activity logging and notifications
- Bulk CSV import for change requests, test cases, users, and bugs (with downloadable templates); test-case import links to the CR by CR number + story number
- Bulk delete of test cases, bugs and test cycles (select one or more) from the CR tabs or the list pages — ADMIN, SA Lead, QA Lead only
- Delete change requests — single from the CR detail page, or one or more from the CR list; each archives its whole subtree (cycles, test cases, bugs and activities) — ADMIN, CR Manager, SA Lead, QA Lead only
- **JIRA integration**: "Import from JIRA" on the Change Request list opens a search form (created-from date + status), fetches the matching issues with a configurable JQL, shows them as a paginated selectable table and imports the selected ones — inserting new change requests and refreshing existing ones (matched on Epic number + name) with a notification to their QA Lead and SA Lead. Connection settings (API URL, email, API token, max results, JQL, fields, status options) are maintained by an ADMIN under Administration → JIRA Configuration
- Test-case status history (CREATED / EXECUTED entries with the old → new status, shown on the test case page)
- **Bulk test execution** — select test cases on the list (or in a CR's Test Cases tab), choose one status, and record it on all of them at once; a blank actual result keeps whatever each case already had
- **SLA tracking** — every open bug is classified WITHIN / APPROACHING / BREACHED its SLA from its severity and age, filterable from the bug list and summarised in an **SLA & Aging report** (by severity, assignee and change request) with CSV export; an optional scheduled sweep escalates breached bugs to their owners once per window
- **Notification email** through either your intranet **SMTP relay** or the corporate **SOAP email adapter** (Administration → Email Settings, with a *Send test email* button), dispatched off the request thread so a dead relay or adapter never slows or breaks a workflow action; each user can opt out of email (they keep the in-app notification)
- The sidebar **Administration** menu collapses and expands, and remembers the choice per browser
- Failing a test case opens a pre-filled, test-case-linked bug form; bug lifecycle notifications (assign → developer, fixed → QA/SA Lead, ready for retest → QA Testers, reopened → developer)
- Bug and test-case histories are paginated (10/page), sortable and filterable tables
- Admin "Export All" bundle: users, projects, change requests, test cases, test cycles and bugs as sequenced, re-importable CSVs
- **UI Settings (ADMIN only)** — global theme (red / blue / dark), base font (System / Verdana / Courier / Inter / Roboto / Open Sans / Ubuntu — the last four are bundled as local `woff2` files, no internet needed), table-row font size and rows-per-page; persisted in the `IT_ui_settings` table and applied to all users. The dropdown values for rows-per-page and font size are configurable in `application.yml` (`app.ui.page-sizes: 10,25,50,100`, `app.ui.font-sizes: 10,11,12` — comma-separated)
- **Analytics (ADMIN only)** — user-wise activity (logins/logouts, CRs created/updated, test cases executed, bugs raised), time spent per feature, bugs grouped by change request / test case / developer, test-execution pass rate, defect distribution, and CR cycle time; filterable, paginated, column-sortable and exportable to Excel (.xlsx via Apache POI)
- A default test cycle and default test case are created automatically with every new change request (toggle with `app.default-test-case.enabled`) 
- Defect classification (SIT/UAT/production category, test phase found, sprint)
- In-app notifications (email abstraction logs in dev)
- ADMIN-only user impersonation ("View as") — open the app as another user with one-click switch-back
- Audit log for important operations
- CSV export for bugs and CRs
- Flyway migrations and `ddl-auto: validate`

## Database

All application tables are prefixed with **`IT_`** (e.g. `IT_change_request`, `IT_bug`, `IT_users`, `IT_test_case`,
`IT_notification`, `IT_audit_log`). Column, constraint and index names are unchanged, and Flyway's own
`flyway_schema_history` table is not prefixed.

Each table's `id` is generated by a sequence named **`IT_<TABLE>_SEQ`** (e.g. `IT_USERS_SEQ`,
`IT_CHANGE_REQUEST_SEQ`); the application (`@SequenceGenerator`) and the seed DML both use these sequences. The
original identity definition is retained on the id columns for backward-compatibility but is not used by the app.
On a database that already contains rows, restart a sequence above the current maximum before use, e.g.
`ALTER SEQUENCE IT_USERS_SEQ RESTART WITH <max(id)+1>`.

`IT_user_activity` stores the ADMIN-only "time spent" telemetry (user, feature, path, started_at, duration_seconds),
written by a small client-side page timer. Login and logout are recorded in `IT_audit_log` as `LOGIN` / `LOGOUT`.

## Notifications and SLA

**In-app + email.** Every notification is stored in `IT_notification` and shown under the bell; when email is
configured it is *also* sent to each recipient by the transport selected in `IT_mail_config`
(Administration → Email Settings), which is seeded from `app.mail.*`:

* **SMTP relay** (`transport: smtp`) — host, port, optional username/password, STARTTLS.
* **OSB SOAP API** (`transport: soap`) — calls the corporate email adapter: an **endpoint URL** template
  (shipped default `{{sit_osb}}/EMAILAdapter/ProxyService/EmailWithAttachmentProxy`), a **request payload template**
  (the built-in `EmailWithAttachmentProxy` envelope, editable on the screen), SOAP username/password, and
  `name=value` **SOAP variables** used to fill `{{placeholders}}`.

Both services' settings are always visible on the screen — the **Email service** radio picks which one is used and the
sections are marked *in use* / *not used* — and every value (endpoint, credentials, variables, request template,
relay) starts from `app.mail.*` and is then stored in `IT_mail_config`, which is what notifications actually use.

Payload placeholders: `{{fromMailFromConfiguration}}`, `{{toMailDynamic}}`, `{{subjectDynamic}}`,
`{{htmlBodyDynamic}}` (sent as HTML, inserted inside the template's CDATA) and `{{username}}` / `{{password}}`, plus
any variable. Values placed in XML are escaped automatically and an unresolved placeholder is reported instead of
being sent literally.

Email is off by default (`enabled: false`) — nothing is sent until a transport is switched on and configured. Sending
happens on a small background pool with a bounded timeout, so a slow or dead relay/adapter can never slow down or roll
back the action that produced the notification; the failure is logged and the in-app notification remains. A user with
**Email notifications** unticked on their user record keeps the in-app notification but is not emailed.

**SLA.** `app.sla.<severity>-hours` defines the window for each severity (`SlaService` derives the deadline from the
reported time): a bug is *approaching* at 75% of its window and *breached* past it; closed and rejected bugs are always
within SLA. The bug list has an **SLA** column and filter, `/reports/sla` (CSV at `/reports/sla.csv`) summarises the
open backlog by severity, assignee and change request, and the dashboard shows the overdue count with how many are
approaching.

An optional sweep escalates breached bugs to the assignee and the change request's QA Lead and SA Lead (one
notification each, per `app.sla.escalate-every-hours`). It is **off by default**; enable it with
`app.sla.escalation-enabled: true` (pick it up live with *Reload Config* — the `escalation-cron` itself is read at
start-up, so changing the schedule needs a restart). An ADMIN can also run the sweep immediately from the SLA report.

## JIRA integration

The Change Request list offers **Import from JIRA**. It searches JIRA with the configured JQL and imports the
selected issues as change requests:

| JIRA | Change request |
|---|---|
| `parent.key` | Epic Number (`cr_number`) |
| `fields.summary` | Name (`cr_name`) |
| issue `key` | Story Number (`story_number`) |
| `customfield_10037` : `customfield_10038` | Description (`CM PM : CM SA`) |
| `customfield_10146` | DAC submit date |
| `customfield_10105` | HLD Submit Date |
| — | Project: the default project, Environment: `SIT`, Priority/Complexity: `MEDIUM`, Status: `CREATED` |

`customfield_10066` (RFBT Date), `customfield_10034` (Project Manager → ADM PM), `customfield_10033` (Solution
Architect → ADM SA), `customfield_10159` (Kick-off Date) and `customfield_10143` (Developer) are shown in the review
table but are not mapped to a change request field; `customfield_10150` (Missed RFBT) is retrieved for reference only.
The fields the import cannot work without are always requested, even if the configured list omits them.

An issue whose Epic Number + Name already exists is **updated** (story number, description, DAC and HLD dates) rather
than duplicated — its status, priority, owners, environment and project are left alone — and its QA Lead and SA Lead
get a "Change request updated from JIRA" notification. Each run reports how many were created, updated and failed.

Before importing, the review table shows one row per issue with the epic number (prefixed with a red star when that
change request does not exist yet, otherwise carrying an Update chip), epic summary/type/status, story
number/summary/type/status, priority, created date, CM PM, CM SA, ADM PM, ADM SA, Kick-off Date, DAC Submit Date, HLD
Submit Date, RFBT Date, Developer (the `customfield_10143` display name, falling back to the change request's
development owner) and QA (the QA Lead already recorded on an existing change request). A row whose RFBT Date is before
today is highlighted in red as needing attention. Ticks survive paging, so you can select across pages before pressing
**Import selected**.

Everything is configured at runtime by an ADMIN under **Administration → JIRA Configuration** (stored in
`IT_jira_config`, seeded from `app.jira.*` in `application.yml`): an **Enable JIRA** switch (when unticked, the
*Import from JIRA* button is hidden everywhere), the JIRA API URL — shipped default
`https://okducagile.atlassian.net/rest/api/3/search/jql` — the email + API token, an optional **CA certificate** (PEM)
for trusting a private CA or a reverse proxy in front of JIRA, max results (default 50, maximum
500, fetched in pages of 100), the request timeout, the status options offered by the search form, the JQL template
(with `{{status}}` and `{{createdFrom}}` placeholders) and the list of JIRA fields to retrieve.

The connection settings (`app.jira.base-url`, `app.jira.email`, `app.jira.api-token`, `app.jira.ca-certificate`) act as **defaults**: they pre-fill
the screen and are used whenever the stored value is blank, so the credentials can simply live in `application.yml`
(note that the file then holds a secret) and the import works without anyone visiting the screen. Values saved by an
ADMIN take precedence; clearing a field and saving falls back to the configured default.

Both JIRA search APIs are supported: a URL containing `/search/jql` (JIRA Cloud) is paged with
`nextPageToken`/`isLast`, anything else (`.../rest/api/2/search`, `.../rest/api/3/search`) with `startAt`/`total`. A
bare `https://something.atlassian.net` resolves to the Cloud endpoint, and any other bare URL to the Server/DC one.

On the review table, **Export Selected** (next to *Import selected*) downloads the ticked rows as a CSV in the
**change-request bulk-import format** (`cr_upload_template.csv`'s columns) holding exactly what importing them would
store — default project, environment SIT, priority/complexity MEDIUM, the *CM PM : CM SA* description — so the file can
be fed straight back into Change Requests → Bulk Import. **Import from JIRA** opens in the same tab as the list.

Import endpoints live under `/change-requests/jira-import` (ADMIN, CR_MANAGER, SA_LEAD, QA_LEAD); the configuration
screen is `/admin/jira-config` (ADMIN). JIRA is called over HTTP basic auth with the configured credentials and a
configurable timeout, and failures are reported on the page — an unreachable or misconfigured JIRA never affects the
rest of the application.

### When the application server cannot reach JIRA

A locked-down server usually has no internet route, while the users' browsers do. **Connect to JIRA from the user's
browser** on Administration → JIRA Configuration switches the search to the browser:

* the browser runs the same JQL against the same endpoint, from the credentials each user types on the import screen
  (kept in that browser tab — **never** posted to this application, and the credentials stored above are not used in
  this mode);
* the issues it received are posted back, and everything after that is the identical server-side code: the same field
  mapping, the same review table with the epic/story/custom-field columns and the RFBT highlight, the same import,
  audit and notification. Only who *calls* JIRA changes;
* **Test connection** on the import screen proves the browser can reach JIRA before anyone relies on it;
* with the box unticked the server calls JIRA exactly as before, and a plain (JavaScript-off) submit in browser mode is
  refused with a message instead of quietly falling back to the server-side call.

**The requirement: JIRA must allow calls from this page (CORS).** Measured against the real products:

| JIRA | Browser call from this application? |
|---|---|
| JIRA Cloud, `https://<site>.atlassian.net/rest/api/3/...` with basic auth | **No.** Atlassian's edge sends no `Access-Control-Allow-Origin`, so the browser discards the response. Nothing can be configured on Cloud to change this |
| JIRA Cloud through Atlassian's OAuth 2.0 gateway `api.atlassian.com` (bearer token) | Yes — the gateway answers preflight with `Access-Control-Allow-Origin`/`Allow-Credentials`. Needs an OAuth app (client id/secret, redirect URI, consent); not implemented here |
| JIRA Server/Data Center, or Cloud behind your own reverse proxy | Yes, if the proxy adds `Access-Control-Allow-Origin: <this site's origin>` (and `Allow-Headers: authorization`, `Allow-Methods: GET,OPTIONS`) to the JIRA responses |

If a corporate **forward proxy** is what provides internet access, the simpler answer is usually to let *this server*
use it — no code or configuration change here, Java's HTTP client honours the standard properties (`https.` for an
`https://` JIRA URL, `http.` for a plain `http://` one):

```bash
java -Dhttps.proxyHost=proxy.example.com -Dhttps.proxyPort=8080 -jar cr-qa-management-0.0.1-SNAPSHOT.jar
```

(Verified end to end: with `-Dhttp.proxyHost/-Dhttp.proxyPort` set, the JIRA search was sent to the proxy as an
absolute-URI request and the proxy's response was what the review table rendered.)

Which to choose: use the browser mode when the users' desktops are the *only* things with a route to JIRA (and the
JIRA end allows the origin); use the proxy when the server has a way out through a proxy. The browser mode also
carries the per-user audit benefit of everyone importing with their own JIRA account.

### "Could not reach JIRA at …" — reading the error

The search never left the machine, so it is not a JIRA problem. The message now carries the transport cause, because
the three causes have completely different fixes:

| What the message shows | What it means | Fix |
|---|---|---|
| `SSLHandshakeException: unable to find valid certification path …` (also seen as `PKIX path building failed`) | TLS interception (Zscaler, Netskope, …), a private CA, or a reverse proxy (nginx) whose chain the JVM cannot build. The browser trusts the intercepting root, **the JVM does not** | paste the signing CA into **Administration → JIRA Configuration → CA certificate** (no server change), or import it into the truststore the service uses (below) |
| `UnknownHostException: <host>` | the server cannot resolve the host — DNS, or no route at all | fix DNS/routing, use the forward-proxy properties above, or switch to browser mode |
| `ConnectException: Connection refused` / `SocketTimeoutException` | a firewall, a wrong host/port, or a proxy that has to be used | check the URL and port, then the firewall between the servers |

TLS interception is the usual one on a corporate network: everything looks fine from a browser or `curl`, and only this
JVM complains. Note what an SSL handshake error proves: a TLS peer **did** answer, so the route works and the network is
not the problem — only the certificate is.

**Easiest fix — no server change.** An ADMIN pastes the certificate that signs JIRA's (or the reverse proxy's)
certificate into **CA certificate** on **Administration → JIRA Configuration**; the app then trusts it for the JIRA
calls, from the database, and it survives a `dnf update java-*` that would replace `cacerts`. This also covers JIRA
**behind a reverse proxy such as nginx**: browsers recover from an incomplete chain by fetching the intermediate, the
JVM does not, so pasting the chain here (or making nginx serve the full chain) is what fixes it.

`deploy/check-jira-trust.sh` does the whole diagnosis for you when you prefer to fix it on the server:

```bash
./deploy/check-jira-trust.sh okducagile.atlassian.net            # which CA signs JIRA, and does this JVM trust it
./deploy/check-jira-trust.sh okducagile.atlassian.net --import   # build a truststore with that CA and re-test
```

Importing the CA that signs JIRA's certificate is an operating-system change — the application needs no configuration
change and the original server-side import then works:

```bash
# Which root is in the way? (run where the JVM runs)
openssl s_client -connect okducagile.atlassian.net:443 -showcerts </dev/null 2>/dev/null | grep -E "s:|i:" | head -4

# Import it into the truststore of the JDK the service starts with (systemd ExecStart), then restart the service
sudo /usr/lib/jvm/java-17-openjdk/bin/keytool -importcert -noprompt -alias corporate-root \
     -file corporate-root.pem -cacerts -storepass changeit
sudo systemctl restart cr-qa-management

# ...or leave the JDK alone and give the service its own truststore:
#   Environment=JAVA_OPTS=-Djavax.net.ssl.trustStore=/etc/cr-qa-management/truststore.jks
```

**Pin the truststore in the unit rather than editing the JDK's `cacerts`** if you can: a JDK update
(`dnf update java-17-openjdk`) replaces `cacerts` and silently drops imported CAs, which is exactly how a working
server starts failing with `PKIX path building failed` after routine patching. A truststore the service owns
(`JAVA_OPTS=-Djavax.net.ssl.trustStore=…`) survives it.

To check what the JVM makes of JIRA before/after, without touching the application:

```bash
jshell -q - <<'EOF'
try { System.out.println("HTTP " + new java.net.URL("https://okducagile.atlassian.net/rest/api/3/myself").openConnection().getResponseCode()); }
catch (Exception e) { System.out.println("FAILED: " + e); }
EOF
# HTTP 401 = reachable and trusted (any HTTP status is fine); SSLHandshakeException = import the CA
```


Deleting a change request, test case, test cycle or bug does not hard-delete the row: it is first copied into an archive
table (`IT_change_request_archive`, `IT_test_case_archive`, `IT_test_cycle_archive`, `IT_bug_archive`, and the
`IT_*_archive` tables for CR/test-case activities and bug comments/history/attachments), which mirror the source columns
plus `archived_at` (date/time of deletion) and `archived_by` (the deleting user's id). Deleting a change request
archives and removes its whole subtree. Attachment files are retained on disk.

Schema is managed by Flyway with a per-dialect location set: `db/migration` (H2 dev + PostgreSQL prod),
`db/postgresql`, and `db/oracle`.

For a **new** environment, `db/consolidated/` holds one self-contained script per dialect (schema **and** seed data in a
single file, with the sequence-ordering trap of the migration chain fixed) plus the `baseline-on-migrate` step that
keeps such a database upgradeable — see `db/consolidated/README.md`. Existing environments are unaffected and keep
migrating as before.

## Build

```bash
mvn clean package
```

## Deploying on a server (Linux, incl. RHEL 9)

The application writes `cr-qa-management.log` (daily rotation, 30 days, 1 GB cap) **and** always logs to standard
output, which under systemd is the journal. The one thing to get right is **where the file goes**: the log directory is
`$LOG_PATH`, and when it is not set it is relative to the *working directory* — which on a server is rarely where you
think it is.

```bash
# the application prints the file it is using, once, at start-up:
#   Application log file: /var/log/cr-qa-management/cr-qa-management.log
journalctl -u cr-qa-management -f          # always available, whatever the file path is
```

- **Set `LOG_PATH` explicitly** on a server: `-DLOG_PATH=/var/log/cr-qa-management`, an exported `LOG_PATH`, or
  `logging.file.path` in the yml (all three resolve to the same variable).
- **The directory must exist and be writable by the service user**:
  `install -d -o crqa -g crqa -m 0750 /var/log/cr-qa-management`. If it cannot be created the application stops at
  start-up with `Logback configuration error detected: ... Failed to create parent directories for [...]`, and
  `LogbackStatusListener` prints the reason on stderr (so it shows up in the journal too).
- **SELinux**: a directory outside `/var/log` needs a writable label.
  `semanage fcontext -a -t var_log_t "/opt/cr-qa-management/logs(/.*)?" && restorecon -Rv /opt/cr-qa-management/logs`.
  Check denials with `ausearch -m avc -ts recent`.
- **systemd**: `deploy/cr-qa-management.service` is a ready example. `LogsDirectory=cr-qa-management` creates
  `/var/log/cr-qa-management` with the right owner *and* SELinux label in one line; `Environment=LOG_PATH=...` points the
  application at it. A hardened unit (`ProtectSystem=strict`) also needs
  `ReadWritePaths=/var/log/cr-qa-management`.
- **`sudo` resets the environment**: `LOG_PATH=/var/log/x sudo ./start.sh` silently drops the variable. Use
  `sudo env LOG_PATH=/var/log/x ./start.sh`, or set it in the unit's `Environment=`/`EnvironmentFile=`.
- **`start.sh`/`stop.sh`** honour `LOG_PATH` too (`LOG_PATH=/var/log/cr-qa-management ./start.sh`); they create the
  directory, refuse to start with a clear message when it is not writable, and pass `-DLOG_PATH` to the JVM, so the
  file always lands where the script says it does.

If no log file appears, check in this order: the `Application log file:` line at start-up (it is absolute) → the
directory's ownership (`ls -ld`) → its SELinux context (`ls -lZ`, and `ausearch -m avc -ts recent`) → whether the file
is actually being written (`lsof -p <pid> | grep cr-qa-management.log`) → `journalctl -u cr-qa-management` for the
`[logging] ERROR` line that names the reason.

## Git workflow

Development happens in a **local** repository (no shared server). `master` is the integrated branch and always holds
runnable code.

### Feature branches

Build each new feature and each non-trivial fix on its own branch, so `master` never carries half-finished
work. Name the branch after the work: `feature/<short-name>` for an enhancement and `fix/<short-name>` for a
bug fix.

```bash
git switch master
git switch -c feature/sla-escalation      # enhancement:  feature/<short-name>
                                          #     bug fix:  fix/<short-name>
# ... implement, build, verify ...
git add -A
git commit -F - <<'MSG'
feat(sla): escalate breached bugs to their owners

Adds the scheduled sweep, the per-bug escalation stamp and the aging report.
Verified by pointing the cron at the next minute and checking the notifications.
MSG
```

Commit messages: `<type>(<area>): <imperative summary>`, where type is one of `feat`, `fix`, `docs`, `refactor`,
`test`, `chore` — then a short body saying **what** changed, **why**, and **how it was verified**. Keep the subject
under ~72 characters.

When the feature or fix is accepted, merge it, tag the merge, and delete the branch:

```bash
git switch master
git merge --no-ff feature/sla-escalation -m "Merge feature/sla-escalation"
git tag -a v1.1.0 -m "v1.1.0: SLA escalation"      # tag the merge commit
git branch -d feature/sla-escalation
```

### Tags (releases)

Every merge to `master` is tagged with a semantic version, `vMAJOR.MINOR.PATCH`:

- **MINOR** — a feature merge (e.g. `v1.1.0`).
- **PATCH** — a bug-fix merge (e.g. `v1.1.1`).
- **MAJOR** — a breaking or behavioural overhaul (e.g. `v2.0.0`).

The tag is created on the merge commit (`master` at the time of the merge) and annotated with a short note.

```bash
git tag -a v1.1.0 -m "v1.1.0: bulk-import error references"   # tag the current master HEAD
git tag                                                       # list tags
git show v1.1.0                                               # what the tag points at

git switch --detach v1.1.0                                    # inspect a tagged release
git switch -c fix/urgent v1.1.0                               # ... or cut a branch from it
```

Because the repository is local, tags travel with it — a clone or a bundle file carries them along.

### Getting the code of a specific feature branch

Find it, see what it contains, then either check it out or extract just that tree:

```bash
git branch                                                   # local branches
git log --oneline master..feature/sla-escalation             # commits only on that branch
git diff --stat master...feature/sla-escalation              # files that branch touches
git switch feature/sla-escalation                            # check it out in this working copy
```

Extract the branch's tree **without** touching the current checkout (useful for review, or to hand to a server):

```bash
git archive --format=zip --output=/tmp/sla-escalation.zip feature/sla-escalation
git archive feature/sla-escalation | tar -x -C /tmp/sla-escalation
```

Check it out in a separate directory, leaving this checkout untouched:

```bash
git worktree add ../v1-sla-escalation feature/sla-escalation
git worktree remove ../v1-sla-escalation        # ... when finished with it
```

Because the repository is local, move a branch to another machine by cloning it, or by handing over a single bundle
file that contains just that branch:

```bash
git clone /Users/chown/ideaj-workspace/ai-agent-projects/issue-tracker/v1 /tmp/v1-copy
cd /tmp/v1-copy && git switch feature/sla-escalation

git bundle create sla-escalation.bundle feature/sla-escalation   # unpack with: git clone sla-escalation.bundle
```

### What is deliberately not in the repository

Build output (`target/`), uploaded attachments (`uploads/`), generated exports (`export/`), logs and the Command Code
tool state (`.commandcode/`) are ignored — see `.gitignore`.

## Project structure

```
src/main/java/com/duc/crqa/
  attachment/  audit/  bug/  common/  config/  controller/
  cr/  dashboard/  notification/  project/  report/
  security/  testcase/  testcycle/  user/
src/main/resources/
  db/migration/V1__init.sql
  samples/ (bulk-upload CSV sample templates served by the "download template" links)
  templates/ (Thymeleaf views + fragments)
  static/ (CSS/JS)
  application.yml
  application-dev.yml
  application-prod.yml
```

### Trusting JIRA certificates

```sh
# Check which CA signs JIRA, and whether this JVM trusts it in Java 26
sudo cp /Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home/lib/security/cacerts /Users/chown/Downloads/OKW/CR_PSG_Myfatoorah/backup/cacerts_20261013
sudo keytool -import -file /Users/chown/Downloads/_.atlassian.net.cer -keystore /Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home/lib/security/cacerts -alias jira
sudo keytool -list -keystore /Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home/lib/security/cacerts
```