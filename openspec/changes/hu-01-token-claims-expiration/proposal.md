# Proposal

## Why

El Issue [#13](https://github.com/user-bcp-student14/workshop-auth-backend/issues/13) solicita generar el cambio a partir de HU-01. Vincular el token con el cliente declarado y hacer verificable su expiracion reduce la ambiguedad para los consumidores y evita que la respuesta anuncie una vigencia distinta de la firmada.

La baseline Dia 1 de `docs/ENDPOINTS.md`, recuperada del commit `3bde2ceb37d0cb0d327acb17d8b52f4b8db862a5`, define un login con `client` opcional y sin efecto sobre el token. Hacerlo obligatorio y reflejarlo como claim es el cambio de contrato autorizado por HU-01; se conserva el resto del contrato documentado.

## What Changes

- **BREAKING**: `POST /auth/login` requiere un `client` no vacio y lo valida. Se propone rechazar un valor ausente, nulo, vacio o compuesto solo por espacios con HTTP 400, sin emitir token. No se propone una lista cerrada de clientes porque Issue/HU no la define.
- Los tokens emitidos incluyen `sub`, `client`, `iat` y `exp`; se mantienen los claims existentes `role`, `iss` y `aud`.
- `expiresIn` conserva la duracion documentada de 3600 segundos y debe ser coherente con `exp - now`. Supuesto para revision: tolerancia maxima de un segundo, por la precision entera de los timestamps.
- Se preservan la ruta, el HTTP 200, la forma del cuerpo exitoso (`token`, `expiresIn`, `user` y `debug`), el contenido publico de `user`, y las respuestas existentes para credenciales invalidas y errores internos.
- Los logs no exponen tokens, secretos ni material de firma.
- No se cambia el mecanismo de firma establecido por la baseline (RS256); decidir o migrar la tecnologia de firma no forma parte de este cambio.

### Fuera de alcance

- Cambiar ruta, nombres o forma de los campos de respuesta, el contenido de `user` o el valor contractual de `expiresIn`.
- Cambiar la autenticacion de usuario, los codigos de error existentes salvo el nuevo rechazo de `client`, o agregar endpoints de registro, refresh, logout o revocacion.
- Cambiar la politica de claves, `issuer`, `audience`, roles o algoritmo de firma.
- Implementar consumidores externos de tokens o definir una lista de clientes permitidos que Issue/HU no especifica.
- Disenar o implementar la solucion tecnica; este cambio contiene solamente propuesta y requisitos.

## Capabilities

### New Capabilities

- `auth-token`: contrato observable del token emitido por el login, sus claims, vigencia y la vinculacion validada con el cliente.

### Modified Capabilities

- Ninguna. El inventario OpenSpec actual no contiene capabilities existentes.

## Impact

- API: `POST /auth/login`; `client` pasa de opcional a obligatorio, cambio intencional derivado de HU-01.
- Contrato de token: nuevo claim `client`, conservando claims, firma y duracion documentados.
- Seguridad observable: no registrar tokens ni secretos.
- Fuentes de alcance: Issue #13, [HU-01.md](../../../HU-01.md) y la version historica Dia 1 de [docs/ENDPOINTS.md](https://github.com/user-bcp-student14/workshop-auth-backend/blob/3bde2ceb37d0cb0d327acb17d8b52f4b8db862a5/docs/ENDPOINTS.md).