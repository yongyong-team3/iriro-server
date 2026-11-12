package com.iriro.server.member.presentation;

import com.iriro.server.auth.infrastructure.JwtTokenService;
import com.iriro.server.auth.domain.EmailCredential;
import com.iriro.server.auth.domain.SocialAccount;
import com.iriro.server.auth.domain.SocialProvider;
import com.iriro.server.auth.infrastructure.EmailCredentialRepository;
import com.iriro.server.auth.infrastructure.SocialAccountRepository;
import com.iriro.server.auth.application.SocialAccountLoginService;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-photo-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class UserPhotoApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository members;
    @Autowired JwtTokenService jwt;
    @Autowired EmailCredentialRepository credentials;
    @Autowired SocialAccountRepository socialAccounts;
    @Autowired SocialAccountLoginService socialLogin;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void uploadReturnsPersistentUrlAndResetUsesFrontendDefault() throws Exception {
        Member member = members.saveAndFlush(new Member("PhotoUser", "photouser"));
        byte[] png = png();
        String authorization = bearer(member);
        String url = "/api/v1/users/" + member.getId() + "/photo";

        mockMvc.perform(multipart("/api/v1/users/me/photo")
                        .file(new MockMultipartFile("photo", "avatar.png", "image/png", png))
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").value(url));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").value(url));
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));

        byte[] jpeg = jpeg();
        mockMvc.perform(multipart("/api/v1/users/me/photo")
                        .file(new MockMultipartFile("photo", "new-avatar.jpg", "image/jpeg", jpeg))
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").value(url));
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(jpeg));

        mockMvc.perform(delete("/api/v1/users/me/photo").header("Authorization", authorization))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").value((Object) null));
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnauthenticatedAndInvalidImages() throws Exception {
        Member member = members.saveAndFlush(new Member("PhotoUser", "photouser"));
        byte[] png = png();
        mockMvc.perform(multipart("/api/v1/users/me/photo")
                        .file(new MockMultipartFile("photo", "avatar.png", "image/png", png)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/v1/users/me/photo")
                        .file(new MockMultipartFile("photo", "avatar.txt", "text/plain", png))
                        .header("Authorization", bearer(member)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(multipart("/api/v1/users/me/photo")
                        .file(new MockMultipartFile("photo", "avatar.png", "image/png", new byte[]{1, 2, 3}))
                        .header("Authorization", bearer(member)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(multipart("/api/v1/users/me/photo")
                        .file(new MockMultipartFile("photo", "avatar.png", "image/png", new byte[2_000_001]))
                        .header("Authorization", bearer(member)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(multipart("/api/v1/users/me/photo")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void withdrawalDeletesUploadedPhoto() throws Exception {
        Member member = members.saveAndFlush(new Member("PhotoUser", "photouser"));
        String url = "/api/v1/users/" + member.getId() + "/photo";
        mockMvc.perform(multipart("/api/v1/users/me/photo")
                        .file(new MockMultipartFile("photo", "avatar.png", "image/png", png()))
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/users/me").header("Authorization", bearer(member)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void emailAndSocialLoginReturnUploadedPhotoUrl() throws Exception {
        Member member = members.saveAndFlush(new Member("PhotoUser", "photouser"));
        credentials.saveAndFlush(new EmailCredential(member, "photo@example.com", passwordEncoder.encode("ValidPassword123!")));
        socialAccounts.saveAndFlush(new SocialAccount(member, SocialProvider.KAKAO, "photo-provider-id"));
        String url = "/api/v1/users/" + member.getId() + "/photo";
        mockMvc.perform(multipart("/api/v1/users/me/photo")
                        .file(new MockMultipartFile("photo", "avatar.png", "image/png", png()))
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/email/login").contentType("application/json")
                        .content("{\"email\":\"photo@example.com\",\"password\":\"ValidPassword123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member.profileImageUrl").value(url));
        org.assertj.core.api.Assertions.assertThat(
                socialLogin.login(SocialProvider.KAKAO, "photo-provider-id").member().profileImageUrl())
                .isEqualTo(url);
    }

    @Test
    void swaggerShowsUploadAndResetContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/users/me/photo'].post.requestBody.content['multipart/form-data']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me/photo'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me/photo'].post.responses['413']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me/photo'].delete.responses['204']").exists());
    }

    private String bearer(Member member) {
        return "Bearer " + jwt.issueAccessToken(member.getId()).value();
    }

    private byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private byte[] jpeg() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "jpeg", out);
        return out.toByteArray();
    }
}
