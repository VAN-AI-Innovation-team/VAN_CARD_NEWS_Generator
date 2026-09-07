package com.van.cardnews.domain.publish.service;

import com.van.cardnews.domain.publish.entity.PublishStatus;
import com.van.cardnews.domain.publish.repository.PublishRecordRepository;
import com.van.cardnews.global.publish.instagram.MockInstagramClient;
import com.van.cardnews.global.time.KoreaTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 워커를 실제 DB에 붙여 검증한다. 조건부 UPDATE의 원자성은 SQL의 성질이라 목킹으로는 판정되지 않는다.
 *
 * 로컬에는 Postgres가 없어 이 클래스는 CI에서만 실행된다(워크플로의 postgres 서비스).
 * dev 프로필 + MockInstagramClient 기준이며, 폴링 간격만 1ms로 낮춰 테스트가 분 단위로 늘어지지 않게 한다.
 */
@SpringBootTest
@TestPropertySource(properties = "app.publish.instagram.poll-interval-ms=1")
class PublishWorkerIntegrationTest {

    @Autowired
    private PublishWorker publishWorker;

    @Autowired
    private PublishRecordRepository publishRecordRepository;

    @Autowired
    private MockInstagramClient instagramClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long templateId;
    private Long contentId;

    @BeforeEach
    void setUp() {
        instagramClient.reset();

        String code = "van11-" + System.nanoTime();
        templateId = jdbcTemplate.queryForObject("""
                INSERT INTO templates (code, name, content_type, canvas_width, canvas_height,
                                       layout_definition, design_tokens, version)
                VALUES (?, ?, 'news', 1080, 1080, '{}'::jsonb, '{}'::jsonb, 1)
                RETURNING id
                """, Long.class, code, code);

        contentId = jdbcTemplate.queryForObject("""
                INSERT INTO contents (title, body, template_id, status)
                VALUES ('VAN-11 워커 테스트', '본문', ?, 'DRAFT')
                RETURNING id
                """, Long.class, templateId);

        for (int i = 0; i < 2; i++) {
            jdbcTemplate.update("""
                    INSERT INTO generated_card_images (content_id, card_type, card_index, sort_order,
                                                       image_url, resolution_width, resolution_height)
                    VALUES (?, 'CONTENT', ?, ?, ?, 1080, 1080)
                    """, contentId, i, i, "https://example.com/card-" + i + ".jpg");
        }
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM publish_records WHERE content_id = ?", contentId);
        jdbcTemplate.update("DELETE FROM generated_card_images WHERE content_id = ?", contentId);
        jdbcTemplate.update("DELETE FROM contents WHERE id = ?", contentId);
        jdbcTemplate.update("DELETE FROM templates WHERE id = ?", templateId);
    }

    // ------------------------------------------------------------------
    // 중복 실행 방지
    // ------------------------------------------------------------------

    @Test
    void 동시_선점은_한_워커만_통과한다() throws Exception {
        Long recordId = insertScheduled(KoreaTime.now().minusSeconds(1));

        List<Integer> claimed = runConcurrently(
                () -> publishRecordRepository.claim(recordId, KoreaTime.now()));

        assertThat(claimed).containsExactlyInAnyOrder(1, 0);
    }

    @Test
    void 워커를_동시에_두_번_돌려도_발행_호출은_1회다() throws Exception {
        Long recordId = insertScheduled(KoreaTime.now().minusSeconds(1));

        runConcurrently(() -> publishWorker.runDue());

        assertThat(callCount("publishContainer")).isEqualTo(1);
        assertThat(statusOf(recordId)).isEqualTo(PublishStatus.SUCCESS.name());
    }

    // ------------------------------------------------------------------
    // 도래·회수·유예
    // ------------------------------------------------------------------

    @Test
    void 도래한_예약은_개입_없이_발행되고_permalink가_기록된다() {
        Long recordId = insertScheduled(KoreaTime.now().minusSeconds(1));

        publishWorker.runDue();

        assertThat(statusOf(recordId)).isEqualTo(PublishStatus.SUCCESS.name());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT permalink FROM publish_records WHERE id = ?", String.class, recordId))
                .startsWith("https://www.instagram.com/p/");
    }

    @Test
    void 아직_도래하지_않은_예약은_건드리지_않는다() {
        Long recordId = insertScheduled(KoreaTime.now().plusMinutes(30));

        publishWorker.runDue();

        assertThat(statusOf(recordId)).isEqualTo(PublishStatus.SCHEDULED.name());
        assertThat(instagramClient.calls()).isEmpty();
    }

    @Test
    void PROCESSING에서_멈춘_건은_임계_시간을_넘기면_회수된다() {
        // 워커가 발행 도중 죽은 상태 — 아무도 손대지 않으면 영구 정체된다
        Long recordId = insertProcessing(KoreaTime.now().minusMinutes(30));

        publishWorker.runDue();

        assertThat(statusOf(recordId)).isEqualTo(PublishStatus.FAILED.name());
    }

    @Test
    void 유예_시간을_넘긴_예약은_발행하지_않고_실패로_남는다() {
        Long recordId = insertScheduled(KoreaTime.now().minusMinutes(120));

        publishWorker.runDue();

        assertThat(statusOf(recordId)).isEqualTo(PublishStatus.FAILED.name());
        assertThat(instagramClient.calls()).isEmpty();
    }

    // ------------------------------------------------------------------

    private Long insertScheduled(LocalDateTime scheduledAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO publish_records (content_id, channel, status, scheduled_at, caption)
                VALUES (?, 'INSTAGRAM', 'SCHEDULED', ?, '캡션')
                RETURNING id
                """, Long.class, contentId, toTimestamp(scheduledAt));
    }

    private Long insertProcessing(LocalDateTime processingStartedAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO publish_records (content_id, channel, status, scheduled_at,
                                             processing_started_at, caption)
                VALUES (?, 'INSTAGRAM', 'PROCESSING', ?, ?, '캡션')
                RETURNING id
                """, Long.class, contentId,
                toTimestamp(processingStartedAt), toTimestamp(processingStartedAt));
    }

    /** 컬럼이 TIMESTAMPTZ이므로 서울 벽시계를 그 자리의 순간으로 바꿔 넣는다(CI JVM은 UTC다). */
    private Timestamp toTimestamp(LocalDateTime seoulWallClock) {
        return Timestamp.from(seoulWallClock.atZone(KoreaTime.ZONE_ID).toInstant());
    }

    private String statusOf(Long recordId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM publish_records WHERE id = ?", String.class, recordId);
    }

    private long callCount(String prefix) {
        return instagramClient.calls().stream().filter(call -> call.startsWith(prefix)).count();
    }

    /** 두 스레드를 같은 지점에서 동시에 출발시킨다. */
    private <T> List<T> runConcurrently(Callable<T> task) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await();
                    return task.call();
                }));
            }

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
