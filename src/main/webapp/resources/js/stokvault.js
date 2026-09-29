/*
 * Small progressive enhancements for the Faces pages. Everything works without JavaScript;
 * this only adds the "Hide amounts" toggle, dialogs, list filters and auto-hiding toasts.
 */
(function () {
    "use strict";

    // ---- Hide amounts: masks every money value (elements with class "amt"), e.g. when the
    // screen is shown at a meeting. Remembered per browser.
    var KEY = "stokvault.hideAmounts";

    function applyHidden(hidden) {
        document.documentElement.classList.toggle("hide-amounts", hidden);
        document.querySelectorAll(".amt").forEach(function (el) {
            if (hidden) {
                if (!el.hasAttribute("data-amt")) {
                    el.setAttribute("data-amt", el.textContent);
                }
                el.textContent = "R •••••";
            } else if (el.hasAttribute("data-amt")) {
                el.textContent = el.getAttribute("data-amt");
                el.removeAttribute("data-amt");
            }
        });
        // Sentences with an amount inside ("R 500 due Fri 3 Oct"): mask just the amount
        document.querySelectorAll(".amt-text").forEach(function (el) {
            var walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
            for (var node = walker.nextNode(); node; node = walker.nextNode()) {
                if (hidden) {
                    if (node.svOriginal === undefined) { node.svOriginal = node.nodeValue; }
                    node.nodeValue = node.svOriginal.replace(/R[\s\u00a0\u202f]?\d+(?:[\s\u00a0\u202f]\d{3})*(?:,\d\d)?/g, "R •••••");
                } else if (node.svOriginal !== undefined) {
                    node.nodeValue = node.svOriginal;
                    delete node.svOriginal;
                }
            }
        });
    }

    function isHidden() {
        try { return localStorage.getItem(KEY) === "1"; } catch (e) { return false; }
    }

    window.svToggleAmounts = function () {
        var hidden = !document.documentElement.classList.contains("hide-amounts");
        try { localStorage.setItem(KEY, hidden ? "1" : "0"); } catch (e) { /* private mode */ }
        applyHidden(hidden);
        return false;
    };

    // ---- Dialogs: open a <dialog id="..."> and optionally preselect a radio / select value
    window.svOpen = function (id, fieldName, value) {
        var dialog = document.getElementById(id);
        if (!dialog) { return false; }
        if (fieldName && value !== undefined) {
            dialog.querySelectorAll("[name$='" + fieldName + "']").forEach(function (input) {
                if (input.type === "radio") { input.checked = input.value === value; }
                else { input.value = value; }
            });
        }
        dialog.showModal();
        return false;
    };

    window.svClose = function (el) {
        var dialog = el.closest("dialog");
        if (dialog) { dialog.close(); }
        return false;
    };

    // ---- List filters: buttons with data-filter="all|outstanding" show/hide rows with data-state
    window.svFilter = function (button, list, state) {
        var group = button.parentElement;
        group.querySelectorAll("button").forEach(function (b) { b.classList.toggle("on", b === button); });
        document.querySelectorAll("#" + list + " [data-state]").forEach(function (row) {
            row.style.display = !state || row.getAttribute("data-state") === state ? "" : "none";
        });
        return false;
    };

    // ---- Text search over rows with data-search
    window.svSearch = function (input, list) {
        var q = input.value.trim().toLowerCase();
        var shown = 0;
        document.querySelectorAll("#" + list + " [data-search]").forEach(function (row) {
            var match = !q || row.getAttribute("data-search").toLowerCase().indexOf(q) >= 0;
            row.style.display = match ? "" : "none";
            if (match) { shown++; }
        });
        var none = document.querySelector("#" + list + " .no-match");
        if (none) { none.style.display = shown ? "none" : ""; }
    };

    document.addEventListener("DOMContentLoaded", function () {
        applyHidden(isHidden());
        // Success messages fade after a few seconds; errors stay until the next action
        setTimeout(function () {
            document.querySelectorAll(".toasts li.toast-info").forEach(function (li) {
                li.classList.add("fade");
                setTimeout(function () { li.remove(); }, 400);
            });
        }, 4500);
        // Close dialogs by clicking the backdrop
        document.querySelectorAll("dialog.modal").forEach(function (d) {
            d.addEventListener("click", function (e) { if (e.target === d) { d.close(); } });
        });
        // "Pay now" on the home page links to group.xhtml?...&pay=1: open the payment window
        if (/[?&]pay=1\b/.test(location.search)) {
            svOpen("record");
        }
        // A link to a section inside a closed <details> (e.g. #to-check): open it and show it
        openTarget();
        window.addEventListener("hashchange", openTarget);
    });

    function openTarget() {
        if (!location.hash) { return; }
        var target = document.getElementById(location.hash.substring(1));
        if (!target) { return; }
        for (var el = target; el; el = el.parentElement) {
            if (el.tagName === "DETAILS") { el.open = true; }
        }
        if (target.tagName === "DETAILS") { target.open = true; }
        target.scrollIntoView({block: "start"});
    }

    // ---- "Reject" and similar actions that need a reason: show the reason box first
    window.svReveal = function (button, id) {
        var box = document.getElementById(id);
        if (box) {
            box.hidden = false;
            var input = box.querySelector("input, textarea");
            if (input) { input.focus(); }
        }
        button.hidden = true;
        return false;
    };
})();
