# StokVault user interface: summary for SDD section 6.1

This summary describes the user interface as built. Use it to update SDD section 6.1.

## Design principle

Every screen answers **"what do I need to do right now?"** within about 3 seconds. Screens are organised around the
**tasks** of each type of user, not around database tables. The view of a stokvel depends on the person's role **in
that stokvel**, because roles are per group: the same person can be treasurer of one stokvel and a plain member of
another.

Rules applied everywhere:

- **One main button** per card or section. Secondary actions are plain links.
- **Plain language.**
  - "Contribution" became **payment**, and "Cycle 4" became **"September"** (the month the round is due).
  - "Awaiting verification" became **"Treasurer is checking it"**, and "Verified" became **"Paid ✓"**.
  - "Rejected" became **"Not accepted"**, and "Eligibility check" became **"Checks passed / failed"**.
  - "Arrears" became **"Owes"**, and "Position 3" became **"3rd in line"**.
- **Empty states say what to do next**, e.g. "No payment due right now ✓" or "Nothing to check ✓".
- **Works on a phone.** On small screens the menu becomes a compact top bar.
- **Hide amounts** is still available on every screen for projecting at meetings.

## Screens by user

### Home: "My stokvels" (all users)

- **One card per stokvel, most urgent first.** The order is overdue, then due soon, then being checked, then all
  good.
- **Each card has one status line.** Examples:
  - "R 500 due Fri 3 Oct"
  - "Overdue by 3 days" (amber)
  - "Treasurer is checking your payment"
  - "All paid ✓ · Next payment due 1 Nov"
- **One button** on the card: **Pay now** when something is owed.
- **The payout turn** for rotational stokvels, e.g. "5th in line · around Jan 2027". "You're next to receive the
  payout!" is highlighted as good news.
- **Treasurers and committee members** also get a **"Needs your attention"** box at the top. It covers all their
  stokvels, e.g. "4 payments to check", "2 members haven't paid", "1 payout ready for you", "Approve R 7 500 to …".
  Each item links straight to where it is handled.

### One stokvel: member view

A single page with no tabs:

1. **Status block** for this round's payment: the amount to pay, the due date, and **Pay now**. Or "Your treasurer
   is checking your payment", or "Paid ✓". If a payment was not accepted, the reason is shown here.
2. **Your turn**: the place in the payout line and roughly when (rotational), or one sentence on how burial, grocery
   and investment stokvels pay out.
3. **Your payments**: the member's own history (round, date, method, amount, plain status).
4. **More about this group** (folded away): group totals, payout order, who has paid this round, members, and the
   personal statement (PDF).

**Pay flow.** The "Tell us you paid" window opens with the amount due and today's date already filled in. They are
shown as "R 500 · paid today" with a *Change* link. The member only types the **reference on the bank slip** and
picks **how they paid** (Cash / Bank transfer / Debit order). A note explains what happens next: *"Your treasurer
will check this against the bank statement. You will get an SMS once it is confirmed."*

### One stokvel: treasurer view ("This month" board)

- **Round progress:** e.g. "R 2 000 of R 2 500 collected · 4 of 5 members paid". If no round is open, it says so,
  with one button: "Open the October round".
- **To check:** payments members reported, with **Mark as paid ✓** and **Not accepted…** (which asks for a reason
  the member will see) on each row. Payments flagged automatically show "Needs a second look" and the reason.
- **Not paid yet:** each person shows what they owe, with a **Record payment** button.
- **Paid ✓:** folded away.
- **Payout card:** who is next, and the **one next step** for the payout's current stage:
  - plan the payout
  - checks running
  - checks failed (with the reason)
  - confirm the payout
  - waiting for the committee
  - mark as paid out
- **Manage group:** one entry point for everything not needed every month, in five sections:
  - **Rounds & payments:** open or close a round, mark it as balanced, the payment grid, and every payment.
  - **Payouts:** the payout order, planning a payout, and every payout with all its actions.
  - **Members:** roles, place in the payout line, adding, registering and removing people.
  - **Records & reports:** the tamper check, statements and history downloads (PDF / spreadsheet), and the
    history of every change.
  - **Settings:** the stokvel's rules, and pausing or closing it.

### Committee member

- They get the member view, plus a **"Needs your decision"** box on the stokvel page. It lists:
  - big payouts, with **Approve** and **Decline…** (a reason is required);
  - payouts whose checks failed: "Checks failed: [reason]. You can allow it anyway if the committee agrees", with
    **Allow it anyway…** (a reason is required and kept in the records).
- The treasurer's own payment appears under **To check**, because a treasurer can't check their own payment.
- The **Approvals** page is a simple to-do list across all their stokvels.
- They can open **Manage group**. The server still limits what they may change.

### Coop Office (admin)

The sections are in order of how often they are used. Each has a one-line explanation.

1. Summary tiles:
   - stokvels
   - people in stokvels
   - money held
   - owed by members
   - payments to check
   - payouts to approve
2. **Register a stokvel.** The less common rules are folded under "More rules".
3. **Register a person.**
4. **Stokvels:** every stokvel with its **Records** status, "Safe ✓" or "Changed!". The records are checked for
   tampering every time the page opens.
5. **People:** everyone registered, "Active" or "Invited".
6. **Messages sent:** the notification log, e.g. "Sent ✓", "Trying again" or "Couldn't send".
7. **Run the nightly jobs now:** re-check all payouts, or send payment reminders.

## What did not change

- The business rules, services, database and REST API are unchanged. All 49 unit tests and all 48 end-to-end API
  checks still pass.
- Every action is still available to the same roles; some have only moved into **Manage group**.
- The audit trail, reports, four-eyes approvals and tamper detection all work and can be demonstrated as before.
