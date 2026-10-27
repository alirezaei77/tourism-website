# tourism-website
Tourist attractions in Iran are being collected on this website.
# 🌏 Iran Tourism — Smart Atlas of Iran's Attractions

> An interactive, map-driven atlas for discovering Iran's natural, historical, cultural, and lesser-known attractions — backed by a lightweight server with **zero external dependencies** and runnable on **JDK 11+**.

---

## 📖 Table of Contents

- [Project Overview](#-project-overview)
- [Features](#-features)
- [Architecture](#-architecture)
- [Prerequisites](#-prerequisites)
- [Installation & Running](#-installation--running)
- [Project Structure](#-project-structure)
- [Configuration](#-configuration)
- [API Reference](#-api-reference)
- [Data Model](#-data-model)
- [User Guide](#-user-guide)
- [Development & Customization](#-development--customization)
- [Limitations & Security Notes](#-limitations--security-notes)
- [Roadmap](#-roadmap)
- [Contributing](#-contributing)
- [License](#-license)

---

## 🎯 Project Overview

**Iran Tourism** is a full-stack project implemented in just **two independent files**:

| Layer | File | Description |
|-------|------|-------------|
| **Frontend** | `index.html` | A Single Page Application (SPA) built with pure HTML/CSS/JS — no frameworks |
| **Backend** | `IranTourismServer.java` | A lightweight HTTP server built on `com.sun.net.httpserver` with **no external dependencies** |

The goal of the project is to provide an **interactive atlas platform** for registering, searching, and discovering tourism attractions across Iran, with strong emphasis on:

- **Documentation accuracy** — each record carries a validity status, source references, and visit warnings
- **Responsible discovery** — respecting heritage and nature protection
- **Accessibility** — ARIA roles, keyboard navigation, and focus management
- **Offline resilience** — a complete fallback dataset when the API is unavailable

---

## ✨ Features

### 🗺️ Map & Atlas
- Interactive map powered by **Leaflet** and the **Neshan SDK**
- Marker clustering with custom styling
- Color-coded markers per attraction category (Natural, Historical, Geological, Cultural, Food, Route)
- Map-based search, zoom controls, reset view, and on-map legend
- Rich popups with direct links to the detail view

### 🔍 Search & Filtering
- Text search with Persian normalization (unifying ی/ي, ک/ك; stripping diacritics and ZWNJ)
- Multi-dimensional filters: category, province, accessibility difficulty, season, visit duration, and data status
- Sort options: name, province, or accessibility difficulty
- Active filter chips with individual removal
- Progressive result loading (lazy pagination)

### 📍 Attractions
- All **31 provinces** represented with a rich sample dataset (400+ records)
- Attraction cards showing image, type, category, and location
- Detailed modal with visit guidance, tags, warnings, and source notes
- Sharing via the Web Share API with clipboard fallback

### 👤 Digital Passport
- Save favorite attractions
- Mark attractions as visited
- Count discovered provinces
- Export a plain-text summary of the passport
- Server-side persistence keyed by a **stable Client ID** stored in `localStorage`

### 📝 User Contributions
- Submission form for proposing new attractions
- Report/Correction form for flagging errors in existing records
- Input validation (coordinates, required fields)

### 🎨 Design & UX
- Full **RTL** layout using **Vazirmatn** font
- Nature-inspired color palette (Forest / Lime / Cream)
- Fully responsive with breakpoints at 1180 / 900 / 560 px
- Honors `prefers-reduced-motion`
- Modal focus management (focus trap) with restoration of the previously focused element

### 🛡️ Resilience & Stability
- **Offline fallback**: if the API is unavailable, the frontend loads an offline dataset embedded directly in the HTML
- **Optimistic UI** for save/visit actions with rollback on error
- **Atomic file writes** (`ATOMIC_MOVE`) in the backend

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     Client (Browser)                    │
│  ┌───────────────────────────────────────────────────┐  │
│  │             index.html (SPA + Leaflet)            │  │
│  │  • UI / UX                                        │  │
│  │  • State (filters, saved, visited)                │  │
│  │  • Neshan Map + Marker Clustering                 │  │
│  │  • API Client (fetch + X-Client-Id)               │  │
│  │  • Offline fallback via RAW_ATTRACTIONS           │  │
│  └───────────────────────────────────────────────────┘  │
└──────────────────────────┬──────────────────────────────┘
                           │  HTTP/JSON
                           ▼
┌─────────────────────────────────────────────────────────┐
│              IranTourismServer (JDK 11+)                │
│  ┌───────────────────────────────────────────────────┐  │
│  │  Routes:                                          │  │
│  │   GET    /                    → index.html         │  │
│  │   GET    /api/health                               │  │
│  │   GET    /api/places          → list + filters     │  │
│  │   GET    /api/places/{id}                          │  │
│  │   GET    /api/provinces                            │  │
│  │   GET    /api/passport/{saved|visited}             │  │
│  │   POST   /api/passport/{saved|visited}/{placeId}   │  │
│  │   DELETE /api/passport/{saved|visited}/{placeId}   │  │
│  │   POST   /api/submissions                          │  │
│  │   POST   /api/submissions/report                   │  │
│  │   POST   /api/newsletter                           │  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
│  Persistence (data/):                                   │
│   • places.json                                         │
│   • passport.json                                       │
│   • submissions.json                                    │
│   • newsletter.json                                     │
└─────────────────────────────────────────────────────────┘
```

**Key design note:** On first run, if neither `places.json` nor `places-seed.json` exists, the backend attempts to **extract the attraction dataset directly from `index.html`** (from the `RAW_ATTRACTIONS` block) and persists it to `data/places.json`. This makes a single self-contained HTML file act as both the frontend and the seed source for the server.

---

## 🧰 Prerequisites

### Backend
- **JDK 11 or later** (no need for Maven/Gradle)
- OS: Windows / Linux / macOS

### Frontend
- Any modern browser (Chrome / Firefox / Safari / Edge)
- **Internet access** to load:
  - Vazirmatn font (Google Fonts)
  - Neshan SDK (map)
  - Leaflet MarkerCluster (CDN)
  - Unsplash and Placehold images

> ⚠️ If offline, the UI will still load, but the **map** and **fonts** may not render correctly.

---

## 🚀 Installation & Running

### 1) Clone or download the project

```bash
git clone <repo-url>
cd iran-tourism
```

### 2) Place the files in a single folder

```
iran-tourism/
├── IranTourismServer.java
└── index.html
```

### 3) Compile and run

```bash
javac IranTourismServer.java
java IranTourismServer
```

On successful startup you will see:

```
============================================
  Iran Tourism Server
  URL        : http://localhost:8080/
  API        : http://localhost:8080/api
  Base dir   : /path/to/iran-tourism
  Data dir   : /path/to/iran-tourism/data
  Places     : 412
  Java       : 17.0.9
============================================
```

### 4) Open in the browser

```
http://localhost:8080/
```

### Changing the port

```bash
# Via command-line argument
java IranTourismServer --port=9090

# Or via environment variable
PORT=9090 java IranTourismServer
```

---

## 📁 Project Structure

```
iran-tourism/
├── IranTourismServer.java     # Single-file backend (zero dependencies)
├── index.html                 # SPA frontend + offline dataset
└── data/                      # Created automatically
    ├── places.json            # Attraction data (extracted from HTML or seed)
    ├── passport.json          # Saved/visited per Client ID
    ├── submissions.json       # User submissions and reports
    └── newsletter.json        # Newsletter email subscriptions
```

### Optional files

- `places-seed.json` — if present, takes precedence over HTML extraction (an array of Place objects)
- `public/index.html` — alternative path for the frontend

---

## ⚙️ Configuration

### Frontend (`index.html`)

| Variable | Location | Description |
|----------|----------|-------------|
| `API_BASE` | Top of the script | Base URL of the API (default: `/api`) |
| `NESHAN_API_KEY` | Main script | Neshan map API key |
| `CLIENT_ID` | `localStorage` | A stable unique identifier per user |
| `RESULTS_PAGE_SIZE` | Script | Number of results per pagination step (default: 50) |

To change the API base URL (e.g., in a split frontend/backend dev setup):

```html
<script>
  window.__API_BASE__ = "http://localhost:8080/api";
</script>
```

### Backend (`IranTourismServer.java`)

| Parameter | Default | Description |
|-----------|---------|-------------|
| `DEFAULT_PORT` | `8080` | Default port |
| `PORT_ENV` | `PORT` | Name of the port environment variable |
| `MAX_PAGE_SIZE` | `50000` | Maximum page size |
| `DEFAULT_PAGE_SIZE` | `1000` | Default page size |

---

## 📚 API Reference

### 🔹 `GET /api/health`

Server health check.

```json
{
  "status": "ok",
  "places": 412,
  "time": "2025-01-15T10:30:00Z"
}
```

### 🔹 `GET /api/places`

List attractions with full filtering.

**Query Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `query` | string | Text search (normalized) |
| `category` | string | Category: `طبیعی` / `تاریخی` / `زمین‌شناسی` / `فرهنگی` / `خوراک` / `مسیر` |
| `province` | string | Province name |
| `difficulty` | string | `آسان` / `متوسط` / `دشوار` / `نامشخص` |
| `season` | string | `بهار` / `تابستان` / `پاییز` / `زمستان` / `چهارفصل` |
| `duration` | string | `کوتاه` / `نیم‌روز` / `روزانه` |
| `status` | string | `نمونه نمایشی` / `تأییدشده` / `نیازمند بررسی` |
| `sort` | string | `default` / `name` / `province` / `difficulty` |
| `savedOnly` | boolean | Only items saved by the current Client ID |
| `page` | int | Page number (zero-based) |
| `size` | int | Page size |

**Request headers:**
- `X-Client-Id` (optional): required if `savedOnly=true`

**Response:**

```json
{
  "items": [ { "...Place..." } ],
  "total": 412,
  "page": 0,
  "size": 1000
}
```

The response also includes an `X-Total-Count` header with the total item count.

### 🔹 `GET /api/places/{id}`

Retrieve a single attraction. Returns `404` if not found.

### 🔹 `GET /api/provinces`

Sorted list of provinces.

```json
["آذربایجان شرقی", "آذربایجان غربی", "...", "یزد"]
```

### 🔹 Passport — `GET/POST/DELETE /api/passport/{saved|visited}[/{placeId}]`

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/passport/saved` | List saved attractions |
| `POST` | `/api/passport/saved/{placeId}` | Add to saved list |
| `DELETE` | `/api/passport/saved/{placeId}` | Remove from saved list |
| `GET` | `/api/passport/visited` | List visited attractions |
| `POST` | `/api/passport/visited/{placeId}` | Mark as visited |
| `DELETE` | `/api/passport/visited/{placeId}` | Unmark as visited |

> 🔑 The `X-Client-Id` header is **mandatory**. If missing → `400`.

### 🔹 `POST /api/submissions`

Submit a new attraction proposal.

```json
{
  "name": "Sample Waterfall",
  "type": "جاذبه طبیعی",
  "province": "لرستان",
  "locality": "خرم‌آباد",
  "lat": 33.5,
  "lng": 48.3,
  "reason": "Explanation of why this is worth featuring",
  "source": "Source or evidence",
  "access": "Access conditions"
}
```

**Success response (201):** The new `Place` object with `status: "نیازمند بررسی"` (Needs Review).

### 🔹 `POST /api/submissions/report`

Report an error or request a correction.

```json
{
  "name": "Attraction name",
  "placeId": "place-0042",
  "reason": "Description of the issue",
  "source": "...",
  "access": "..."
}
```

**Success response:** `204 No Content`

### 🔹 `POST /api/newsletter`

Register an email for the newsletter.

```json
{ "email": "user@example.com" }
```

**Success response:** `204 No Content`

### 🔹 Error responses

```json
{
  "timestamp": "2025-01-15T10:30:00Z",
  "status": 400,
  "message": "Error message in Persian"
}
```

---

## 🧩 Data Model

Every attraction (`Place`) has the following structure:

```json
{
  "id": "place-0001",
  "name": "Persepolis",
  "province": "Fars",
  "locality": "Marvdasht",
  "category": "تاریخی",
  "type": "Achaemenid Complex (World Heritage)",
  "lat": 29.9352,
  "lng": 52.8916,
  "short": "Short summary",
  "description": "Full description",
  "image": "https://...",
  "difficulty": "آسان",
  "season": "چهارفصل",
  "duration": "نیم‌روز",
  "status": "نمونه نمایشی",
  "tags": ["Achaemenid", "UNESCO"],
  "visit": "Visit guidance",
  "alert": "Important warning",
  "verified": false,
  "color": "#c09142"
}
```

### Constant values

| Field | Allowed values |
|-------|----------------|
| `category` | `طبیعی`, `تاریخی`, `زمین‌شناسی`, `فرهنگی`, `خوراک`, `مسیر` |
| `difficulty` | `آسان`, `متوسط`, `دشوار`, `نامشخص` |
| `season` | `بهار`, `تابستان`, `پاییز`, `زمستان`, `چهارفصل` |
| `duration` | `کوتاه`, `نیم‌روز`, `روزانه` |
| `status` | `نمونه نمایشی`, `تأییدشده`, `نیازمند بررسی` |

---

## 🖱️ User Guide

### For end users

1. **Search:** Use the hero search bar or the map search field.
2. **Random discovery:** Click "Surprise me" to open a random attraction.
3. **Advanced filters:** Click "Filters" at the top of the atlas.
4. **Save/Visit:** Use the bookmark icon on a card or in the detail modal.
5. **Passport:** Open "My List" to view your personal list and export it.
6. **Contribute:** Use "Submit Attraction" to propose a new place, or the "Report Correction" button inside the detail modal to flag an error.

### Keyboard shortcuts

| Key | Action |
|-----|--------|
| `/` | Focus the main search field |
| `Esc` | Close modal or close map popup |
| `Tab` / `Shift+Tab` | Navigate and cycle focus within modals |

---

## 🛠️ Development & Customization

### Adding a new attraction (quick way)

Attraction data lives inside `RAW_ATTRACTIONS` in `index.html`. Add a new entry under the relevant province:

```javascript
"Lorestan": [
  {
    name: "Sample Waterfall",
    cat: "طبیعی",
    type: "Waterfall",
    lat: 33.50, lng: 48.30,
    short: "Short description",
    tags: ["Waterfall", "Forest"],
    visit: "Visit guidance"
  }
]
```

Then delete `data/places.json` and restart the server — the data will be re-extracted from the HTML.

### Adding a new field to `Place`

1. Add the field to the `Place` class (Java).
2. Include it in `Place.toJson()` and `Place.fromJson()`.
3. Populate it in `extractFromHtml()`.
4. Display it in the frontend (card and modal).

### Changing visual styling

All design tokens are defined in `:root`:

```css
:root {
  --forest: #102c27;
  --lime: #c4dba5;
  --gold: #d5a85b;
  --font: "Vazirmatn", Tahoma, sans-serif;
  /* ... */
}
```

### Switching the map provider

The map service is initialized inside `initMap()`. To switch from Neshan to Mapbox or OpenStreetMap, you only need to modify the `new L.Map(...)` call and its tile layer.

---

## ⚠️ Limitations & Security Notes

### Design limitations

| Limitation | Description |
|------------|-------------|
| **Authentication** | Only via `X-Client-Id` (no real login). Production requires JWT or OAuth. |
| **Open CORS** | `Access-Control-Allow-Origin: *` — suitable for development, **not** for production. |
| **No rate limiting** | Vulnerable to bulk abuse. |
| **File-based storage** | Not suitable for high write volumes. A database is required at scale. |
| **No rich content validation** | Text fields are only length-limited. |
| **Attraction coordinates** | Some coordinates are approximate and intended for sample display only. |

### Pre-production recommendations

- [ ] Add **real authentication** (JWT) and associate passports with users
- [ ] Restrict **CORS** to specific domains
- [ ] Add **rate limiting** (e.g., per IP)
- [ ] Enforce **HTTPS** and secure response headers
- [ ] Migrate to a database (SQLite / PostgreSQL) for concurrent writes
- [ ] Build an admin panel to review `submissions`
- [ ] Integrate a **geocoding service** to validate coordinates
- [ ] Add **Cache-Control** and **compression**

---

## 🗺️ Roadmap

- [x] Interactive atlas with Neshan
- [x] Digital passport (save + visited)
- [x] Submission and report forms
- [x] Offline fallback
- [ ] Admin panel for reviewing submissions
- [ ] User login via OTP
- [ ] Multi-stop Trip Planner
- [ ] User reviews and ratings
- [ ] PWA (installability and full cache)
- [ ] Internationalization (English / Arabic)
- [ ] PDF / GPX export of the passport

---

## 🤝 Contributing

Contributions are welcome. To contribute:

1. **Fork** the repository
2. Create a new **branch**: `git checkout -b feature/my-feature`
3. **Commit** your changes: `git commit -m "feat: add new feature"`
4. **Push** to your fork: `git push origin feature/my-feature`
5. Open a **Pull Request**

### Coding standards

- **Java:** Standard style, no dependencies, JDK 11+ (pattern matching for `instanceof` is allowed).
- **HTML/JS:** ES2020+, no frameworks, ARIA and accessibility are mandatory.
- **Commit messages:** Follow [Conventional Commits](https://www.conventionalcommits.org/).

---

## 📄 License

This project is published for educational and prototyping purposes. Before commercial use, please coordinate with the project maintainer.

### External resources and dependencies

| Resource | Purpose | License |
|----------|---------|---------|
| [Leaflet](https://leafletjs.com/) | Map library | BSD-2-Clause |
| [Leaflet.markercluster](https://github.com/Leaflet/Leaflet.markercluster) | Marker clustering | MIT |
| [Neshan SDK](https://platform.neshan.org/) | Iranian map service | Proprietary |
| [Vazirmatn](https://github.com/rastikerdar/vazirmatn) | Persian font | SIL OFL 1.1 |
| [Unsplash](https://unsplash.com/) | Sample images | Unsplash License |
| [Placehold.co](https://placehold.co/) | Placeholder images | Free |

> ⚠️ **The Neshan API key** present in `index.html` is a public sample key. For production use, obtain your own key from the [Neshan Platform](https://platform.neshan.org/).

---

## 🙏 Acknowledgements

- The **Neshan team** for providing an Iranian map SDK
- The **Leaflet community** for open-source tooling
- **Saber Rastikerdar** for the beautiful Vazirmatn font
- And **everyone documenting Iran's attractions** — your work makes this project possible

---

<div align="center">

**🇮🇷 Responsible discovery, deeper understanding**

*Built with ❤️ for Iran*

</div>
