const API = {
  async json(path, opts = {}) {
    const headers = opts.headers ? { ...opts.headers } : {};
    headers["Content-Type"] = "application/json";

    const token = localStorage.getItem("token");
    if (token) headers["Authorization"] = `Bearer ${token}`;

    const res = await fetch(path, { ...opts, headers });
    const text = await res.text();

    let data;
    try {
      data = text ? JSON.parse(text) : {};
    } catch {
      data = { ok: false, message: text || "Invalid server response" };
    }

    if (!res.ok) {
      const msg = data && data.message ? data.message : `Request failed (${res.status})`;
      throw new Error(msg);
    }

    return data;
  },
};

function $(sel) { return document.querySelector(sel); }
function $all(sel) { return Array.from(document.querySelectorAll(sel)); }

function toast(message, type = "success") {
  const t = $("#toast");
  t.className = `toast show ${type}`;
  t.textContent = message;
  window.clearTimeout(toast._t);
  toast._t = window.setTimeout(() => {
    t.className = "toast";
    t.textContent = "";
  }, 3200);
}

function escapeHtml(s) {
  return String(s)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#039;");
}

function billHtml(b) {
  const now = new Date();
  const lines = [
    `<div style="font-size:18px;font-weight:700;margin-bottom:8px;">Ocean View Resort - Bill</div>`,
    `<div style="font-size:12px;color:#444;margin-bottom:12px;">Generated: ${escapeHtml(now.toLocaleString())}</div>`,
    `<div><b>Reservation</b>: ${escapeHtml(b.reservationNumber)}</div>`,
    `<div><b>Guest</b>: ${escapeHtml(b.guestName)}</div>`,
    `<div><b>Room Type</b>: ${escapeHtml(b.roomType)}</div>`,
    `<div><b>Check-in</b>: ${escapeHtml(b.checkIn)}</div>`,
    `<div><b>Check-out</b>: ${escapeHtml(b.checkOut)}</div>`,
    `<hr style="border:0;border-top:1px solid #ccc;margin:12px 0;">`,
    `<div><b>Nights</b>: ${b.nights}</div>`,
    `<div><b>Rate per night</b>: LKR ${b.ratePerNight}</div>`,
    `<div style="margin-top:8px;font-size:16px;"><b>Total</b>: LKR ${b.total}</div>`,
  ];
  return lines.join("");
}

async function printBillForReservation(reservationNumber) {
  const data = await API.json(`/api/bill/${encodeURIComponent(reservationNumber)}`);
  const b = data.bill;

  const html = `<!doctype html>
  <html>
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Bill - ${escapeHtml(b.reservationNumber)}</title>
    <style>
      body { font-family: Arial, sans-serif; padding: 24px; color: #111; }
      .wrap { max-width: 640px; margin: 0 auto; }
      @media print { button { display: none; } body { padding: 0; } .wrap { max-width: none; } }
    </style>
  </head>
  <body>
    <div class="wrap">
      ${billHtml(b)}
      <div style="margin-top:18px;">
        <button onclick="window.print()">Print / Save as PDF</button>
      </div>
    </div>
  </body>
  </html>`;

  const w = window.open("", "_blank");
  if (!w) throw new Error("Popup blocked");
  w.document.open();
  w.document.write(html);
  w.document.close();

  w.focus();
  w.onload = () => {
    try { w.print(); } catch { }
  };
}

function bindBillPrint() {
  const btn = $("#billPrintBtn");
  if (!btn) return;
  btn.addEventListener("click", async () => {
    const id = $("#billReservationNumber").value.trim();
    if (!id) {
      toast("Enter a reservation number", "error");
      return;
    }
    try {
      await printBillForReservation(id);
    } catch (e) {
      toast(e.message || "Unable to print", "error");
    }
  });
}

function setPage(target) {
  const map = {
    dashboard: { title: "Dashboard", subtitle: "Welcome" },
    addReservation: { title: "Add Reservation", subtitle: "Register a new guest" },
    viewReservation: { title: "View Reservation", subtitle: "Search by reservation number" },
    updateReservation: { title: "Update Reservation", subtitle: "Update your booking" },
    bill: { title: "Bill", subtitle: "Calculate total cost" },
    help: { title: "Help", subtitle: "How to use the system" },
  };

  const meta = map[target] || map.dashboard;
  $("#pageTitle").textContent = meta.title;
  $("#pageSubtitle").textContent = meta.subtitle;

  const panels = {
    dashboard: "#panel-dashboard",
    addReservation: "#panel-addReservation",
    viewReservation: "#panel-viewReservation",
    updateReservation: "#panel-updateReservation",
    bill: "#panel-bill",
    help: "#panel-help",
  };

  Object.values(panels).forEach((id) => ($(id).hidden = true));
  $(panels[target] || panels.dashboard).hidden = false;

  $all(".nav-item").forEach((b) => b.classList.remove("active"));
  const active = $all(".nav-item").find((b) => b.dataset.target === target);
  if (active) active.classList.add("active");
}

async function refreshRates() {
  const data = await API.json("/api/rates");
  const lines = data.rates
    .map((r) => `<div><b>${escapeHtml(r.roomType)}</b>: LKR ${r.ratePerNight} / night</div>`)
    .join("");
  $("#rates").innerHTML = lines;
}

async function ensureHelp() {
  const data = await API.json("/api/help");
  $("#helpContent").innerHTML = `<div>${escapeHtml(data.text).replace(/\n/g, "<br>")}</div>`;
}

function showLogin() { $("#loginModal").style.display = "flex"; }
function hideLogin() { $("#loginModal").style.display = "none"; }

async function requireCustomerSession() {
  const token = localStorage.getItem("token");
  if (!token) {
    $("#authChip").textContent = "Not logged in";
    showLogin();
    return null;
  }

  try {
    const data = await API.json("/api/me");
    if (data.role !== "CUSTOMER") {
      localStorage.removeItem("token");
      window.location.href = "/";
      return null;
    }
    $("#authChip").textContent = `Logged in as ${data.username} (CUSTOMER)`;
    hideLogin();
    return data;
  } catch {
    localStorage.removeItem("token");
    $("#authChip").textContent = "Not logged in";
    showLogin();
    return null;
  }
}

function bindNav() {
  $all(".nav-item").forEach((btn) => {
    if (!btn.dataset.target) return;
    btn.addEventListener("click", async () => {
      setPage(btn.dataset.target);
      if (btn.dataset.target === "help") {
        try { await ensureHelp(); } catch (e) { toast(e.message, "error"); }
      }
    });
  });
}

function bindLogin() {
  $("#loginForm").addEventListener("submit", async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    const username = fd.get("username");
    const password = fd.get("password");

    try {
      const data = await API.json("/api/login", {
        method: "POST",
        body: JSON.stringify({ username, password }),
      });
      localStorage.setItem("token", data.token);

      if (data.role !== "CUSTOMER") {
        toast("This account is not a customer", "error");
        localStorage.removeItem("token");
        return;
      }

      toast("Login successful", "success");
      await requireCustomerSession();
    } catch (err) {
      toast(err.message, "error");
    }
  });
}

function bindLogout() {
  $("#logoutBtn").addEventListener("click", async () => {
    try {
      await API.json("/api/logout", { method: "POST" });
    } catch {
    } finally {
      localStorage.removeItem("token");
      window.location.href = "/";
    }
  });
}

function bindAddReservation() {
  $("#addForm").addEventListener("submit", async (e) => {
    e.preventDefault();

    const fd = new FormData(e.target);
    const payload = {
      reservationNumber: fd.get("reservationNumber").trim(),
      guestName: fd.get("guestName").trim(),
      address: fd.get("address").trim(),
      contactNumber: fd.get("contactNumber").trim(),
      roomType: fd.get("roomType"),
      checkIn: fd.get("checkIn"),
      checkOut: fd.get("checkOut"),
    };

    try {
      const data = await API.json("/api/reservations", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      toast(data.message || "Reservation saved", "success");
      e.target.reset();
      setPage("viewReservation");
      $("#searchReservationNumber").value = payload.reservationNumber;

      try {
        await printBillForReservation(payload.reservationNumber);
      } catch (e) {
      }
    } catch (err) {
      toast(err.message, "error");
    }
  });
}

function bindSearchReservation() {
  $("#searchReservationBtn").addEventListener("click", async () => {
    const id = $("#searchReservationNumber").value.trim();
    if (!id) {
      toast("Enter a reservation number", "error");
      return;
    }

    try {
      const data = await API.json(`/api/reservations/${encodeURIComponent(id)}`);
      $("#reservationOutput").textContent = JSON.stringify(data.reservation, null, 2);
    } catch (err) {
      $("#reservationOutput").textContent = "";
      toast(err.message, "error");
    }
  });
}

async function loadReservationToUpdate(id) {
  const data = await API.json(`/api/reservations/${encodeURIComponent(id)}`);
  const r = data.reservation;

  const form = $("#updateForm");
  form.guestName.value = r.guestName;
  form.address.value = r.address;
  form.contactNumber.value = r.contactNumber;
  form.roomType.value = r.roomType;
  form.checkIn.value = r.checkIn;
  form.checkOut.value = r.checkOut;
  form.hidden = false;
}

function bindUpdate() {
  $("#loadUpdateBtn").addEventListener("click", async () => {
    const id = $("#updateReservationNumber").value.trim();
    if (!id) {
      toast("Enter a reservation number", "error");
      return;
    }
    try {
      await loadReservationToUpdate(id);
      toast("Loaded", "success");
    } catch (e) {
      $("#updateForm").hidden = true;
      toast(e.message, "error");
    }
  });

  $("#updateForm").addEventListener("submit", async (e) => {
    e.preventDefault();
    const id = $("#updateReservationNumber").value.trim();
    if (!id) {
      toast("Enter a reservation number", "error");
      return;
    }

    const fd = new FormData(e.target);
    const payload = {
      guestName: fd.get("guestName").trim(),
      address: fd.get("address").trim(),
      contactNumber: fd.get("contactNumber").trim(),
      roomType: fd.get("roomType"),
      checkIn: fd.get("checkIn"),
      checkOut: fd.get("checkOut"),
    };

    try {
      const data = await API.json(`/api/reservations/${encodeURIComponent(id)}`, {
        method: "PUT",
        body: JSON.stringify(payload),
      });
      toast(data.message || "Updated", "success");
    } catch (err) {
      toast(err.message, "error");
    }
  });
}

function bindBill() {
  $("#billBtn").addEventListener("click", async () => {
    const id = $("#billReservationNumber").value.trim();
    if (!id) {
      toast("Enter a reservation number", "error");
      return;
    }

    try {
      const data = await API.json(`/api/bill/${encodeURIComponent(id)}`);
      const b = data.bill;
      $("#billOutput").innerHTML = `
        <div><b>Reservation</b>: ${escapeHtml(b.reservationNumber)}</div>
        <div><b>Guest</b>: ${escapeHtml(b.guestName)}</div>
        <div><b>Room Type</b>: ${escapeHtml(b.roomType)}</div>
        <div><b>Check-in</b>: ${escapeHtml(b.checkIn)}</div>
        <div><b>Check-out</b>: ${escapeHtml(b.checkOut)}</div>
        <hr style="border:0;border-top:1px solid rgba(255,255,255,0.18);margin:12px 0;">
        <div><b>Nights</b>: ${b.nights}</div>
        <div><b>Rate per night</b>: LKR ${b.ratePerNight}</div>
        <div style="margin-top:8px;font-size:18px;"><b>Total</b>: LKR ${b.total}</div>
      `;
    } catch (err) {
      $("#billOutput").innerHTML = "";
      toast(err.message, "error");
    }
  });
}

async function init() {
  bindNav();
  bindLogin();
  bindLogout();
  bindAddReservation();
  bindSearchReservation();
  bindUpdate();
  bindBill();
  bindBillPrint();

  setPage("dashboard");

  try {
    await refreshRates();
  } catch (e) {
    toast(e.message, "error");
  }

  await requireCustomerSession();
}

init();
