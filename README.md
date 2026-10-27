# README.md

```markdown
# 🧭 Iran Tourism Atlas

> **An intelligent, interactive web-based atlas for discovering Iran's natural, historical, cultural, and lesser-known attractions.**

[![Made with](https://img.shields.io/badge/made%20with-HTML%20%7C%20CSS%20%7C%20JS%20%7C%20Java-blue)](https://)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Status](https://img.shields.io/badge/status-demo-orange.svg)]()

---

## 📖 Overview

**Iran Tourism Atlas** is a full-stack, map-centric web application that provides an interactive atlas of Iran's attractions. It pairs a lightweight vanilla JavaScript frontend (HTML5 + CSS3) with a Spring Boot REST API backend. The interface is designed entirely in **Persian (RTL)** with a modern, accessible, and responsive layout.

The project intentionally avoids front-end frameworks and third-party libraries like jQuery, keeping the frontend performant and dependency-light, while the Java backend handles data persistence, filtering, user passports (saved/visited lists), and user submissions.

---

## ✨ Features

### 🗺️ Interactive Map (Neshan SDK)
- Full-screen Leaflet-based map powered by the **Neshan Maps API**
- Cluster-based marker rendering with category-aware colors and icons
- Zoom controls, layer toggling, and "reset view" shortcut
- Floating map search bar with debounced live results
- Legend for attraction categories (natural, historical, geological, cultural)

### 🔍 Discovery & Filtering
- Full-text search over name, province, locality, category, type, tags, and description
- Filter chips with the ability to remove individual active filters
- Advanced filter modal (category, province, difficulty, season, duration, status)
- Sortable result list (name, province, accessibility)
- Debounced progressive loading (50 results per page chunk)
- Randomized "Discover" feature that surfaces lesser-known attractions

### 📍 Attraction Details
- Rich modal with cover image, verified-status badge, and alert banners
- Accessibility, season, duration, province, and entity type facts
- Guided-visit tips and relevance tags
- Share via Web Share API (fallback to clipboard)
- Inline report-and-correct workflow

### 🧳 Digital Passport
- Save/unsave attractions per user (identified by a browser-scoped `X-Client-Id`)
- Mark attractions as visited
- Live stats: saved count, visited count, provinces discovered
- Persistent state loaded from the API on startup
- Plain-text export of the passport (`iran-tourism-passport.txt`)

### 📝 User Contributions
- **Submit new attractions** with name, type, province, locality, coordinates, reason, source, and access notes
- **Report corrections** on existing attractions
- Submissions are stored with `نیازمند بررسی` ("pending review") status

### 🎨 Design
- Persian (RTL) typography with **Vazirmatn** font
- Custom icon system built with inline SVG `<symbol>` sprites
- Fully responsive (mobile, tablet, desktop breakpoints)
- `prefers-reduced-motion` support
- Accessible modals with focus trapping and keyboard shortcuts

### 🌐 Resilience
- Automatic **offline fallback**: if the API is unreachable, the frontend loads a curated static dataset (`RAW_ATTRACTIONS`)
- Optimistic UI updates for save/visit operations

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────┐
│                    Browser (RTL)                    │
│  ┌───────────────────────────────────────────────┐  │
│  │  index.html  ←  style.css  ←  app.js          │  │
│  │                              ↑                │  │
│  │                        data.js (fallback)     │  │
│  └───────────────────────────────────────────────┘  │
│                 │ fetch (X-Client-Id)               │
└─────────────────┼───────────────────────────────────┘
                  │
        ┌─────────▼─────────┐
        │  Spring Boot API  │
        │  (in-memory store)│
        └───────────────────┘
```

### Project Structure

```
iran-tourism-atlas/
├── frontend/
│   ├── index.html
│   ├── css/
│   │   └── style.css
│   └── js/
│       ├── data.js        # Static dataset + buildPlaces()
│       └── app.js         # Application logic
│
└── backend/
    ├── pom.xml
    └── src/main/
        ├── java/com/irantourism/
        │   ├── IranTourismApplication.java
        │   ├── config/
        │   │   ├── CorsConfig.java
        │   │   └── DataSeeder.java        # optional: seeds from JSON
        │   ├── model/
        │   │   ├── Place.java
        │   │   ├── Submission.java
        │   │   └── Newsletter.java
        │   ├── store/
        │   │   └── InMemoryStore.java
        │   └── controller/
        │       ├── PlaceController.java
        │       ├── PassportController.java
        │       ├── SubmissionController.java
        │       └── NewsletterController.java
        └── resources/
            ├── application.properties
            └── seed/
                └── places.json            # optional bulk seed
```

---

## 🛠️ Tech Stack

| Layer       | Technologies                                                        |
|-------------|---------------------------------------------------------------------|
| **Frontend**| HTML5, CSS3 (custom properties, Grid, Flexbox), Vanilla JavaScript (ES2020+) |
| **Maps**    | Leaflet 1.9.4 + Neshan SDK 1.0.8 + Leaflet.markercluster 1.5.3      |
| **Typography** | Vazirmatn (Google Fonts)                                         |
| **Backend** | Java 17, Spring Boot 3.2, Spring Web, Spring Validation             |
| **Storage** | In-memory `ConcurrentHashMap` (swap for JPA/PostgreSQL in production) |
| **Build**   | Maven                                                               |

---

## 🚀 Getting Started

### Prerequisites
- **Node.js** (optional, only for static file serving)
- **JDK 17+**
- **Maven 3.8+**
- A modern browser with `fetch`, `crypto.randomUUID`, and `<dialog>`/modal support

### 1. Clone the repository

```bash
git clone https://github.com/your-username/iran-tourism-atlas.git
cd iran-tourism-atlas
```

### 2. Configure the map API key

The frontend uses the **Neshan** map service. The key is defined in `frontend/js/app.js`:

```javascript
const NESHAN_API_KEY = "web.624be8c281c1439a8183326986ac35a0";
```

> ⚠️ **Important:** Replace this with your own Neshan API key. Obtain one for free at [neshan.org](https://platform.neshan.org/).

### 3. Run the backend

```bash
cd backend
mvn spring-boot:run
```

The API will start on **`http://localhost:8080`**.

### 4. Serve the frontend

```bash
cd frontend
python3 -m http.server 3000
# or: npx serve .
```

Then open **`http://localhost:3000`**.

> 💡 The frontend expects the API at `/api`. If you run the frontend on a different origin, configure a dev proxy or set `window.__API_BASE__ = "http://localhost:8080/api"` before `app.js` loads.

### 5. (Optional) Seed bulk data

```bash
curl -X POST http://localhost:8080/api/places/bulk \
  -H "Content-Type: application/json" \
  --data-binary @backend/src/main/resources/seed/places.json
```

---

## 🔌 API Reference

All endpoints are prefixed with `/api`. Mutating endpoints on `/passport/*` require the `X-Client-Id` header.

### Places

| Method | Path                      | Description                          |
|--------|---------------------------|--------------------------------------|
| `GET`  | `/api/places`             | List places (paginated)              |
| `GET`  | `/api/places/{id}`        | Get a single place                   |
| `POST` | `/api/places/bulk`        | Bulk insert/upsert places            |

**Example** — `GET /api/places?page=0&size=20`

```json
{
  "items": [
    {
      "id": "place-0001",
      "name": "جنگل‌های ارسباران",
      "province": "آذربایجان شرقی",
      "category": "طبیعی",
      "lat": 38.92,
      "lng": 47.06,
      "shortDescription": "پهنهٔ ۱۶۴ هزار هکتاری...",
      "tags": ["جنگل", "تنوع زیستی", "یونسکو"],
      "status": "نمونه نمایشی"
    }
  ],
  "total": 412,
  "page": 0,
  "size": 20
}
```

### Passport (Saved / Visited)

| Method   | Path                                  | Description                          |
|----------|---------------------------------------|--------------------------------------|
| `GET`    | `/api/passport/saved`                 | List saved places for this client    |
| `GET`    | `/api/passport/visited`               | List visited places for this client  |
| `POST`   | `/api/passport/saved/{placeId}`       | Add a place to the saved list        |
| `DELETE` | `/api/passport/saved/{placeId}`       | Remove a place from the saved list   |
| `POST`   | `/api/passport/visited/{placeId}`     | Mark a place as visited              |
| `DELETE` | `/api/passport/visited/{placeId}`     | Unmark a place as visited            |

**Header required:** `X-Client-Id: <uuid>` (generated automatically by the frontend and stored in `localStorage`).

### Submissions

| Method | Path                       | Description                        |
|--------|----------------------------|------------------------------------|
| `POST` | `/api/submissions`         | Submit a new attraction            |
| `POST` | `/api/submissions/report`  | Report an error on existing place  |
| `GET`  | `/api/submissions`         | List all submissions (admin)       |

**Example** — `POST /api/submissions`

```json
{
  "name": "آبشار نمونه",
  "type": "جاذبه طبیعی",
  "province": "لرستان",
  "locality": "خرم‌آباد",
  "lat": 33.49,
  "lng": 48.35,
  "reason": "چشم‌انداز کم‌نظیر و دسترسی نسبتاً آسان",
  "source": "https://example.com/source",
  "access": "با هماهنگی محیط‌زیست"
}
```

### Newsletter

| Method | Path               | Description           |
|--------|--------------------|-----------------------|
| `POST` | `/api/newsletter`  | Subscribe an email    |

---

## 🎨 UI Structure

| Section             | Purpose                                                      |
|---------------------|--------------------------------------------------------------|
| `#home` — Hero      | Search bar, popular searches, discovery statistics           |
| Quick access grid   | One-click category filtering                                 |
| `#discovery`        | Curated promos + random discovery                            |
| `#atlas`            | Map + result list + advanced filters                         |
| `#provinces`        | 31 province cards with images                                |
| `#collections`      | Thematic collections (history, nature, food, routes)         |
| Digital passport    | Save/visit counters and export                               |
| `#about`            | Trust principles                                             |
| Modals              | Detail, filters, submission, report, passport                |
| Toast region        | Non-blocking notifications                                   |

---

## ♿ Accessibility

- **Focus trapping** in modals with Tab/Shift+Tab navigation
- **`Escape`** closes modals; **`/`** focuses the global search
- Semantic HTML with `aria-label`, `aria-live`, `aria-modal`
- Visible focus rings respecting `:focus-visible`
- `prefers-reduced-motion` disables transitions and animations
- RTL-first, keyboard-operable result cards

---

## ⚙️ Configuration

### Frontend

- **API base URL:** override with `window.__API_BASE__ = "/api"` before loading `app.js`.
- **Neshan API key:** `frontend/js/app.js` → `NESHAN_API_KEY`.
- **Offline dataset:** `frontend/js/data.js` → `RAW_ATTRACTIONS`.

### Backend

| Property                       | Default | Description                        |
|--------------------------------|---------|------------------------------------|
| `server.port`                  | `8080`  | HTTP port                          |
| `spring.application.name`      | —       | Application identifier             |
| `logging.level.root`           | `INFO`  | Log level                          |

---

## 🧪 Testing

> The demo ships without automated tests. Recommended starting points:

- **Frontend:** Vitest or Playwright for E2E (search, filter, save/visit flows).
- **Backend:** Spring Boot `@WebMvcTest` for controllers and `@SpringBootTest` for the store.

---

## 🗺️ Roadmap

- [ ] Persist data to PostgreSQL with JPA/Flyway
- [ ] Authentication & per-user passports
- [ ] Admin moderation workflow for submissions
- [ ] Full English localization (i18n)
- [ ] Offline-first PWA with service worker
- [ ] Additional map layers (satellite, terrain)
- [ ] Route planning across multiple attractions
- [ ] Comprehensive test coverage

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository.
2. Create a feature branch: `git checkout -b feature/your-feature`.
3. Follow the existing code style (Vanilla JS, no frameworks, no jQuery).
4. Write clear commit messages.
5. Open a pull request with a description of the change and any related issues.

Please respect the project's commitment to:
- **Data integrity** — sources and review status must be explicit.
- **Site safety** — sensitive coordinates and dangerous routes are governed by protection policies.
- **Responsible travel** — the atlas discourages irresponsible visitation.

---

## 📄 License

This project is released under the **MIT License**. See [`LICENSE`](LICENSE) for details.

Third-party components retain their own licenses:
- [Leaflet](https://leafletjs.com/) — BSD-2-Clause
- [Leaflet.markercluster](https://github.com/Leaflet/Leaflet.markercluster) — MIT
- [Neshan Maps SDK](https://platform.neshan.org/) — Neshan Terms of Service
- [Vazirmatn](https://github.com/rastikerdar/vazirmatn) — SIL OFL 1.1

---

## 🙏 Acknowledgments

- **Neshan Maps** for the map infrastructure and SDK
- The **Vazirmatn** team for the Persian typeface
- The **Leaflet** community for marker clustering
- All contributors who help document Iran's diverse attractions responsibly

---

## 📬 Contact

For questions, suggestions, or collaborations, please open an issue or contact the maintainers at **[maintainers@example.com](mailto:maintainers@example.com)**.

---

<div align="center">

**Made with ❤️ for responsible discovery of Iran**

*اطلس هوشمند جاذبه‌های ایران*

</div>
```

---

## Notes on this README

| Section | Why it's included |
|---|---|
| **Badges** | Quick visual signal of stack, license, and demo status |
| **Overview** | One-paragraph explanation of *what* and *why*, mentioning the RTL/Persian-first design and no-jQuery constraint |
| **Features** | Grouped by user-facing domain (map, discovery, passport, contributions, design, resilience) so reviewers can skim |
| **Architecture** | ASCII diagram + tree — helps new contributors orient in <30 seconds |
| **Tech Stack table** | Explicit about *not* using frameworks on the frontend |
| **Getting Started** | Numbered steps, copy-pasteable commands, and a bold callout for the **Neshan API key** (a common setup trap) |
| **API Reference** | Full endpoint table with a working JSON example — required for anyone wiring the frontend to a different backend |
| **UI Structure** | Maps DOM section IDs to product intent so designers can navigate |
| **Accessibility** | Short but concrete; most portfolios omit this |
| **Configuration** | Frontend + backend, in one place |
| **Roadmap** | Communicates project maturity and direction without overpromising |
| **Contributing** | Reinforces the three editorial principles baked into the product |
| **License & Acknowledgments** | Legally correct per-dependency attribution, including Neshan's ToS |
