package com.iriro.server.activity.application;

import com.iriro.server.activity.presentation.ActivityCreateRequest;
import com.iriro.server.activity.presentation.ActivityCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 활동 저장(ACT-01)의 동시 요청 처리용 진입점.
 *
 * 같은 Idempotency-Key 요청 두 개가 동시에 들어오면 둘 다 "기존 요청 없음"으로 판단하고
 * INSERT 를 시도해서, 한쪽은 유니크 제약(uk_activity_idempotency) 위반으로 실패한다.
 *
 * 이 예외를 ActivityService 안에서 잡으면 이미 롤백 전용(rollback-only)으로 표시된
 * 트랜잭션을 계속 쓰게 되므로, 트랜잭션이 없는 이 클래스에서 잡는다.
 * (이 클래스에는 @Transactional 을 붙이지 않는다.)
 *
 *  - 유니크 제약 위반: 먼저 커밋된 활동이 있으면 그 결과를 반환 (재전송과 동일하게 처리)
 *  - 데드락/락 타임아웃: MySQL 은 같은 유니크 키를 동시에 INSERT 하면 데드락이 날 수 있으므로
 *    먼저 커밋된 결과를 찾거나, 없으면 몇 번 재시도한다.
 */
@Service
@RequiredArgsConstructor
public class ActivityCreateFacade {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MILLIS = 50;

    private final ActivityService activityService;

    public ActivityCreateResponse createActivity(
            Long memberId,
            String idempotencyKey,
            ActivityCreateRequest request
    ) {

        for (int attempt = 1; ; attempt++) {

            try {
                return activityService.createActivity(memberId, idempotencyKey, request);

            } catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {

                // 같은 키로 먼저 커밋된 활동이 있으면 그 결과를 반환한다.
                // (다른 본문이면 IDEMPOTENCY_CONFLICT 가 그대로 던져진다)
                Optional<ActivityCreateResponse> existing =
                        activityService.findExistingResult(memberId, idempotencyKey, request);

                if (existing.isPresent()) {
                    return existing.get();
                }

                // 같은 키로 저장된 활동이 없다면 동시 요청 문제가 아니므로 원래 예외를 그대로 던진다.
                // 단, 데드락 희생자는 상대 트랜잭션이 아직 커밋 전일 수 있으니 잠시 후 다시 시도한다.
                boolean retryable = e instanceof PessimisticLockingFailureException
                        && attempt < MAX_ATTEMPTS;

                if (!retryable) {
                    throw e;
                }

                sleep(RETRY_BACKOFF_MILLIS * attempt);
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}