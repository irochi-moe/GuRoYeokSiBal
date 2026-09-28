# GuRoYeokSiBal

마인크래프트 서버용 욕설 필터 플러그인입니다. 채팅, 귓속말, 팀·마을 이름에 들어간 욕설을 막거나 `*`로 가립니다. `ㅅㅣㅂㅏㄹ`, `시이이발`, `시.발`, `시 발`, `s1bal`처럼 바꿔 쓴 욕설도 잡습니다.

## 설치

Paper 또는 Folia 1.20.1 이상, Java 17 이상이 필요합니다.

1. [Releases](https://github.com/irochi-moe/GuRoYeokSiBal/releases)에서 `GuRoYeokSiBal-<버전>.jar`를 받아 `plugins` 폴더에 넣습니다.
2. 서버를 켜면 `plugins/GuRoYeokSiBal`에 설정 파일과 기본 욕설 목록이 생깁니다. 이 상태로 바로 동작합니다.
3. 설정을 고친 뒤에는 `/guroyeoksibal reload`로 적용합니다. 항목 설명은 [config.yml](src/main/resources/config.yml) 주석에 있습니다.

## 욕설 필터

`config.yml`의 `action`으로 욕설을 어떻게 처리할지 고릅니다.

- `BLOCK`(기본값): 욕설이 들어간 메시지를 보내지 않습니다.
- `REPLACE`: 욕설 부분만 `*`로 바꿔 보냅니다. 대소문자와 색 코드는 그대로 둡니다.

일반 채팅 말고도 귓속말(`/msg`, `/w`, `/r`)과 팀·마을·국가 이름을 정하는 명령어(`/team create`, `/town new` 등)를 검사합니다. 검사할 명령어는 `commands-with-target`과 `commands-full`에서 바꿉니다. 영문 대문자로만 쓴 도배 채팅도 막습니다.

욕설이 걸리면 `irochi.guroyeoksibal.notify` 권한이 있는 관리자에게 게임 안에서 알림이 갑니다.

띄어 쓴 글자가 우연히 욕설처럼 이어지는 경우(`다시 발로`, `10시 발 열차`)와 영단어 안에 욕설이 들어 있는 경우(`class`, `cocktail`)는 막지 않습니다.

## 욕설 목록

욕설 목록은 `plugins/GuRoYeokSiBal` 폴더의 `.csv` 파일입니다. `default.csv`가 기본으로 들어 있고 새 `.csv` 파일을 만들면 함께 읽습니다. 한 줄에 여러 단어를 쉼표로 이어 적을 수 있고 `#`로 시작하는 줄은 무시합니다.

```csv
# 서버 전용 금칙어
시발
fuck,fucking,fucker
```

바꿔 쓴 형태는 알아서 잡으니 기본형만 적으면 됩니다. 영단어는 단어 전체가 같아야 걸리므로 `fucking`처럼 변형도 따로 적어야 합니다.

## 채팅 쿨타임

채팅을 연달아 보내지 못하게 합니다. 기본은 15초이고 `chat-cooldown-seconds`로 바꿉니다. 끄려면 `chat-cooldown-enabled: false`로 바꾸세요.

후원 등급처럼 쿨타임을 줄여 줄 그룹이 있으면 `chat-cooldown-tiers`에 등급 이름과 초를 적고 `irochi.guroyeoksibal.cooldown.<등급이름>` 권한을 줍니다. 기본 등급은 `pro`(7초), `proplus`(3초)입니다. 여러 등급이 있으면 가장 짧은 쿨타임을 씁니다.

## 채팅 플러그인 연동

아래 플러그인을 쓰면 채널이나 채팅 종류마다 필터와 쿨타임을 따로 켤 수 있습니다. 설치되어 있으면 자동으로 연동하고 없으면 모든 채팅에 적용합니다.

| 플러그인 | 구분 | 참고 |
| --- | --- | --- |
| [TownyChat](https://github.com/TownyAdvanced/TownyChat) | `general`, `town`, `nation` 등 채널 | |
| [Azurite](https://builtbybit.com/resources/azurite-hcf-core-fully-configurable.24593) | `public`, `team`, `ally` 등 | `public` 외 채팅은 막을 수 없어 욕설만 `*`로 가립니다. |
| [EssentialsX Chat](https://essentialsx.net/) | `global`, `local`, `shout`, `question` | Essentials의 `chat.radius`가 0이면 모두 `global`입니다. |
| [CMI](https://www.zrips.net/cmi/) | `global`, `local`, `chatroom`, `staff` | CMI의 `Chat.ModifyChatFormat`이 켜져 있어야 합니다. |

`config.yml`의 `<플러그인>-filtered-...` 목록에는 필터를, `<플러그인>-cooldown-...` 목록에는 쿨타임을 적용할 채널을 적습니다. `*`는 전체입니다.

## 명령어와 권한

| 명령어 | 하는 일 |
| --- | --- |
| `/guroyeoksibal reload` | 설정, 욕설 목록, 연동 다시 읽기 |
| `/guroyeoksibal status` | 불러온 욕설 수, 처리 방식, 알림 여부 확인 |

| 권한 | 하는 일 | 기본 |
| --- | --- | --- |
| `irochi.guroyeoksibal.reload` | `/guroyeoksibal` 명령어 사용 | op |
| `irochi.guroyeoksibal.notify` | 욕설 감지 알림 받기 | op |
| `irochi.guroyeoksibal.bypass` | 욕설 필터 무시 | op |
| `irochi.guroyeoksibal.cooldown.bypass` | 채팅 쿨타임 무시 | op |
| `irochi.guroyeoksibal.cooldown.<등급이름>` | 등급별 쿨타임 적용 | 없음 |

op는 필터와 쿨타임을 받지 않습니다. op로 필터를 시험하려면 권한 플러그인에서 `bypass` 권한을 false로 두세요.

## 그 밖에

- 메시지는 플레이어의 게임 언어에 맞춰 한국어, 영어, 일본어로 나갑니다. 문구는 `lang/<언어코드>.yml`에서 고치고 파일을 추가하면 다른 언어도 쓸 수 있습니다.
- [bStats](https://bstats.org/plugin/bukkit/GuRoYeokSiBal/33411)로 서버 버전, 플레이어 수, Java 버전 같은 익명 통계를 보냅니다. 채팅 내용과 욕설 목록은 보내지 않습니다. 그대로 두면 개발에 도움이 됩니다. 끄려면 `bstats: false`로 바꾼 뒤 재시작하세요.
- 채팅을 막을 때 `ChatBlockedEvent`가 발생해 다른 플러그인이 막힌 이유와 원문을 받을 수 있습니다. [SinBalSinGo](https://github.com/irochi-moe/SinBalSinGo)가 이 이벤트를 씁니다.
- 직접 빌드하려면 `./gradlew build`를 실행하세요. 결과물은 `build/libs`에 생깁니다.

## 라이선스

이 프로젝트는 [GPL v3](LICENSE.md) 라이선스를 따릅니다.
