# Spec Delta

## Purpose

Define el contrato observable del token que emite `POST /auth/login`, su vinculacion validada con el cliente y las garantias de vigencia y confidencialidad que deben conservar sus consumidores.

## ADDED Requirements

### Requirement: The login response preserves its documented contract

Una autenticacion valida SHALL conservar `POST /auth/login`, responder HTTP 200 y devolver JSON con `token`, `expiresIn`, `user` y `debug`. `user` SHALL conservar `username`, `displayName` y `role`; el orden de propiedades no forma parte del contrato. El token SHALL seguir firmado como RS256 y mantener los claims existentes `sub`, `role`, `iss`, `aud`, `iat` y `exp`. La unica ampliacion autorizada por Issue #13 y HU-01 es exigir y reflejar `client` en el token.

#### Scenario: Successful login keeps the response shape
- **WHEN** un usuario existente envia username, password y un client valido a `POST /auth/login`
- **THEN** recibe HTTP 200 con `token`, `expiresIn`, `user` y `debug`, y `user` conserva `username`, `displayName` y `role`
- **AND** el cuerpo no agrega `client` como campo de respuesta ni cambia los campos existentes

### Requirement: A valid client is required for token issuance

El login SHALL requerir un `client` de tipo string que no sea nulo, vacio ni compuesto solo por espacios. Cuando sea valido, su valor SHALL aparecer sin cambios en el claim `client`. No se define una lista cerrada de clientes en Issue #13 ni en HU-01; establecer una allowlist requiere una decision posterior.

#### Scenario: Login binds the token to the submitted client
- **WHEN** las credenciales son validas y `client` contiene un string no vacio
- **THEN** la autenticacion puede completar y el claim `client` del token coincide con el valor enviado

#### Scenario: Missing or blank client is rejected
- **WHEN** `client` se omite, es nulo, vacio o contiene solo espacios
- **THEN** el login responde HTTP 400 y no emite un token

### Requirement: The token contains the required identity and validity claims

Todo token emitido SHALL contener `sub`, `client`, `iat` y `exp`. `sub` SHALL coincidir con el username autenticado; `client` SHALL coincidir con el client validado. `iat` y `exp` SHALL ser timestamps enteros Unix en segundos, con `exp` posterior a `iat`. El token SHALL conservar `role`, `iss` y `aud` de acuerdo con la baseline Dia 1.

#### Scenario: Issued token contains required claims
- **WHEN** el login responde exitosamente
- **THEN** el token contiene `sub` igual al username, `client` igual al valor validado, y los claims enteros `iat` y `exp`
- **AND** conserva `role`, `iss` y `aud`

### Requirement: The advertised lifetime matches the token expiration

La duracion documentada SHALL continuar siendo 3600 segundos y `expiresIn` SHALL continuar siendo el entero 3600. La expiracion SHALL representar esa duracion desde `iat`. Al comparar `expiresIn` con `exp - now` al formar la respuesta, la diferencia absoluta SHALL ser como maximo un segundo. Un token SHALL considerarse expirado cuando `exp` sea menor o igual al instante actual.

#### Scenario: Login reports the signed token lifetime
- **WHEN** se emite un token en un login exitoso
- **THEN** `exp - iat` es 3600 segundos, `expiresIn` es 3600 y la diferencia entre `expiresIn` y `exp - now` no supera un segundo

#### Scenario: A token past its expiration is expired
- **WHEN** un consumidor conforme evalua un token cuyo `exp` es menor o igual al instante actual
- **THEN** el token se considera expirado y no se acepta como vigente

### Requirement: Sensitive token material is not exposed in logs

El flujo de login SHALL evitar registrar el valor del token, secretos de autenticacion o material privado de firma. Esta regla aplica tanto al login exitoso como a los errores.

#### Scenario: Login does not log credentials or token material
- **WHEN** ocurre un login exitoso o fallido
- **THEN** los logs producidos por el flujo no contienen el token, el secreto de autenticacion, material privado de firma ni el password

### Requirement: Existing credential and server error behavior is preserved

Los cambios de claims SHALL NOT alterar las respuestas documentadas para username o password ausentes/vacios, credenciales invalidas ni excepciones internas. Las validaciones existentes de credenciales mantienen HTTP 400, las credenciales incorrectas mantienen HTTP 401 y los errores internos mantienen HTTP 500 con un cuerpo generico que solo contiene `message`.

#### Scenario: Invalid credentials retain their documented response
- **WHEN** username y password tienen formato valido pero no autentican una cuenta existente
- **THEN** el login responde HTTP 401 con el mensaje de credenciales invalidas documentado

#### Scenario: Internal failures remain generic
- **WHEN** ocurre una excepcion interna durante el login
- **THEN** el login responde HTTP 500 con un cuerpo generico que solo contiene `message`, sin clase ni detalles internos