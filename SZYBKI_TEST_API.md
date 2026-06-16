# Szybki test API (Windows CMD)

## 1. Uruchomienie

```cmd
docker-compose up --build
```

Poczekaj aż w logach pojawi się `Started TroubleTicketApiApplication`. Trwa ~30-60 sek.

## 2. Sprawdź czy działa

```cmd
curl http://localhost:8080/health
```

Powinno zwrócić: `{"status":"UP",...}`

## 3. Pobierz token

```cmd
curl -s -X POST http://localhost:8080/auth/token -H "Content-Type: application/json" -d "{\"tenantId\":\"tenant-netia\",\"userId\":\"user-1\"}"
```

Odpowiedź (przykład):
```json
{"token":"eyJhbGciOiJIUzUxMiJ9.xxx.yyy","tenantId":"tenant-netia","userId":"user-1"}
```

⚠️ **Skopiuj wartość `token` z odpowiedzi.** Dalej wszędzie zamiast `<TOKEN>` wstawiaj swoją rzeczywistą wartość.

## 4. Utwórz zgłoszenie (oczekiwana odpowiedź: 201)

```cmd
curl -s -X POST http://localhost:8080/api/v1/troubleTicket -H "Content-Type: application/json" -H "Authorization: Bearer <TOKEN>" -d "{\"externalId\":\"OK-123456\",\"serviceId\":987654321,\"description\":\"Brak transmisji danych.\",\"status\":\"new\",\"note\":\"Testowa notatka.\"}"
```

Odpowiedź (przykład):
```json
{"id":"TT-1781641955380-9445EB4F","externalId":"OK-123456",...,"status":"acknowledged"}
```

⚠️ **Skopiuj wartość `id` z odpowiedzi** (np. `TT-1781641955380-9445EB4F`). Dalej wszędzie zamiast `<TICKET_ID>` wstawiaj tę wartość.

## 5. Lista zgłoszeń (oczekiwana odpowiedź: 200)

```cmd
curl -s http://localhost:8080/api/v1/troubleTicket -H "Authorization: Bearer <TOKEN>"
```

## 6. Pobierz zgłoszenie po ID (oczekiwana odpowiedź: 200)

⚠️ Zamień `<TICKET_ID>` na rzeczywiste ID z kroku 4!

```cmd
curl -s http://localhost:8080/api/v1/troubleTicket/<TICKET_ID> -H "Authorization: Bearer <TOKEN>"
```

Przykład z rzeczywistym ID:
```cmd
curl -s http://localhost:8080/api/v1/troubleTicket/TT-1781641955380-9445EB4F -H "Authorization: Bearer <TOKEN>"
```

## 7. Dodaj notatkę (oczekiwana odpowiedź: 201)

```cmd
curl -s -X POST http://localhost:8080/api/v1/troubleTicket/<TICKET_ID>/note -H "Content-Type: application/json" -H "Authorization: Bearer <TOKEN>" -d "{\"text\":\"Technik wyslany na miejsce.\"}"
```

## 8. Zamknij zgłoszenie (oczekiwana odpowiedź: 200)

```cmd
curl -s -X PATCH http://localhost:8080/api/v1/troubleTicket/<TICKET_ID> -H "Content-Type: application/json" -H "Authorization: Bearer <TOKEN>" -d "{\"status\":\"closed\"}"
```

## 9. Błędy — weryfikacja

Brak tokenu (oczekiwana odpowiedź: 401):
```cmd
curl -s http://localhost:8080/api/v1/troubleTicket
```

Nieistniejące zgłoszenie (oczekiwana odpowiedź: 404):
```cmd
curl -s http://localhost:8080/api/v1/troubleTicket/TT-FAKE -H "Authorization: Bearer <TOKEN>"
```

Niedozwolony status (oczekiwana odpowiedź: 400):
```cmd
curl -s -X POST http://localhost:8080/api/v1/troubleTicket -H "Content-Type: application/json" -H "Authorization: Bearer <TOKEN>" -d "{\"externalId\":\"BAD\",\"serviceId\":1,\"description\":\"t\",\"status\":\"closed\",\"note\":\"t\"}"
```

## 10. Zatrzymanie

```cmd
docker-compose down
```

Usunąć dane bazy:
```cmd
docker-compose down -v
```

---

**Swagger UI:** http://localhost:8080/swagger-ui.html
