// Claude 사용량 중계 서버 (Cloudflare Worker)
//
// 필요한 Secret 2개 (Cloudflare 대시보드 → Settings → Variables and Secrets):
//   CLAUDE_TOKEN : Claude 로그인 토큰 (위젯에는 절대 넣지 않음)
//   ACCESS_KEY   : 아무도 못 맞힐 긴 임의 문자열 (위젯 주소에 붙여 씀)
//
// 주소:
//   /usage?key=ACCESS_KEY  → 위젯용 JSON
//   /raw?key=ACCESS_KEY    → 원본 응답 확인용 (처음 연결 테스트할 때)

const UPSTREAM = "https://api.anthropic.com/api/oauth/usage"

export default {
  async fetch(request, env) {
    const url = new URL(request.url)

    if (!env.ACCESS_KEY || url.searchParams.get("key") !== env.ACCESS_KEY) {
      return json({ error: "unauthorized" }, 401)
    }

    if (url.pathname === "/raw") {
      const r = await callUpstream(env)
      return new Response(await r.text(), {
        status: r.status,
        headers: { "content-type": "application/json; charset=utf-8" },
      })
    }

    if (url.pathname !== "/usage") {
      return json({ error: "not found" }, 404)
    }

    let r
    try {
      r = await callUpstream(env)
    } catch (e) {
      return json({ error: "network", detail: String(e) }, 502)
    }
    if (!r.ok) {
      return json({ error: "upstream", status: r.status }, 502)
    }

    const body = toWidgetFormat(await r.json())
    return json(body, 200)
  },
}

function callUpstream(env) {
  return fetch(UPSTREAM, {
    headers: {
      Authorization: `Bearer ${env.CLAUDE_TOKEN}`,
      "anthropic-beta": "oauth-2025-04-20",
      "User-Agent": "claude-usage-widget",
    },
  })
}

// 원본 응답 → 위젯이 쓰는 형식
// 원본: { five_hour: { utilization, resets_at }, seven_day: { utilization, resets_at }, ... }
// 사용 기록이 없으면 구간이 null로 올 수 있어 0%로 처리
function toWidgetFormat(d) {
  return {
    session: bucket(d && d.five_hour),
    weekly: bucket(d && d.seven_day),
    updatedAt: new Date().toISOString(),
  }
}

function bucket(b) {
  const p = b && typeof b.utilization === "number" ? b.utilization : 0
  return {
    percent: Math.max(0, Math.min(100, p)),
    resetsAt: (b && b.resets_at) || "",
  }
}

function json(obj, status, extraHeaders = {}) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      ...extraHeaders,
    },
  })
}
