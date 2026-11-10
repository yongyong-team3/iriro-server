package com.iriro.server.auth.application;

import com.iriro.server.common.application.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class CurrentMemberProvider {

    public static final String MEMBER_ID_ATTRIBUTE = CurrentMemberProvider.class.getName() + ".memberId";

    public Long requireMemberId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            Object memberId = attributes.getRequest().getAttribute(MEMBER_ID_ATTRIBUTE);
            if (memberId instanceof Long id) {
                return id;
            }
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
    }
}
