# LendTrack --- Offline-First Architecture and Synchronization Design

## 1. Purpose

This document defines the proposed architecture for **LendTrack**, a
native Android tablet application for managing borrowers, loans,
installment schedules, balances, and payments.

The system is designed to operate during intermittent internet
connectivity while keeping cloud data secure and financial records
consistent.

## 2. Architecture Decision

LendTrack will use the following architecture:

-   **Android application:** Kotlin and Jetpack Compose
-   **Local database:** Room over SQLite
-   **Background synchronization:** Android WorkManager
-   **Authentication:** Firebase Authentication
-   **Backend:** NestJS REST API hosted online
-   **Cloud database:** Firebase Cloud Firestore
-   **Backend-to-Firebase access:** Firebase Admin SDK
-   **Network communication:** HTTPS

**Core design principle:** Room is the local source of truth for the
Android UI, while NestJS validates and controls cloud writes to
Firestore.

The Android application should not contain Firebase service-account
                                                                                                                                                                 credentials. Cloud writes that affect financial records should pass
through the authenticated backend.

## 3. High-Level Architecture

``` text
┌────────────────────────────────────────────────────┐
│                 Android Tablet App                 │
│                                                    │
│  Kotlin · Jetpack Compose · ViewModels             │
│  Dashboard · Borrowers · Loans · Calendar          │
│  Balance Ledger · Payments · Reports               │
│                                                    │
│  ┌────────────────────┐  ┌──────────────────────┐  │
│  │ Room / SQLite      │  │ WorkManager          │  │
│  │ Local records      │  │ Sync scheduling      │  │
│  │ Pending changes    │  │ Retry and backoff    │  │
│  └────────────────────┘  └──────────────────────┘  │
└───────────────────────────┬────────────────────────┘
                            │ HTTPS REST API
                            ▼
┌────────────────────────────────────────────────────┐
│            Hosted NestJS Backend                   │
│                                                    │
│  Firebase ID token verification                    │
│  Authorization and input validation                │
│  Financial business rules                          │
│  Idempotency and audit logging                     │
└───────────────────────────┬────────────────────────┘
                            │ Firebase Admin SDK
                            ▼
┌────────────────────────────────────────────────────┐
│             Firebase Cloud Firestore               │
│                                                    │
│  users · borrowers · loans · installments          │
│  payments · auditLogs · synchronization metadata   │
└────────────────────────────────────────────────────┘
```

## 4. Component Responsibilities

### 4.1 Android application

The Android app provides the user interface and application workflows.
It should use a layered structure with UI, ViewModels, domain/business
logic, repositories, and data sources.

Responsibilities: - Display locally available borrower, loan,
installment, and payment data. - Save supported actions locally so they
remain available offline. - Validate basic input before saving. - Show
synchronization status and errors clearly. - Schedule pending changes
for upload when a suitable network connection is available.

### 4.2 Room / SQLite

Room stores the tablet's working copy of data.

Responsibilities: - Persist borrower, loan, installment, and payment
records locally. - Store pending synchronization operations. - Track
local changes, server identifiers, versions, and synchronization
status. - Supply observable data to the UI. - Keep the UI responsive
without requiring a network request for every screen.

A local financial operation and its pending-sync queue entry should be
committed in the same Room transaction where practical.

### 4.3 WorkManager

WorkManager schedules retryable background work, including
synchronization.

Responsibilities: - Run synchronization when the required network
conditions are met. - Retry temporary network/server failures with
backoff. - Process queued changes in a safe, predictable order where
dependencies require it. - Avoid parallel processing of the same pending
operation. - Preserve pending operations across app restarts and device
reboots.

WorkManager does not replace application-level duplicate prevention or
conflict handling.

### 4.4 Firebase Authentication

Firebase Authentication provides the lender's identity.

Recommended request flow: 1. The lender signs in to the Android
application. 2. The app obtains a Firebase ID token. 3. The app sends
the token to the NestJS API over HTTPS. 4. NestJS verifies the token
using the Firebase Admin SDK. 5. NestJS checks the authenticated user's
authorization before processing the request.

The backend must not trust a user ID supplied only in a request body. It
should derive the authenticated identity from the verified token and
enforce access restrictions on every protected operation.

### 4.5 NestJS backend

NestJS is the cloud-facing API and validation layer.

Responsibilities: - Authenticate requests by verifying Firebase ID
tokens. - Authorize access to the requested lender's data. - Validate
request payloads and business rules. - Process loan and payment
operations. - Prevent duplicate submissions through idempotency
controls. - Write accepted changes to Firestore using the Firebase Admin
SDK. - Record important financial actions in an audit trail. - Return
clear success, validation, conflict, and retryable error responses.

Deploy NestJS to a hosting service that provides a stable HTTPS
endpoint. The final provider and deployment configuration are to be
selected separately.

### 4.6 Firebase Cloud Firestore

Firestore is the cloud database for persistent application records.

The NestJS backend should use the Firebase Admin SDK for privileged
server-side access. If the Android app does not need direct Firestore
access, configure Firestore Security Rules to deny unintended direct
client reads and writes. Server-side authorization remains necessary
because Admin SDK operations have privileged access.

## 5. Suggested Firestore Collections

The following is a starting structure. The final schema should be
adjusted to the app's query patterns and business rules.

### `users`

-   `uid`
-   `displayName`
-   `role`
-   `createdAt`
-   `updatedAt`
-   `settings`

### `borrowers`

-   `id`
-   `ownerUid`
-   `fullName`
-   `contactNumber`
-   `address` (only if required)
-   `status`
-   `createdAt`
-   `updatedAt`
-   `version`

### `loans`

-   `id`
-   `ownerUid`
-   `borrowerId`
-   `principalAmount`
-   `interestType`
-   `interestRate`
-   `startDate`
-   `endDate` (if applicable)
-   `scheduleType`
-   `status`
-   `createdAt`
-   `updatedAt`
-   `version`

### `installments`

-   `id`
-   `ownerUid`
-   `loanId`
-   `dueDate`
-   `scheduledAmount`
-   `amountPaid`
-   `status`
-   `createdAt`
-   `updatedAt`
-   `version`

### `payments`

-   `id`
-   `ownerUid`
-   `borrowerId`
-   `loanId`
-   `installmentIds`
-   `amount`
-   `paymentDate`
-   `paymentMethod`
-   `reference`
-   `status`
-   `createdAt`
-   `serverRecordedAt`
-   `idempotencyKey`

### `auditLogs`

-   `id`
-   `ownerUid`
-   `actorUid`
-   `entityType`
-   `entityId`
-   `action`
-   `reason` (where applicable)
-   `createdAt`
-   `serverRecordedAt`

### Synchronization metadata

Depending on the chosen implementation, track metadata such as: - Local
operation ID - Entity ID - Operation type - Creation/update time -
Server version or revision - Attempt count and last error - Current sync
status

Avoid storing unnecessary personal information. Define access controls
and retention practices for borrower and payment data.

## 6. Offline-First Synchronization Workflow

### 6.1 Local write

When the lender creates or changes a record: 1. Validate the input
locally. 2. Generate a stable unique ID for the operation and record as
appropriate. 3. Save the record in Room. 4. Add a pending operation to
the sync queue in the same local transaction where practical. 5. Update
the UI from Room immediately. 6. Show the record as **Pending Sync**
until the server confirms it.

This approach lets the lender continue using the core workflows without
internet access.

### 6.2 Upload pending changes

When a suitable network connection is available: 1. WorkManager starts a
sync worker. 2. The worker loads pending operations in dependency-safe
order. 3. The app sends each operation to the NestJS API over HTTPS. 4.
Each request includes the stable operation/entity ID and a valid
Firebase ID token. 5. NestJS verifies identity and permissions,
validates the payload, and applies business rules. 6. NestJS writes the
accepted operation to Firestore. 7. The API returns a result containing
the operation ID and relevant server metadata. 8. Room marks the
operation as synchronized only after successful confirmation.

### 6.3 Download remote changes

If remote changes may occur outside the tablet, the app also needs a
download strategy. For example, NestJS can expose a change-feed endpoint
using a cursor or server revision. The app fetches changes since its
last successful sync, applies them to Room in a transaction, and
advances its cursor only after the local update succeeds.

Because the first rollout uses one tablet, this can be simpler
initially, but the design should still document how it will work if
additional devices are introduced.

### 6.4 Retry behavior

-   Retry temporary network errors and transient server failures with
    backoff.
-   Do not endlessly retry permanent validation or authorization
    failures.
-   Preserve failed operations and show a useful status to the user.
-   Prevent two workers from submitting the same operation
    simultaneously.
-   Keep logs useful for troubleshooting without exposing tokens or
    unnecessary borrower information.

## 7. Payment Integrity and Idempotency

Payment operations require stronger safeguards than ordinary profile
edits.

### Stable payment IDs

Create the payment ID on the tablet before the first upload. Reuse that
same ID on every retry. The backend should recognize an
already-processed payment ID and return the original outcome rather than
creating a second payment.

### Server-side validation

Before accepting a payment, NestJS should verify: - The authenticated
user has permission to access the loan. - The borrower and loan
references are valid. - The amount is positive and within the permitted
rules. - The payment has not already been processed. - The installment
allocation is valid. - The request's idempotency key has not been used
for a different operation.

### Financial history

-   Preserve posted payment records.
-   Prefer explicit correction or reversal records to silently deleting
    historical transactions.
-   Record important changes in `auditLogs`.
-   Keep payment effective date distinct from the server recording
    timestamp.
-   Use a consistent, tested calculation policy for principal, interest,
    rounding, partial payments, and remaining balances.

### Monetary representation

Do not use binary floating-point types for financial calculations. Use
integer minor currency units where appropriate, or a precise decimal
representation with documented rounding rules. Ensure serialization and
calculations remain consistent between Kotlin and TypeScript.

## 8. Conflict Handling

Even with one tablet, conflicts can occur after retries, app reinstalls,
data recovery, or future multi-device use.

Recommended principles: - Use stable IDs and version/revision
metadata. - Do not silently overwrite a financial record with a
conflicting version. - Treat immutable posted payments differently from
editable borrower contact details. - Allow safe, explicitly defined
merge rules for low-risk fields. - Flag conflicts affecting loan terms,
installment schedules, or posted payments for review. - Preserve enough
audit history to explain the final state.

The first implementation should explicitly document which operations are
safe to retry and which require user review.

## 9. Suggested REST API Groups

These are proposed endpoint groups, not implemented endpoints:

  --------------------------------------------------------------------------------
  Group                   Example routes                   Purpose
  ----------------------- -------------------------------- -----------------------
  Borrowers               `GET /borrowers`,                Manage borrower records
                          `POST /borrowers`,               
                          `PATCH /borrowers/:id`           

  Loans                   `GET /loans`, `POST /loans`,     Manage loans
                          `PATCH /loans/:id`               

  Installments            `GET /loans/:id/installments`    Retrieve a loan
                                                           schedule

  Payments                `POST /payments`,                Record and review
                          `GET /payments`,                 payments
                          `POST /payments/:id/reversals`   

  Sync                    `POST /sync/push`,               Upload pending
                          `GET /sync/changes?cursor=...`   operations and retrieve
                                                           changes

  Reports                 `GET /reports/summary`,          Retrieve report data
                          `GET /reports/collections`       
  --------------------------------------------------------------------------------

The exact endpoint design should be agreed upon before implementation.
Use DTO validation, consistent error formats, authentication guards, and
API documentation.

## 10. Error and Sync States

Suggested local operation states:

-   **Pending:** saved locally and waiting to upload.
-   **Syncing:** currently being submitted.
-   **Synced:** confirmed persisted by the backend.
-   **Needs attention:** rejected or blocked and requires user action.

A transient network failure should not erase a pending operation. A
validation error should not be treated as a successful sync. The UI
should explain whether a payment is locally recorded, server-confirmed,
or awaiting resolution.

## 11. Security Requirements

-   Use HTTPS for all communication with NestJS.
-   Verify Firebase ID tokens on protected requests.
-   Enforce authorization on every operation and resource.
-   Keep service-account credentials only on the backend.
-   Store secrets in a hosting provider's secret manager or secure
    environment configuration.
-   Restrict direct Firestore client access if all writes are intended
    to pass through NestJS.
-   Do not log passwords, access tokens, or unnecessary
    personal/financial data.
-   Back up cloud data and define a recovery procedure.
-   Test unauthorized access and cross-account data access.

## 12. Implementation Plan

### Phase 1 --- Foundation

-   Create the Android Compose project and NestJS project.
-   Configure Firebase Authentication and a Firestore development
    project.
-   Define the initial entities and API contract.
-   Set up secure environment configuration.

### Phase 2 --- Local workflows

-   Implement Room entities and DAOs.
-   Build repositories and ViewModels.
-   Implement borrower and loan management.
-   Implement payment recording and balance calculations.
-   Add unit tests for core financial rules.

### Phase 3 --- Cloud API

-   Implement Firebase token verification in NestJS.
-   Add authorization guards and DTO validation.
-   Configure Firebase Admin SDK.
-   Implement Firestore persistence and audit logging.
-   Add idempotent payment handling.

### Phase 4 --- Synchronization

-   Add a local outbox/pending-operation table.
-   Implement WorkManager with network constraints and backoff.
-   Add upload confirmation and local sync-state updates.
-   Add remote change retrieval if required.
-   Test airplane mode, interrupted requests, app restarts, and repeated
    retries.

### Phase 5 --- Verification and deployment

-   Test consistent totals across dashboard, ledger, calendar, and
    reports.
-   Test duplicate payment prevention.
-   Test authentication and authorization failures.
-   Test offline-to-online recovery.
-   Deploy NestJS to the selected hosting provider.
-   Configure production secrets, Firestore access controls, backups,
    and monitoring.
-   Document setup, deployment, and recovery procedures.

## 13. Decisions to Finalize Before Production

Resolve these questions before finalizing business logic: 1. What
interest models are supported (flat, reducing balance, or another rule)?
2. Which installment schedules and partial-payment allocation rules are
allowed? 3. Can posted payments be edited, or must corrections use
reversals? 4. Will the app remain single-tablet, or is multi-device
access planned? 5. Can any data be edited from a separate admin
interface? 6. What authentication and account-recovery flow is required?
7. What data-retention and backup policy will apply?

Do not invent answers to these financial rules. Record each decision and
implement it consistently across the Android app and backend.

## 14. Final Architecture Summary

LendTrack uses a **native Kotlin/Compose tablet app**, **Room/SQLite for
local-first operation**, **WorkManager for retryable synchronization**,
**Firebase Authentication for identity**, **a hosted NestJS REST API for
trusted validation and cloud writes**, and **Firebase Cloud Firestore
for cloud persistence**.

The key architectural boundary is that Room powers the offline user
experience, while NestJS verifies requests and controls cloud updates.
Stable IDs, idempotency, explicit sync states, and an audit trail
protect payment integrity during intermittent connectivity.
