# Salida de ejemplo: incidencia preparada

Este objeto cumple el contrato MCP común de incidencia. Se presenta para revisión humana; no implica que haya sido aprobado ni registrado.

```json
{
  "case_id": "caso-02",
  "service": "acceso",
  "error_code": "AUTH_DEPENDENCY_UNAVAILABLE",
  "correlation_id": "LOGIN-DEMO-201",
  "first_seen": "2026-11-03T15:00:01Z",
  "last_seen": "2026-11-03T15:00:04Z",
  "occurrences": 2,
  "priority": "P2",
  "summary": "La dependencia identidad-demo no respondió correctamente durante el inicio de sesión (HTTP 503).",
  "impact": "El intento de inicio de sesión asociado a LOGIN-DEMO-201 se vio afectado; el log no confirma el resultado final para el cliente.",
  "evidence": [
    {
      "file": "caso-02-acceso.jsonl",
      "line": 2,
      "text": "{\"timestamp\":\"2026-11-03T10:00:01-05:00\",\"level\":\"ERROR\",\"service\":\"acceso\",\"correlation_id\":\"LOGIN-DEMO-201\",\"event_id\":\"evt-202\",\"code\":\"AUTH_DEPENDENCY_UNAVAILABLE\",\"http_status\":503,\"dependency\":\"identidad-demo\",\"password\":\"[REDACTADO]\"}"
    },
    {
      "file": "caso-02-acceso.jsonl",
      "line": 4,
      "text": "{\"timestamp\":\"2026-11-03T10:00:04-05:00\",\"level\":\"ERROR\",\"service\":\"acceso\",\"correlation_id\":\"LOGIN-DEMO-201\",\"event_id\":\"evt-203\",\"code\":\"AUTH_DEPENDENCY_UNAVAILABLE\",\"http_status\":503,\"dependency\":\"identidad-demo\",\"token\":\"[REDACTADO]\"}"
    }
  ],
  "missing_data": [
    "El log no confirma si el cliente pudo iniciar sesión después de estos eventos.",
    "El log no indica cuántos otros intentos o clientes pudieron verse afectados."
  ],
  "recommended_action": "Verificar manualmente la disponibilidad y los registros de identidad-demo para LOGIN-DEMO-201; confirmar el resultado del inicio de sesión antes de indicar un reintento."
}
```

Antes de registrar, solicita aprobación explícita. Si se aprueba, envía el objeto bajo `incident` junto con `approved: true`; después verifica el ticket con `consultar_incidencia`.
