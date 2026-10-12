// Claude 사용량 위젯 (iOS · Scriptable)
// 세션·주간 사용량을 흑백 막대로 보여줍니다.

// ── 설정 ───────────────────────────────────────────────
const USE_MOCK = true // true: 서버 없이 예시 데이터로 동작
const SERVER_URL = "https://YOUR-SERVER.example.com/usage"
const THRESHOLD = 80 // 이 % 부터 막대가 빠르게 밝아짐

const C = {
  bg: "#17181B",
  text: "#EDEEF0",
  label: "#D5D8DE",
  caption: "#A6ABB5",
}

// ── 저장소 ─────────────────────────────────────────────
const fm = FileManager.local()
const DIR = fm.joinPath(fm.documentsDirectory(), "claude-usage")
if (!fm.fileExists(DIR)) fm.createDirectory(DIR)
const CACHE = fm.joinPath(DIR, "usage.json")
const MOCK_INDEX = fm.joinPath(DIR, "mock-index.txt")

function readCache() {
  try {
    return fm.fileExists(CACHE) ? JSON.parse(fm.readString(CACHE)) : null
  } catch (e) {
    return null
  }
}

// ── 데이터 가져오기 ─────────────────────────────────────
async function loadUsage() {
  // 방금(2분 이내) 앱에서 새로고침했다면 그 값을 그대로 사용
  const cached = readCache()
  if (config.runsInWidget && cached && Date.now() - new Date(cached.updatedAt) < 2 * 60 * 1000) {
    return { usage: cached, failed: false }
  }
  try {
    const usage = USE_MOCK ? mockUsage() : await fetchUsage()
    fm.writeString(CACHE, JSON.stringify(usage))
    return { usage, failed: false }
  } catch (e) {
    return { usage: cached, failed: true } // 실패하면 이전 값 유지
  }
}

async function fetchUsage() {
  const req = new Request(SERVER_URL)
  req.timeoutInterval = 8
  const data = await req.loadJSON()
  if (!data || !data.session || !data.weekly || !data.updatedAt) throw new Error("형식 오류")
  return data
}

function mockUsage() {
  const presets = [[34, 51], [62, 74], [86, 93], [97, 99]]
  const i = fm.fileExists(MOCK_INDEX) ? parseInt(fm.readString(MOCK_INDEX)) || 0 : 0
  fm.writeString(MOCK_INDEX, String((i + 1) % presets.length))
  const now = Date.now()
  return {
    session: { percent: presets[i][0], resetsAt: new Date(now + 102 * 60000).toISOString() },
    weekly: { percent: presets[i][1], resetsAt: new Date(now + 78 * 3600000).toISOString() },
    updatedAt: new Date(now).toISOString(),
  }
}

// ── 표시용 도우미 ───────────────────────────────────────
// 흑백 막대 색: 기준점까지 천천히 밝아지다가, 넘으면 빠르게 흰색에 가까워짐
function tone(p) {
  const v = Math.max(0, Math.min(100, p))
  const l = v <= THRESHOLD
    ? 0.36 + 0.30 * (v / THRESHOLD)
    : 0.66 + 0.30 * ((v - THRESHOLD) / (100 - THRESHOLD))
  const hex = Math.round(l * 255).toString(16).padStart(2, "0")
  return new Color("#" + hex + hex + hex)
}

function remaining(iso) {
  const ms = new Date(iso) - Date.now()
  if (isNaN(ms)) return "--"
  if (ms <= 0) return "곧"
  const m = Math.floor(ms / 60000)
  const d = Math.floor(m / 1440)
  const h = Math.floor(m / 60) % 24
  const mm = m % 60
  if (d > 0) return `${d}일 ${h}시간 후`
  if (h > 0) return `${h}시간 ${mm}분 후`
  return `${mm}분 후`
}

function clock(iso) {
  const d = new Date(iso)
  if (isNaN(d)) return "--:--"
  return String(d.getHours()).padStart(2, "0") + ":" + String(d.getMinutes()).padStart(2, "0")
}

function txt(stack, s, size, color, weight = "regular") {
  const t = stack.addText(s)
  t.font = weight === "medium" ? Font.mediumSystemFont(size)
    : weight === "light" ? Font.lightSystemFont(size)
    : Font.systemFont(size)
  t.textColor = new Color(color)
  t.lineLimit = 1
  return t
}

function barImage(p, w, h) {
  const ctx = new DrawContext()
  ctx.size = new Size(w, h)
  ctx.opaque = false
  ctx.respectScreenScale = true

  const track = new Path()
  track.addRoundedRect(new Rect(0, 0, w, h), h / 2, h / 2)
  ctx.addPath(track)
  ctx.setFillColor(new Color("#FFFFFF", 0.09))
  ctx.fillPath()

  if (p > 0) {
    const fw = Math.max(h, (w * Math.min(100, p)) / 100)
    const fill = new Path()
    fill.addRoundedRect(new Rect(0, 0, fw, h), h / 2, h / 2)
    ctx.addPath(fill)
    ctx.setFillColor(tone(p))
    ctx.fillPath()
  }
  return ctx.getImage()
}

// ── 위젯 그리기 ─────────────────────────────────────────
function buildWidget(family, usage, failed) {
  const small = family === "small"
  const large = family === "large"
  const pad = large ? 18 : 14

  // 막대는 가장 작은 위젯에도 들어가는 기본 길이로 그리고,
  // 실제 위젯 너비에 맞춰 늘어나게 함 (iPhone·iPad 공통)
  const barW = small ? 118 : 288

  const w = new ListWidget()
  w.backgroundColor = new Color(C.bg)
  w.setPadding(pad, pad, pad, pad)
  w.refreshAfterDate = new Date(Date.now() + 15 * 60000)
  w.url = URLScheme.forRunningScript() // 위젯을 탭하면 새로고침

  // 헤더
  const header = w.addStack()
  header.layoutHorizontally()
  header.centerAlignContent()
  const titles = header.addStack()
  titles.layoutVertically()
  txt(titles, small ? "Claude" : "Claude 사용량", large ? 16 : 14, C.text, "medium")
  const status = !usage ? "불러오기 실패"
    : failed ? `갱신 실패 · ${clock(usage.updatedAt)}`
    : `${clock(usage.updatedAt)} 갱신`
  txt(titles, status, 11, C.caption)
  header.addSpacer()

  if (!small) {
    const btn = header.addStack()
    btn.size = new Size(32, 32)
    btn.cornerRadius = 16
    btn.backgroundColor = new Color("#FFFFFF", 0.06)
    btn.centerAlignContent()
    const icon = btn.addImage(SFSymbol.named("arrow.clockwise").image)
    icon.imageSize = new Size(13, 13)
    icon.tintColor = new Color(C.text)
    btn.url = URLScheme.forRunningScript()
  }

  if (!usage) return w

  // 헤더 아래는 고정 간격, 남는 공간은 막대 사이와 아래 여백에 나눔
  // (고정 간격 = 최소 간격, 빈 addSpacer() = 남는 공간 분배)
  const gap = large ? 14 : 8
  w.addSpacer(large ? 18 : 10)
  addLimit(w, "세션", usage.session, family, barW)
  w.addSpacer(gap)
  w.addSpacer()
  addLimit(w, "주간", usage.weekly, family, barW)
  if (large) {
    w.addSpacer(gap)
    w.addSpacer()
    addLegend(w, barW)
  }
  w.addSpacer()
  return w
}

function addLimit(w, label, limit, family, barW) {
  const pct = Math.round(limit.percent) + "%"
  const left = remaining(limit.resetsAt)
  const row = w.addStack()
  row.layoutHorizontally()

  if (family === "large") {
    row.bottomAlignContent()
    const col = row.addStack()
    col.layoutVertically()
    txt(col, label + " 한도", 13, C.label, "medium")
    txt(col, left + " 초기화", 12, C.caption)
    row.addSpacer()
    txt(row, pct, 30, C.text, "light")
  } else if (family === "medium") {
    row.centerAlignContent()
    txt(row, label, 12, C.label, "medium")
    row.addSpacer(8)
    txt(row, left + " 초기화", 11, C.caption)
    row.addSpacer()
    txt(row, pct, 13, C.text, "medium")
  } else {
    row.centerAlignContent()
    txt(row, label, 12, C.label, "medium")
    row.addSpacer()
    txt(row, pct, 16, C.text, "medium")
  }

  const h = family === "small" ? 4 : 6
  w.addSpacer(family === "large" ? 8 : 5)
  const bar = w.addImage(barImage(limit.percent, barW, h))
  bar.resizable = true
  bar.leftAlignImage()

  if (family === "small") {
    w.addSpacer(3)
    txt(w, left, 11, C.caption)
  }
}

function addLegend(w, barW) {
  txt(w, "밝기 기준", 11, C.caption)
  w.addSpacer(6)
  const ctx = new DrawContext()
  ctx.size = new Size(barW, 4)
  ctx.opaque = false
  ctx.respectScreenScale = true
  const n = 40
  for (let i = 0; i < n; i++) {
    ctx.setFillColor(tone(((i + 0.5) * 100) / n))
    ctx.fillRect(new Rect((i * barW) / n, 0, barW / n + 0.5, 4))
  }
  const img = w.addImage(ctx.getImage())
  img.resizable = true
  img.leftAlignImage()
  img.cornerRadius = 2
  w.addSpacer(6)
  const r = w.addStack()
  txt(r, "0%", 11, C.caption)
  r.addSpacer()
  txt(r, `${THRESHOLD}%부터 급격히 변화`, 11, C.caption)
  r.addSpacer()
  txt(r, "100%", 11, C.caption)
}

// ── 실행 ───────────────────────────────────────────────
const { usage, failed } = await loadUsage()

if (config.runsInWidget) {
  const fam = config.widgetFamily
  const family = fam === "medium" || fam === "large" ? fam
    : fam === "extraLarge" ? "large"
    : "small"
  Script.setWidget(buildWidget(family, usage, failed))
} else {
  // 앱에서 실행하거나 위젯을 탭했을 때: 새 데이터로 미리보기
  await buildWidget("medium", usage, failed).presentMedium()
}
Script.complete()
