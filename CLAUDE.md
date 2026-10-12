# Claude 사용량 위젯

Claude 구독 사용량(5시간 세션 · 주간)을 iOS·안드로이드 홈 화면 위젯에 막대로 보여주는 개인 프로젝트.

## 작업 규칙
- 응답과 코드 주석은 한국어로.
- 사용자는 폰·iPad 중심으로 작업하고 Mac이 없다. iOS는 Scriptable 방식으로 유지.
- 아래 "확정된 디자인"은 사용자와 여러 번 조율해 정한 것이니 임의로 바꾸지 말 것.

## 핵심 기능
1. 세션·주간 사용량을 막대가 채워지는 형식으로 표시 (최대 100%)
2. 새로고침 버튼으로 즉시 갱신
3. 위젯 크기 소·중·대 지원 (위치는 OS가 처리)
4. 초기화까지 남은 시간 표시 ("1시간 42분 후", "3일 6시간 후")

## 확정된 디자인
- 완전 흑백. 색 없음, 빛 번짐 효과 없음.
- 배경 #17181B, 본문 #EDEEF0, 라벨 #D5D8DE, 보조 텍스트 #A6ABB5, 막대 트랙 흰색 9% 투명도.
- 막대 밝기 공식 (기준점 THRESHOLD = 80):
  - 기준점 이하: 밝기 0.36 → 0.66 으로 천천히
  - 기준점 초과: 0.66 → 0.96 으로 급격히
- 막대 두께: 소형 4, 중·대형 6. 숫자는 흰색 통일.
- 새로고침 버튼: 32 크기 원형(아이콘 13~14), 터치 영역은 더 넓게. 소형은 위젯 전체 탭으로 갱신.
- 세로 간격: 헤더 아래는 고정 간격, 남는 공간은 "두 막대 사이"와 "아래 여백"에 반반 분배.
  (전부 아래에 몰거나 전부 막대 사이로 퍼뜨리는 건 사용자가 거절함)
- 대형 위젯 하단에 밝기 기준표(0% ~ 100%, "80%부터 급격히 변화").

## 데이터 형식 (서버 → 위젯)
```json
{
  "session": { "percent": 62, "resetsAt": "2026-10-12T15:40:00Z" },
  "weekly":  { "percent": 87, "resetsAt": "2026-10-15T00:00:00Z" },
  "updatedAt": "2026-10-12T13:58:00Z"
}
```

## 구성
- `android/` — Jetpack Glance 위젯. 30분 자동 갱신 + 새로고침 버튼. `Usage.kt`의 `USE_MOCK`, `SERVER_URL`.
- `ios/claude-usage-widget.js` — Scriptable 스크립트. 상단의 `USE_MOCK`, `SERVER_URL`.
  막대 길이를 화면 너비로 추정하면 iPad에서 잘리므로 금지. 기본 길이로 그리고 resizable로 늘림.
- `server/worker.js` — Cloudflare Worker. Secret: `CLAUDE_TOKEN`, `ACCESS_KEY`.
  `/usage?key=` 위젯용, `/raw?key=` 원본 확인용.
- `.github/workflows/android.yml` — push 시 APK 빌드, 아티팩트 `app-debug`.

## 데이터 출처 (주의)
- 공식 구독 사용량 API는 없음. 비공식 `GET https://api.anthropic.com/api/oauth/usage`
  (헤더 `Authorization: Bearer <토큰>`, `anthropic-beta: oauth-2025-04-20`)를 사용.
  응답의 `five_hour` / `seven_day` 각각 `utilization`, `resets_at`.
- 비공식이라 바뀔 수 있음. 토큰은 Worker Secret에만 두고 위젯·저장소에 절대 넣지 않음.

## 현재 상태
- iOS: iPad Air 11(2025)에서 예시 데이터로 테스트 완료, 레이아웃 확정.
- 안드로이드: Actions 빌드 통과(APK 생성), 실기기 테스트는 아직.
- 서버: 코드 작성 완료, `server/wrangler.jsonc` 추가(Worker 이름 `claude-usage`). 아직 배포 안 함.

## 다음 단계
1. Cloudflare에 Worker 배포: 대시보드에서 GitHub 저장소 연결, 루트 디렉터리 `server`.
2. 토큰 확인: PC에서 `claude setup-token`으로 받은 토큰이 `/raw`에서 동작하는지 검증.
   안 되면 `~/.claude/.credentials.json` 토큰 + 자동 갱신 기능 검토.
3. 동작 확인 후 두 위젯의 `USE_MOCK`을 끄고 `SERVER_URL` 연결.
