# CampusDesk

CampusDesk is a college issue-tracking system with role-based access, ticket assignment, SLA deadlines, and an audit trail.

## Features

- Student self-registration, sign-in, and private ticket ownership.
- Staff ticket queue with server-side search, bounded pagination, assignment, status transitions, and comments.
- Operations dashboard showing live ticket counts, SLA compliance, priority/category breakdowns, and recent tickets.
- Admin forms for supported category, location, and SLA policy creation.
- Responsive React interface with accessible forms, session-expiry feedback, and ticket detail conversations.
- PostgreSQL schema and sample service catalog managed by Flyway.

## Architecture

- `backend/`: Java 21, Spring Boot 3.4, Spring Security/JWT, Spring Data JPA, Flyway, PostgreSQL.
- `frontend/`: React, TypeScript, and Vite; browser API calls use `VITE_API_BASE_URL`.
- Local fallback: H2 is selected when no database environment variables are provided.
- Database schema and initial categories, locations, and SLA policies are managed by Flyway. Hibernate validates the schema rather than creating it.
- The default profile creates demo admin/staff/student accounts. The `prod` profile disables demo accounts and bootstraps one administrator from environment variables.

Students can create and view only their own tickets. Staff and admins can view the ticket queue, assign tickets to enabled staff/admin accounts, update permitted statuses, and use the dashboard. Ticket creation records response and resolution deadlines; lifecycle operations are recorded in `ticket_events`.

## Requirements

- JDK 21
- Maven 3.9+
- Node.js 22 LTS and npm
- Docker Desktop with Docker Compose for PostgreSQL local development and PostgreSQL integration tests

Check versions in PowerShell:

```powershell
java -version
mvn -version
node --version
npm.cmd --version
docker --version
docker compose version
```

Install Temurin JDK 21 on Windows if it is not present:

```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK --exact
```

Open a new PowerShell window after installation and configure the session to use JDK 21 (adjust the path if your installer used another location):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn -version
```

Confirm `mvn -version` reports Java 21 before building.

Install Docker Desktop on Windows if needed (requires WSL 2 and may request administrator approval; start Docker Desktop and wait for its engine before using Compose or Testcontainers):

```powershell
winget install --id Docker.DockerDesktop --exact
docker version
docker compose version
```

## Run locally with PostgreSQL

From the project root in PowerShell:

```powershell
Copy-Item .env.example .env
notepad .env
```

Set unique local values for `DB_PASSWORD` and `JWT_SECRET` in `.env`; do not commit `.env`. Generate a random JWT secret if desired:

```powershell
$bytes = New-Object byte[] 48
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
$rng.Dispose()
```

Start the database and backend:

```powershell
docker compose up --build -d
docker compose ps
Invoke-WebRequest http://localhost:8080/actuator/health -UseBasicParsing
```

Expected: PostgreSQL becomes `healthy`, the backend starts after it, Flyway applies the schema migration, and the health endpoint returns HTTP 200 with `{"status":"UP"}`. On subsequent runs, Compose reuses the named `campusdesk_pgdata` volume. `docker compose down` stops/removes containers but preserves that volume. Do not use `docker compose down -v` unless you intentionally want to delete the local database.

Start the frontend in another terminal:

```powershell
Set-Location .\frontend
npm ci
$env:VITE_API_BASE_URL = 'http://localhost:8080/api'
npm.cmd run dev
```

Open http://localhost:5173. The default local Compose profile seeds the demo accounts:

- Student: `student` / `Student@123`
- Staff: `staff` / `Staff@123`
- Admin: `admin` / `Admin@123`

These are local demo credentials only; do not use them for a public deployment.

### If Docker is unavailable: install PostgreSQL on Windows

Install PostgreSQL 16 using WinGet (the installer may request administrator approval):

```powershell
winget install --id PostgreSQL.PostgreSQL.16 --exact
```

During setup, keep port `5432` and remember the password assigned to the `postgres` superuser. Open PowerShell and run:

```powershell
& 'C:\Program Files\PostgreSQL\16\bin\psql.exe' -h localhost -U postgres -d postgres
```

At the `psql` prompt, create an application user and database. `\password` prompts without placing the password in a command:

```sql
CREATE ROLE campusdesk LOGIN;
\password campusdesk
CREATE DATABASE campusdesk OWNER campusdesk;
\q
```

Copy `.env.example` to `.env`, set `DB_PASSWORD` to the value entered for `campusdesk`, and set a unique `JWT_SECRET`. Load those values into the current PowerShell process and run the backend without activating `prod`:

```powershell
Get-Content .env | Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    Set-Item -Path "Env:$name" -Value $value
}
Set-Location .\backend
mvn spring-boot:run
```

Do not run both native PostgreSQL and Compose PostgreSQL on port 5432 at the same time. This native install path has not been executed in the current environment.

### Run the backend outside Docker

If using Docker, start only PostgreSQL using Compose, which keeps the database volume intact:

```powershell
docker compose up -d postgres
```

In a second terminal, load the values from `.env` into the process environment as above (PowerShell does not automatically load `.env`), set Java/Maven as needed, then run:

```powershell
Set-Location .\backend
mvn spring-boot:run
```

Alternatively, unset `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_DRIVER_CLASS_NAME`, and `HIBERNATE_DIALECT` to use the in-memory H2 fallback.

## Tests and builds

Run backend unit/application tests (uses isolated in-memory H2):

```powershell
Set-Location .\backend
mvn test
```

Run PostgreSQL integration tests (requires a working Docker daemon; Testcontainers creates and removes its own isolated test container):

```powershell
Set-Location .\backend
mvn -Ppostgres-integration verify
```

Ticket queue endpoints (`/api/tickets`, `/api/tickets/my`, and `/api/tickets/assigned`) accept `page` (zero-based, default `0`) and `size` (default `50`, maximum `100`) query parameters. The React queue loads additional pages on demand.

Run the reproducible frontend install and production build:

```powershell
Set-Location .\frontend
npm ci
npm run build
```

The GitHub Actions workflow runs the backend tests on Java 21, PostgreSQL integration tests with Testcontainers, and the frontend production build. The PostgreSQL integration profile is separate so local H2 tests do not require Docker.

## GitHub release preparation

The project-local repository should be the Git root. Before the first commit, review the complete candidate list and check that `.env`, local databases, signing keys, `node_modules`, `target`, and `dist` are ignored:

```powershell
Set-Location 'C:\path\to\CampusDesk'
git rev-parse --show-toplevel
git status --short --untracked-files=all
git check-ignore -v .env backend\target\sample.jar frontend\node_modules\sample.js frontend\dist\sample.js
```

After reviewing the list, stage only intended CampusDesk source, tests, configuration, and documentation, then inspect the complete staged diff:

```powershell
git add -A
git diff --cached --name-only
git diff --cached --check
git diff --cached
```

If any secret or unrelated file appears, unstage that specific path with `git rm --cached -- <path>` (this keeps the working copy) and inspect again. This works before the first commit, when there is no `HEAD` to restore from. Choose the standard branch name before committing:

```powershell
git branch -M main
git commit -m "Initial CampusDesk application"
```

To publish later, first create an empty GitHub repository in the browser (do not initialize it with a README, license, or `.gitignore`), then add its URL and push the `main` branch:

```powershell
git remote add origin https://github.com/<your-account>/<repository>.git
git remote -v
git push -u origin main
```

The project GitHub repository is `https://github.com/shruthikuragayala-02/CampusDesk`. Its existing `main` branch contains an initial README-only commit. Fetch and inspect `origin/main` before the first push, preserve that history, and reconcile unrelated initial histories with a reviewed merge if needed. Never force-push. The workflow runs on pushes to `main`/`master` and pull requests; review its jobs in the repository's **Actions** tab. CI does not require app credentials; deploy secrets belong in the selected hosting provider's secret settings, never in GitHub source files.

## Configuration and deployment

Do not commit `.env`, production database credentials, admin passwords, or JWT signing keys. For deployment, set these as the hosting provider's environment variables:

- `SPRING_PROFILES_ACTIVE=prod`
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET` (use a random value of at least 32 bytes)
- `CORS_ALLOWED_ORIGINS` (comma-separated exact HTTPS frontend origins)
- `ADMIN_USERNAME`, `ADMIN_EMAIL`, `ADMIN_FULL_NAME`, `ADMIN_PASSWORD` (the initial admin password must be at least 12 characters)
- `SERVER_PORT` if the provider requires a specific port
- `PORT` is honored automatically for platforms that inject a listening port

The production profile requires PostgreSQL/JWT/CORS variables, suppresses SQL bind logging and detailed error responses, and does not create demo users. It creates the initial administrator once if the username is not already present. Flyway runs at application startup. Set `VITE_API_BASE_URL` to the deployed backend API URL when building the frontend; Vite values are embedded at build time.

The backend Dockerfile builds with Java 21, packages the runnable Spring Boot JAR, and runs it as a non-root user. Compose is intended for local development. The checked-in [Render Blueprint](./render.yaml) describes a free Docker web service only; it does not create a database or contain credentials. It uses Render's injected `PORT`, checks `/actuator/health`, and prompts for externally managed database and admin settings. The frontend's [Cloudflare Pages headers](./frontend/public/_headers) add browser security headers to static responses.

### Recommended no-cost demo deployment

This combination avoids paid actions while using a managed PostgreSQL database rather than ephemeral local files. It is suitable for a disposable demo, not for storing real student or institutional data without explicit privacy/security approval.

1. **Create the database in Neon.** Sign in to Neon, create a Free PostgreSQL project in a region close to the backend, and create a dedicated application role/database. Keep the credentials in a password manager. Use the pooled connection hostname if offered. Set Render's `DB_URL` in JDBC form, for example `jdbc:postgresql://<pooled-host>/<database>?sslmode=require`; set `DB_USERNAME` and `DB_PASSWORD` separately in Render. Do not paste credentials into this repository or chat. Do not use Render's Free PostgreSQL for durable demo data: it expires after 30 days and has no backups.
2. **Create the Cloudflare Pages site.** In Cloudflare Pages, connect the GitHub repository and choose the `main` branch. Set the root directory to `frontend`, build command to `npm ci && npm run build`, and output directory to `dist`. Set the project name and note its exact HTTPS `https://<project>.pages.dev` origin. The app may not reach the API until the backend is ready.
3. **Create the Render backend from the Blueprint.** In Render, choose **New > Blueprint**, authorize access to the CampusDesk repository, and select `main`. Review `render.yaml` and ensure the service plan remains `free`. At the initial prompt, set:
   - `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` from Neon.
   - `CORS_ALLOWED_ORIGINS` to the exact Cloudflare Pages HTTPS origin, with no trailing slash.
   - `ADMIN_USERNAME`, `ADMIN_EMAIL`, `ADMIN_FULL_NAME`, and a unique `ADMIN_PASSWORD` of at least 12 characters.
   - `JWT_SECRET` is generated by Render; do not replace it with a committed value.
4. **Wait for migrations and health.** Render builds the Java 21 Docker image, starts the app with the production profile, and Flyway applies the migrations to the newly created database. In Render's service page, wait for deployment to finish and check `/actuator/health` returns HTTP 200 and `{"status":"UP"}`. Never point this new service at a database containing data you do not intend to migrate or modify.
5. **Connect and rebuild the frontend.** In Cloudflare Pages > Settings > Environment variables, set `VITE_API_BASE_URL` to `https://<render-service>.onrender.com/api` for production builds. Redeploy the Pages project so Vite embeds the API URL. Confirm Render's `CORS_ALLOWED_ORIGINS` exactly matches the resulting `pages.dev` origin; if changed, update it in Render and redeploy the backend.
6. **Smoke-test the live deployment.** Use the admin bootstrap account to sign in, register a separate student, create a test ticket, and verify staff assignment/status changes and ticket ownership behavior. Check browser network/console output for CORS or failed API calls. Use test-only data until institution approval is in place.

**Free-tier limits (recheck provider terms before setup):**

- **Cloudflare Pages Free** can serve the static frontend; current limits include 500 builds/month and 20-minute build timeouts. It does not run the Spring Boot API or PostgreSQL. See [Pages limits](https://developers.cloudflare.com/pages/platform/limits/).
- **Render Free** runs the Docker backend but spins it down after 15 minutes idle; waking can take about a minute. Free services have ephemeral filesystems and shared monthly instance-hour limits. Render may suspend unusually high outbound traffic to external databases. See [Render free instance limitations](https://render.com/docs/free).
- **Neon Free** currently includes 100 compute-unit hours per project/month, 1 GB storage per project, and 20 GB total storage per account. It scales to zero and its Free plan has a short history window and limited snapshot/recovery features. See [Neon plans](https://neon.com/docs/introduction/plans).

If the application must be available continuously or store institutional/personal data, do not use this free stack. Use an always-on backend and a database plan with backups, after reviewing the institution's privacy/security requirements and approving the provider's current pricing. No paid deployment is performed by this repository.

No live deployment has been created or claimed. A deployment is verified only after the hosted frontend, API, and database have been exercised together.

## API endpoints

- `POST /api/auth/login`
- `POST /api/auth/register` (registration creates a student account)
- `POST /api/tickets`
- `GET /api/tickets/my`
- `GET /api/tickets` (staff/admin queue; student result is limited to their own tickets)
- `GET /api/tickets/assigned`
- `POST /api/tickets/{ticketId}/assign`
- `PATCH /api/tickets/{ticketId}/status`
- `POST /api/tickets/{ticketId}/comments`
- `GET /api/categories`
- `GET /api/locations`
- `GET /api/dashboard` (staff/admin)
- `GET /api/admin/users` (staff/admin assignee list)

Protected API requests without a valid JWT return `401`; authenticated users denied by role or ticket-ownership checks receive `403`. Student ticket ownership is enforced by the backend regardless of request IDs, query parameters, or extra request fields. The API does not provide ticket update or delete routes.
