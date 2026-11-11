package com.iriro.server.member.domain;

import java.util.regex.Pattern;

public final class NicknamePolicy {
    public static final String REGEXP = "^[a-zA-Z0-9가-힣]{2,12}$";
    private static final Pattern PATTERN = Pattern.compile(REGEXP);

    private NicknamePolicy() {
    }

    public static boolean isValid(String nickname) {
        return nickname != null && PATTERN.matcher(nickname).matches();
    }
}
