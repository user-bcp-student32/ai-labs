# Entrada de ejemplo: caso-02

Caso: `caso-02`, «El cliente no puede entrar a su banca digital»  
Archivo: `caso-02-acceso.jsonl`  
Formato: un objeto JSON por línea. Los datos personales o de autenticación se muestran redactados.

```jsonl
{"timestamp":"2026-11-03T10:00:00-05:00","level":"INFO","service":"acceso","correlation_id":"LOGIN-DEMO-201","event_id":"evt-201","message":"Inicio de autenticacion","user":"[REDACTADO]"}
{"timestamp":"2026-11-03T10:00:01-05:00","level":"ERROR","service":"acceso","correlation_id":"LOGIN-DEMO-201","event_id":"evt-202","code":"AUTH_DEPENDENCY_UNAVAILABLE","http_status":503,"dependency":"identidad-demo","password":"[REDACTADO]"}
{"timestamp":"2026-11-03T10:00:01-05:00","level":"ERROR","service":"acceso","correlation_id":"LOGIN-DEMO-201","event_id":"evt-202","code":"AUTH_DEPENDENCY_UNAVAILABLE","http_status":503,"dependency":"identidad-demo","password":"[REDACTADO]"}
{"timestamp":"2026-11-03T10:00:04-05:00","level":"ERROR","service":"acceso","correlation_id":"LOGIN-DEMO-201","event_id":"evt-203","code":"AUTH_DEPENDENCY_UNAVAILABLE","http_status":503,"dependency":"identidad-demo","token":"[REDACTADO]"}
{"timestamp":"2026-11-03T10:01:00-05:00","level":"WARN","service":"acceso","correlation_id":"LOGIN-DEMO-202","event_id":"evt-204","code":"INVALID_CREDENTIALS","http_status":401}
```
