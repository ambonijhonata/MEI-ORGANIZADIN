package com.api;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import com.api.support.RequiresDocker;

@SpringBootTest(
        classes = MeiOrganizadinApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@RequiresDocker
public abstract class IntegrationTestBase {

    @LocalServerPort
    protected int port;

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
        RestAssured.basePath = "/";
    }
}
