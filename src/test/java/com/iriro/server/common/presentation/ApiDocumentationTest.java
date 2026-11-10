package com.iriro.server.common.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:api-docs;MODE=MySQL;DB_CLOSE_DELAY=-1")
class ApiDocumentationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void documentsOnlyImplementedRoutesAndBearerAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.summary").value("카카오·네이버 로그인"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.requestBody.content.application/json.schema").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['200'].content.application/json.schema").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['401'].content.application/json.schema").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/trails']").doesNotExist());
    }
}
