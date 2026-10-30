# Shared backend contract

The active Android client and website use one backend. **Do not bootstrap or
upgrade it from this directory's historical migrations alone.** In particular,
`20261024155504_mobile_workable_system.sql` is an early start/loaded-only wrapper,
not the current driver dispatcher. Do not edit/replay an applied historical file.

Canonical shared changes live in
[mabutol-trucking-react](https://github.com/NavhiAngeles/mabutol-trucking-react/tree/5ec367ab18c66ff286906c67d94ef3ba6befc350/supabase).
This is the verified source revision for this client. Apply that repository's
versioned migrations in timestamp order on the matching application schema.
The migrations are incremental: an empty Supabase project first needs the base
application schema (historical schema-only reference: this repository's
`migrations/live_schema_dump.sql`), platform Auth/Storage, and reviewed base
configuration. A dump is not proof of a tested empty-project restoration.
Never apply both copies of the old mobile baseline: the shared
`20261024192436_shared_mobile_dev_baseline.sql` already contains it.

Required shared revisions:

- `20261024192830_shared_delivery_workflow.sql`: authoritative
  `driver_milestone`, start/arrival/showed/loaded/no_show, own-delivery checks,
  idempotency, operating hours, geofence and office-owned cancellation.
- The following quote/event/review/proof/allowance/status-guard corrections in
  the same migration sequence, through `20261024200650`.
- `20261027105338` and `20261027143548`: fuel/history contracts.
- `20261028120249`: incident/expense evidence and private file policies.
- `20261028124801`: confirmed truck routes/ETA.
- `20261028135707` and `20261028140313`: conversations and assignment guards.
- Later signup/contact-alias migrations remain in canonical timestamp order.

`mobile_driver_action(p_delivery,p_action)` must delegate to
`driver_milestone(p_delivery,p_action)`. Drivers request no-show review; they do
not cancel trips. Do not replace the canonical dispatcher with the bundled
start/loaded-only wrapper or bypass authorization to make a test pass.

Before using any restored or upgraded backend:

1. Run `tests/active_driver_contract.sql` in its SQL editor or psql. It is a
   read-only catalog gate: missing signatures, argument names, policies or an
   obsolete dispatcher fail explicitly.
2. Run canonical `supabase/tests/shared_delivery_workflow.sql` against the
   intended **development** database. It creates fictional fixtures inside a
   rollback transaction and tests ordering, geofence, replay, no-show timing,
   authorization and office review. Run the evidence/operational/conversation
   suites when validating those features. Never substitute a build for these
   checks, or run fixture tests against a production project.
3. Check native assigned-trip reads and action results with a test account.
   A catalog gate proves setup prerequisites, not physical GPS/file acceptance.

The 2026-10-30 Dev check confirms the installed shared dispatcher already matches
this source. No remote migration is needed to fix S01 on Dev; the defect was
treating these archival Android SQL files as the complete backend setup.
