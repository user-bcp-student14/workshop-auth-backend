# Tasks

## 1. Caracterizar el contrato actual

- [x] 1.1 Confirmar con `AuthResourceTest` la respuesta exitosa actual (`token`, `expiresIn`, `user`, `debug`), sus campos de usuario y la precedencia de validaciones; ejecutar `mvn -B -ntp -Dtest=AuthResourceTest test` antes de cambiar producción. Resultado: 8 pruebas, 0 fallos/errores/omitidas (`-Dquarkus.http.test-port=0`, JDK 21).
- [x] 1.2 Confirmar con `TokenServiceTest` RS256, claims existentes, vida exacta de 3600 segundos y rechazo en `exp`; ejecutar `mvn -B -ntp -Dtest=TokenServiceTest test` antes de cambiar producción. Resultado: 2 pruebas, 0 fallos/errores/omitidas (JDK 21).
- [x] 1.3 Confirmar con `EndpointContractTest`, `EndpointFailureTest` y `AuthResourceTest` las respuestas 400/401/500 y la redacción de logs ya cubiertas; ejecutar las pruebas focalizadas y registrar cualquier hueco antes del cambio. Resultado: `EndpointContractTest` 11/11, `EndpointFailureTest` 6/6 y `AuthResourceTest` 8/8; todas con 0 fallos/errores/omitidas, ejecutadas por clase para evitar colisión de perfiles Quarkus en el puerto 8081.

## 2. Añadir pruebas para el comportamiento acordado

- [x] 2.1 Añadir a `TokenServiceTest` casos con reloj fijo para `client`, claims requeridos, claims existentes, RS256 y límites de expiración; verificar que las nuevas aserciones describen la spec y fallan solo donde falta el comportamiento nuevo. Resultado: `TokenServiceTest` 2/2 y `AuthServiceTest` 10/10.
- [x] 2.2 Añadir a `EndpointContractTest` casos de `client` omitido, nulo, vacío y solo espacios con HTTP 400 y sin token; reemplazar la expectativa permisiva existente y verificar con `mvn -B -ntp -Dtest=EndpointContractTest test`. Resultado: `EndpointContractTest` 13/13; `AuthResourceTest` 8/8.
- [x] 2.3 Ampliar la prueba de login exitoso para comprobar que el token firmado contiene el `client` enviado y que el cuerpo conserva exactamente su forma pública; verificar con `mvn -B -ntp -Dtest=AuthResourceTest test`. Resultado: `AuthResourceTest` 8/8; claim `client` exacto, envelope intacto y vigencia comprobada.
- [x] 2.4 Añadir o completar una prueba de logs para login exitoso y fallido con valores centinela de password, token y secreto; verificar que no aparecen en registros y que el error HTTP 500 sigue siendo genérico. Resultado: `AuthResourceTest` 8/8 y `EndpointFailureTest` 6/6; las pruebas observan logs de ambos flujos, el 500 genérico y la ausencia de centinelas.

## 3. Implementar el cambio incremental

- [x] 3.1 Validar `client` en `AuthResource` después de las validaciones existentes de request, username y password; comprobar 400 para cada valor ausente/vacío y que las validaciones previas mantienen precedencia. Resultado: `EndpointContractTest` 13/13 y `AuthResourceTest` 8/8.
- [x] 3.2 Propagar el `client` validado por `AuthService` hasta `TokenService` e incluirlo como claim sin alterar la respuesta; verificar con `TokenServiceTest` y `AuthResourceTest` que el claim coincide y la firma sigue siendo RS256. Resultado: `TokenServiceTest` 2/2, `AuthServiceTest` 10/10 y `AuthResourceTest` 8/8.
- [x] 3.3 Mantener una sola referencia temporal para `iat` y `exp`, vida de 3600 segundos y `expiresIn` coherente con tolerancia maxima de un segundo; verificar los límites con el reloj fijo de `TokenServiceTest`. Resultado: `TokenServiceTest` verifica límites exp-1/exp/exp+1; `AuthResourceTest` verifica exp-iat=3600 y tolerancia de un segundo.
- [x] 3.4 Preservar el manejo genérico de errores y evitar registrar password, token, secreto o clave privada; verificar con `EndpointFailureTest` y la prueba de logs centinela. Resultado: `EndpointFailureTest` 6/6 y `AuthResourceTest` 8/8, con 500 genérico y sin centinelas en logs.

## 4. Verificación de integración

- [x] 4.1 Ejecutar `mvn -B -ntp test` y confirmar que pasan la suite completa y los escenarios de contrato de `auth-token`. Resultado: 55 pruebas, 0 fallos/errores/omitidas; `BUILD SUCCESS` con JDK 21 y `QUARKUS_HTTP_TEST_PORT=8182`.
- [x] 4.2 Ejecutar `openspec validate "hu-01-token-claims-expiration" --type change --json` y confirmar que el cambio OpenSpec queda válido. Resultado: `valid: true`, 0 issues.

## Notas de ejecución

Para las tareas 1.1–3.4 se usó JDK 21, según `docs/TESTING.md`. El JDK predeterminado de esta sesión no es compatible con JaCoCo 0.8.12 (`Unsupported class file major version 69`); además, el puerto de test 8081 estaba ocupado. La suite completa requiere `QUARKUS_HTTP_TEST_PORT=8182` para mantener el puerto al cambiar de perfil; las pruebas focales se ejecutaron por clase. No se alteró la configuración del proyecto ni se detuvo ningún proceso externo.
