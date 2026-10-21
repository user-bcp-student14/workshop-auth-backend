package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class CorsTest {
    static final String ALLOWED_ORIGIN = "https://client.example.test";
    static final String DENIED_ORIGIN = "https://untrusted.example.test";

    @Test
    void characterizesPreflight() {
        given().header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .options("/auth/login").then().statusCode(200)
                .header("Access-Control-Allow-Origin", is(ALLOWED_ORIGIN))
                .header("Access-Control-Allow-Methods", containsString("POST"));
    }

    @Test
    void deniesUnknownOrigin() {
        given().header("Origin", DENIED_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .options("/auth/login").then()
                .header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    void doesNotPermitDeleteFromAllowedOrigin() {
        given().header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "DELETE")
                .options("/auth/login").then()
                .header("Access-Control-Allow-Methods", not(containsString("DELETE")));
    }
}
