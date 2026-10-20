# Workshop Auth Backend — Java Quarkus

> **Laboratorio intencionalmente vulnerable.** Ejecutar únicamente en local, repositorios privados y ambientes aislados. No desplegar en Internet, CERT o PROD.

Backend Quarkus que autentica usuarios de laboratorio almacenados en SQLite y entrega un token consumido por el frontend Angular y la API Python.

## Ejecución

Requisitos: JDK 21 y Maven 3.9+.

Dependencias alineadas con Quarkus BOM 3.27.6; Commons Text 1.14.0 es
administrado por ese BOM, sin override. Inventario, avisos contrastados y
limites de la comprobacion: [evidencia SCA H-06](evidencias/31-h06-sca.md).

```bash
mvn quarkus:dev -Dquarkus.enforceBuildGoal=false
```

Servicio: `http://localhost:8080`

### Configuracion obligatoria antes de arrancar

Definir en el entorno del proceso (sin guardar secretos en Git):

| Variable | Uso |
|---|---|
| `AUTH_TOKEN_PRIVATE_KEY_PATH` | Archivo PEM PKCS#8 de clave privada RSA, minimo 2048 bits; proteger permisos. |
| `AUTH_TOKEN_ISSUER` | Identificador del emisor; para la API del workshop usar `workshop-auth-backend`. |
| `AUTH_TOKEN_AUDIENCE` | Identificador del consumidor; para la API del workshop usar `workshop-requirements-api`. |
| `AUTH_CORS_ORIGINS` | Origenes HTTP(S) exactos separados por coma, sin rutas ni wildcard. |

No hay valores de produccion por defecto para estos cuatro parametros. Una
configuracion ausente o invalida impide el arranque. CORS permite POST/OPTIONS
y mantiene los headers accept, authorization, content-type, x-requested-with.

`AUTH_DB_URL` conserva su valor por defecto `jdbc:sqlite:./data/workshop.db`.
`AUTH_PASSWORD_COST` usa 12 por defecto. La calibracion local de BCrypt
hash+verificacion dio medianas de 315/499/941 ms para costes 10/11/12;
se eligio 12 priorizando resistencia al cracking. Son mediciones de desarrollo,
no un SLA de produccion. Las pruebas usan coste 4 para velocidad y no configuran
credenciales reales.

El emisor publica su clave RSA publica actual en
`GET http://127.0.0.1:8080/auth/.well-known/jwks.json`. La respuesta incluye
el `kid` del JWT y nunca publica la clave privada. El endpoint esta destinado
a consumidores servidor-a-servidor. En contenedores, configurar en la API la
URL alcanzable del servicio auth mediante `AUTH_JWKS_URL`.

Para una base vacia, aprovisionar explicitamente una cuenta STUDENT con
`AUTH_BOOTSTRAP_USERNAME` y `AUTH_BOOTSTRAP_PASSWORD`. Deben configurarse juntas.
No se crean cuentas conocidas automaticamente y no se cambia la contrasena de
una cuenta existente. Quitar ambas variables despues del aprovisionamiento.
Las cuentas legadas existentes conservan identidad, rol y contrasena tras migrar.

### Migracion y coordinacion antes del despliegue

1. Detener todas las instancias que escriben la base; no ejecutar dos
   migraciones concurrentes. Realizar un respaldo consistente de SQLite (por
   ejemplo con su API de backup) y verificar una restauracion en un archivo
   aislado. Custodiar el respaldo: contiene contrasenas antiguas.
2. Configurar las variables anteriores y arrancar. La migracion transaccional
   usa `PRAGMA user_version=1`, sustituye password por password_hash y conserva
   cuentas e ids; un fallo revierte la transaccion e impide el arranque.
   No hay fallback a texto plano. Reiniciar no vuelve a hashear.
3. Para restaurar, detener el servicio, preservar la base fallida para analisis
   controlado y restaurar el respaldo con la version de aplicacion compatible.
   No arrancar la version antigua sobre la base migrada.
4. La migracion elimina el texto plano del esquema activo, pero no garantiza
   borrado forense de paginas SQLite, journals ni respaldos. Acordar su
   retencion y saneamiento seguro con el operador; no se borran automaticamente.
5. Coordinar consumidores antes de activar JWT: verificar RS256 con la clave
   publica distribuida por un canal confiable, issuer, audience y expiracion.
   Los tokens legados no son JWT; no hay fallback inseguro. Planificar
   reautenticacion y rotacion. Este repositorio no modifica otros componentes.

### Pruebas y cobertura

```bash
mvn -B -ntp clean verify
```

La suite genera sus propias bases temporales y claves RSA dentro de target,
las elimina al terminar y no necesita secretos ni cuentas del desarrollador.
JaCoCo combina pruebas JUnit y Quarkus sin doble instrumentacion y exige al
menos 85% de lineas cubiertas. El reporte esta en target/site/jacoco/index.html.
Las evidencias de esta implementacion estan en
[evidencias_test_post.txt](evidencias_test_post.txt) y [evidencias/](evidencias/).
El contrato y los cambios autorizados se detallan en [ENDPOINTS.md](docs/ENDPOINTS.md).

## Endpoints

- `POST /auth/login`
- `GET /auth/.well-known/jwks.json`
- `GET /q/health`

## Propósito de la rama `main`

La rama partio con deuda tecnica deliberada. El estado vigente y su evidencia
se mantienen exclusivamente en [HALLAZGOS.md](HALLAZGOS.md); no debe considerarse
un servicio apto para produccion por haber corregido cuatro hallazgos.
