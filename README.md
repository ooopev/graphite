# oQuQ

**Молниеносный VPN/прокси-клиент для Android.** Чистейший минимализм Material 3
в чёрно-графитовой палитре, три движка, ноль компромиссов по скорости.

```
     ●          ●
          ────
      o Qu Q
```

## Что это

Kotlin + Jetpack Compose клиент уровня Happ / v2rayNG / NekoBox — и быстрее:
**три ядра**, каждое берёт то, в чём оно лучшее в мире:

| Движок | Библиотека | Отдаёт |
|---|---|---|
| **sing-box** (Go, libbox.aar) | sing-box 1.14.x | TUN-владелец + VLESS REALITY/Vision, VMess, Trojan, Shadowsocks-2022, Hysteria2, TUIC, WireGuard, SOCKS/HTTP |
| **Xray-core** (Go, libxray.aar) | Xray 26.x | XHTTP и Xray-специфика: локальный SOCKS-хоп за sing-box TUN |
| **AmneziaWG** (Go, libawg.aar) | amnezia-wg | Обфусцированный WireGuard (Ic/Jc/Jmin/Jmax/S1/S2/H1–H4), fd идёт прямо в userspace-ядро, без посредников |

Почему так: Happ работает **только** на Xray-core; sing-box не умеет AmneziaWG
(issues SagerNet#3159, #4045), а Xray — единственный, кто умеет XHTTP. Три
ядра = не уступаем никому ни по одному протоколу.

Языки: **Kotlin** (приложение) + **Go** (ядра как готовые AAR). Rust/Zig-ядер
с REALITY+XHTTP+AWG в природе не существует; слой `engine/` — интерфейс,
Rust-ядро подключается позже без переписывания приложения.

## Возможности

- VLESS (REALITY, Vision, XHTTP), VMess, Trojan, SS/SS2022, Hysteria2, TUIC, WireGuard, **AmneziaWG**, SOCKS, HTTP
- Импорт: ссылка / буфер / QR / Amnezia `vpn://` / wg-conf / подписки (base64+plain, квота трафика из `subscription-userinfo`)
- Подписки с автообновлением (WorkManager, батарея-осознанный)
- **Маршрутизация по приложениям** (только выбранные / обход выбранных)
- Kill switch: `strict_route` + инструкция на системный «блокировать без VPN»
- Быстрая плитка, автозапуск после загрузки, deep-link импорт (`vless://…` из браузера)
- Живая скорость в UI и уведомлении, TCP-пинг всех серверов параллельно
- Бэкап/рестор (SAF), SOCKS5-шеринг в локальную сеть
- RU / EN, тёмная-only тема: чистый чёрный AMOLED / графит / Material You dynamic

## Производительность и батарея

- TUN стек `mixed` (system TCP + gvisor UDP), MTU 9000 по умолчанию
- `cache_file` в sing-box: кэш rule-set и состояния между запусками — быстрый холодный старт
- `packet_encoding: xudp` для VLESS/VMess — UDP без лишних накладных
- DNS через DoH через прокси + `independent_cache`, при WG — DNS туннеля
- FGS `specialUse` (subtype `vpn`) — Android 14+ совместимо, no 6-часовых лимитов
- WorkManager вместо сервисов для подписок, `setRequiresBatteryNotLow`
- Уведомление IMPORTANCE_LOW + `setOnlyAlertOnce`; UI-поток статистики засыпает вместе со экраном
- R8 full mode, baseline-profile через profileinstaller, non-transitive R класс

## Сборка

```bash
# 1. Ядра (один раз и после апгрейдов версий)
scripts/fetch_cores.sh            # libbox.aar + libxray.aar + libawg.aar → app/libs/

# 2. Приложение (Android Studio Ladybug+ или Gradle 8.14+)
gradle assembleRelease            # или откры́ть проект в Studio и Run
```

Gradle wrapper не положен в репозиторий — сгенерируйте: `gradle wrapper --gradle-version 8.14.3`.

Требования: JDK 17, Android SDK 36, для libawg — Go 1.22+ и Android NDK.

## Точки интеграции (важно при апгрейде ядер)

Вся привязка к Go-биндингам изолирована в трёх файлах:

- `vpn/engine/SingBoxEngine.kt` — `io.nekohasekai.libbox.*` (gomobile, sing-box 1.12+ API)
- `vpn/engine/XrayEngine.kt` — `io.github.xtls.libxray.LibXray` (runXray/stopXray)
- `vpn/engine/AwgEngine.kt` — `go.oquqawg.Oquqawg` (наш gomobile-врапер, `scripts/awgbind/awg.go`)

Если новая версия AAR переименовала метод — правится ровно один файл.

## Структура

```
app/src/main/java/com/oquq/app/
├── core/          модели, Room, парсеры ссылок, билдеры конфигов (sing-box/Xray/AWG), бэкап
├── data/          DataStore настройки, WorkManager подписок
├── vpn/           VpnService (оркестратор 3 движков), контроллер, движки, плитка, boot
└── ui/            theme (black/graphite M3), home (power button + список), add, settings, apps, subs
```

## Лицензии ядер

sing-box — GPLv3 (совместимо: приложение распространять вместе с исходниками),
Xray-core — MIT/Apache-2.0, amnezia-wg — Apache-2.0 (fork wireguard-go).
