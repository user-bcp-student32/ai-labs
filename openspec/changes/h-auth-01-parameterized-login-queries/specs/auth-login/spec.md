# Spec Delta

## Purpose

Define el contrato observable de `POST /auth/login` y la garantía de que las credenciales recibidas se tratan como datos y no pueden alterar la verificación de identidad (hallazgo H-AUTH-01, Issue #22).

## ADDED Requirements

### Requirement: Inicio de sesión legítimo preservado
El sistema SHALL responder `200` a `POST /auth/login` cuando `username` y `password` corresponden a un usuario registrado, con `token`, `expiresIn`, `user` (`username`, `displayName`, `role`) y el campo `debug` existente. Los campos adicionales de la solicitud SHALL ignorarse.

#### Scenario: Usuario del taller inicia sesión
- **WHEN** se envía `POST /auth/login` con `{"username":"student","password":"Workshop123!","client":"angular-workshop"}`
- **THEN** la respuesta es `200`
- **AND** `user.username` es `student`
- **AND** `token` está presente y no está vacío
- **AND** la respuesta incluye `expiresIn`, `user.displayName`, `user.role` y `debug`

#### Scenario: Segundo usuario registrado inicia sesión
- **WHEN** se envía `POST /auth/login` con `{"username":"trainer","password":"Trainer123!"}`
- **THEN** la respuesta es `200`
- **AND** `user.username` es `trainer`
- **AND** `token` está presente y no está vacío

### Requirement: Campos obligatorios preservados
El sistema SHALL responder `400` a `POST /auth/login` cuando `username` o `password` están ausentes o vacíos, sin consultar credenciales ni devolver `token`.

#### Scenario: Usuario ausente
- **WHEN** se envía `POST /auth/login` con `{"password":"Workshop123!"}`
- **THEN** la respuesta es `400`
- **AND** la respuesta no contiene `token`

#### Scenario: Contraseña vacía
- **WHEN** se envía `POST /auth/login` con `{"username":"student","password":""}`
- **THEN** la respuesta es `400`
- **AND** la respuesta no contiene `token`

### Requirement: Credenciales inválidas rechazadas
El sistema SHALL responder `401` a `POST /auth/login` cuando el par `username` y `password` no corresponde a ningún usuario registrado, sin devolver `token`.

#### Scenario: Contraseña incorrecta
- **WHEN** se envía `POST /auth/login` con `{"username":"student","password":"incorrecta"}`
- **THEN** la respuesta es `401`
- **AND** la respuesta no contiene `token`

#### Scenario: Usuario inexistente
- **WHEN** se envía `POST /auth/login` con `{"username":"desconocido","password":"Workshop123!"}`
- **THEN** la respuesta es `401`
- **AND** la respuesta no contiene `token`

### Requirement: Credenciales tratadas como datos
El sistema SHALL verificar `username` y `password` como valores literales, de modo que ningún carácter o secuencia contenido en ellos modifique la lógica de la verificación. Un valor de credencial SHALL NOT ser interpretado como parte de una sentencia SQL.

#### Scenario: Inyección en el campo usuario
- **WHEN** se envía `POST /auth/login` con `username` igual a `' OR '1'='1` y cualquier `password` no vacío
- **THEN** la respuesta es `401`
- **AND** la respuesta no contiene `token`

#### Scenario: Inyección en el campo contraseña
- **WHEN** se envía `POST /auth/login` con `username` igual a `student` y `password` igual a `' OR '1'='1`
- **THEN** la respuesta es `401`
- **AND** la respuesta no contiene `token`

#### Scenario: Comentario SQL para omitir la contraseña
- **WHEN** se envía `POST /auth/login` con `username` igual a `student'--` y `password` igual a `cualquiera`
- **THEN** la respuesta es `401`
- **AND** la respuesta no contiene `token`

#### Scenario: Inyección en ambos campos
- **WHEN** se envía `POST /auth/login` con `username` y `password` iguales a `' OR '1'='1`
- **THEN** la respuesta es `401`
- **AND** la respuesta no contiene `token`

### Requirement: Cobertura automatizada del intento de inyección
La suite de pruebas del componente `auth` SHALL incluir en `AuthResourceTest` al menos una prueba que envíe un intento de inyección SQL a `POST /auth/login` y verifique `401` sin `token`. Las pruebas SHALL ejecutarse con `mvn test` desde `auth/` y el código modificado SHALL alcanzar una cobertura de 85 % o superior.

#### Scenario: Prueba de inyección presente y en verde
- **WHEN** se ejecuta `mvn test` desde el directorio `auth/`
- **THEN** `AuthResourceTest` contiene una prueba del intento de inyección que pasa
- **AND** la prueba de inicio de sesión legítimo con `student` sigue pasando

#### Scenario: Cobertura del código modificado
- **WHEN** se genera el reporte de cobertura tras `mvn test`
- **THEN** el código modificado por este cambio tiene cobertura igual o superior a 85 %
