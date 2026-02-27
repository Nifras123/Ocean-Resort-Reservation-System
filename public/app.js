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

function setPage(target) {
  const map = {
    dashboard: { title: "Dashboard", subtitle: "Welcome" },
    addReservation: { title: "Add Reservation", subtitle: "Register a new guest" },
    viewReservation: { title: "Display Reservation", subtitle: "Search by reservation number" },
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

function escapeHtml(s) {
  return String(s)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#039;");
}

function showLogin() {
  $("#loginModal").style.display = "flex";
}

function hideLogin() {
  $("#loginModal").style.display = "none";
}

async function checkSession() {
  const token = localStorage.getItem("token");
  if (!token) {
    $("#authChip").textContent = "Not logged in";
    showLogin();
    return;
  }

  try {
    const data = await API.json("/api/me");
    $("#authChip").textContent = `Logged in as ${data.username}`;
    hideLogin();
  } catch {
    localStorage.removeItem("token");
    $("#authChip").textContent = "Not logged in";
    showLogin();
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
      toast("Login successful", "success");
      await checkSession();
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
      toast("Logged out", "success");
      setPage("dashboard");
      await checkSession();
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
  bindBill();
  setPage("dashboard");

  try {
    await refreshRates();
  } catch (e) {
    toast(e.message, "error");
  }

  await checkSession();
}

init();
