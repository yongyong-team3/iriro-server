package com.iriro.server.auth.infrastructure;

import com.iriro.server.auth.domain.SocialProvider;

public interface SocialProviderClient {

    SocialProvider provider();

    SocialUserInfo verifyAndGetUser(String accessToken);

    record SocialUserInfo(String providerUserId) { }
}
