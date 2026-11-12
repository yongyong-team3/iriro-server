package com.iriro.server.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "member_profile_photos")
public class MemberProfilePhoto {
    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] content;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected MemberProfilePhoto() { }

    public MemberProfilePhoto(Long memberId, String contentType, byte[] content, LocalDateTime updatedAt) {
        this.memberId = memberId;
        replace(contentType, content, updatedAt);
    }

    public void replace(String contentType, byte[] content, LocalDateTime updatedAt) {
        this.contentType = contentType;
        this.content = content.clone();
        this.updatedAt = updatedAt;
    }

    public String getContentType() { return contentType; }
    public byte[] getContent() { return content.clone(); }
}
