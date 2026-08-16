# GuRoYeokSiBal

마인크래프트 1.20+ Paper 서버용 욕설 필터 플러그인입니다.

## 주요 기능

**탐지**

- Aho-Corasick 알고리즘으로 다수의 금칙어를 한 번에 검사
- 우회 표현 정규화: 자모 분리(`ㅅㅣㅂㅏㄹ`), 늘여쓰기(`시이이발`), 특수문자 삽입(`시.발`), 띄어쓰기(`시 발`), 리트 표기(`5hit`, `s1bal`), 자모 모양 문자(`^`→`ㅅ`)
- 오탐 방지: 띄어쓰기 매치는 앞뒤에 다른 단어가 붙으면 무시(`다시 발로`, `10시 발 열차`), 영문 금칙어는 단어 경계에서만 탐지(`class`, `cocktail` 안전, 파생형은 목록에 직접 추가)

**동작**

- 감지 시 `BLOCK`(전체 차단) 또는 `REPLACE`(욕설 부분만 검열, 원문 대소문자/색상코드 보존)
- 일반 채팅과 명령어 인자 모두 검사: 귓속말(`/msg`, `/w`, `/r`), 팀/마을/국가 생성/변경(`/team create`, `/town new` 등)
- 전체 대문자 도배 차단
- 관리자 실시간 감지 알림 (`notify` 권한)

**연동**

- [TownyChat](https://github.com/TownyAdvanced/TownyChat) / [Azurite](https://builtbybit.com/resources/azurite-hcf-core-fully-configurable.24593) / [EssentialsX Chat](https://essentialsx.net/) 채널·채팅 종류별 필터와 쿨타임 (Azurite의 public 외 채팅은 취소가 불가능해 REPLACE 검열만 적용, EssentialsX는 `chat.radius` 기준 global/local/shout/question 구분)
- 권한 기반 등급별 채팅 쿨타임 (등급 자유 추가)
- 다른 플러그인용 `ChatBlockedEvent` API (예: [SinBalSinGo](https://github.com/irochi-moe/SinBalSinGo))
- 한국어, 영어, 일본어 메시지, 플레이어 클라이언트 언어 자동 적용

## 명령어

| 명령어 | 설명 |
|---|---|
| `/guroyeoksibal reload` | 설정과 욕설 목록, 연동을 다시 불러옵니다. |
| `/guroyeoksibal status` | 로드된 욕설 수, 동작 모드, 알림 상태를 확인합니다. |

## 권한

| 권한 | 설명 | 기본값 |
|---|---|---|
| `irochi.guroyeoksibal.bypass` | 욕설 필터 우회 | false |
| `irochi.guroyeoksibal.cooldown.<등급이름>` | 채팅 쿨타임 등급 적용 (`chat-cooldown-tiers` 등급, 기본 제공: `proplus`, `pro`) | false |
| `irochi.guroyeoksibal.cooldown.bypass` | 채팅 쿨타임 우회 | false |
| `irochi.guroyeoksibal.reload` | `/guroyeoksibal` 명령어 사용 | op |
| `irochi.guroyeoksibal.notify` | 욕설 감지 시 인게임 알림 수신 | op |

## 설정 파일

| 파일 | 내용 |
|---|---|
| `config.yml` | 동작 모드, 검사 대상 명령어, 연동 채널, 쿨타임 |
| `*.csv` | 금칙어 목록 (`default.csv` 기본 제공, 파일 자유 추가/수정) |
| `lang/<언어코드>.yml` | 메시지 문구 (ko, en, ja 기본 제공, 언어 자유 추가) |

수정 후 `/guroyeoksibal reload` 로 반영합니다.

## 요구 사항

- Paper 1.20.1 이상
- Java 17 이상
- (선택) TownyChat, Azurite, EssentialsX Chat

## 빌드

```bash
./gradlew build
```

빌드 결과물은 `build/libs/GuRoYeokSiBal-<version>.jar`에 생성됩니다.

## 통계

익명 사용 통계를 [bStats](https://bstats.org/plugin/bukkit/GuRoYeokSiBal/33411)로 전송합니다. 서버 버전, 플레이어 수, Java 버전 같은 정보만 수집하며 채팅 내용이나 금칙어 목록은 보내지 않습니다.

어떤 서버 버전과 환경에서 쓰이는지 참고하는 용도라 그대로 두면 개발에 도움이 됩니다. 끄려면 `config.yml`의 `bstats`를 `false`로 두거나, `plugins/bStats/config.yml`에서 서버 전체 통계를 끄면 됩니다. 서버 재시작 후 적용됩니다.

## 라이선스

이 프로젝트는 [GPL v3](LICENSE.md) 라이선스를 따릅니다.
