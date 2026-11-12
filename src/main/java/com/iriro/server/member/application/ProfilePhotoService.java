package com.iriro.server.member.application;

import com.iriro.server.common.application.ApiException;
import com.iriro.server.member.domain.MemberProfilePhoto;
import com.iriro.server.member.infrastructure.MemberProfilePhotoRepository;
import com.iriro.server.member.infrastructure.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Locale;

@Service
public class ProfilePhotoService {
    private static final long MAX_BYTES = 2_000_000;
    private static final int MAX_DIMENSION = 4096;
    private final MemberProfilePhotoRepository photos;
    private final MemberRepository members;

    public ProfilePhotoService(MemberProfilePhotoRepository photos, MemberRepository members) {
        this.photos = photos;
        this.members = members;
    }

    @Transactional
    public String upload(Long memberId, MultipartFile photo) {
        requireMember(memberId);
        if (photo == null || photo.isEmpty() || photo.getSize() > MAX_BYTES) throw invalid();
        String type = photo.getContentType();
        if (!"image/png".equals(type) && !"image/jpeg".equals(type)) throw invalid();
        byte[] content;
        try {
            content = photo.getBytes();
        } catch (IOException exception) {
            throw invalid();
        }
        validateImage(content, type);
        LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
        MemberProfilePhoto saved = photos.findById(memberId)
                .orElseGet(() -> new MemberProfilePhoto(memberId, type, content, now));
        saved.replace(type, content, now);
        photos.saveAndFlush(saved);
        return url(memberId);
    }

    @Transactional
    public void reset(Long memberId) {
        requireMember(memberId);
        photos.deleteById(memberId);
        photos.flush();
    }

    @Transactional(readOnly = true)
    public MemberProfilePhoto get(Long memberId) {
        return photos.findById(memberId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "PROFILE_PHOTO_NOT_FOUND", "프로필 사진이 없습니다."));
    }

    @Transactional(readOnly = true)
    public String urlIfPresent(Long memberId) {
        return photos.existsById(memberId) ? url(memberId) : null;
    }

    public String url(Long memberId) {
        return "/api/v1/users/" + memberId + "/photo";
    }

    private void requireMember(Long memberId) {
        if (members.findByIdForUpdate(memberId).isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증 정보를 확인해 주세요.");
        }
    }

    private void validateImage(byte[] content, String contentType) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) throw invalid();
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!("image/png".equals(contentType) && "png".equals(format))
                        && !("image/jpeg".equals(contentType) && ("jpeg".equals(format) || "jpg".equals(format)))) {
                    throw invalid();
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) throw invalid();
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ApiException apiException) throw apiException;
            throw invalid();
        }
    }

    private ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "PNG 또는 JPEG 사진(최대 2MB, 4096px)을 확인해 주세요.");
    }
}
