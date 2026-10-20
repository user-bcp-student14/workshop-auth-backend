# Contrato actual de endpoints

Verificado el 2026-10-19 contra el codigo actual y una instancia local aislada
de Quarkus 3.27.4, perfil prod, con datos y clave RSA generados solo para esta
documentacion. H-05 se verifico despues con la suite de contrato y fallos aislados;
no se utilizaron cuentas reales.
Los cambios autorizados respecto al legado (JWT y CORS) estan registrados en
[PLAN.md](../PLAN.md); el seguimiento esta en [HALLAZGOS.md](../HALLAZGOS.md).

## Conexion y preparacion

- URL local habitual: `http://127.0.0.1:8080`. El puerto se configura en
  [application.properties:1](../src/main/resources/application.properties#L1).
  No hay prefijo adicional para las rutas siguientes. En prod escucha por
  defecto en `0.0.0.0`; los ejemplos se verificaron en un puerto temporal.
- Para arrancar son obligatorios `AUTH_TOKEN_PRIVATE_KEY_PATH` (PEM PKCS#8 RSA,
  minimo 2048 bits), `AUTH_TOKEN_ISSUER`, `AUTH_TOKEN_AUDIENCE` y
  `AUTH_CORS_ORIGINS` (origenes HTTP(S) exactos separados por coma, sin rutas).
  No hay clave de firma predeterminada.
- No se crean automaticamente las antiguas cuentas student/trainer. Para
  aprovisionar una cuenta STUDENT en una base vacia, el operador configura
  conjuntamente `AUTH_BOOTSTRAP_USERNAME` y `AUTH_BOOTSTRAP_PASSWORD`. Una
  cuenta existente no cambia su clave. Las credenciales de los ejemplos son
  exclusivas de la instancia temporal, no funcionan en otra instalacion.
- Migracion de SQLite, respaldo, aprovisionamiento y ejecucion:
  [README.md](../README.md). No desplegar este laboratorio en Internet.
- CORS admite POST/OPTIONS y headers `accept`, `authorization`, `content-type`,
  `x-requested-with`. CORS no equivale a autenticacion ni afecta a clientes
  servidor-a-servidor que no envian Origin.

Evidencia: [application.properties:4](../src/main/resources/application.properties#L4),
[LegacyDatabase.java:73](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L73)
y [TokenService.java:34](../src/main/java/com/bcp/workshop/auth/TokenService.java#L34).

## POST /auth/login

### 1. Que hace

Comprueba las credenciales de una cuenta existente y entrega su identidad, rol
y un token para que los consumidores puedan reconocer al usuario autenticado.
No crea cuentas ni modifica contrasenas.

### 2. Autenticacion y headers

No requiere autenticacion previa ni header `Authorization`. Las credenciales
se envian en el cuerpo, no mediante HTTP Basic.

```http
POST /auth/login
Content-Type: application/json
Accept: application/json
```

En navegador, `Origin` debe corresponder a la allowlist del operador; el
preflight usa OPTIONS. La aplicacion no verifica un Bearer token en esta ruta.
Evidencia: [AuthResource.java:12](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L12).

### 3. Request

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| username | string | Si | Nombre almacenado de la cuenta. No puede ser null, vacio ni solo espacios. Se usa exactamente como llega, sin trim para la consulta. |
| password | string | Si | Contrasena de la cuenta. No puede ser null, vacia ni solo espacios; los espacios adicionales son significativos. |
| client | string o null | No | Identificador del cliente declarado en el DTO; actualmente no se valida, no se usa para autenticar y no cambia el token. Puede omitirse. |

Ejemplo completo, usado en la comprobacion HTTP con cuenta sintetica:

```json
{
  "username": "docs-student",
  "password": "Docs-test-only!",
  "client": "docs-example"
}
```

La precedencia de validacion es cuerpo nulo, username y password. Se ignoran
propiedades JSON desconocidas. No hay validacion de longitud o patron declarada
para estos campos; esto no implica ausencia de limites HTTP del servidor.
Enviar los tipos indicados, sin depender de coerciones de Jackson.

Evidencia: [LoginRequest.java:3](../src/main/java/com/bcp/workshop/auth/LoginRequest.java#L3),
[AuthResource.java:43](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L43),
[application.properties:8](../src/main/resources/application.properties#L8).

### 4. Response exitosa

**HTTP 200**, `Content-Type: application/json; charset=UTF-8`.

| campo | tipo | descripcion |
|---|---|---|
| token | string | JWT firmado RS256; no es el antiguo Base64 con secreto. |
| expiresIn | integer | Duracion del token en segundos: 3600. |
| user | object | Identidad publica; no contiene id de base, contrasena ni hash. |
| user.username | string | Nombre almacenado de la cuenta autenticada. |
| user.displayName | string | Nombre visible almacenado, escapado mediante escapeHtml4. No hacer una segunda interpretacion HTML insegura. |
| user.role | string | Rol almacenado. No hay enum ni lista cerrada validada por este endpoint; una cuenta aprovisionada por bootstrap recibe STUDENT. |
| debug | string | Valor actual fijo: authenticated-with-legacy-flow, incluso con JWT. |

Ejemplo completo capturado de la instancia temporal. El token corresponde a
una clave de prueba ya descartada y a un instante concreto: no es una
credencial reutilizable ni un valor que deba copiarse en clientes.

```json
{
  "token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJkb2NzLXRlc3QtaXNzdWVyIiwiYXVkIjoiZG9jcy10ZXN0LWF1ZGllbmNlIiwic3ViIjoiZG9jcy1zdHVkZW50Iiwicm9sZSI6IlNUVURFTlQiLCJpYXQiOjE3OTI0NDYyNTUsImV4cCI6MTc5MjQ0OTg1NSwianRpIjoiZjgwNGEwNDEtYTQ4OS00Y2NiLTllMjctYTUzZTk0MDMyNzM0In0.HnD21GwEmlOF02v2HfN_ng6_4ADw5VLQLqTfqlc2v2jb7T9bMTBUzZ8tY_kbH-8XlU-13-Impsy9-VoZRvntOUmLMuYPaELVS9VmQ9z4pizZx4khps2WzJnkIHtFSmibMHdkrVVLDBfi90uyQ3XbI9sPoLlzCgX9JLfjUN1FPeErYNVqQjs-Bb83cCoxLiaKebBefjAaS9MreZF9DUiBSlYPOXJhawVJDIUy1KhNfmAKVwlZUMiJczKAHwLV_GOjFURo5v3SmeMTGnYaXGJ-Ye8ZBWTpqn739yR-h6PWC8YTmEFABEAg3zjxW_aI8I3q1lIpzTp4W69ZhhNF9vobwQ",
  "expiresIn": 3600,
  "user": {
    "username": "docs-student",
    "displayName": "docs-student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

El orden de propiedades JSON no forma parte del contrato. Los tiempos y la
firma cambian entre emisiones.

Claims establecidos por la aplicacion dentro del token (no son campos
adicionales del cuerpo de login):

| campo | tipo | descripcion |
|---|---|---|
| sub | string | user.username. |
| role | string | user.role; no se publica un claim groups por codigo de la aplicacion. |
| iss | string | Emisor configurado por el operador. |
| aud | string | Audiencia configurada, emitida como string; el consumidor debe comparar el valor exacto, no una coincidencia parcial. Para la API del workshop es `workshop-requirements-api`. |
| iat | integer | Instante de emision, segundos Unix UTC. |
| exp | integer | iat + 3600, segundos Unix UTC. |

La libreria puede incorporar otros metadatos JWT; no se exige al cliente
rechazar claims adicionales. El algoritmo de firma se fija en RS256. El
header JOSE incluye `kid`, derivado del thumbprint SHA-256 de los miembros
publicos RSA `e`, `kty` y `n` publicados por el JWKS.

Evidencia: [LoginResponse.java:5](../src/main/java/com/bcp/workshop/auth/LoginResponse.java#L5),
[AuthResource.java:34](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L34)
y [TokenService.java:50](../src/main/java/com/bcp/workshop/auth/TokenService.java#L50).

## GET /auth/.well-known/jwks.json

Publica la clave RSA publica que corresponde al `kid` de los JWT vigentes.
No requiere autenticacion y responde HTTP 200 con `application/json`:

```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "<thumbprint-publico>",
      "n": "<modulo-base64url>",
      "e": "<exponente-base64url>"
    }
  ]
}
```

La respuesta incluye `Cache-Control: public, max-age=300` y solo parametros
publicos; nunca contiene `d` ni otros parametros privados. El endpoint se
destina a consumidores servidor-a-servidor y no reemplaza la configuracion
CORS del login.

Para validar un JWT, el consumidor debe seleccionar la clave por `kid`,
permitir unicamente RS256 y comprobar firma, expiracion, issuer y audience
exactos. El issuer recomendado para este workshop es
`workshop-auth-backend`; para `workshop-requirements-api`, la audience es
`workshop-requirements-api`. En contenedores, configurar la URL interna
alcanzable del servicio auth.

### 5. Respuestas de error

Las primeras cinco ramas son explicitas en el Resource. Los ejemplos 400/401
se comprobaron por HTTP; el 500 se verifica con una excepcion sintetica en
[AuthResourceTest.java:67](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L67).
No es necesario provocar un fallo real del almacenamiento para integrarse.

| codigo | cuando ocurre | ejemplo completo del cuerpo |
|---|---|---|
| 400 | Cuerpo ausente o JSON null llega como request nulo. | `{"message":"Request is null"}` |
| 400 | Falta username, es null, vacio o solo espacios. | `{"message":"Username is required"}` |
| 400 | username valido y password ausente, null, vacia o solo espacios. | `{"message":"Password is required"}` |
| 401 | Cuenta inexistente o clave incorrecta. Los fallos de almacenamiento no se confunden con credenciales invalidas. | `{"message":"Invalid user docs-student or password"}` |
| 500 | Excepcion inesperada durante login, incluido fallo SQL. Con o sin mensaje interno, el cuerpo tiene unicamente message; nunca exception ni detalles internos. | `{"message":"Internal server error"}` |

Los siguientes errores se producen antes de invocar el Resource. Se observaron
en la instancia empaquetada actual; no tienen el objeto `message` anterior:

| codigo | cuando ocurre | ejemplo del cuerpo observado |
|---|---|---|
| 400 | JSON mal formado (por ejemplo `{`) o tipo no deserializable (username como objeto). | Cero bytes, sin cuerpo JSON. |
| 403 | Origin rechazado por CORS. Se comprobo con https://untrusted.example.test. | Cero bytes. |
| 405 | Metodo no soportado por la ruta; se comprobo GET /auth/login. | Cero bytes. |
| 406 | Accept no compatible; se comprobo Accept: text/plain. | Cero bytes. |
| 415 | Content-Type no compatible; se comprobo text/plain. | Cero bytes. |

No intentar parsear JSON cuando el cuerpo esta vacio. En estos casos el
Content-Type tampoco estaba presente. Estos ejemplos corresponden al perfil
prod, no a las paginas de diagnostico que pudiera mostrar dev.
No se especifican aqui errores de un proxy o gateway externo inexistente en
este repositorio.

Evidencia: [AuthResource.java:25](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L25),
[AuthExceptionMapper.java](../src/main/java/com/bcp/workshop/auth/AuthExceptionMapper.java),
[LegacyDatabase.java:66](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L66)
y configuracion CORS en
[application.properties:4](../src/main/resources/application.properties#L4).

### 6. Notas de seguridad y preguntas abiertas

- El secreto legado ya no viaja en el token. La clave privada RSA se obtiene
  de un archivo externo; JWT esta firmado, **no cifrado**, y sus claims son
  legibles. No almacenar contrasenas ni datos sensibles en ellos.
- exp esta firmado, pero el tiempo por si solo no revoca un token: **el
  consumidor debe validar firma RS256, exp, iss y aud** con la clave publica
  confiable. Este backend emite tokens; no implementa endpoints consumidores
  protegidos ni demuestra que otros servicios rechacen tokens vencidos.
- Las contrasenas se verifican con BCrypt con salt sobre Base64(SHA-256),
  sin consultar contrasenas en claro
  ([AuthService.java:17](../src/main/java/com/bcp/workshop/auth/AuthService.java#L17),
  [PasswordHasher.java:28](../src/main/java/com/bcp/workshop/auth/PasswordHasher.java#L28)).
- H-05 cambia intencionalmente el contrato legado de 500 con la aprobacion
  humana de PLAN.md: respuesta generica y fallo SQL como 500, no 401.
  El servidor registra una sola vez clase y stack del fallo, omitiendo mensajes
  arbitrarios de excepcion para evitar secretos en logs. El 401 aun refleja
  username. No registrar cuerpos de login ni tokens en clientes.
  Usar transporte TLS al integrar fuera de local.
- No existen en el codigo inspeccionado endpoints de refresh, logout o
  revocacion. No asumir que un token legado se acepta como JWT.
- **Preguntas abiertas:** valores finales de issuer/audience, distribucion y
  rotacion de clave publica, consumidores preparados y URL/TLS del despliegue.
  Deben acordarse con el operador, no se deducen de este repositorio.

## GET /q/health

### 1. Que hace

Permite al operador comprobar si la instancia informa un estado de salud
correcto para monitorizacion; no confirma por si sola que un usuario pueda
iniciar sesion o que la base de credenciales responda.

### 2. Autenticacion y headers

No requiere autenticacion ni header Authorization en la configuracion actual.
Solicitud recomendada desde un monitor o cliente servidor-a-servidor:

```http
GET /q/health
Accept: application/json
```

No enviar Content-Type ni cuerpo si no se necesita. La extension respondio
JSON incluso con Accept: text/plain; no comparte la negociacion JAX-RS del
login. En navegador, GET con Origin fue rechazado por la politica CORS actual,
incluso con origen incluido, porque solo permite POST/OPTIONS.

Evidencia: extension quarkus-smallrye-health declarada en [pom.xml](../pom.xml)
y [application.properties:6](../src/main/resources/application.properties#L6).
No hay un Resource ni restricciones de autenticacion propios para esta ruta.

### 3. Request

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| Ninguno | No aplica | No | No requiere campos de cuerpo, parametros de ruta ni query definidos por la aplicacion. |

**Ejemplo real:** `GET /q/health` sin cuerpo. No existe un JSON de request
requerido. Para mostrar explicitamente el caso JSON pedido en esta
documentacion, se comprobo tambien un GET con cuerpo `{}`: el handler lo ignora
y devuelve el mismo estado. No es necesario enviarlo ni debe asumirse que un
proxy lo admite:

```json
{}
```

La ruta es provista por la extension, no por un DTO del proyecto.

### 4. Response exitosa

**HTTP 200**, cuerpo JSON completo observado:

```json
{
  "status": "UP",
  "checks": []
}
```

| campo | tipo | descripcion |
|---|---|---|
| status | string | Estado agregado; UP en la instancia actual comprobada. |
| checks | array | Resultados de comprobaciones de salud. En la instalacion actual es vacio; el proyecto no declara checks propios. No se inventa un esquema de elementos inexistentes. |

El estado UP con checks vacio no acredita conectividad a SQLite ni verificacion
de tokens. El formato lo genera SmallRye Health, no LoginResponse.
Evidencia de version y extension: [pom.xml:13](../pom.xml#L13) y
[pom.xml](../pom.xml).

### 5. Respuestas de error

| codigo | cuando ocurre | ejemplo del cuerpo |
|---|---|---|
| 403 | Peticion con Origin rechazada por la politica CORS; se verifico GET con un origen permitido y uno no permitido. | Cero bytes, sin JSON. |
| 503 | El estado agregado es DOWN. El handler SmallRyeHealthHandlerBase de la extension instalada fija este codigo cuando isDown() es true. | Pregunta abierta: no se obtuvo un cuerpo DOWN del codigo actual, que no declara checks y devuelve UP. No se inventa un check ni un mensaje de error. |

No hay ramas de error de negocio propias para salud. No se documenta 401:
no hay autenticacion configurada. Tampoco se atribuyen 405/406 del login:
la extension contesto 200 a POST y a Accept: text/plain en la comprobacion.
Esto no recomienda otros metodos; el contrato de integracion aqui es GET.

Una excepcion no controlada de infraestructura puede producir una respuesta del
framework, pero su codigo/cuerpo exacto no esta definido por codigo del proyecto
ni se reprodujo: queda abierto, no se inventa un JSON 500. Si la instancia no
arranca o no escucha, hay un error de conexion, no una respuesta HTTP de salud.

### 6. Notas de seguridad y preguntas abiertas

- El endpoint es accesible sin token a quien alcance el puerto. No publicar
  el laboratorio en Internet ni usar salud como prueba de autorizacion.
- La politica CORS no evita solicitudes de herramientas que omiten Origin.
  Para monitorizacion usar un cliente servidor-a-servidor.
- **Preguntas abiertas:** que checks de negocio deben existir (por ejemplo,
  SQLite), formato real DOWN cuando se incorporen y tratamiento de fallos
  inesperados. Hoy no se puede prometer deteccion de fallos de autenticacion
  a partir de un UP con checks vacio.

## Fuentes consultadas y limites de verificacion

- Documentos: [README.md](../README.md), version previa de este contrato,
  [PLAN.md](../PLAN.md), [HALLAZGOS.md](../HALLAZGOS.md),
  [REGLAS.md](../REGLAS.md) y [GOOD_PRACTICES.md](GOOD_PRACTICES.md).
- Codigo: AuthResource, LoginRequest, LoginResponse, AuthService,
  LegacyDatabase, PasswordHasher, TokenService y application.properties,
  enlazados arriba; prueba de contrato
  [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java).
- Framework: dependencias resueltas desde pom.xml, handler SmallRye Health
  instalado y comprobaciones HTTP sobre la aplicacion empaquetada actual.
  Se uso `mvn -B -ntp quarkus:build`, sin editar codigo.
- No se inspeccionaron otros componentes ni se verificaron consumidores,
  proxies o infraestructura externos. No se ejecutaron nuevamente los tests
  unitarios para esta actualizacion documental.
