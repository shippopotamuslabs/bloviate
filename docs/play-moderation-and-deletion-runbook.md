# Play moderation and account-deletion runbook

Operator notes for BLO-148 (output reporting), BLO-149 (custom-style
guardrails), and BLO-178 (sign-out and account deletion). Written for the
person running Bloviate — currently Sean, in the Firebase console.

## Output reports (BLO-148)

Reports exist only when a user taps **Report this output**, picks a
reason, and submits. Nothing is uploaded by generating output, opening
the picker, or viewing the Setup tab.

### Where reports live

Firebase Console → Firestore Database → **`reports`** collection.
Each document id is `sha256(reporterUid + ":" + clientReportId)`, so a
double-tap or a retried submission is one document, never two.

Fields: `reporterUid`, `styledOutput`, `sourceTranscript`, `reason`
(`offensive` | `sexual` | `hateful` | `wrong` | `other`), `styleId`,
`provider`, `model` (either can be null when provenance was
unavailable), `appVersion`, `clientReportId`, `status` (`new`), and
`createdAt` (server timestamp).

Only the most recent styled dictation of the current app session can be
reported: the client keeps it in memory only, never on disk. Older
dictations cannot be reported and no provenance is ever reconstructed
for them.

### Review loop

1. Filter `reports` by `status == "new"`.
2. Read the report. Judge it against the BLO-149 restricted set only:
   sexual content involving minors, non-consensual sexual content or
   sexual content about real people, hate/dehumanization personas
   targeting a protected class, explicit sexual generation. A report
   that output was profane, rude, or edgy is **not** actionable — that
   content is allowed by design (Sean's ruling, 2026-10-23).
3. Act or dismiss. There is no in-app moderation UI and no automatic
   action: submitting a report never changes the reporter's account.
4. Set `status` to `reviewed` (or delete the document) when handled.

### Revoking a user's cloud access (manual)

There is no dedicated ban mechanism in code. The levers are the
entitlement documents themselves:

1. `users/{uid}/entitlement/play` — set `active: false`.
2. `users/{uid}/entitlement/override` — delete the document if present.

Nothing in this codebase rewrites those documents afterward, so the
change sticks until an operator reverses it. Any Play-sync writer
outside this repo is outside this guarantee — check before relying on
it. Revocation is an operator decision; a report alone never triggers
it.

## Account deletion (BLO-178)

In-app path (shipped labels): **Setup tab → Account card → "Delete
account"** row ("Deletes your account and its server records for
good") → dialog **"Delete your account?"** (buttons: Cancel /
Continue) → dialog **"Delete account for good?"** (buttons: Cancel /
Delete account). Tapping **Delete account** first re-authenticates with
Google, then calls the callable. The step-1 body copy reads: "This
deletes your account record, including your name and email address,
your subscription entitlement records, your fair-use usage counters,
your custom styles stored with your account, and any other server-side
records tied to your account. Deleting is permanent. Deleting your
account does not cancel your Google Play subscription. Cancel in
Google Play too, or billing continues. Dictations and data that live
only on this phone stay on this phone until you uninstall Bloviate."
Success lands back on the Setup tab signed out, with the note "Your
account was deleted."

### What the `deleteAccount` callable does, in order

1. Writes a tombstone at `deletedAccounts/{uid}`.
2. Recursive-deletes the whole `users/{uid}` subtree.
3. Deletes every `reports` document where `reporterUid == uid`.
4. Revokes the user's refresh tokens and deletes the Firebase Auth
   user — last, so a partial failure never removes the only way to
   retry.

Every step is idempotent; a failure returns
`unavailable` / "Account deletion did not finish. Try again." and the
user can retry while signed in. The callable requires a Google sign-in
whose token `auth_time` is within 10 minutes; the app re-authenticates
before calling. The target uid comes from the verified token only —
the request payload must be empty.

### Account-data inventory and deletion coverage

| Data | Location | Deleted |
|---|---|---|
| Account identity (name, email, provider) | Firebase Auth user | Yes — Auth user deleted |
| Subscription entitlement (Play) | `users/{uid}/entitlement/play` | Yes — subtree delete |
| Complimentary entitlement override | `users/{uid}/entitlement/override` | Yes — subtree delete |
| Custom styles | `users/{uid}/customStyles/*` | Yes — subtree delete |
| Custom-style generation limits | `users/{uid}/customGenerationLimits/*` | Yes — subtree delete |
| Fair-use usage counters | `users/{uid}/usage/*` | Yes — subtree delete |
| Submitted output reports | `reports` where `reporterUid == uid` | Yes — step 3 |
| Deletion tombstone | `deletedAccounts/{uid}` | **Kept on purpose** — uid hash only, no content; blocks entitlement resurrection |
| Dictations, on-device settings, local style cache | The user's phone only | Not server data; removed on uninstall. Deletion also wipes the local custom-style cache and account-tied selections on the device that deletes |

What deletion does **not** do: it does not cancel a Google Play
subscription. The user must cancel in Google Play; the in-app copy and
the published deletion page both say so. Google keeps its own purchase
records.

A person who signs up again with the same Google account receives a
new Firebase uid; the tombstone binds to the old uid and does not block
the new account.

### Trade-off on record

Reports the user submitted are deleted with the account, matching the
published page's promise ("any server-side records tied to your
account"). The cost: an account deleted to dodge moderation takes its
report evidence with it. Accepted for launch; revisit if abuse appears.

## Custom-style guardrails (BLO-149)

Three layers, narrow restricted set (see above). Everything else —
profanity, crude and dark humor, roasting, edgy personas — is allowed
at any intensity.

1. **Save-time screening** (`customStyleScreening.ts`): deterministic
   lexicon/rules over name, description, and instructions at
   `saveCustomStyle`; over the description before quota reservation and
   over the generated draft at `generateCustomStyle`. Rejections return
   a plain reason and log the outcome code only.
2. **Prompt clause**: `RESTRICTED_CONTENT` is appended last to every
   style system prompt, built-in and custom identically. Custom
   instructions are untrusted input and never override it.
3. **Runtime guard** (`styleOutputGuard`): output joining a minor term
   with a sexual term is refused for every style; for custom styles,
   explicit terms or slurs the speaker did not dictate are also
   refused. A refused link advances the provider chain; if every link
   fails, the callable errors and the client keeps the user's own
   transcript — the safe replacement.

Legacy custom styles saved before screening existed are screened on
the `styleText` read path and refused with the same message, so the
user can edit or delete them.

Logs never contain persona text, transcripts, or generated content —
only uid hashes, outcome codes, and screening codes. The user-initiated
report flow is the only content-upload path.

## Firestore rules note

This repo has no `firestore.rules` file; rules are managed in the
Firebase console. The deployed rules are **deny-all for clients**
(verified in the 2026-10-24 console security audit): every read and
write goes through callables / the Admin SDK. That posture already
covers the new collections — `reports` and `deletedAccounts` are
callable-only by construction, and no rules change is needed for this
work. If the rules are ever relaxed per-collection, keep both of these
at no client access.
