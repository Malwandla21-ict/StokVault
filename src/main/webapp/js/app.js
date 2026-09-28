/*
 * StokVault dashboard. Plain JavaScript, no framework or build step.
 * Talks to the REST API under ./api and re-renders from its responses.
 */
"use strict";

// The API lives next to this page: /stokvault/ -> /stokvault/api
const API = (() => {
    let path = location.pathname.replace(/index\.html$/, "");
    if (!path.endsWith("/")) path += "/";
    return path + "api";
})();

const state = {
    stokvels: [],
    selectedId: null,
    view: "stokvels",
};

// ---- Helpers -------------------------------------------------------------------------

const $ = (selector) => document.querySelector(selector);

const rand = new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" });
const money = (value) => rand.format(Number(value ?? 0));

const today = () => new Date().toLocaleDateString("en-CA"); // yyyy-mm-dd in local time

const label = (enumValue) =>
    String(enumValue ?? "").toLowerCase().replace(/_/g, " ").replace(/^\w/, (c) => c.toUpperCase());

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (c) => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
    })[c]);
}

/** Calls the API. Throws an Error carrying the server's message (and field details) on failure. */
async function api(method, path, body) {
    const options = { method, headers: { Accept: "application/json" } };
    if (body !== undefined) {
        options.headers["Content-Type"] = "application/json";
        options.body = JSON.stringify(body);
    }
    const response = await fetch(API + path, options);
    if (response.status === 204) return null;
    const data = await response.json().catch(() => null);
    if (!response.ok) {
        const message = data?.message ?? `${response.status} ${response.statusText}`;
        const details = data?.details?.length ? "\n" + data.details.join("\n") : "";
        throw new Error(message + details);
    }
    return data;
}

function toast(message, isError = false) {
    const el = document.createElement("div");
    el.className = "toast" + (isError ? " error" : "");
    el.textContent = message;
    $("#toasts").append(el);
    setTimeout(() => el.remove(), isError ? 7000 : 3500);
}

/** Runs an action, reporting success or the API's error message. */
async function run(action, successMessage) {
    try {
        await action();
        if (successMessage) toast(successMessage);
    } catch (error) {
        toast(error.message, true);
    }
}

// ---- Form dialog ---------------------------------------------------------------------

/**
 * Opens the shared dialog with the given fields. onSubmit receives the values; if it throws,
 * the error is shown in the dialog so the user can correct the form.
 * Field: { name, label, type: text|email|number|date|select|textarea, value, required, options, hint, step }
 */
function openForm({ title, fields, submitLabel = "Save", onSubmit }) {
    const dialog = $("#form-dialog");
    const form = $("#dialog-form");
    const error = $("#dialog-error");
    $("#dialog-title").textContent = title;
    $("#dialog-submit").textContent = submitLabel;
    error.hidden = true;

    $("#dialog-fields").innerHTML = fields.map((f) => {
        const id = "f-" + f.name;
        const required = f.required ? "required" : "";
        const value = escapeHtml(f.value ?? "");
        let input;
        if (f.type === "select") {
            input = `<select id="${id}" name="${f.name}" ${required}>` +
                f.options.map((o) => {
                    const opt = typeof o === "object" ? o : { value: o, text: label(o) };
                    const selected = String(opt.value) === String(f.value ?? "") ? "selected" : "";
                    return `<option value="${escapeHtml(opt.value)}" ${selected}>${escapeHtml(opt.text)}</option>`;
                }).join("") + "</select>";
        } else if (f.type === "textarea") {
            input = `<textarea id="${id}" name="${f.name}" rows="3" ${required}>${value}</textarea>`;
        } else {
            const step = f.step ? `step="${f.step}"` : "";
            input = `<input id="${id}" name="${f.name}" type="${f.type ?? "text"}" value="${value}" ${step} ${required}>`;
        }
        const hint = f.hint ? `<div class="hint">${escapeHtml(f.hint)}</div>` : "";
        return `<div class="field"><label for="${id}">${escapeHtml(f.label)}</label>${input}${hint}</div>`;
    }).join("");

    form.onsubmit = async (event) => {
        event.preventDefault();
        const values = {};
        for (const f of fields) {
            const raw = form.elements[f.name].value.trim();
            if (raw === "") values[f.name] = null;
            else if (f.type === "number") values[f.name] = Number(raw);
            else values[f.name] = raw;
        }
        const submit = $("#dialog-submit");
        submit.disabled = true;
        try {
            await onSubmit(values);
            dialog.close();
        } catch (e) {
            error.textContent = e.message;
            error.hidden = false;
        } finally {
            submit.disabled = false;
        }
    };
    $("#dialog-cancel").onclick = () => dialog.close();
    dialog.showModal();
    form.querySelector("input, select, textarea")?.focus();
}

// ---- Health --------------------------------------------------------------------------

async function checkHealth() {
    const el = $("#health");
    try {
        const h = await api("GET", "/health");
        el.textContent = "API online, database " + h.database;
        el.className = "health up";
    } catch {
        el.textContent = "API or database unavailable";
        el.className = "health down";
    }
}

// ---- Stokvels ------------------------------------------------------------------------

async function loadStokvels() {
    state.stokvels = await api("GET", "/stokvels");
    if (state.selectedId && !state.stokvels.some((s) => s.id === state.selectedId)) {
        state.selectedId = null;
    }
    if (!state.selectedId && state.stokvels.length) state.selectedId = state.stokvels[0].id;
    renderStokvelList();
    await renderStokvel();
}

function renderStokvelList() {
    const list = $("#stokvel-list");
    if (!state.stokvels.length) {
        list.innerHTML = `<li class="empty">No stokvels yet.</li>`;
        return;
    }
    list.innerHTML = state.stokvels.map((s) => `
        <li><button type="button" data-id="${s.id}" aria-current="${s.id === state.selectedId}">
            <span class="name">${escapeHtml(s.name)}</span>
            <span class="meta">${label(s.type)} · ${money(s.contributionAmount)} ${label(s.frequency).toLowerCase()}
                ${s.status === "CLOSED" ? ' · <span class="badge CLOSED">Closed</span>' : ""}</span>
        </button></li>`).join("");
    list.querySelectorAll("button").forEach((b) => b.addEventListener("click", async () => {
        state.selectedId = Number(b.dataset.id);
        renderStokvelList();
        await run(renderStokvel);
    }));
}

async function renderStokvel() {
    const container = $("#stokvel-detail");
    const id = state.selectedId;
    if (!id) {
        container.innerHTML = `<p class="empty">Select a stokvel, or create one.</p>`;
        return;
    }
    const stokvel = state.stokvels.find((s) => s.id === id);
    const [summary, contributions, payouts, next] = await Promise.all([
        api("GET", `/stokvels/${id}/summary`),
        api("GET", `/stokvels/${id}/contributions`),
        api("GET", `/stokvels/${id}/payouts`),
        stokvel.type === "ROTATING" && stokvel.status === "ACTIVE"
            ? api("GET", `/stokvels/${id}/payouts/next`).catch(() => null)
            : Promise.resolve(null),
    ]);
    if (state.selectedId !== id) return; // the user clicked another stokvel meanwhile
    const active = stokvel.status === "ACTIVE";

    container.innerHTML = `
        <div class="detail-head">
            <div>
                <h1>${escapeHtml(stokvel.name)} <span class="badge ${stokvel.status}">${label(stokvel.status)}</span></h1>
                <p>${label(stokvel.type)} stokvel · ${money(stokvel.contributionAmount)} ${label(stokvel.frequency).toLowerCase()}
                   since ${stokvel.startDate}${stokvel.closedOn ? ` · closed ${stokvel.closedOn}` : ""}</p>
                ${stokvel.description ? `<p>${escapeHtml(stokvel.description)}</p>` : ""}
            </div>
            <div class="row">
                <button type="button" class="btn" id="edit-stokvel">Edit</button>
                ${active
                    ? `<button type="button" class="btn" id="close-stokvel">Close</button>`
                    : `<button type="button" class="btn" id="reopen-stokvel">Reopen</button>`}
                <button type="button" class="btn danger" id="delete-stokvel">Delete</button>
            </div>
        </div>

        <div class="cards">
            <div class="card"><div class="label">Balance</div><div class="value">${money(summary.balance)}</div></div>
            <div class="card"><div class="label">Total contributed</div><div class="value">${money(summary.totalContributions)}</div></div>
            <div class="card"><div class="label">Paid out</div><div class="value">${money(summary.totalPaidOut)}</div></div>
            <div class="card"><div class="label">Scheduled payouts</div><div class="value">${money(summary.totalScheduled)}</div></div>
            <div class="card ${Number(summary.totalArrears) > 0 ? "warn" : ""}"><div class="label">Arrears</div><div class="value">${money(summary.totalArrears)}</div></div>
            <div class="card"><div class="label">Active members</div><div class="value">${summary.activeMembers}</div></div>
        </div>

        ${next ? `
        <div class="callout">
            <div><strong>Next in rotation:</strong> ${escapeHtml(next.memberName)} (position ${next.payoutPosition})
                · suggested payout ${money(next.suggestedAmount)}</div>
            <button type="button" class="btn primary small" id="schedule-next">Schedule this payout</button>
        </div>` : ""}

        <div class="section">
            <div class="section-head">
                <h2>Members</h2>
                ${active ? `<button type="button" class="btn small primary" id="add-membership">Add member</button>` : ""}
            </div>
            <div class="section-body">${membersTable(summary.members, active)}</div>
        </div>

        <div class="section">
            <div class="section-head">
                <h2>Contributions</h2>
                ${active ? `<button type="button" class="btn small primary" id="add-contribution">Record contribution</button>` : ""}
            </div>
            <div class="section-body">${contributionsTable(contributions)}</div>
        </div>

        <div class="section">
            <div class="section-head">
                <h2>Payouts</h2>
                ${active ? `<button type="button" class="btn small primary" id="add-payout">Schedule payout</button>` : ""}
            </div>
            <div class="section-body">${payoutsTable(payouts)}</div>
        </div>`;

    bindStokvelActions(stokvel, summary, next);
}

function membersTable(members, active) {
    if (!members.length) return `<p class="empty" style="padding:16px">No members yet.</p>`;
    return `<table>
        <thead><tr><th class="num">Pos</th><th>Name</th><th>Role</th><th class="num">Paid in</th>
            <th class="num">Expected</th><th class="num">Arrears</th><th class="num">Received</th><th></th></tr></thead>
        <tbody>${members.map((m) => `
            <tr class="${m.active ? "" : "inactive"}">
                <td class="num">${m.active ? m.payoutPosition : "-"}</td>
                <td>${escapeHtml(m.name)}${m.active ? "" : " (left)"}</td>
                <td>${label(m.role)}</td>
                <td class="num">${money(m.totalContributed)}</td>
                <td class="num">${money(m.expectedToDate)}</td>
                <td class="num ${Number(m.arrears) > 0 ? "arrears" : ""}">${money(m.arrears)}</td>
                <td class="num">${money(m.totalReceived)}</td>
                <td class="actions">${m.active && active ? `
                    <button type="button" class="btn small" data-edit-membership="${m.memberId}">Edit</button>
                    <button type="button" class="btn small danger" data-leave="${m.memberId}">Remove</button>` : ""}</td>
            </tr>`).join("")}
        </tbody></table>`;
}

function contributionsTable(contributions) {
    if (!contributions.length) return `<p class="empty" style="padding:16px">No contributions yet.</p>`;
    return `<table>
        <thead><tr><th>Date</th><th>Member</th><th class="num">Amount</th><th>Method</th><th>Reference</th><th></th></tr></thead>
        <tbody>${contributions.map((c) => `
            <tr>
                <td>${c.contributionDate}</td>
                <td>${escapeHtml(c.memberName)}</td>
                <td class="num">${money(c.amount)}</td>
                <td>${label(c.paymentMethod)}</td>
                <td>${escapeHtml(c.reference ?? "")}</td>
                <td class="actions"><button type="button" class="btn small danger" data-delete-contribution="${c.id}">Delete</button></td>
            </tr>`).join("")}
        </tbody></table>`;
}

function payoutsTable(payouts) {
    if (!payouts.length) return `<p class="empty" style="padding:16px">No payouts yet.</p>`;
    return `<table>
        <thead><tr><th>Date</th><th>Recipient</th><th class="num">Amount</th><th>Status</th><th>Paid on</th><th>Notes</th><th></th></tr></thead>
        <tbody>${payouts.map((p) => `
            <tr>
                <td>${p.payoutDate}</td>
                <td>${escapeHtml(p.memberName)}</td>
                <td class="num">${money(p.amount)}</td>
                <td><span class="badge ${p.status}">${label(p.status)}</span></td>
                <td>${p.paidOn ?? ""}</td>
                <td class="wrap">${escapeHtml(p.notes ?? "")}</td>
                <td class="actions">${p.status === "SCHEDULED" ? `
                    <button type="button" class="btn small primary" data-pay="${p.id}">Mark paid</button>
                    <button type="button" class="btn small" data-cancel="${p.id}">Cancel</button>` : ""}</td>
            </tr>`).join("")}
        </tbody></table>`;
}

function bindStokvelActions(stokvel, summary, next) {
    const id = stokvel.id;
    const refresh = () => loadStokvels();
    const on = (selector, handler) => $(selector)?.addEventListener("click", handler);
    const activeMembers = summary.members.filter((m) => m.active)
        .map((m) => ({ value: m.memberId, text: `${m.name} (position ${m.payoutPosition})` }));

    on("#edit-stokvel", () => openStokvelForm(stokvel));

    on("#close-stokvel", () => {
        if (!confirm(`Close ${stokvel.name}? No new members, contributions or payouts will be accepted.`)) return;
        run(async () => { await api("POST", `/stokvels/${id}/close`); await refresh(); }, "Stokvel closed");
    });
    on("#reopen-stokvel", () =>
        run(async () => { await api("POST", `/stokvels/${id}/reopen`); await refresh(); }, "Stokvel reopened"));
    on("#delete-stokvel", () => {
        if (!confirm(`Delete ${stokvel.name}? This only works if it has no contributions or payouts.`)) return;
        run(async () => { await api("DELETE", `/stokvels/${id}`); state.selectedId = null; await refresh(); }, "Stokvel deleted");
    });

    on("#add-membership", async () => {
        const all = await api("GET", "/members");
        const current = new Set(summary.members.filter((m) => m.active).map((m) => m.memberId));
        const candidates = all.filter((m) => !current.has(m.id));
        if (!candidates.length) {
            toast("Everyone is already a member. Add people on the Members tab first.", true);
            return;
        }
        openForm({
            title: `Add a member to ${stokvel.name}`,
            submitLabel: "Add",
            fields: [
                { name: "memberId", label: "Member", type: "select", required: true,
                  options: candidates.map((m) => ({ value: m.id, text: `${m.name} (${m.email})` })) },
                { name: "role", label: "Role", type: "select", value: "MEMBER",
                  options: ["MEMBER", "CHAIRPERSON", "TREASURER", "SECRETARY"] },
                { name: "joinedOn", label: "Joined on", type: "date", value: today(),
                  hint: "Contributions are expected from this date (or the stokvel's start date, if later)." },
            ],
            onSubmit: async (v) => {
                await api("POST", `/stokvels/${id}/members`, { ...v, memberId: Number(v.memberId) });
                toast("Member added");
                await refresh();
            },
        });
    });

    document.querySelectorAll("[data-edit-membership]").forEach((b) => b.addEventListener("click", () => {
        const m = summary.members.find((x) => x.memberId === Number(b.dataset.editMembership));
        openForm({
            title: `Edit ${m.name}`,
            fields: [
                { name: "role", label: "Role", type: "select", value: m.role,
                  options: ["MEMBER", "CHAIRPERSON", "TREASURER", "SECRETARY"] },
                { name: "payoutPosition", label: "Payout position", type: "number", value: m.payoutPosition, step: "1",
                  hint: "Choosing a position someone else holds swaps the two members." },
            ],
            onSubmit: async (v) => {
                await api("PUT", `/stokvels/${id}/members/${m.memberId}`, v);
                toast("Membership updated");
                await refresh();
            },
        });
    }));

    document.querySelectorAll("[data-leave]").forEach((b) => b.addEventListener("click", () => {
        const m = summary.members.find((x) => x.memberId === Number(b.dataset.leave));
        if (!confirm(`Remove ${m.name} from ${stokvel.name}? Their contribution history is kept.`)) return;
        run(async () => { await api("DELETE", `/stokvels/${id}/members/${m.memberId}`); await refresh(); }, `${m.name} removed`);
    }));

    on("#add-contribution", () => openForm({
        title: `Record a contribution to ${stokvel.name}`,
        submitLabel: "Record",
        fields: [
            { name: "memberId", label: "Member", type: "select", required: true, options: activeMembers },
            { name: "amount", label: "Amount (R)", type: "number", step: "0.01", required: true, value: stokvel.contributionAmount },
            { name: "contributionDate", label: "Date paid", type: "date", value: today() },
            { name: "paymentMethod", label: "Payment method", type: "select", value: "EFT", options: ["EFT", "CASH", "DEBIT_ORDER"] },
            { name: "reference", label: "Reference (optional)", type: "text" },
        ],
        onSubmit: async (v) => {
            await api("POST", `/stokvels/${id}/contributions`, { ...v, memberId: Number(v.memberId) });
            toast("Contribution recorded");
            await refresh();
        },
    }));

    document.querySelectorAll("[data-delete-contribution]").forEach((b) => b.addEventListener("click", () => {
        if (!confirm("Delete this contribution? Only do this to correct a capture mistake.")) return;
        run(async () => { await api("DELETE", `/stokvels/${id}/contributions/${b.dataset.deleteContribution}`); await refresh(); },
            "Contribution deleted");
    }));

    const openPayoutForm = (memberId, amount) => openForm({
        title: `Schedule a payout from ${stokvel.name}`,
        submitLabel: "Schedule",
        fields: [
            { name: "memberId", label: "Recipient", type: "select", required: true, options: activeMembers, value: memberId },
            { name: "amount", label: "Amount (R)", type: "number", step: "0.01", required: true, value: amount },
            { name: "payoutDate", label: "Payout date", type: "date", value: today() },
            { name: "notes", label: "Notes (optional)", type: "textarea" },
        ],
        onSubmit: async (v) => {
            await api("POST", `/stokvels/${id}/payouts`, { ...v, memberId: Number(v.memberId) });
            toast("Payout scheduled");
            await refresh();
        },
    });
    on("#add-payout", () => openPayoutForm(null, null));
    on("#schedule-next", () => openPayoutForm(next.memberId, next.suggestedAmount));

    document.querySelectorAll("[data-pay]").forEach((b) => b.addEventListener("click", () =>
        run(async () => { await api("POST", `/stokvels/${id}/payouts/${b.dataset.pay}/pay`); await refresh(); }, "Payout marked as paid")));
    document.querySelectorAll("[data-cancel]").forEach((b) => b.addEventListener("click", () => {
        if (!confirm("Cancel this payout?")) return;
        run(async () => { await api("POST", `/stokvels/${id}/payouts/${b.dataset.cancel}/cancel`); await refresh(); }, "Payout cancelled");
    }));
}

function openStokvelForm(stokvel) {
    const editing = Boolean(stokvel);
    openForm({
        title: editing ? `Edit ${stokvel.name}` : "New stokvel",
        submitLabel: editing ? "Save" : "Create",
        fields: [
            { name: "name", label: "Name", required: true, value: stokvel?.name },
            { name: "type", label: "Type", type: "select", value: stokvel?.type ?? "ROTATING",
              options: ["ROTATING", "SAVINGS", "GROCERY", "BURIAL", "INVESTMENT"] },
            { name: "contributionAmount", label: "Contribution per period (R)", type: "number", step: "0.01",
              required: true, value: stokvel?.contributionAmount },
            { name: "frequency", label: "Frequency", type: "select", value: stokvel?.frequency ?? "MONTHLY",
              options: ["WEEKLY", "FORTNIGHTLY", "MONTHLY"] },
            { name: "startDate", label: "Start date", type: "date", required: true, value: stokvel?.startDate ?? today() },
            { name: "description", label: "Description (optional)", type: "textarea", value: stokvel?.description },
        ],
        onSubmit: async (v) => {
            const saved = editing
                ? await api("PUT", `/stokvels/${stokvel.id}`, v)
                : await api("POST", "/stokvels", v);
            state.selectedId = saved.id;
            toast(editing ? "Stokvel updated" : "Stokvel created");
            await loadStokvels();
        },
    });
}

// ---- Members -------------------------------------------------------------------------

async function loadMembers() {
    const search = $("#member-search").value.trim();
    const members = await api("GET", "/members" + (search ? "?search=" + encodeURIComponent(search) : ""));
    const container = $("#member-table");
    if (!members.length) {
        container.innerHTML = `<p class="empty">${search ? "No members match your search." : "No members yet."}</p>`;
        return;
    }
    container.innerHTML = `<div class="section"><div class="section-body"><table>
        <thead><tr><th>Name</th><th>Email</th><th>Phone</th><th>Registered</th><th></th></tr></thead>
        <tbody>${members.map((m) => `
            <tr>
                <td>${escapeHtml(m.name)}</td>
                <td>${escapeHtml(m.email)}</td>
                <td>${escapeHtml(m.phone ?? "")}</td>
                <td>${m.createdAt ? m.createdAt.slice(0, 10) : ""}</td>
                <td class="actions">
                    <button type="button" class="btn small" data-member-stokvels="${m.id}">Stokvels</button>
                    <button type="button" class="btn small" data-edit-member="${m.id}">Edit</button>
                    <button type="button" class="btn small danger" data-delete-member="${m.id}">Delete</button>
                </td>
            </tr>`).join("")}
        </tbody></table></div></div>`;

    container.querySelectorAll("[data-edit-member]").forEach((b) => b.addEventListener("click", () =>
        openMemberForm(members.find((m) => m.id === Number(b.dataset.editMember)))));
    container.querySelectorAll("[data-delete-member]").forEach((b) => b.addEventListener("click", () => {
        const m = members.find((x) => x.id === Number(b.dataset.deleteMember));
        if (!confirm(`Delete ${m.name}?`)) return;
        run(async () => { await api("DELETE", `/members/${m.id}`); await loadMembers(); }, "Member deleted");
    }));
    container.querySelectorAll("[data-member-stokvels]").forEach((b) => b.addEventListener("click", () =>
        run(async () => {
            const m = members.find((x) => x.id === Number(b.dataset.memberStokvels));
            const memberships = await api("GET", `/members/${m.id}/memberships`);
            toast(memberships.length
                ? `${m.name}:\n` + memberships.map((ms) =>
                    `${ms.stokvelName} - ${label(ms.role)}${ms.active ? "" : " (left " + ms.leftOn + ")"}`).join("\n")
                : `${m.name} hasn't joined a stokvel yet.`);
        })));
}

function openMemberForm(member) {
    const editing = Boolean(member);
    openForm({
        title: editing ? `Edit ${member.name}` : "New member",
        submitLabel: editing ? "Save" : "Create",
        fields: [
            { name: "name", label: "Full name", required: true, value: member?.name },
            { name: "email", label: "Email", type: "email", required: true, value: member?.email },
            { name: "phone", label: "Phone (optional)", type: "tel", value: member?.phone, hint: "e.g. 082 123 4567" },
        ],
        onSubmit: async (v) => {
            if (editing) await api("PUT", `/members/${member.id}`, v);
            else await api("POST", "/members", v);
            toast(editing ? "Member updated" : "Member created");
            await loadMembers();
        },
    });
}

// ---- Navigation and start-up ---------------------------------------------------------

function showView(view) {
    state.view = view;
    document.querySelectorAll(".tab").forEach((t) => {
        if (t.dataset.view === view) t.setAttribute("aria-current", "page");
        else t.removeAttribute("aria-current");
    });
    $("#view-stokvels").hidden = view !== "stokvels";
    $("#view-members").hidden = view !== "members";
    run(view === "stokvels" ? loadStokvels : loadMembers);
}

document.querySelectorAll(".tab").forEach((t) => t.addEventListener("click", () => showView(t.dataset.view)));
$("#new-stokvel").addEventListener("click", () => openStokvelForm(null));
$("#new-member").addEventListener("click", () => openMemberForm(null));
let searchTimer;
$("#member-search").addEventListener("input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => run(loadMembers), 250);
});

checkHealth();
showView("stokvels");
