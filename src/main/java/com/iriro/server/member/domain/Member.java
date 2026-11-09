package com.iriro.server.member.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "member_name", length = 12)
    private String memberName;

    @Column(name = "member_name_key", length = 12, unique = true)
    private String memberNameKey;

    // @Column(name = "profile_image_url")
    // private String profileImageUrl; // DB 설정 변경 후 추가 필요

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Member(String memberName, String memberNameKey) {
        this.memberName = memberName;
        this.memberNameKey = memberName != null ? memberName.toLowerCase() : null;
        // this.profileImageUrl = profileImageUrl;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void updateMemberName(String newMemberName) {
        this.memberName = newMemberName;
        // 영문 대소문자 무시 중복 검사를 위해 소문자로 정규화 키 설정
        this.memberNameKey = newMemberName.toLowerCase();
        this.updatedAt = LocalDateTime.now();
    }
}