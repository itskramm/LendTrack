# LendTrack

**Native Android tablet application for managing borrowers, loans, and payment collections.**

> Platform: Android tablet · Language: Kotlin · UI: Jetpack Compose + Material 3 · DB: Room + PostgreSQL (backend)

---

## Project Structure

```
LendTrack/
├── app/                                     # Android application module
│   └── src/main/java/com/lendtrack/app/
│       ├── core/
│       │   ├── database/                    # Room database, entities, DAOs
│       │   ├── network/                     # Retrofit, API interfaces, DTOs
│       │   ├── sync/                        # WorkManager workers, outbox
│       │   ├── security/                    # Auth, credential protection
│       │   └── ui/
│       │       ├── theme/                   # Colors, typography, shapes, theme
│       │       └── components/              # Shared Compose components
│       ├── data/                            # Repositories, data sources
│       ├── domain/                          # Models, use cases, calculations
│       ├── feature/
│       │   ├── dashboard/                   # Dashboard screen + ViewModel
│       │   ├── borrowers/                   # Borrower management
│       │   ├── calendar/                    # Payment calendar
│       │   ├── ledger/                      # Balance ledger
│       │   ├── payments/                    # Payment recording
│       │   ├── reports/                     # Reports + CSV export
│       │   └── settings/                    # Sync status + settings
│       ├── navigation/                      # NavGraph, destinations
│       └── ui/                              # LendTrackApp scaffold
└── backend/                                 # NestJS REST API (Phase 4)
```

---

## Phase Checklist

| Phase | Status     | Description                                       |
|-------|------------|---------------------------------------------------|
| 1     | ✅ Done     | Native UI — theme, scaffold, dashboard, all screens |
| 2     | ⬜ Next     | Room persistence — borrowers, loans, installments, payments |
| 3     | ⬜ Pending  | Offline sync — WorkManager outbox, retry, reconciliation |
| 4     | ⬜ Pending  | NestJS backend — PostgreSQL, auth, idempotency    |
| 5     | ⬜ Pending  | Reports, CSV export, release APK                  |

---

## Setup — Phase 1

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android tablet or emulator (10"+ recommended, API 26+)

### 1. Clone and open

```bash
git clone <repo-url>
cd LendTrack
# Open in Android Studio: File → Open → select LendTrack/
```

### 2. Download fonts

See `app/src/main/res/font/FONT_SETUP.md` for the required font files.
                                                                                                    
Download from Google Fonts and place `.ttf` files in `app/src/main/res/font/`.

### 3. Build and run

```bash
./gradlew assembleDebug
# Or use Android Studio: Run → Run 'app'
```

### 4. Run unit tests

```bash
./gradlew test
```

---

## Design System

### Color palette (matches HTML prototype exactly)

| Token           | Hex       | Usage                          |
|-----------------|-----------|--------------------------------|
| `LtBackground`  | #FFF5F0   | Page background                |
| `LtSurface`     | #FFFCFA   | Card / panel surface           |
| `LtBlush`       | #F6D8D8   | Sidebar, accents               |
| `LtDusty`       | #D98C96   | Borders, secondary             |
| `LtRose`        | #C45C72   | Primary buttons, active states |
| `LtBurgundy`    | #783746   | Headings, primary text         |
| `LtText`        | #4B3035   | Body text                      |
| `LtMuted`       | #96757A   | Secondary / hint text          |
| `LtSage`        | #6F8F72   | "Paid" badge                   |
| `LtAmber`       | #C99A3C   | "Due Soon" badge               |
| `LtRed`         | #A8454F   | "Overdue" badge, errors        |

### Typography

- **Headings:** Playfair Display (serif)
- **Body / UI:** Inter (sans-serif)

---

## Interest Calculation

Two methods are explicitly supported. The method must be chosen when creating a loan.

### Flat Rate

```
totalInterest    = principal × annualRate × (n × frequencyDays / 30) / 12
totalPayable     = principal + totalInterest
installmentAmount = totalPayable / n
```

Every installment is the same amount. The last installment absorbs rounding residuals.

### Reducing Balance

```
periodicRate = annualRate / (365 / frequencyDays)
payment      = principal × r(1+r)^n / ((1+r)^n − 1)
```

Standard amortisation. Each installment has a different interest/principal split but the payment amount is fixed (except the last).

### Payment allocation

Payments are applied FIFO — earliest unpaid installment first. Partial payments leave an installment open with a remaining balance. Each allocation is recorded in `payment_allocations`.

---

## Offline-First Architecture

```
UI → ViewModel → Use Case → Repository
                              ↓           ↓
                           Room DB    (offline)
                              ↑
                         WorkManager (sync outbox)
                              ↓
                        NestJS Backend
                              ↓
                         PostgreSQL
```

Every mutation:
1. Validates locally
2. Saves to Room atomically with a `SyncOperation` record
3. Updates the UI immediately via Flow
4. WorkManager drains the outbox when connectivity returns
5. Server confirms → record marked `SYNCED`

Pending payments are clearly distinguished from server-confirmed payments throughout the UI.

---

## Known Limitations (Phase 1)

- Fonts must be downloaded manually (see `FONT_SETUP.md`)
- Dashboard stats are reactive but the database starts empty; add borrowers and loans to populate
- Loan creation screen (AddLoan) is not yet implemented — Phase 2
- WorkManager sync worker not yet wired — Phase 3
- Backend not yet implemented — Phase 4
- `mipmap/ic_launcher` uses the default Android icon until a custom icon is added

---

## Decisions and Assumptions

| Topic                  | Decision                                                                          |
|------------------------|-----------------------------------------------------------------------------------|
| Interest method        | Must be explicitly chosen; no silent default                                      |
| Payment allocation     | FIFO — earliest unpaid installment first                                          |
| Duplicate prevention   | Idempotency key on every payment; checked before insert                           |
| UUID strategy          | Device-generated UUIDs; server assigns `serverId` after confirmation              |
| Offline data survival  | Room is the source of truth; WorkManager outbox survives kills and restarts       |
| Sync conflict policy   | Server wins for rejected operations; UI shows `REQUIRES_ATTENTION` for manual review |
| Database migration     | Explicit `Migration` objects required; no destructive migration in production     |
| Backup / recovery      | Local DB excluded from cloud backup; backend PostgreSQL is the recovery source    |
