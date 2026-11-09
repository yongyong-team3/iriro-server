package com.iriro.server.badge.domain;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BadgeAwardId implements Serializable {

    private Long member;
    private String badgeCode;
}