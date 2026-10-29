# Spec Delta: auth-token

## Purpose

Define el contrato del token emitido por `POST /auth/login` en el componente `auth/`: formato JWT firmado con HS256, claims mínimos, vigencia, verificación de firma y expiración, y configuración segura del secreto de firma (HU-01, Issue #24).

## ADDED Requirements

### Requirement: Token JWT firmado con HS256
El token emitido en un login exitoso MUST ser un JWT firmado con HS256. Un consumidor MUST poder verificar su firma con el secreto configurado en `AUTH_TOKEN_SECRET`. El token MUST NOT ser texto plano ni Base64 sin firma.

#### Scenario: Firma verificable
- **WHEN** un consumidor verifica el token de un login exitoso con el valor de `AUTH_TOKEN_SECRET`
- **THEN** la verificación de firma es correcta

#### Scenario: Firma alterada
- **WHEN** se modifica un carácter del payload del token y se verifica con `AUTH_TOKEN_SECRET`
- **THEN** la verificación falla y el token se rechaza

#### Scenario: Secreto distinto
- **WHEN** se verifica el token con un secreto distinto del configurado
- **THEN** la verificación falla y el token se rechaza

### Requirement: Algoritmo restringido a HS256
Un consumidor que verifique el token MUST aceptar únicamente HS256. Los tokens con `alg` distinto de HS256, incluido `none`, MUST rechazarse.

#### Scenario: Algoritmo none
- **WHEN** se presenta un token con cabecera `alg: none`
- **THEN** el token se rechaza

#### Scenario: Algoritmo distinto de HS256
- **WHEN** se presenta un token firmado con otro algoritmo, como RS256
- **THEN** el token se rechaza

### Requirement: Claims obligatorios
El payload del token MUST contener los claims `sub`, `client`, `iat` y `exp`. `sub` MUST identificar al usuario autenticado. `client` MUST ser igual al valor validado de la solicitud. `iat` y `exp` MUST ser instantes en segundos Unix.

#### Scenario: Claims presentes
- **WHEN** se decodifica el token de un login exitoso
- **THEN** contiene `sub`, `client`, `iat` y `exp` con los tipos definidos

#### Scenario: Identidad y cliente correctos
- **WHEN** un usuario `student` inicia sesión con `client` `angular-workshop`
- **THEN** el claim `sub` es `student` y el claim `client` es `angular-workshop`

### Requirement: Vigencia de 3600 segundos
El token MUST tener una vigencia de exactamente 3600 segundos, de modo que `exp - iat` sea igual a 3600.

#### Scenario: Diferencia exacta
- **WHEN** se decodifica el token de un login exitoso
- **THEN** `exp - iat` es igual a 3600

### Requirement: expiresIn coherente con exp
El campo `expiresIn` de la respuesta MUST expresar los segundos restantes hasta `exp`. La diferencia absoluta entre `expiresIn` y `exp - now`, medida al construir la respuesta, MUST ser como máximo 5 segundos.

#### Scenario: Coherencia dentro de tolerancia
- **WHEN** se recibe la respuesta de un login exitoso y se calcula `exp - now` en ese instante
- **THEN** `|expiresIn - (exp - now)|` es menor o igual que 5

### Requirement: Token vencido no se acepta
Un consumidor MUST rechazar un token cuyo `exp` sea anterior al instante actual.

#### Scenario: Token expirado
- **WHEN** se verifica un token con `exp` en el pasado
- **THEN** la verificación falla y el token se rechaza

### Requirement: Secreto configurado desde el entorno
El secreto de firma MUST leerse de la variable de entorno `AUTH_TOKEN_SECRET`. El valor MUST NOT figurar en el código fuente, en logs ni en respuestas.

#### Scenario: Secreto en variable de entorno
- **WHEN** `AUTH_TOKEN_SECRET` está definido y se realiza un login exitoso
- **THEN** el token se firma con ese valor

#### Scenario: Secreto ausente fuera de laboratorio
- **WHEN** `AUTH_TOKEN_SECRET` no está definido en un perfil distinto del de laboratorio
- **THEN** la aplicación falla de forma explícita y no emite tokens

### Requirement: Sin secreto predeterminado fuera de laboratorio
Fuera de un perfil exclusivamente de laboratorio, el sistema MUST NOT usar un secreto de firma predeterminado ni literal. El secreto `WORKSHOP_SECRET_2026` MUST NOT ser usado en el camino de emisión de tokens fuera de laboratorio.

#### Scenario: Sin fallback en producción
- **WHEN** se ejecuta el login en un perfil distinto del de laboratorio sin `AUTH_TOKEN_SECRET`
- **THEN** no se firma ningún token con `WORKSHOP_SECRET_2026` ni con otro valor predeterminado

### Requirement: Longitud mínima del secreto
El valor de `AUTH_TOKEN_SECRET` MUST tener al menos 32 bytes. Un valor más corto MUST provocar un fallo explícito y no emitir tokens.

#### Scenario: Secreto corto
- **WHEN** `AUTH_TOKEN_SECRET` tiene menos de 32 bytes
- **THEN** la aplicación falla de forma explícita y no emite tokens

### Requirement: Logs sin tokens ni secretos
Los logs generados durante el login MUST NOT contener el token emitido, el valor de `AUTH_TOKEN_SECRET`, la contraseña ni el valor de `password`.

#### Scenario: Revisión de logs de login exitoso
- **WHEN** se realiza un login exitoso mientras se capturan los logs
- **THEN** los logs no contienen el token, el secreto ni la contraseña

#### Scenario: Revisión de logs de error
- **WHEN** falla la emisión de un token mientras se capturan los logs
- **THEN** los logs no contienen el secreto, la contraseña ni ningún token
