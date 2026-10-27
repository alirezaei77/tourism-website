"use strict";

/* ===== پیکربندی API ===== */
const API_BASE = (window.__API_BASE__ || "/api").replace(/\/$/, "");
const NESHAN_API_KEY = "web.624be8c281c1439a8183326986ac35a0";

const CLIENT_ID = (() => {
  const key = "iran-tourism-client-id";
  try {
    let id = localStorage.getItem(key);
    if (!id) {
      id = (crypto?.randomUUID?.()) || `c-${Date.now()}-${Math.random().toString(36).slice(2,10)}`;
      localStorage.setItem(key, id);
    }
    return id;
  } catch { return `c-${Date.now()}-${Math.random().toString(36).slice(2,10)}`; }
})();

const clientHeaders = (extra={}) => ({ "X-Client-Id": CLIENT_ID, ...extra });

async function apiJson(path, options={}) {
  const res = await fetch(`${API_BASE}${path}`, options);
  if (res.status === 204) return null;
  if (!res.ok) {
    let message = `خطای ${res.status}`;
    try { const body = await res.json(); message = body.message || body.error || message; } catch {}
    throw new Error(message);
  }
  const ct = res.headers.get("content-type") || "";
  return ct.includes("application/json") ? res.json() : null;
}

/* ===== ابزارها ===== */
const $  = (s,r=document) => r.querySelector(s);
const $$ = (s,r=document) => Array.from(r.querySelectorAll(s));
const icon = name => `<svg class="icon" aria-hidden="true"><use href="#i-${name}"/></svg>`;
const escapeHTML = v => String(v ?? "").replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const normalizeText = v => String(v ?? "").toLocaleLowerCase("fa")
  .replace(/[يى]/g,"ی").replace(/ك/g,"ک").replace(/[\u064B-\u065F\u0670]/g,"")
  .replace(/[\u200c\u200e\u200f\sـ]+/g," ").trim();
const formatNumber = n => new Intl.NumberFormat("fa-IR").format(n);

function toast(message, type = "success") {
  const region = $("#toast-region"); if (!region) return;
  const node = document.createElement("div");
  node.className = `toast ${type === "error" ? "error" : ""}`;
  node.innerHTML = `${icon(type === "error" ? "alert" : "check")}<span>${escapeHTML(message)}</span>`;
  region.appendChild(node);
  setTimeout(() => { node.style.opacity = "0"; setTimeout(() => node.remove(), 250); }, 3500);
}

/* ===== State ===== */
const state = {
  places:[], query:"", category:"", province:"", difficulty:"", season:"", duration:"",
  status:"", sort:"default", saved:new Set(), visited:new Set(), selectedId:null,
  showSavedOnly:false, mapLayer:"neshan", map:null, markerLayer:null,
  markerById:new Map(), lastFocused:null, submitMode:"new", reportTarget:null, apiOnline:false
};

/* ===== Modals ===== */
function openModal(id) {
  const modal = document.getElementById(id); if (!modal) return;
  state.lastFocused = document.activeElement;
  modal.classList.add("open");
  document.body.classList.add("modal-open");
  const f = modal.querySelector("button, input, select, textarea, [tabindex]:not([tabindex='-1'])");
  if (f) setTimeout(() => f.focus(), 40);
}
function closeModal(modal) {
  if (!modal) return;
  modal.classList.remove("open");
  if (!$(".modal-backdrop.open")) {
    document.body.classList.remove("modal-open");
    state.lastFocused?.focus?.();
  }
}
document.addEventListener("click", e => {
  const btn = e.target.closest("[data-close-modal]");
  if (btn) closeModal(btn.closest(".modal-backdrop"));
});
$$(".modal-backdrop").forEach(bd => {
  bd.addEventListener("mousedown", e => { if (e.target === bd) closeModal(bd); });
  bd.addEventListener("keydown", e => {
    if (e.key !== "Tab") return;
    const f = Array.from(bd.querySelectorAll('a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])')).filter(el=>el.offsetParent);
    if (!f.length) return;
    const first=f[0], last=f[f.length-1];
    if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
    else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
  });
});
document.addEventListener("keydown", e => {
  if (e.key === "Escape") { const open = $(".modal-backdrop.open"); if (open) closeModal(open); }
  if (e.key === "/" && !/INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName)) {
    e.preventDefault(); $("#hero-search-input").focus();
  }
});

/* ===== Filtering ===== */
function getFilteredPlaces() {
  let results = state.places.filter(p => {
    if (state.category && p.category !== state.category) return false;
    if (state.province && p.province !== state.province) return false;
    if (state.season && p.season !== state.season) return false;
    if (state.duration && p.duration !== state.duration) return false;
    if (state.status && p.status !== state.status) return false;
    if (state.showSavedOnly && !state.saved.has(p.id)) return false;
    if (state.query) {
      const s = normalizeText([p.name,p.province,p.locality,p.category,p.type,p.short,p.description,...(p.tags||[])].join(" "));
      if (!s.includes(normalizeText(state.query))) return false;
    }
    return true;
  });
  if (state.sort === "name") results.sort((a,b)=>a.name.localeCompare(b.name,"fa"));
  else if (state.sort === "province") results.sort((a,b)=>a.province.localeCompare(b.province,"fa"));
  else if (state.sort === "difficulty") {
    const o = {"آسان":0,"متوسط":1,"دشوار":2,"نامشخص":3};
    results.sort((a,b)=>(o[a.difficulty]??4)-(o[b.difficulty]??4));
  }
  return results;
}

function renderActiveFilters() {
  const holder = $("#active-filters"); if (!holder) return;
  holder.innerHTML = "";
  const filters = [
    ["query",state.query,`جست‌وجو: ${state.query}`], ["category",state.category,state.category],
    ["province",state.province,state.province], ["difficulty",state.difficulty,`دسترسی: ${state.difficulty}`],
    ["season",state.season,`فصل: ${state.season}`], ["duration",state.duration,`مدت: ${state.duration}`],
    ["status",state.status,`وضعیت: ${state.status}`]
  ].filter(i=>i[1]);
  if (state.showSavedOnly) filters.push(["savedOnly","true","فقط ذخیره‌شده‌ها"]);
  filters.forEach(([key,,label]) => {
    const chip = document.createElement("span"); chip.className = "filter-chip";
    chip.innerHTML = `${escapeHTML(label)} <button type="button" aria-label="حذف">${icon("x")}</button>`;
    chip.querySelector("button").addEventListener("click", () => {
      if (key === "savedOnly") state.showSavedOnly = false; else state[key] = "";
      syncFilterControls(); renderAll();
    });
    holder.appendChild(chip);
  });
  if (filters.length > 1) {
    const clear = document.createElement("button"); clear.type="button"; clear.className="text-link";
    clear.style.fontSize="10px"; clear.textContent="پاک کردن همه"; clear.addEventListener("click",clearFilters);
    holder.appendChild(clear);
  }
}

function syncFilterControls() {
  const map = { "#filter-category":state.category, "#filter-province":state.province,
    "#sort-results":state.sort, "#map-search-input":state.query, "#hero-search-input":state.query,
    "#advanced-category":state.category, "#advanced-province":state.province,
    "#advanced-difficulty":state.difficulty, "#advanced-season":state.season,
    "#advanced-duration":state.duration, "#advanced-status":state.status };
  Object.entries(map).forEach(([sel,val]) => { const el=$(sel); if(el) el.value=val; });
  $$(".quick-item").forEach(i => i.classList.toggle("selected", i.dataset.category === state.category && !!state.category));
}

function clearFilters() {
  Object.assign(state, { query:"",category:"",province:"",difficulty:"",season:"",duration:"",status:"",sort:"default",showSavedOnly:false });
  syncFilterControls(); renderAll();
}

/* ===== Results ===== */
function createPlaceCard(place) {
  const card = document.createElement("article");
  card.className = `place-card ${state.selectedId === place.id ? "selected" : ""}`;
  card.tabIndex = 0; card.setAttribute("role","button");
  card.setAttribute("aria-label", `مشاهده جزئیات ${place.name}`);
  card.dataset.id = place.id;
  const saved = state.saved.has(place.id);
  card.innerHTML = `
    <div class="place-image" style="background-image:url('${place.image}')" role="img" aria-label="${escapeHTML(place.name)}">
      <span class="tiny-badge">${escapeHTML(place.category)}</span>
    </div>
    <div class="place-info">
      <span class="place-type">${escapeHTML(place.type)}</span>
      <h4>${escapeHTML(place.name)}</h4>
      <p>${escapeHTML(place.short)}</p>
      <div class="place-meta">
        <span>${icon("pin")}${escapeHTML(place.province)}</span>
        <button type="button" class="place-save ${saved?"saved":""}" data-save="${escapeHTML(place.id)}" aria-label="ذخیره" aria-pressed="${saved}">
          <svg class="icon"><use href="#i-bookmark"/></svg>
        </button>
      </div>
    </div>`;
  card.addEventListener("click", e => {
    if (e.target.closest("[data-save]")) return;
    selectPlace(place.id, true); openPlaceDetails(place.id);
  });
  card.addEventListener("keydown", e => {
    if ((e.key === "Enter" || e.key === " ") && e.target === card) {
      e.preventDefault(); selectPlace(place.id, true); openPlaceDetails(place.id);
    }
  });
  card.querySelector("[data-save]").addEventListener("click", e => { e.stopPropagation(); toggleSaved(place.id); });
  return card;
}

const RESULTS_PAGE_SIZE = 50;

function renderResults() {
  const results = getFilteredPlaces();
  const list = $("#results-list"); if (!list) return;
  const countEl = $("#results-count"); if (countEl) countEl.textContent = formatNumber(results.length);
  const summaryEl = $("#results-summary");
  if (summaryEl) summaryEl.textContent = state.showSavedOnly ? "فهرست جاذبه‌های ذخیره‌شده"
    : (state.query||state.category||state.province) ? "نتایج مطابق جست‌وجو"
    : "نمونه‌های نمایشی اطلس";
  list.innerHTML = "";
  if (!results.length) {
    list.innerHTML = `<div class="empty-state">${icon("search")}<strong>جاذبه‌ای پیدا نشد</strong>
      <span>عبارت جست‌وجو یا فیلترها را تغییر بده.</span>
      <div style="margin-top:15px"><button class="secondary-btn" id="empty-reset">پاک کردن فیلترها</button></div></div>`;
    $("#empty-reset")?.addEventListener("click", clearFilters);
    renderActiveFilters(); return;
  }
  let shownCount = RESULTS_PAGE_SIZE, total = results.length;
  const appendChunk = (s,e) => results.slice(s,e).forEach(p => list.appendChild(createPlaceCard(p)));
  appendChunk(0, Math.min(shownCount, total));
  if (total > shownCount) {
    const wrap = document.createElement("div"); wrap.className = "results-loadmore";
    wrap.innerHTML = `<span class="count">${formatNumber(shownCount)} از ${formatNumber(total)}</span>
      <button type="button" class="secondary-btn">نمایش ${formatNumber(Math.min(RESULTS_PAGE_SIZE, total-shownCount))} مورد بعدی</button>`;
    wrap.querySelector("button").addEventListener("click", () => {
      const s = shownCount, e = Math.min(shownCount + RESULTS_PAGE_SIZE, total);
      appendChunk(s, e); shownCount = e; wrap.remove();
      if (shownCount < total) {
        list.appendChild(wrap);
        wrap.querySelector(".count").textContent = `${formatNumber(shownCount)} از ${formatNumber(total)}`;
        wrap.querySelector("button").textContent = `نمایش ${formatNumber(Math.min(RESULTS_PAGE_SIZE, total-shownCount))} مورد بعدی`;
      }
    });
    list.appendChild(wrap);
  }
  renderActiveFilters();
}

/* ===== Map ===== */
function markerIcon(place, selected=false) {
  const c = place.color || ICONS.natural, size = selected ? 41 : 34;
  const letter = place.category==="تاریخی"?"ت":place.category==="زمین‌شناسی"?"ز":place.category==="فرهنگی"?"ف":place.category==="خوراک"?"خ":place.category==="مسیر"?"م":"ط";
  return L.divIcon({ className:"", html:`<div class="custom-marker" style="width:${size}px;height:${size}px;background:${c};${selected?"box-shadow:0 0 0 5px rgba(75,149,107,.25),0 4px 14px rgba(0,0,0,.25)":""}"><span>${letter}</span></div>`, iconSize:[size,size], iconAnchor:[size/2,size], popupAnchor:[0,-size+3] });
}

let clusterGroup = null;

function initMap() {
  if (typeof L === "undefined" || typeof L.Map !== "function") {
    $("#iran-map").innerHTML = `<div class="empty-state" style="padding:90px 20px"><strong>نقشه بارگذاری نشد</strong><span>اتصال به CDN نشان را بررسی کنید.</span></div>`;
    return;
  }
  state.map = new L.Map("iran-map", { key:NESHAN_API_KEY, maptype:"neshan", poi:true, traffic:false,
    center:[32.2,53.5], zoom:5, zoomControl:false, attributionControl:true, scrollWheelZoom:true });
  if (typeof L.markerClusterGroup === "function") {
    clusterGroup = L.markerClusterGroup({ chunkedLoading:true, maxClusterRadius:55, spiderfyOnMaxZoom:true,
      showCoverageOnHover:false, zoomToBoundsOnClick:true,
      iconCreateFunction: c => { const n = c.getChildCount(); const s = n<10?"sm":n<100?"md":"lg";
        return L.divIcon({ html:`<div class="marker-cluster-custom ${s}">${n}</div>`, className:"", iconSize:L.point(50,50) }); } });
    state.map.addLayer(clusterGroup); state.markerLayer = clusterGroup;
  } else { state.markerLayer = L.layerGroup().addTo(state.map); }
  renderMarkers();
  setTimeout(() => state.map && state.map.invalidateSize(), 200);
}

function renderMarkers() {
  if (!state.map || !state.markerLayer) return;
  if (clusterGroup) clusterGroup.clearLayers(); else state.markerLayer.clearLayers();
  state.markerById.clear();
  getFilteredPlaces().forEach(place => {
    if (!Number.isFinite(place.lat) || !Number.isFinite(place.lng)) return;
    const marker = L.marker([place.lat, place.lng], { icon:markerIcon(place, state.selectedId===place.id), title:place.name, alt:place.name, keyboard:true });
    marker.bindPopup(`<strong>${escapeHTML(place.name)}</strong><br><span>${escapeHTML(place.province)} · ${escapeHTML(place.category)}</span><button type="button" data-map-detail="${escapeHTML(place.id)}">مشاهده جزئیات</button>`);
    marker.on("click", () => { state.selectedId = place.id; $$(".place-card").forEach(c => c.classList.toggle("selected", c.dataset.id === place.id)); });
    state.markerLayer.addLayer(marker);
    state.markerById.set(place.id, marker);
  });
}

function selectPlace(id, moveMap=false) {
  const place = state.places.find(p => p.id === id); if (!place) return;
  state.selectedId = id;
  $$(".place-card").forEach(c => c.classList.toggle("selected", c.dataset.id === id));
  renderMarkers();
  if (moveMap && state.map && Number.isFinite(place.lat) && Number.isFinite(place.lng)) {
    const z = state.map.getZoom();
    state.map.flyTo([place.lat, place.lng], Math.max(z, 7), { duration:.6 });
    const mk = state.markerById.get(id); if (mk) setTimeout(() => mk.openPopup(), 350);
  }
}

function renderAll() { renderResults(); renderMarkers(); updatePassportStats(); }

function setSearch(query) {
  state.query = String(query||"").trim(); state.showSavedOnly = false;
  syncFilterControls(); renderAll();
  $("#atlas")?.scrollIntoView({ behavior:"smooth", block:"start" });
}
function setCategory(c) { state.category = c; state.showSavedOnly=false; syncFilterControls(); renderAll(); $("#atlas")?.scrollIntoView({ behavior:"smooth", block:"start" }); }
function setProvince(p) { state.province = p; state.showSavedOnly=false; syncFilterControls(); renderAll(); $("#atlas")?.scrollIntoView({ behavior:"smooth", block:"start" }); const f = getFilteredPlaces()[0]; if (f) selectPlace(f.id, true); }

/* ===== Saved / Visited ===== */
async function toggleSaved(id) {
  const wasSaved = state.saved.has(id);
  if (wasSaved) state.saved.delete(id); else state.saved.add(id);
  renderAll();
  if ($("#detail-modal").classList.contains("open") && state.selectedId === id) openPlaceDetails(id, false);
  if (!state.apiOnline) { toast(wasSaved ? "از فهرست محلی حذف شد (آفلاین)." : "به فهرست محلی اضافه شد (آفلاین).", "error"); return; }
  try {
    await apiJson(`/passport/saved/${encodeURIComponent(id)}`, { method: wasSaved ? "DELETE" : "POST", headers: clientHeaders() });
    toast(wasSaved ? "از فهرست ذخیره‌شده‌ها حذف شد." : "به فهرست شخصی اضافه شد.");
  } catch (e) {
    if (wasSaved) state.saved.add(id); else state.saved.delete(id);
    renderAll(); toast(e.message || "ذخیره‌سازی انجام نشد.", "error");
  }
}

async function toggleVisited(id) {
  const wasVisited = state.visited.has(id);
  if (wasVisited) state.visited.delete(id); else state.visited.add(id);
  updatePassportStats();
  if ($("#detail-modal").classList.contains("open") && state.selectedId === id) openPlaceDetails(id, false);
  if (!state.apiOnline) { toast("فقط در حافظهٔ صفحه ثبت شد (آفلاین).", "error"); return; }
  try {
    await apiJson(`/passport/visited/${encodeURIComponent(id)}`, { method: wasVisited ? "DELETE" : "POST", headers: clientHeaders() });
    toast(wasVisited ? "وضعیت بازدید لغو شد." : "بازدید در گذرنامه ثبت شد.");
  } catch (e) {
    if (wasVisited) state.visited.add(id); else state.visited.delete(id);
    updatePassportStats(); toast(e.message || "ثبت بازدید انجام نشد.", "error");
  }
}

/* ===== Detail ===== */
function openPlaceDetails(id, open=true) {
  const place = state.places.find(p => p.id === id); if (!place) return;
  state.selectedId = id;
  const saved = state.saved.has(id), visited = state.visited.has(id);
  $("#detail-content").innerHTML = `
    <div class="detail-cover" style="background-image:url('${escapeHTML(place.image)}')">
      <button class="modal-close" data-close-modal aria-label="بستن" style="position:absolute;top:12px;left:12px;z-index:5;background:rgba(255,255,255,.95);box-shadow:0 2px 8px rgba(0,0,0,.18)"><svg class="icon"><use href="#i-x"/></svg></button>
      <div class="detail-cover-content">
        <span class="place-type">${escapeHTML(place.category)} · ${escapeHTML(place.type)}</span>
        <h2 id="detail-title">${escapeHTML(place.name)}</h2>
        <p>${icon("pin")} ${escapeHTML(place.locality||place.province)}، استان ${escapeHTML(place.province)}</p>
      </div>
    </div>
    <div class="detail-body">
      <div style="display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap;margin-bottom:15px">
        <span class="results-count">${icon("shield")} ${escapeHTML(place.status||"نیازمند بررسی")}</span>
        <span style="font-size:10px;color:var(--muted)">شناسه: ${escapeHTML(place.id)}</span>
      </div>
      ${place.alert ? `<div class="detail-alert">${icon("alert")}<div><strong>نکته مهم</strong><br>${escapeHTML(place.alert)}</div></div>` : ""}
      <p class="detail-summary">${escapeHTML(place.description)}</p>
      <div class="detail-facts">
        <div class="detail-fact"><span>دشواری دسترسی</span><strong>${escapeHTML(place.difficulty)}</strong></div>
        <div class="detail-fact"><span>فصل پیشنهادی</span><strong>${escapeHTML(place.season)}</strong></div>
        <div class="detail-fact"><span>مدت تقریبی</span><strong>${escapeHTML(place.duration)}</strong></div>
        <div class="detail-fact"><span>استان</span><strong>${escapeHTML(place.province)}</strong></div>
        <div class="detail-fact"><span>نوع موجودیت</span><strong>${escapeHTML(place.type)}</strong></div>
        <div class="detail-fact"><span>سطح اعتبار</span><strong>${escapeHTML(place.status||"نیازمند بررسی")}</strong></div>
      </div>
      <h3 class="detail-subheading">راهنمای بازدید</h3>
      <p class="detail-summary">${escapeHTML(place.visit||"شرایط بازدید پیش از سفر بررسی شوند.")}</p>
      <h3 class="detail-subheading">موضوعات مرتبط</h3>
      <div class="detail-tags">${(place.tags||[]).map(t => `<span>${escapeHTML(t)}</span>`).join("")}</div>
      <h3 class="detail-subheading">منابع و اعتبار اطلاعات</h3>
      <p class="detail-summary">اطلاعات این رکورد از API دریافت شده؛ پیش از استفادهٔ عملی، از منابع معتبر تأیید کنید.</p>
      <div class="detail-actions">
        <button class="primary-btn" id="detail-save">${icon("bookmark")} ${saved?"حذف از ذخیره‌شده‌ها":"ذخیره برای سفر"}</button>
        <button class="secondary-btn" id="detail-visited">${icon("check")} ${visited?"لغو ثبت بازدید":"علامت‌گذاری به‌عنوان بازدیدشده"}</button>
        <button class="secondary-btn" id="detail-share">${icon("share")} اشتراک‌گذاری</button>
        <button class="secondary-btn" id="detail-report">${icon("alert")} گزارش اصلاح</button>
      </div>
    </div>`;
  $("#detail-save").addEventListener("click", () => toggleSaved(id));
  $("#detail-visited").addEventListener("click", () => toggleVisited(id));
  $("#detail-share").addEventListener("click", () => sharePlace(place));
  $("#detail-report").addEventListener("click", () => { closeModal($("#detail-modal")); openSubmitModal("report", place); });
  if (open) openModal("detail-modal");
}

async function sharePlace(place) {
  const shareData = { title:`${place.name} | گردشگری ایران`, text:`${place.name}، استان ${place.province} — ${place.short}`, url:window.location.href.split("#")[0]+"#atlas" };
  try {
    if (navigator.share) await navigator.share(shareData);
    else if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(`${shareData.title}\n${shareData.text}\n${shareData.url}`);
      toast("متن معرفی کپی شد.");
    } else toast("اشتراک‌گذاری در این مرورگر در دسترس نیست.", "error");
  } catch (e) { if (e && e.name !== "AbortError") toast("اشتراک‌گذاری انجام نشد.", "error"); }
}

/* ===== Submissions ===== */
function openSubmitModal(mode="new", place=null) {
  state.submitMode = mode; state.reportTarget = place;
  const title = $("#submit-modal-title"), submitBtn = $("#submit-submit-btn");
  const nameInput = $("#submission-name"), provinceSel = $("#submission-province"), typeSel = $("#submission-type");
  if (mode === "report") {
    title.textContent = "گزارش خطا یا اصلاح اطلاعات";
    submitBtn.innerHTML = `${icon("send")} ارسال گزارش`;
    nameInput.value = place?.name || ""; nameInput.readOnly = true;
    provinceSel.value = place?.province || ""; provinceSel.disabled = true;
    typeSel.required = false;
    $("#submission-reason").value = place ? `درخواست بررسی یا اصلاح اطلاعات مربوط به «${place.name}».\n\nتوضیح مشکل:` : "";
    $("#submission-source").value = ""; $("#submission-access").value = "";
    $("#submission-locality").value = place?.locality || "";
    $("#submission-lat").value = place && Number.isFinite(place.lat) ? place.lat : "";
    $("#submission-lng").value = place && Number.isFinite(place.lng) ? place.lng : "";
  } else {
    title.textContent = "پیشنهاد ثبت یک جاذبه";
    submitBtn.innerHTML = `${icon("send")} ثبت پیشنهاد`;
    $("#submit-form").reset(); nameInput.readOnly = false;
    provinceSel.disabled = false; typeSel.required = true;
  }
  openModal("submit-modal");
}

/* ===== Discover ===== */
function discoverRandom() {
  const candidates = getFilteredPlaces();
  if (!candidates.length) { toast("با فیلترهای فعلی جاذبه‌ای برای پیشنهاد نیست.", "error"); return; }
  const pool = candidates.filter(p => p.id !== state.selectedId);
  const pick = (pool.length ? pool : candidates)[Math.floor(Math.random() * (pool.length ? pool.length : candidates.length))];
  selectPlace(pick.id, true); openPlaceDetails(pick.id);
  toast(`پیشنهاد کشف: ${pick.name}`);
}

/* ===== Passport ===== */
function updatePassportStats() {
  const s = $("#passport-saved-count"), v = $("#passport-visited-count"), p = $("#passport-province-count");
  if (s) s.textContent = `${formatNumber(state.saved.size)} جاذبه`;
  if (v) v.textContent = `${formatNumber(state.visited.size)} جاذبه`;
  const provinces = new Set(state.places.filter(x => state.visited.has(x.id)).map(x => x.province));
  if (p) p.textContent = `${formatNumber(provinces.size)} استان`;
  const list = $("#passport-list"); if (!list) return;
  const personal = state.places.filter(x => state.saved.has(x.id) || state.visited.has(x.id));
  if (!personal.length) {
    list.innerHTML = `<div class="empty-state" style="padding:25px 10px">${icon("bookmark")}<strong>فهرست تو خالی است</strong><span>از روی نقشه جاذبه‌ای را ذخیره کن.</span></div>`;
    return;
  }
  list.innerHTML = personal.map(x => `
    <div style="display:flex;align-items:center;gap:11px;padding:12px 0;border-bottom:1px solid var(--line)">
      <img src="${escapeHTML(x.image)}" alt="" loading="lazy" style="width:55px;height:55px;object-fit:cover;border-radius:10px;background:#e7ece4">
      <div style="flex:1;min-width:0">
        <strong style="display:block;font-size:12px">${escapeHTML(x.name)}</strong>
        <span style="display:block;font-size:10px;color:var(--muted)">${escapeHTML(x.province)} · ${escapeHTML(x.category)}</span>
        <span style="display:block;font-size:10px;color:var(--forest-3);margin-top:3px">
          ${state.saved.has(x.id)?"ذخیره‌شده":""}${state.saved.has(x.id)&&state.visited.has(x.id)?" · ":""}${state.visited.has(x.id)?"بازدیدشده":""}
        </span>
      </div>
      <button class="icon-btn" type="button" data-passport-detail="${escapeHTML(x.id)}">${icon("arrow-left")}</button>
    </div>`).join("");
  $$("[data-passport-detail]", list).forEach(b => b.addEventListener("click", () => {
    closeModal($("#passport-modal")); openPlaceDetails(b.dataset.passportDetail);
  }));
}

function exportPassport() {
  const items = state.places.filter(x => state.saved.has(x.id) || state.visited.has(x.id));
  const content = ["گذرنامه دیجیتال ایران","",...items.map(x => [
    x.name,`استان: ${x.province}`,`موضوع: ${x.category}`,
    `ذخیره‌شده: ${state.saved.has(x.id)?"بله":"خیر"}`,
    `بازدیدشده: ${state.visited.has(x.id)?"بله":"خیر"}`,
    `معرفی: ${x.short}`,""
  ].join("\n"))].join("\n");
  const blob = new Blob(["\uFEFF", content], { type:"text/plain;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a"); a.href=url; a.download="iran-tourism-passport.txt";
  document.body.appendChild(a); a.click(); a.remove(); URL.revokeObjectURL(url);
  toast("فهرست متنی آماده شد.");
}

/* ===== Dynamic UI (provinces, quick grid, selects) ===== */
function renderDynamicUI() {
  // Province cards
  const grid = $("#provinces-grid");
  if (grid) {
    grid.innerHTML = Object.keys(PROVINCES).map(p => `
      <button class="province-card" data-province="${escapeHTML(p)}" style="--province-image:url('${provinceImages[p]||""}')">
        <span class="province-inner"><strong>${escapeHTML(p)}</strong><span>${escapeHTML(p)}</span></span>
      </button>`).join("");
    $$("[data-province]", grid).forEach(b => b.addEventListener("click", () => setProvince(b.dataset.province)));
  }
  // Province selects
  const optionsHTML = Object.keys(PROVINCES).map(p => `<option value="${escapeHTML(p)}">${escapeHTML(p)}</option>`).join("");
  ["#filter-province","#submission-province","#advanced-province"].forEach(sel => {
    const el = $(sel); if (el) el.insertAdjacentHTML("beforeend", optionsHTML);
  });
  // Quick grid
  const quick = $("#quick-grid");
  if (quick) {
    const items = [
      ["طبیعی","mountain","طبیعت و چشم‌انداز"], ["تاریخی","landmark","تاریخ و باستان‌شناسی"],
      ["زمین‌شناسی","waves","زمین‌شناسی"], ["فرهنگی","users","فرهنگ و آیین‌ها"],
      ["خوراک","utensils","خوراک و مزه‌های بومی"], ["مسیر","route","مسیرها و ماجراجویی"]
    ];
    quick.innerHTML = items.map(([cat,ic,label]) => `
      <button class="quick-item" data-category="${escapeHTML(cat)}">
        <span class="quick-icon">${icon(ic)}</span><span>${escapeHTML(label)}</span>
      </button>`).join("");
    $$(".quick-item", quick).forEach(b => b.addEventListener("click", () =>
      setCategory(state.category === b.dataset.category ? "" : b.dataset.category)));
  }
}

/* ===== Event bindings ===== */
function bindEvents() {
  $("#hero-search-form").addEventListener("submit", e => { e.preventDefault(); setSearch($("#hero-search-input").value); });
  $("#map-search-form").addEventListener("submit", e => { e.preventDefault(); setSearch($("#map-search-input").value); });
  let searchDebounce;
  $("#map-search-input").addEventListener("input", e => {
    clearTimeout(searchDebounce);
    const v = e.target.value;
    searchDebounce = setTimeout(() => { state.query = v; state.showSavedOnly = false; syncFilterControls(); renderAll(); }, 180);
  });
  $("#hero-search-input").addEventListener("input", e => { if (!e.target.value) { state.query = ""; syncFilterControls(); renderAll(); } });
  $$("[data-query]").forEach(b => b.addEventListener("click", () => setSearch(b.dataset.query)));
  $("#focus-search").addEventListener("click", () => { $("#hero-search-input").focus(); window.scrollTo({ top:0, behavior:"smooth" }); });
  $$("[data-category-link]").forEach(b => b.addEventListener("click", () => setCategory(b.dataset.categoryLink)));
  $$("[data-collection]").forEach(b => b.addEventListener("click", () => setCategory(b.dataset.collection)));
  $("#all-provinces").addEventListener("click", () => { state.province = ""; syncFilterControls(); renderAll(); $("#atlas").scrollIntoView({ behavior:"smooth" }); });
  $("#filter-category").addEventListener("change", e => { state.category = e.target.value; state.showSavedOnly = false; syncFilterControls(); renderAll(); });
  $("#filter-province").addEventListener("change", e => { state.province = e.target.value; state.showSavedOnly = false; syncFilterControls(); renderAll(); });
  $("#sort-results").addEventListener("change", e => { state.sort = e.target.value; renderAll(); });
  $("#open-filters").addEventListener("click", () => openModal("filters-modal"));
  $("#advanced-filter-form").addEventListener("submit", e => {
    e.preventDefault();
    state.category = $("#advanced-category").value;
    state.province = $("#advanced-province").value;
    state.difficulty = $("#advanced-difficulty").value;
    state.season = $("#advanced-season").value;
    state.duration = $("#advanced-duration").value;
    state.status = $("#advanced-status").value;
    state.showSavedOnly = false; syncFilterControls(); renderAll();
    closeModal($("#filters-modal")); toast("فیلترها اعمال شدند.");
  });
  $("#clear-advanced-filters").addEventListener("click", () => { clearFilters(); closeModal($("#filters-modal")); toast("فیلترها پاک شدند."); });
  $("#reset-atlas").addEventListener("click", () => { clearFilters(); state.map?.flyTo([32.2,53.5],5,{duration:.5}); toast("نمای اطلس بازنشانی شد."); });
  $("#map-zoom-in").addEventListener("click", () => state.map?.zoomIn());
  $("#map-zoom-out").addEventListener("click", () => state.map?.zoomOut());
  $("#map-reset-view").addEventListener("click", () => state.map?.flyTo([32.2,53.5],5,{duration:.5}));
  $("#map-layer-toggle").addEventListener("click", () => {
    if (!state.map) { toast("نقشه در دسترس نیست.", "error"); return; }
    state.mapLayer = state.mapLayer === "neshan" ? "dreamy" : "neshan";
    $("#map-layer-toggle").classList.toggle("active", state.mapLayer === "dreamy");
    toast(state.mapLayer === "dreamy" ? "لایه Dreamy فعال شد." : "لایه نشان فعال شد.");
  });
  document.addEventListener("click", e => {
    const btn = e.target.closest("[data-map-detail]");
    if (btn) openPlaceDetails(btn.dataset.mapDetail);
  });
  $("#show-saved").addEventListener("click", () => {
    state.showSavedOnly = !state.showSavedOnly;
    Object.assign(state, { category:"", province:"", query:"" });
    syncFilterControls(); renderAll();
    if (state.showSavedOnly && !state.saved.size) toast("هنوز جاذبه‌ای ذخیره نکرده‌ای.", "error");
  });
  ["random-discover","discover-random-main","hero-discover"].forEach(id => $("#"+id).addEventListener("click", discoverRandom));
  $("#open-submit").addEventListener("click", () => openSubmitModal("new"));
  $("#footer-submit").addEventListener("click", e => { e.preventDefault(); openSubmitModal("new"); });
  $("#footer-report").addEventListener("click", e => { e.preventDefault(); openSubmitModal("report", null); });
  $("#submit-form").addEventListener("submit", handleSubmit);
  $("#open-passport").addEventListener("click", () => { updatePassportStats(); openModal("passport-modal"); });
  $("#export-passport").addEventListener("click", exportPassport);
  $("#newsletter-form").addEventListener("submit", async e => {
    e.preventDefault();
    const form = e.currentTarget, email = (form.querySelector("input[type=email]")?.value || "").trim();
    if (!email) return;
    const btn = form.querySelector("button[type=submit]"); const orig = btn?.innerHTML || "";
    if (btn) { btn.disabled = true; btn.innerHTML = "…"; }
    try {
      await apiJson("/newsletter", { method:"POST", headers:{ "Content-Type":"application/json" }, body:JSON.stringify({ email }) });
      toast("ایمیل شما با موفقیت ثبت شد."); form.reset();
    } catch (e) { toast(e.message || "ثبت ایمیل ناموفق بود.", "error"); }
    finally { if (btn) { btn.disabled = false; btn.innerHTML = orig; } }
  });
  $("#footer-privacy").addEventListener("click", e => { e.preventDefault(); toast("سیاست حریم خصوصی در نسخهٔ عملیاتی ارائه می‌شود."); });
  $("#menu-toggle").addEventListener("click", () => {
    const nav = $("#main-nav"), exp = $("#menu-toggle").getAttribute("aria-expanded") === "true";
    nav.classList.toggle("open", !exp);
    $("#menu-toggle").setAttribute("aria-expanded", String(!exp));
  });
  $$("#main-nav a").forEach(a => a.addEventListener("click", () => {
    $("#main-nav").classList.remove("open");
    $("#menu-toggle").setAttribute("aria-expanded", "false");
  }));
  $("#iran-map").addEventListener("keydown", e => { if (e.key === "Escape" && state.map) state.map.closePopup(); });
  window.addEventListener("resize", () => { if (state.map) setTimeout(() => state.map.invalidateSize(), 150); });
  window.addEventListener("hashchange", () => { if (state.map) setTimeout(() => state.map.invalidateSize(), 100); });
}

async function handleSubmit(event) {
  event.preventDefault();
  const form = event.currentTarget, data = new FormData(form), submitBtn = $("#submit-submit-btn");
  const latRaw = String(data.get("lat")||"").trim(), lngRaw = String(data.get("lng")||"").trim();
  const lat = latRaw === "" ? null : Number(latRaw), lng = lngRaw === "" ? null : Number(lngRaw);

  if (state.submitMode === "report") {
    const reason = String(data.get("reason")||"").trim();
    if (!reason) { toast("توضیح مشکل را وارد کن.", "error"); return; }
    const orig = submitBtn.innerHTML; submitBtn.disabled = true; submitBtn.innerHTML = "در حال ارسال…";
    try {
      await apiJson("/submissions/report", { method:"POST", headers:{ "Content-Type":"application/json" }, body:JSON.stringify({
        name:data.get("name"), province:data.get("province"), locality:data.get("locality"),
        lat, lng, reason, source:data.get("source"), access:data.get("access"),
        placeId:state.reportTarget?.id || null
      }) });
      toast("گزارش شما ثبت شد."); form.reset();
      state.submitMode = "new"; state.reportTarget = null;
      closeModal($("#submit-modal"));
    } catch (e) { toast(e.message || "ارسال ناموفق بود.", "error"); }
    finally { submitBtn.disabled = false; submitBtn.innerHTML = orig; }
    return;
  }

  const name = String(data.get("name")||"").trim(), type = String(data.get("type")||"").trim(), reason = String(data.get("reason")||"").trim();
  if (!name || !type || !reason) { toast("فیلدهای ضروری را کامل کن.", "error"); return; }
  if ((latRaw && !Number.isFinite(lat)) || (lngRaw && !Number.isFinite(lng))) { toast("مختصات معتبر نیست.", "error"); return; }
  if ((lat === null) !== (lng === null)) { toast("هر دو مقدار مختصات لازم است.", "error"); return; }

  const orig = submitBtn.innerHTML; submitBtn.disabled = true; submitBtn.innerHTML = "در حال ارسال…";
  try {
    const newPlace = await apiJson("/submissions", { method:"POST", headers:{ "Content-Type":"application/json" }, body:JSON.stringify({
      name, type, province:data.get("province"), locality:data.get("locality"),
      lat, lng, reason, source:data.get("source"), access:data.get("access")
    }) });
    state.places.unshift(newPlace);
    Object.assign(state, { showSavedOnly:false, query:"", category:"", province:"", selectedId:newPlace.id });
    syncFilterControls(); renderAll();
    form.reset(); closeModal($("#submit-modal"));
    toast("پیشنهاد ثبت شد و با وضعیت «نیازمند بررسی» ثبت گردید.");
    if (Number.isFinite(newPlace.lat) && Number.isFinite(newPlace.lng) && state.map) {
      $("#atlas").scrollIntoView({ behavior:"smooth" }); selectPlace(newPlace.id, true);
    }
  } catch (e) { toast(e.message || "ثبت ناموفق بود.", "error"); }
  finally { submitBtn.disabled = false; submitBtn.innerHTML = orig; }
}

/* ===== Bootstrap ===== */
async function loadPlacesFromApi() {
  const page = await apiJson("/places?size=10000");
  if (!page || !Array.isArray(page.items)) throw new Error("پاسخ API نامعتبر است.");
  return page.items;
}
async function loadPassportFromApi() {
  const [saved, visited] = await Promise.all([
    apiJson("/passport/saved", { headers:clientHeaders() }).catch(() => null),
    apiJson("/passport/visited", { headers:clientHeaders() }).catch(() => null)
  ]);
  saved?.forEach?.(p => state.saved.add(p.id));
  visited?.forEach?.(p => state.visited.add(p.id));
}

async function initApp() {
  const yearEl = $("#current-year"); if (yearEl) yearEl.textContent = new Date().getFullYear();
  $("#results-list").innerHTML = `<div class="empty-state"><span>در حال بارگذاری جاذبه‌ها…</span></div>`;
  try {
    state.places = await loadPlacesFromApi(); state.apiOnline = true;
  } catch (err) {
    console.warn("[API] بارگذاری جاذبه‌ها ناموفق بود، استفاده از دادهٔ آفلاین:", err);
    state.places = buildPlaces(); state.apiOnline = false;
    toast("اتصال به سرور برقرار نشد؛ نسخهٔ آفلاین بارگذاری شد.", "error");
  }
  state.places.forEach(p => { if (!p.color) p.color = ICONS.natural; });
  if (state.apiOnline) { try { await loadPassportFromApi(); } catch (err) { console.warn(err); } }
  const heroCount = $("#hero-place-count"); if (heroCount) heroCount.textContent = formatNumber(state.places.length);
  renderDynamicUI();
  bindEvents();
  syncFilterControls();
  renderAll();
  initMap();
}

if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", initApp);
else initApp();
