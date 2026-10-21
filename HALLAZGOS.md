# Hallazgos del backend de autenticacion

Matriz de seguimiento de los siete casos de [RETO.md](RETO.md), ordenada por
severidad. Actualizada el 2026-10-19 tras implementar el alcance aprobado de
[PLAN.md](PLAN.md). Es el unico registro de estados. Los cambios autorizados
de token y CORS estan documentados en [ENDPOINTS.md](docs/ENDPOINTS.md);
los demas campos y respuestas HTTP se conservaron salvo la correccion de
errores H-05 aprobada posteriormente.

| id | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|
| H-01 | SQL: concatenacion de credenciales | Vulnerabilidad | Permitia alterar el SELECT y autenticar otra identidad. Resuelto con PreparedStatement; la consulta final busca por username y el servicio verifica el hash. La regresion de inyeccion fallo antes y pasa despues. | [LegacyDatabase.java:23](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L23), [LegacyDatabase.java:56](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L56), [LegacyDatabase.java:57](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L57) | alta | Resuelto |
| H-02 | Datos: contrasenas en texto plano | Vulnerabilidad | Leer la base exponia contrasenas. Resuelto con BCrypt con salt, prehash SHA-256/Base64 para no truncar claves largas y migracion transaccional versionada a password_hash. No hay semillas conocidas en produccion ni fallback; respaldo y paginas antiguas requieren tratamiento operativo. | [PasswordHasher.java:29](src/main/java/com/bcp/workshop/auth/PasswordHasher.java#L29), [PasswordHasher.java:42](src/main/java/com/bcp/workshop/auth/PasswordHasher.java#L42), [CredentialMigration.java:28](src/main/java/com/bcp/workshop/auth/CredentialMigration.java#L28), [AuthService.java:19](src/main/java/com/bcp/workshop/auth/AuthService.java#L19) | alta | Resuelto |
| H-03 | Token: Base64 con secreto embebido y sin firma | Vulnerabilidad | Permitia leer el secreto y alterar campos sin proteccion criptografica. Resuelto localmente con JWT RS256, claims explicitos, clave RSA externa y duracion compartida de 3600 s. Pruebas independientes comprueban firma, alteracion, clave incorrecta y expiracion; integracion externa pendiente de coordinacion, no verificada por este repositorio. | [TokenService.java:25](src/main/java/com/bcp/workshop/auth/TokenService.java#L25), [TokenService.java:34](src/main/java/com/bcp/workshop/auth/TokenService.java#L34), [TokenService.java:53](src/main/java/com/bcp/workshop/auth/TokenService.java#L53), [TokenService.java:54](src/main/java/com/bcp/workshop/auth/TokenService.java#L54) | alta | Resuelto |
| H-04 | Configuracion: secreto fijo y CORS permisivo | Vulnerabilidad | La configuracion fija impedia rotar secretos y admitia cualquier origen. Resuelto con archivo externo de clave, issuer/audience y allowlist obligatorios por entorno, validacion al arranque y CORS POST/OPTIONS. Ausencia de cada variable impide arrancar; origen no permitido no recibe permiso CORS. | [application.properties:5](src/main/resources/application.properties#L5), [application.properties:13](src/main/resources/application.properties#L13), [CorsConfiguration.java:19](src/main/java/com/bcp/workshop/auth/CorsConfiguration.java#L19), [TokenService.java:34](src/main/java/com/bcp/workshop/auth/TokenService.java#L34) | media | Resuelto |
| H-05 | Errores: respuesta con detalles internos | Vulnerabilidad | Resuelto: 500 generico sin detalles; SQLException se propaga como CredentialStorageException y ya no produce 401. Log unico de clase/stack sin mensajes sensibles; EndpointFailureTest, RepositoryLifecycleTest y AuthResourceTest verdes (39 pruebas); [diff propio](evidencias/H-05.diff), [antes](evidencias/20-h05-regresion-antes.log), [despues](evidencias/21-h05-despues.log). | [AuthExceptionMapper.java:15](src/main/java/com/bcp/workshop/auth/AuthExceptionMapper.java#L15), [LegacyDatabase.java:67](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L67) | media | Resuelto |
| H-06 | SCA: Quarkus 3.27.4 y Commons Text 1.9 declarados | Sugerencia | Resuelto: BOM 3.27.6 y Text 1.14.0 administrado, sin nuevas dependencias ni supresiones; [SCA](evidencias/31-h06-sca.md) compara 49 coincidencias antes con 0 despues, sin afirmar ausencia global de CVE. LoginResponseTest y matriz HTTP/JWT/migracion verdes (41 pruebas y 5 prod); [diff propio](evidencias/H-06.diff), [resultado](evidencias/28-h06-despues.log). | [pom.xml:13](pom.xml#L13), [pom.xml:57](pom.xml#L57), [LoginResponseTest.java](src/test/java/com/bcp/workshop/auth/LoginResponseTest.java) | media | Resuelto |
| H-07 | Clean code: Resource mezcla responsabilidades | Refactor | Resuelto: Resource solo valida/adapta HTTP; AuthService.login orquesta autenticacion/emision y LoginResponse conserva el mapeo; errores reutilizan H-05. AuthServiceTest verifica emision unica, identidad almacenada y cero emisiones ante rechazo/fallo; 51 pruebas verdes, [diff propio](evidencias/H-07.diff), [antes](evidencias/34-h07-arquitectura-antes.log), [despues](evidencias/35-h07-despues.log). | [AuthResource.java:29](src/main/java/com/bcp/workshop/auth/AuthResource.java#L29), [AuthService.java:19](src/main/java/com/bcp/workshop/auth/AuthService.java#L19), [AuthServiceTest.java](src/test/java/com/bcp/workshop/auth/AuthServiceTest.java) | baja | Resuelto |

Todos los casos de RETO.md tienen evidencia en este repositorio; ninguno se
omite. H-06 confirma las versiones, no la explotabilidad de un advisory.
Si un caso deja de poder verificarse durante una revision, debe conservarse
su fila y marcarse como **No encontrado**, explicando el motivo.

## Evidencia historica de H-01 a H-04

- Antes: [02-antes.log](evidencias/02-antes.log), 12 pruebas, 4 regresiones
  fallidas y 0 errores; las pruebas de caracterizacion pasan. El intento
  anterior con fixture incompleto se conserva en 01-antes.log, no se usa
  como evidencia de correccion.
- H-01: [diff](evidencias/H-01.diff) y [7 pruebas posteriores](evidencias/03-h01-despues.log).
- H-02: [diff](evidencias/H-02.diff), [calibracion](evidencias/05-bcrypt-calibracion.log)
  y [13 pruebas posteriores](evidencias/07-h02-despues.log).
- H-03: [diff](evidencias/H-03.diff) y [7 pruebas posteriores](evidencias/08-h03-despues.log).
- H-04: [diff](evidencias/H-04.diff), [suite final](evidencias_test_post.txt)
  y [cuatro rechazos de arranque](evidencias/12-startup-missing.log).
- Suite final: 21 pruebas, 0 fallos/errores/omitidas; `mvn -B -ntp clean verify`
  exitoso y gate JaCoCo >= 85% activo. Cobertura modificada: 170/174 lineas
  ejecutables, 97.70%; global 182/186, 97.85%
  ([medicion](evidencias/cobertura-modificada.txt)).
- CodeQL y Dependency Review permanecen intactos; no se ejecutaron sus
  workflows remotos. No se declara este laboratorio seguro para produccion.

## Mapa actual del flujo de login

1. [AuthResource](src/main/java/com/bcp/workshop/auth/AuthResource.java) valida
   request, username y password, sin recortar los valores usados. Conserva
   mensajes y precedencia 400 y decision 401 para credenciales invalidas;
   delega errores inesperados al mapper generico aprobado en H-05.
2. [AuthService](src/main/java/com/bcp/workshop/auth/AuthService.java) consulta
   identidad y hash por username en el repositorio, verifica la contrasena y
   orquesta emision y mapeo de respuesta de login, fuera del Resource.
3. [LegacyDatabase](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java)
   inicializa/migra SQLite al arrancar y usa consultas parametrizadas.
   AUTH_DB_URL conserva su default; el aprovisionamiento es explicito.
4. [PasswordHasher](src/main/java/com/bcp/workshop/auth/PasswordHasher.java)
   verifica BCrypt con salt sobre Base64(SHA-256), sin limite truncado de entrada.
5. [TokenService](src/main/java/com/bcp/workshop/auth/TokenService.java) firma
   identidad, rol, emisor, audiencia y fechas, usando clave externa.
6. [LoginResponse](src/main/java/com/bcp/workshop/auth/LoginResponse.java)
   mapea identidad a DTO y escapa displayName, sin exponer hash ni entidad.
7. [Configuracion](src/main/resources/application.properties) conserva puerto
   8080, host local y perfil prod; valida origenes CORS y material de firma.
   Las pruebas HTTP usan el puerto de test 8081 y datos temporales aislados.

## Implementacion H-05 a H-07

- Aprobacion humana de PLAN.md recibida en la solicitud de implementacion,
  incluida la excepcion de contrato de H-05.
- H-05: caracterizacion previa 22/22; regresion previa 2 fallos esperados;
  despues 39/39 y JaCoCo incremental 10/10 (100%).
  [Detalle de pruebas](docs/TESTING.md#h-05-errores-controlados-implementacion-aprobada),
  [medicion](evidencias/22-h05-cobertura.txt) y
  [XML JaCoCo](evidencias/h05-jacoco.xml).
- H-06: caracterizacion previa 30/30 y 5 prod; despues 41/41 y 5 prod.
  SCA por coordenadas de todo el arbol: 49 coincidencias antes, 0 despues;
  [inventario y limites](evidencias/31-h06-sca.md),
  [cobertura global 97.89%](evidencias/32-h06-cobertura.txt).
- H-07: caracterizacion previa 33/33; prueba arquitectonica previa falla;
  despues 51/51 y 5 prod, con [cobertura incremental 100% y global 97.95%](evidencias/36-h07-cobertura.txt).
  Contratos no modificados respecto al cierre H-05; quality gates intactos.
