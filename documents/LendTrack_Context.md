# LendTrack --- Context for Claude

## 1. Project Overview

**Project name:** LendTrack\
**Tagline:** Your Lending, Organized.\
**Product:** A pink-themed lending management application focused on
borrower records, loan balances, collections, installment schedules, and
payment tracking.

Build a **native Android tablet application**. The first deployment will
be used on one tablet, with intermittent internet connectivity. The app
must remain useful offline and synchronize with a cloud backend when
connectivity returns.

This file provides project context and design requirements for an AI
coding assistant. Treat the attached HTML prototype,
`LendTrack — Your Lending, Organized (3).html`, as the primary visual
reference. Inspect it before implementation when it is available.

## 2. Selected Technology Stack

-   **Platform:** Native Android tablet application
-   **Language:** Kotlin
-   **UI:** Jetpack Compose with Material 3
-   **Local persistence:** Room over SQLite
-   **Backend:** NestJS REST API
-   **Cloud database:** PostgreSQL
-   **Background synchronization:** WorkManager
-   **Currency:** Philippine Peso (₱)

Use a maintainable architecture with clear separation between UI,
domain/business logic, data repositories, local storage, API clients,
and synchronization logic. Do not introduce additional frameworks or
services without explaining why they are needed.

## 3. Design Context

### 3.1 Visual direction

Preserve the HTML prototype's visual identity. The application should
feel warm, friendly, organized, feminine, and professional---not like a
generic banking dashboard. Use soft blush and cream surfaces, dusty-rose
accents, burgundy typography, delicate borders, subtle shadows, rounded
corners, and restrained decorative details.

The overall feel should be a polished lending planner with a soft
stationery/journal aesthetic. Prioritize readability and efficient data
entry; decorative elements must not interfere with financial information
or tablet usability.

### 3.2 Color palette

Use these colors as the starting design tokens:

  -----------------------------------------------------------------------
  Token                   Hex                     Intended use
  ----------------------- ----------------------- -----------------------
  Background              `#FFF5F0`               Main cream background

  Card                    `#FFFCFA`               Cards, sheets, and
                                                  panels

  Blush                   `#F6D8D8`               Sidebar, selected
                                                  surfaces, soft emphasis

  Dusty pink              `#D98C96`               Primary accents,
                                                  outlines, selected
                                                  controls

  Rose                    `#C45C72`               Primary buttons and
                                                  important accents

  Burgundy                `#783746`               Strong headings and
                                                  emphasis

  Main text               `#4B3035`               Primary text

  Muted text              `#96757A`               Secondary labels and
                                                  supporting text

  Sage                    `#6F8F72`               Paid/resolved/success
                                                  states

  Amber                   `#C99A3C`               Due-soon/warning states

  Red                     `#A8454F`               Overdue/error states
  -----------------------------------------------------------------------

Use semantic color roles consistently. Maintain sufficient contrast for
text, status labels, and controls. Do not rely on color alone to
communicate payment status.

### 3.3 Typography

The HTML reference uses: - **Playfair Display** for headings - **Inter**
for body text and interface labels - **Great Vibes** for decorative
script accents

Use these fonts if practical and licensing/asset availability permits.
Otherwise, select close, readable Android-compatible alternatives and
document the substitution. Decorative script must be limited to small
accent phrases, never used for amounts, tables, form labels, or critical
information.

### 3.4 Shape, borders, and decoration

-   Warm cream, subtly dotted background where appropriate.
-   Blush sidebar and cream/white cards.
-   Thin dusty-pink borders.
-   Rounded cards, input fields, chips, and buttons.
-   Subtle burgundy-tinted shadows rather than heavy elevation.
-   Occasional dashed inset borders.
-   Small floral corner graphics, heart-inspired accents, and rose
    ribbon-shaped section headers where they fit the native layout.
-   Rounded status badges with clear labels.
-   Avoid clutter, excessive gradients, strong drop shadows, and
    oversized decorations.

Translate web-specific visual effects into native Compose equivalents.
Do not embed the HTML page in a WebView as a substitute for a native
app.

### 3.5 Branding

-   App name: **LendTrack**
-   Tagline: **Your Lending, Organized.**
-   Logo direction: a heart-inspired pink/burgundy mark.
-   Sidebar quote: **"Small steps make big dreams come true."**

Keep branding consistent across the dashboard, navigation drawer,
dialogs, and app-level surfaces.

## 4. Navigation and Responsive Layout

Navigation order:

1.  Dashboard
2.  Borrowers
3.  Payment Calendar
4.  Balance Ledger
5.  Payments
6.  Reports

### Tablet layouts

Design and test both orientations: - Portrait reference size:
approximately **820 × 1180** - Landscape reference size: approximately
**1180 × 820**

In landscape, use a persistent sidebar around 240 dp wide when
appropriate. In portrait, collapse navigation into a drawer or similarly
accessible compact navigation pattern. Use two-column dashboard panels
in wide layouts and a single-column flow in narrow layouts.

Adapt layouts using available window size rather than hardcoding exact
screen dimensions. Ensure controls remain comfortable to tap on a tablet
and that tables can be read without awkward clipping.

## 5. Screen Requirements

### 5.1 Dashboard

Top bar: - Greeting: **"Good morning, Lender!"** - Current date using
the device's actual date - Notification button with unread badge when
applicable - Profile area - Menu/navigation control in portrait layout

Summary cards: - Total Outstanding - Collected This Month - Due Today -
Overdue Payments

Main dashboard panels: - **Upcoming Payments:** search, status filter,
due date, borrower, loan principal, amount due, status badge, and a Pay
action. - **Compact Monthly Calendar:** previous/next month controls,
selected/current date, event indicators, and a legend. - **Balance
Overview:** donut/progress visualization with principal, interest,
payments received, and remaining balance. - **Recent Activity:** recent
loan, borrower, and payment events. - **Quick Actions:** Add Borrower,
Add Loan, Record Payment, Import Borrowers, and View Calendar.

The dashboard should surface the most actionable information first. Make
overdue and due-today items easy to identify without making the whole
interface feel alarming.

### 5.2 Borrowers

Provide a borrower list with: - Borrower name - Contact information -
Number of loans - Outstanding balance - Borrower status - Search and
useful filters - Add Borrower action

Selecting a borrower opens a details view or dialog with their contact
information, loans, balance summary, installment schedule, and
transaction history.

The Add Borrower flow must validate required fields, show helpful
errors, and persist the record locally.

### 5.3 Loans

Include a complete **Add Loan** flow, even if the HTML prototype only
contains hardcoded loan data. Capture the necessary loan details, such
as borrower, principal, interest configuration, start date, payment
schedule, installment amount or calculation method, and notes where
appropriate.

Clearly explain the interest and installment calculation rules in the
UI. Validate inputs and show a review/confirmation step before saving.
Avoid silently changing financial values or rounding rules.

Support mixed payment schedules where needed. Store monetary values with
an appropriate precise representation (for example, integer minor
currency units or a suitable decimal type); do not use floating-point
arithmetic for financial calculations.

### 5.4 Payment Calendar

Provide: - Monthly calendar with previous/next navigation - Indicators
for dates containing payment events - Filters: All, Paid, Upcoming, Due
Soon, and Overdue - Selected-day installment list - Borrower, due date,
amount, and payment status - Navigation to the relevant borrower, loan,
installment, or payment record

Calendar dates and payment status must be derived from saved records,
not demo-only arrays.

### 5.5 Balance Ledger

Show overall loan totals and allow expansion into installment-level
details. Include transaction history and a clear explanation of how
principal, interest, payments received, and remaining balances relate.

Use consistent financial calculations throughout the dashboard, borrower
detail, ledger, payment screens, and reports. Do not calculate the same
concept differently on different screens.

### 5.6 Payments

Provide a searchable/filterable payment history with: - Date -
Borrower - Related loan/installment - Payment method - Amount - Any
applicable reference or notes

Include a **Record Payment** flow. The flow should identify the borrower
and loan, select the installment(s) or amount to pay, choose a payment
date and method, preview the effect on the balance, and confirm before
saving.

Support partial payments if included in the project rules. Prevent
duplicate payment submission where possible. A recorded payment should
update the relevant installment status and outstanding balance
consistently.

### 5.7 Reports

Provide summary metrics and date-range filtering for: - Principal lent -
Total outstanding - Overdue amounts - Collections within the selected
period - Payment/collection summaries

Include CSV export. Make exported data consistent with the filters and
totals displayed in the report. If additional export formats are
proposed, explain their implementation and dependency requirements.

### 5.8 Dialogs and detail views

Provide polished native screens, dialogs, or sheets for: - Add
Borrower - Add Loan - Record Payment - Borrower Details - Installment
Details - Payment Details - CSV Import/Export

Prefer native Compose components with consistent spacing, field styles,
validation, and button hierarchy.

## 6. Functional and Data Requirements

-   Replace prototype-only in-memory state with persistent Room-backed
    data.
-   Use the real current date; the HTML prototype's
    `TODAY = "2025-10-12"` is demo data and must not be retained as a
    production date.
-   Keep sample/seed data separate from production data and make demo
    data easy to distinguish or disable.
-   Add, view, update, and where appropriate archive borrowers and
    loans.
-   Record payments and maintain an auditable transaction history.
-   Derive dashboard cards, calendar events, ledger totals, and reports
    from the same source of truth.
-   Validate required fields, dates, positive amounts, and relationships
    between borrowers, loans, installments, and payments.
-   Show loading, empty, success, error, and offline states.
-   Confirm destructive or financially consequential actions.
-   Use accessible labels and adequate touch targets.
-   Avoid fake buttons, nonfunctional navigation, placeholder-only
    screens, and success messages that appear before an operation
    actually succeeds.

### CSV import/export

The prototype supports CSV rather than true Excel `.xlsx` files.
Preserve CSV import/export for the MVP unless `.xlsx` is explicitly
added as a separate requirement.

For CSV import: - Validate headers and required fields. - Preview rows
before committing. - Identify invalid rows and likely duplicates. - Let
the user cancel before data is written. - Report the number of
successfully imported and rejected rows.

For CSV export: - Use predictable headers and safe escaping. - Respect
the selected report/date filters. - Avoid exporting unnecessary
sensitive information.

## 7. Offline-First Architecture and Synchronization

The app must remain functional during intermittent connectivity.

### Local-first behavior

-   Room is the local source used by the UI so that saved data can be
    viewed and updated offline.
-   Use repositories to coordinate local data and remote API operations.
-   UI should observe local database flows and update when local records
    change.
-   Clearly indicate whether data is local, pending synchronization,
    synchronized, or in conflict when that distinction matters.

### Synchronization

-   Use WorkManager for deferred and retryable background
    synchronization.
-   Queue local changes for upload when online.
-   Retry temporary failures with appropriate backoff.
-   Make API operations idempotent where practical, especially payment
    submissions.
-   Use stable identifiers and timestamps/version metadata to identify
    changes.
-   Define conflict handling for edits made on the tablet and in the
    cloud.
-   Do not silently overwrite financial records during conflict
    resolution.
-   Explain what happens when the same borrower, loan, installment, or
    payment is changed on multiple devices. The initial rollout is one
    tablet, but the architecture should not make future expansion
    unnecessarily difficult.

### Financial integrity

-   Preserve payment history and avoid destructive replacement of posted
    financial transactions.
-   Prefer corrections or reversals with an audit trail over silently
    editing historical payment facts.
-   Ensure offline operations cannot create duplicate payments during
    retry.
-   Define how IDs are created offline and reconciled with the backend.
-   Document the synchronization states and failure recovery behavior.

## 8. Backend and Database Expectations

Use **NestJS REST API** and **PostgreSQL**. Design a clear API contract
and database schema covering, at minimum, the entities needed for: -
Borrowers - Loans - Installments or payment schedules -
Payments/transactions - Synchronization metadata or change tracking,
where needed

Include appropriate relationships, validation, timestamps, and indexes.
Define authentication and authorization appropriate to a lender-only
application; do not expose borrower financial information publicly. Keep
credentials and secrets out of source code.

Document: - API endpoints and request/response DTOs - Database
migrations - Error response format - Authentication approach -
Local-to-server synchronization behavior - Setup instructions for
development and deployment

Do not invent a cloud deployment provider unless one is selected later.
Keep local development setup reproducible.

## 9. Code Quality and Project Organization

-   Use idiomatic Kotlin and Jetpack Compose.
-   Use Material 3 components styled through a centralized theme.
-   Separate composables from business logic.
-   Use ViewModels and state holders appropriately.
-   Use repositories for data access.
-   Use Room entities/DAOs and explicit data models/DTOs.
-   Keep calculations and validation in testable domain functions.
-   Avoid putting the whole application in one file.
-   Avoid hardcoded demo values in production paths.
-   Avoid pseudocode or disconnected code snippets when implementation
    is requested.
-   Include useful comments for non-obvious business rules,
    synchronization, and financial calculations.
-   Add tests for core financial calculations, validation, and
    synchronization behavior where practical.

## 10. Workflow for the Coding Assistant

Work in clear phases rather than attempting an unreviewable, all-at-once
implementation.

### Phase 1 --- Inspect and plan

1.  Inspect the supplied HTML prototype and its assets.
2.  Summarize its visual structure, reusable components, and existing
    behavior.
3.  Identify which behaviors are only mocked or in-memory.
4.  Propose the Android project structure, data model, and
    implementation sequence.
5.  State any assumptions or unresolved financial rules before
    implementing them.

### Phase 2 --- Build the visual foundation

1.  Configure the Compose project and Material 3 theme.
2.  Implement the color, typography, spacing, shape, and elevation
    tokens.
3.  Implement navigation and portrait/landscape tablet layouts.
4.  Build reusable cards, status badges, buttons, input fields, dialogs,
    and empty states.
5.  Match the HTML reference's visual identity while keeping the result
    genuinely native.

### Phase 3 --- Implement local features

1.  Define Room entities and DAOs.
2.  Implement repositories and ViewModels.
3.  Build Borrowers, Loans, Calendar, Ledger, Payments, and Reports
    screens.
4.  Connect all dashboard totals and lists to persisted data.
5.  Add validation and tests for financial calculations.

### Phase 4 --- Implement backend and sync

1.  Define the NestJS API contract and PostgreSQL schema.
2.  Implement authentication and required endpoints.
3.  Add WorkManager-based synchronization.
4.  Handle retries, duplicate prevention, offline IDs, and conflicts.
5.  Test offline-first flows and recovery after connectivity returns.

### Phase 5 --- Polish and verify

1.  Test portrait and landscape layouts.
2.  Test empty, populated, error, and offline states.
3.  Test borrower creation, loan creation, partial/full payments if
    supported, overdue status, and reports.
4.  Verify that totals agree across screens.
5.  Provide build/run instructions and a concise list of completed and
    remaining tasks.

## 11. First Response Expected from Claude

Before writing a large amount of code, respond with: 1. A concise
understanding of the app and its design. 2. A list of prototype elements
that must be preserved. 3. A proposed Android project/file structure. 4.
The initial Room data model and key relationships. 5. Any financial
business-rule questions that must be resolved before calculations are
finalized. 6. A phased implementation plan.

Then begin with the first implementation phase and provide complete,
file-by-file code as the project progresses. Clearly identify each file
path. Do not claim that a feature is complete unless its UI,
persistence, and relevant behavior are implemented.

## 12. Important Constraints

-   The HTML prototype is a **design and behavior reference**, not the
    production app itself.
-   The final app must be native Android using Kotlin and Jetpack
    Compose---not a WebView wrapper.
-   The app must work offline for core workflows and sync safely when
    connectivity returns.
-   Use Philippine Peso (₱) for displayed currency.
-   Preserve the soft pink/cream/burgundy LendTrack identity.
-   Financial calculations must be explicit, consistent, and testable.
-   Separate assumptions from confirmed requirements.
-   Do not fabricate functionality, API behavior, test results, or
    deployment status.
