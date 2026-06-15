# Trouble Ticket API

Implementacja backendowa minimalnego profilu **TMF621 Trouble Ticket** zgodna z kontraktem OpenAPI dostarczonym przez Netia SA.

---

## Spis treści

- [Stos technologiczny](#stos-technologiczny)
- [Wymagania wstępne](#wymagania-wstępne)
- [Szybki start](#szybki-start)
- [Weryfikacja działania](#weryfikacja-działania)
- [Struktura projektu](#struktura-projektu)
- [Decyzje techniczne](#decyzje-techniczne)
- [Przyjęte założenia](#przyjęte-założenia)
- [Uruchamianie testów](#uruchamianie-testów)

---

## Stos technologiczny

| Komponent | Technologia |
|---|---|
| Język | Java 21 (Virtual Threads / Project Loom) |
| Framework | Spring Boot 3.3 |
| Baza danych | PostgreSQL 16 |
| Cache / Idempotencja | Redis 7 |
| Migracje schematu | Flyway |
| Autentykacja | JWT (HS256) — lokalnie; docelowo Keycloak / OIDC |
| Dokumentacja API | SpringDoc OpenAPI 3 (Swagger UI) |
| Testy | JUnit 5, Mockito, Testcontainers |
| Konteneryzacja | Docker, Docker Compose |

---

## Wymagania wstępne

- **Docker Desktop** — [https://www.docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop)
- **Docker Compose** (wbudowany w Docker Desktop)
- Port `8080`, `5432`, `6379` wolne na lokalnej maszynie

> Java 21 i Maven **nie są wymagane** lokalnie — budowanie odbywa się wewnątrz kontenera Docker (multi-stage build).

---

## Szybki start

### 1. Sklonuj repozytorium

```bash
git clone <URL_REPOZYTORIUM>
cd netia-trouble-ticket-api
```

### 2. Uruchom środowisko

```bash
docker-compose up --build
```

Docker Compose uruchomi kolejno:
1. **PostgreSQL 16** — czeka na healthcheck `pg_isready`
2. **Redis 7** — czeka na healthcheck `redis-cli ping`
3. **Aplikacja** — startuje dopiero gdy oba serwisy są gotowe, następnie Flyway automatycznie aplikuje migracje schematu (V1, V2, V3, V4)

### 3. Sprawdź czy działa

```bash
curl http://localhost:8080/health
```

Oczekiwana odpowiedź:
```json
{"status":"UP","service":"trouble-ticket-api","version":"1.0.0"}
```

### 4. Swagger UI

Otwórz w przeglądarce:
```
http://localhost:8080/swagger-ui.html
```

---

## Weryfikacja działania

Poniższy scenariusz pokrywa wszystkie endpointy zdefiniowane w kontrakcie OpenAPI.

### Krok 1 — Uzyskaj token JWT

```bash
curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d "{\"tenantId\":\"tenant-netia\",\"userId\":\"user-1\"}"
```

Odpowiedź:
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "tenantId": "tenant-netia",
  "userId": "user-1"
}
```

Skopiuj wartość pola `token`. W kolejnych krokach zastąp nią `TWÓJ_TOKEN`.

> **Windows CMD** — użyj dokładnie tej składni z cudzysłowami `\"` wewnątrz `"..."`.

---

### Krok 2 — Utwórz zgłoszenie `POST /api/v1/troubleTicket` → oczekiwany status `201`

```bash
curl -s -X POST http://localhost:8080/api/v1/troubleTicket \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TWÓJ_TOKEN" \
  -d "{\"externalId\":\"OK-123456\",\"serviceId\":987654321,\"description\":\"Brak transmisji danych dla uslugi klienta.\",\"status\":\"new\",\"note\":\"Zgloszenie utworzone przez konto API partnera.\"}"
```

Oczekiwana odpowiedź `201 Created`:
```json
{
  "id": "TT-1718200000000-A1B2C3D4",
  "externalId": "OK-123456",
  "serviceId": 987654321,
  "description": "Brak transmisji danych dla uslugi klienta.",
  "status": "acknowledged",
  "notes": [
    {
      "id": "NOTE-A1B2C3D4E5F6",
      "text": "Zgloszenie utworzone przez konto API partnera.",
      "date": "2026-06-15T10:00:00"
    }
  ]
}
```

> Skopiuj wartość pola `id`. W kolejnych krokach zastąp nią `TICKET_ID`.

---

### Krok 3 — Idempotencja: ten sam request ponownie → oczekiwany status `200`

```bash
curl -s -o nul -w "HTTP STATUS: %{http_code}" -X POST http://localhost:8080/api/v1/troubleTicket \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TWÓJ_TOKEN" \
  -d "{\"externalId\":\"OK-123456\",\"serviceId\":987654321,\"description\":\"Brak transmisji danych dla uslugi klienta.\",\"status\":\"new\",\"note\":\"Zgloszenie utworzone przez konto API partnera.\"}"
```

Oczekiwany wynik: `HTTP STATUS: 200` — ten sam tykiet zwrócony bez tworzenia duplikatu.

---

### Krok 4 — Lista zgłoszeń `GET /api/v1/troubleTicket` → oczekiwany status `200`

```bash
curl -s http://localhost:8080/api/v1/troubleTicket \
  -H "Authorization: Bearer TWÓJ_TOKEN"
```

Oczekiwana odpowiedź — tablica z polami `externalId`, `serviceId`, `description`, `status` (bez `notes` — zgodnie z kontraktem summary):
```json
[
  {
    "externalId": "OK-123456",
    "serviceId": 987654321,
    "description": "Brak transmisji danych dla uslugi klienta.",
    "status": "acknowledged"
  }
]
```

---

### Krok 5 — Pobierz zgłoszenie po ID `GET /api/v1/troubleTicket/{id}` → oczekiwany status `200`

```bash
curl -s http://localhost:8080/api/v1/troubleTicket/TICKET_ID \
  -H "Authorization: Bearer TWÓJ_TOKEN"
```

Oczekiwana odpowiedź — pełna reprezentacja z listą `notes`.

---

### Krok 6 — Dodaj notatkę `POST /api/v1/troubleTicket/{id}/note` → oczekiwany status `201`

```bash
curl -s -X POST http://localhost:8080/api/v1/troubleTicket/TICKET_ID/note \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TWÓJ_TOKEN" \
  -d "{\"text\":\"Technik wyslany na miejsce. Usterka potwierdzona.\"}"
```

Oczekiwana odpowiedź `201 Created`:
```json
{
  "id": "NOTE-B2C3D4E5F6A7",
  "text": "Technik wyslany na miejsce. Usterka potwierdzona.",
  "date": "2026-06-15T10:05:00"
}
```

---

### Krok 7 — Zamknij zgłoszenie `PATCH /api/v1/troubleTicket/{id}` → oczekiwany status `200`

```bash
curl -s -X PATCH http://localhost:8080/api/v1/troubleTicket/TICKET_ID \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TWÓJ_TOKEN" \
  -d "{\"status\":\"closed\"}"
```

Oczekiwana odpowiedź: `200 OK` z `"status": "closed"`.

---

### Krok 8 — Walidacja: niedozwolony status przy tworzeniu → oczekiwany status `400`

```bash
curl -s -X POST http://localhost:8080/api/v1/troubleTicket \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TWÓJ_TOKEN" \
  -d "{\"externalId\":\"OK-BAD\",\"serviceId\":1,\"description\":\"test\",\"status\":\"closed\",\"note\":\"test\"}"
```

Oczekiwana odpowiedź:
```json
{"code":"VALIDATION_ERROR","message":"Pole status ma niedozwoloną wartość dla tej operacji."}
```

---

### Krok 9 — Brak tokenu → oczekiwany status `401`

```bash
curl -s http://localhost:8080/api/v1/troubleTicket
```

Oczekiwana odpowiedź: `401 Unauthorized`.

---

### Krok 10 — Nieistniejący tykiet → oczekiwany status `404`

```bash
curl -s http://localhost:8080/api/v1/troubleTicket/TT-NONEXISTENT \
  -H "Authorization: Bearer TWÓJ_TOKEN"
```

Oczekiwana odpowiedź:
```json
{"code":"TROUBLE_TICKET_NOT_FOUND","message":"Zgłoszenie nie istnieje albo nie jest widoczne w tenant scope użytkownika."}
```

---

### Krok 11 — Izolacja tenantów (RLS)

Utwórz token dla drugiego tenanta:
```bash
curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d "{\"tenantId\":\"tenant-OTHER\",\"userId\":\"user-2\"}"
```

Spróbuj pobrać tykiet pierwszego tenanta tokenem drugiego:
```bash
curl -s http://localhost:8080/api/v1/troubleTicket/TICKET_ID \
  -H "Authorization: Bearer TOKEN_DRUGIEGO_TENANTA"
```

Oczekiwana odpowiedź: `404` — dane innego tenanta są niewidoczne dzięki PostgreSQL Row Level Security.

---

### Krok 12 — Weryfikacja danych w bazie PostgreSQL

```bash
docker exec -it trouble-ticket-db psql -U postgres -d trouble_ticket \
  -c "SELECT id, tenant_id, external_id, status, created_at FROM trouble_ticket;"
```

```bash
docker exec -it trouble-ticket-db psql -U postgres -d trouble_ticket \
  -c "SELECT id, text, created_at FROM note;"
```

---

## Struktura projektu

```
netia-trouble-ticket-api/
├── src/
│   ├── main/java/com/netia/
│   │   ├── TroubleTicketApiApplication.java
│   │   ├── common/
│   │   │   ├── config/          # Jackson, OpenAPI, FilterConfiguration
│   │   │   ├── controller/      # HealthController, AuthController (dev only)
│   │   │   ├── dto/             # ErrorResponse
│   │   │   ├── exception/       # GlobalExceptionHandler, ApiException, ...
│   │   │   ├── idempotency/     # IdempotencyService (Redis)
│   │   │   ├── redis/           # RedisConfig
│   │   │   ├── security/        # JWT, RLS, RateLimitFilter, SecurityConfiguration
│   │   │   ├── util/            # RequestIdHolder
│   │   │   └── web/             # RequestIdFilter
│   │   └── troubleticket/
│   │       ├── controller/      # TroubleTicketController
│   │       ├── domain/          # TroubleTicket, Note, TroubleTicketStatus
│   │       ├── dto/             # Request/Response records
│   │       ├── mapper/          # TroubleTicketMapper
│   │       ├── repository/      # TroubleTicketRepository
│   │       └── service/         # TroubleTicketService
│   ├── main/resources/
│   │   ├── db/migration/
│   │   │   ├── V1__Initial_Schema.sql
│   │   │   ├── V2__RLS.sql
│   │   │   ├── V3__Add_Optimistic_Locking.sql
│   │   │   └── V4__Least_Privilege_Roles.sql
│   │   ├── logback-spring.xml
│   │   ├── application.yml
│   │   └── application-prod.yml
│   └── test/
│       ├── java/com/netia/
│       │   ├── common/security/rls/  # RLSIntegrationTest
│       │   └── troubleticket/
│       │       ├── controller/       # TroubleTicketControllerIntegrationTest
│       │       └── service/          # TroubleTicketServiceTest
│       └── resources/application-test.yml
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

---

## Decyzje techniczne

### Izolacja danych — PostgreSQL Row Level Security (RLS)

Dane każdego tenanta są izolowane na poziomie silnika bazy danych. Migracja `V2__RLS.sql` tworzy politykę `USING (tenant_id = current_setting('app.current_tenant', true))`. Klasa `TenantStatementInspector` (Hibernate `StatementInspector`) automatycznie poprzedza każde zapytanie SQL komendą `SET LOCAL app.current_tenant = '<id>'` wyciągniętą z JWT. Dzięki temu nawet jeśli programista zapomni filtra `WHERE tenant_id = ?`, baza danych i tak zwróci tylko dane właściwego tenanta.

### Idempotencja dwuwarstwowa

Operacja `POST /troubleTicket` jest idempotentna na dwa sposoby:

1. **Redis (warstwa szybka)** — nagłówek `X-Idempotency-Key` mapowany atomową operacją `SETNX` na wynik. Kolejne próby z tym samym kluczem natychmiast zwracają zbuforowany `ticketId` bez obciążania bazy.
2. **Unikalny indeks w PostgreSQL** na `(tenant_id, external_id)` — siatka bezpieczeństwa na wypadek niedostępności Redis lub race condition. `DataIntegrityViolationException` jest przechwytywany, a istniejący rekord zwracany.

### Java 21 Virtual Threads

Włączone przez `spring.threads.virtual.enabled: true`. Każde żądanie HTTP obsługiwane jest przez lekki wątek wirtualny zamiast wątku OS. Dla API blokującego się na bazie danych i Redis daje znaczący wzrost przepustowości bez przepisywania kodu na reaktywny (WebFlux/R2DBC).

### Dlaczego nie Reactive (WebFlux/R2DBC)

R2DBC wymaga jawnych `JOIN`-ów zamiast `@EntityGraph`, komplikuje zarządzanie transakcjami i nie współpracuje z JPA/Hibernate. Virtual Threads dają porównywalną skalowalność przy zachowaniu prostego, synchronicznego kodu.

### HikariCP — rozmiar puli połączeń

`maximum-pool-size: 10`, `minimum-idle: 10` (stała pula). Formuła: `(CPU cores × 2) + spindles ≈ 10` na jedną replikę. Przy skalowaniu w Kubernetes obowiązuje niezmiennik: `liczba replik × 10 < max_connections w PostgreSQL`. Przy większej liczbie replik należy wdrożyć **PgBouncer** jako proxy puli połączeń.

### Migracje Flyway — strategia

Domyślnie Flyway uruchamia się przy starcie aplikacji (`FLYWAY_ENABLED=true`), co upraszcza środowisko lokalne. W produkcji zalecane jest uruchamianie migracji jako osobny krok (K8s Job / initContainer) z `FLYWAY_ENABLED=false` w podach aplikacji — eliminuje to problem blokad przy równoczesnym starcie wielu replik i pozwala stosować oddzielną rolę DDL z ograniczonymi uprawnieniami.

### Autentykacja — JWT lokalnie, Keycloak w produkcji

`JwtTokenProvider` (HS256) służy wyłącznie do lokalnego generowania tokenów i testów. W produkcji zastępuje go Spring Security OAuth2 Resource Server z weryfikacją tokenów przez JWKS endpoint Keycloak:

```yaml
spring.security.oauth2.resourceserver.jwt.issuer-uri: https://keycloak.example.com/realms/netia
```

### Optimistic Locking

Encja `TroubleTicket` posiada kolumnę `@Version`. Dwa równoczesne żądania `PATCH` na tym samym tykiecie powodują `ObjectOptimisticLockingFailureException`, który `GlobalExceptionHandler` mapuje na `409 Conflict` zamiast cichego nadpisania danych.

### Rate Limiting

`RateLimitFilter` używa Redis (`INCR` + `EXPIRE`) do zliczania żądań per tenant. Zapewnia wspólny licznik dla wszystkich replik aplikacji — w przeciwieństwie do in-memory `ConcurrentHashMap`, który byłby lokalny dla każdego poda.

### Separacja ról bazy danych

Migracja `V4__Least_Privilege_Roles.sql` tworzy rolę `ticket_app_user` z uprawnieniami wyłącznie DML (SELECT, INSERT, UPDATE, DELETE). W produkcji aplikacja łączy się jako `ticket_app_user` (`DB_USER=ticket_app_user`), a migracje Flyway uruchamiane są przez osobny K8s Job jako `postgres`. Dzięki temu nawet w przypadku kompromitacji aplikacji atakujący nie może modyfikować schematu bazy.

### Strukturalne logi JSON

`logback-spring.xml` używa `logstash-logback-encoder` z profilem:
- **dev** — czytelny kolorowy tekst z MDC w nawiasach kwadratowych
- **prod** — jeden obiekt JSON per linię na stdout, gdzie `requestId` i `tenantId` są top-level kluczami

Fluentd/Fluent Bit zbiera logi z kontenera bez żadnych reguł grok — gotowe do indeksowania w Elasticsearch i wizualizacji w Kibana.

---

## Przyjęte założenia

| Temat | Założenie |
|---|---|
| `status` przy tworzeniu | Klient przesyła wyłącznie `new`. API zwraca `acknowledged` (symulacja SOZ). |
| `status` przy aktualizacji | Publiczny klient może zmieniać status wyłącznie na `closed`. |
| Paginacja listy | v1 nie wspiera paginacji — nałożono server-side limit 500 najnowszych rekordów jako zabezpieczenie przed OOM. |
| `serviceId` | Uproszczone pole `BIGINT` top-level. W pełnym profilu TMF621 byłoby zagnieżdżonym obiektem `relatedEntity`. |
| Izolacja 404 vs 403 | Zasób niewidoczny w tenant scope zwraca `404` (nie `403`) — zapobiega enumeracji IDOR. |
| Endpoint `/auth/token` | Dostępny wyłącznie poza profilem `prod` — służy do lokalnych testów i demonstracji. |
| Generowanie kodu z OpenAPI | DTOs i kontrolery napisane ręcznie jako Java records zamiast generowania przez `openapi-generator-maven-plugin`. Decyzja świadoma: dla minimalnego profilu v1 ręczne rekordy są prostsze, czytelniejsze i łatwiejsze do testowania. Przy rozbudowie do v2 warto rozważyć generator. |
| Separacja ról DB | Migracja `V4__Least_Privilege_Roles.sql` tworzy rolę `ticket_app_user` (tylko DML). Docker Compose używa `postgres` dla uproszczenia lokalnego środowiska. W produkcji aplikacja łączy się jako `ticket_app_user` (`DB_USER=ticket_app_user`), migracje uruchamia osobny K8s Job jako `postgres`. |
| Format logów | `logback-spring.xml` z `logstash-logback-encoder`: profil dev — czytelny tekst kolorowy, profil `prod` — strukturalne JSON na stdout (Fluentd/Logstash bez grepów). MDC fields `requestId` i `tenantId` są top-level kluczami w każdym log-line. |

---

## Uruchamianie testów

```bash
# Wymaga lokalnego Docker (Testcontainers uruchamia PostgreSQL w kontenerze)
mvn test
```

Testy obejmują:
- **Unit testy** (`TroubleTicketServiceTest`) — logika biznesowa z Mockito, bez Spring kontekstu
- **Testy integracyjne** (`TroubleTicketControllerIntegrationTest`) — pełny stack z prawdziwą bazą danych
- **Testy RLS** (`RLSIntegrationTest`) — weryfikacja izolacji tenantów na poziomie PostgreSQL

---

## Przydatne komendy

```bash
# Zatrzymaj środowisko
docker-compose down

# Zatrzymaj i usuń dane bazy
docker-compose down -v

# Logi aplikacji na żywo
docker-compose logs -f application

# Połącz się z PostgreSQL
docker exec -it trouble-ticket-db psql -U postgres -d trouble_ticket

# Połącz się z Redis
docker exec -it trouble-ticket-redis redis-cli

# Metryki Prometheus
curl http://localhost:8080/actuator/prometheus

# Health check z detalami zależności (DB + Redis)
curl http://localhost:8080/actuator/health/readiness
```
