package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(AuthTestResource.class)
class EndpointContractTest {
    private static final String LOGIN = "/auth/login";
    private static final String HEALTH = "/q/health";

    @Test
    void rejectsAbsentAndNullLoginBodies() {
        given().contentType("application/json").post(LOGIN).then()
                .statusCode(400).body("message", is("Request is null"));
        given().contentType("application/json").body("null").post(LOGIN).then()
                .statusCode(400).body("message", is("Request is null"));
    }

    @Test
    void rejectsNullEmptyAndWhitespaceFieldsInValidationOrder() {
        for (String username : new String[]{"null", "\"\"", "\" \\t \""}) {
            given().contentType("application/json")
                    .body("{\"username\":" + username + ",\"password\":null}").post(LOGIN).then()
                    .statusCode(400).body("message", is("Username is required"));
        }
        for (String password : new String[]{"null", "\"\"", "\" \\t \""}) {
            given().contentType("application/json")
                    .body("{\"username\":\"test-student\",\"password\":" + password + "}").post(LOGIN).then()
                    .statusCode(400).body("message", is("Password is required"));
        }
    }

    @Test
    void rejectsMalformedJson() {
        given().contentType("application/json").body("{").post(LOGIN).then()
                .statusCode(400).body(is(""));
    }

    @Test
    void rejectsObjectInsteadOfUsername() {
        // Quarkus test mode adds diagnostics; the empty prod body is checked separately.
        given().contentType("application/json").body("{\"username\":{},\"password\":\"test\"}")
                .post(LOGIN).then().statusCode(400)
                .body("objectName", is("LoginRequest")).body("attributeName", is("username"));
    }

    @Test
    void rejectsDisallowedLoginOrigin() {
        given().contentType("application/json").header("Origin", CorsTest.DENIED_ORIGIN)
                .body("{}").post(LOGIN).then().statusCode(403)
                .header("Access-Control-Allow-Origin", nullValue()).body(is(""));
    }

    @Test
    void rejectsUnsupportedLoginMethod() {
        given().accept("application/json").get(LOGIN).then().statusCode(405).body(is(""));
    }

    @Test
    void rejectsUnacceptableLoginResponseType() {
        given().contentType("application/json").accept("text/plain").body("{}")
                .post(LOGIN).then().statusCode(406).body(is(""));
    }

    @Test
    void rejectsUnsupportedLoginContentType() {
        given().contentType("text/plain").accept("application/json").body("test")
                .post(LOGIN).then().statusCode(415).body(is(""));
    }

    @Test
    void reportsHealthWithoutAuthentication() {
        given().accept("application/json").get(HEALTH).then().statusCode(200)
                .contentType("application/json").body("status", is("UP"))
                .body("checks", empty()).body("size()", is(2));
    }

    @Test
    void ignoresEmptyJsonHealthBodyAndTextAccept() {
        given().contentType("application/json").body("{}").get(HEALTH).then()
                .statusCode(200).body("status", is("UP")).body("checks", empty());
        given().accept("text/plain").get(HEALTH).then().statusCode(200)
                .contentType("application/json").body("status", is("UP")).body("checks", empty());
    }

    @Test
    void rejectsHealthOriginsEvenWhenOriginIsAllowedForPost() {
        for (String origin : new String[]{CorsTest.ALLOWED_ORIGIN, CorsTest.DENIED_ORIGIN}) {
            given().header("Origin", origin).get(HEALTH).then().statusCode(403).body(is(""));
        }
    }
}
