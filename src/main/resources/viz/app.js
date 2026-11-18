(() => {
  const listEl = document.getElementById("class-list");
  const statusEl = document.getElementById("status");
  const filterEl = document.getElementById("filter");
  const sentinelEl = document.getElementById("list-sentinel");

  const PAGE_SIZE = 40;

  /** @type {Array<{branch:string,start:string,end:string,className:string,instructor:string,sourceUrl:string}>} */
  let classes = [];
  /** @type {typeof classes} */
  let filtered = [];
  let renderedCount = 0;

  function parseCsv(text) {
    const rows = [];
    let field = "";
    let row = [];
    let inQuotes = false;
    for (let i = 0; i < text.length; i++) {
      const c = text[i];
      if (inQuotes) {
        if (c === '"') {
          if (text[i + 1] === '"') {
            field += '"';
            i++;
          } else {
            inQuotes = false;
          }
        } else {
          field += c;
        }
      } else if (c === '"') {
        inQuotes = true;
      } else if (c === ",") {
        row.push(field);
        field = "";
      } else if (c === "\n") {
        row.push(field);
        rows.push(row);
        row = [];
        field = "";
      } else if (c !== "\r") {
        field += c;
      }
    }
    if (field.length || row.length) {
      row.push(field);
      rows.push(row);
    }
    return rows;
  }

  function formatWhen(iso) {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return new Intl.DateTimeFormat("hu-HU", {
      timeZone: "Europe/Budapest",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      weekday: "short",
      hour: "2-digit",
      minute: "2-digit",
    }).format(d);
  }

  function createItem(c, index) {
    const li = document.createElement("li");
    li.style.animationDelay = `${Math.min(index, 24) * 18}ms`;

    const when = document.createElement("div");
    when.className = "meta";
    when.textContent = formatWhen(c.start);

    const body = document.createElement("div");
    const branch = document.createElement("a");
    branch.href = c.sourceUrl;
    branch.target = "_blank";
    branch.rel = "noopener noreferrer";
    branch.textContent = c.branch;

    const name = document.createElement("div");
    name.className = "class-name";
    name.textContent = c.className;

    const instructor = document.createElement("div");
    instructor.className = "meta";
    instructor.textContent = c.instructor || "—";

    body.append(branch, name, instructor);
    li.append(when, body);
    return li;
  }

  function updateStatus(q) {
    if (q) {
      statusEl.textContent =
        filtered.length === 0
          ? `0 matches for “${filterEl.value.trim()}”`
          : `${filtered.length} match(es) for “${filterEl.value.trim()}” · showing ${renderedCount}`;
      return;
    }
    statusEl.textContent =
      filtered.length === 0
        ? "0 upcoming classes"
        : `${filtered.length} upcoming class(es) · showing ${renderedCount}`;
  }

  function appendNextPage() {
    if (renderedCount >= filtered.length) {
      sentinelEl.hidden = true;
      updateStatus(filterEl.value.trim().toLowerCase());
      return false;
    }

    const start = renderedCount;
    const end = Math.min(start + PAGE_SIZE, filtered.length);
    const frag = document.createDocumentFragment();
    for (let i = start; i < end; i++) {
      frag.append(createItem(filtered[i], i - start));
    }
    listEl.append(frag);
    renderedCount = end;
    sentinelEl.hidden = renderedCount >= filtered.length;
    updateStatus(filterEl.value.trim().toLowerCase());
    return renderedCount < filtered.length;
  }

  /** Keep filling while the sentinel stays in view (short lists / tall screens). */
  function fillVisible() {
    let guard = 0;
    while (
      !sentinelEl.hidden &&
      sentinelEl.getBoundingClientRect().top < window.innerHeight + 200 &&
      appendNextPage() &&
      ++guard < 50
    ) {
      /* append until sentinel leaves the viewport or list is exhausted */
    }
  }

  function render() {
    const q = filterEl.value.trim().toLowerCase();
    filtered = classes
      .filter((c) => {
        if (!q) return true;
        const hay = `${c.branch} ${c.className} ${c.instructor}`.toLowerCase();
        return hay.includes(q);
      })
      .sort((a, b) => a.start.localeCompare(b.start));

    listEl.replaceChildren();
    renderedCount = 0;
    sentinelEl.hidden = filtered.length === 0;
    appendNextPage();
    fillVisible();
  }

  const observer = new IntersectionObserver(
    (entries) => {
      if (!entries.some((e) => e.isIntersecting)) return;
      appendNextPage();
      fillVisible();
    },
    { root: null, rootMargin: "240px 0px", threshold: 0 }
  );
  observer.observe(sentinelEl);

  async function load() {
    try {
      const res = await fetch("classes.csv", { cache: "no-store" });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const text = await res.text();
      const rows = parseCsv(text);
      if (!rows.length) throw new Error("empty CSV");
      const header = rows[0].map((h) => h.trim());
      const idx = Object.fromEntries(header.map((h, i) => [h, i]));
      const now = Date.now();
      classes = rows
        .slice(1)
        .filter((r) => r.length >= 7)
        .map((r) => ({
          branch: r[idx.branch] || "",
          start: r[idx.start] || "",
          end: r[idx.end] || "",
          className: r[idx.class_name] || "",
          instructor: r[idx.instructor] || "",
          sourceUrl: r[idx.source_url] || "#",
          deleted: r[idx.deleted] || "0",
        }))
        .filter((c) => c.deleted === "0")
        .filter((c) => {
          const t = Date.parse(c.start);
          return Number.isNaN(t) || t >= now - 60_000;
        });
      render();
    } catch (err) {
      statusEl.textContent = `Failed to load classes.csv (${err.message})`;
    }
  }

  filterEl.addEventListener("input", render);
  load();
})();
