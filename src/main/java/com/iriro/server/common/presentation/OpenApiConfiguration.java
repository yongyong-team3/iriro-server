package com.iriro.server.common.presentation;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "이리로 API", version = "v1",
        description = "현재 구현된 API만 표시합니다. 성공 응답은 code, message, data 형식이고 실패 응답은 code, message, errors 형식입니다."))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfiguration {

    @Bean
    OpenApiCustomizer responseFormats() {
        return openApi -> {
            openApi.getComponents().addSchemas("AuthErrorResponse", new Schema<>()
                    .type("object")
                    .addProperty("code", new StringSchema())
                    .addProperty("message", new StringSchema())
                    .addProperty("errors", new Schema<>().types(Set.of("null")).description("항상 null")));
            openApi.getPaths().forEach((url, path) -> path.readOperations().forEach(operation ->
                operation.getResponses().forEach((status, response) -> {
                    Content content = response.getContent();
                    if (status.startsWith("4") || status.startsWith("5")) {
                        if (url.startsWith("/api/v1/auth/")) {
                            response.setContent(new Content().addMediaType("application/json",
                                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/AuthErrorResponse"))));
                        } else if (content == null || !content.containsKey("application/json")) {
                            response.setContent(new Content().addMediaType("application/json",
                                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))));
                        }
                    } else if (content != null && content.containsKey("*/*")) {
                        MediaType mediaType = content.remove("*/*");
                        content.addMediaType("application/json", mediaType);
                    }
                })));
        };
    }
}
