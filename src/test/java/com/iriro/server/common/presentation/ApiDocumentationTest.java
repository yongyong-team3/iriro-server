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
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/trails']").doesNotExist());
    }

    @Test
    void authRoutesDocumentContractStatusesSchemasAndAnonymousAccess() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.EmailSignupRequest.properties.memberName").exists())
                .andExpect(jsonPath("$.components.schemas.EmailSignupRequest.properties.nickname").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.EmailSignupRequest.properties.passwordConfirmation").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.EmailSignupRequest.properties.password").exists())
                .andExpect(jsonPath("$.components.schemas.AuthResponse.properties.memberId").exists())
                .andExpect(jsonPath("$.components.schemas.AuthResponse.properties.email").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.SocialLoginResponse.properties.accessTokenExpiresInSeconds").exists())
                .andExpect(jsonPath("$.components.schemas.SocialLoginResponse.properties.refreshTokenExpiresInSeconds").exists())
                .andExpect(jsonPath("$.components.schemas.AuthTokenResponse.properties.refreshTokenExpiresInSeconds").exists())
                .andExpect(jsonPath("$.components.schemas.AuthErrorResponse.properties.errors.type").value("null"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses['201'].content.application/json.schema").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses['400'].content.application/json.schema").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses['400'].content.application/json.schema.\u0024ref")
                        .value("#/components/schemas/AuthErrorResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses['409'].content.application/json.schema").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email/login'].post.responses['200'].content.application/json.schema").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email/login'].post.responses['401'].content.application/json.schema").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/reissue'].post.responses['400'].content.application/json.schema").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/reissue'].post.responses['401'].content.application/json.schema").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.responses['204'].content").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.responses['200']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/reissue'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.security").isEmpty());
    }
}
