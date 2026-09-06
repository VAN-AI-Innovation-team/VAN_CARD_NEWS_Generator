package com.van.cardnews.domain.download.service;

import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.repository.ContentRepository;
import com.van.cardnews.domain.audit.entity.AuditAction;
import com.van.cardnews.domain.audit.service.AuditLogService;
import com.van.cardnews.domain.download.dto.response.DownloadHistoryResponse;
import com.van.cardnews.domain.download.entity.DownloadHistory;
import com.van.cardnews.domain.download.entity.DownloadResult;
import com.van.cardnews.domain.download.entity.DownloadType;
import com.van.cardnews.domain.download.repository.DownloadHistoryRepository;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DownloadHistoryService {

    private final DownloadHistoryRepository downloadHistoryRepository;
    private final ContentRepository contentRepository;
    private final AuditLogService auditLogService;

    /**
     * 다운로드 시도 이력을 생성합니다.
     *
     * 직전 동일 콘텐츠/채널/다운로드 유형의 요청이 실패한 경우
     * retryCount를 1 증가시킵니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(
            Long contentId,
            String channel,
            DownloadType downloadType,
            Long imageId,
            String actorId
    ) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_NOT_FOUND));

        int retryCount = downloadHistoryRepository
                .findTopByContentIdAndChannelAndDownloadTypeOrderByRequestedAtDesc(
                        contentId,
                        channel,
                        downloadType
                )
                .map(previous -> previous.getResult() == DownloadResult.FAILED
                        ? previous.getRetryCount() + 1
                        : 0)
                .orElse(0);

        DownloadHistory history = DownloadHistory.create(
                content,
                channel,
                downloadType,
                retryCount,
                imageId
        );

        Long historyId = downloadHistoryRepository.save(history).getId();
        auditLogService.record(
                actorId,
                AuditAction.DOWNLOAD,
                contentId,
                "콘텐츠 다운로드 요청: " + downloadType.name()
        );

        return historyId;
    }

    /**
     * 다운로드 성공 이력을 기록합니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(Long historyId) {
        DownloadHistory history = downloadHistoryRepository.findById(historyId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.DOWNLOAD_HISTORY_NOT_FOUND)
                );

        // 다운로드는 발행이 아닙니다. contents.status = PUBLISHED는
        // 인스타그램 발행에 성공한 경우에만 전이합니다.
        history.markSuccess();
    }

    /**
     * 다운로드 실패 이력을 기록합니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long historyId, String message) {
        DownloadHistory history = downloadHistoryRepository.findById(historyId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.DOWNLOAD_HISTORY_NOT_FOUND)
                );

        history.markFailed(message);
    }

    /**
     * 콘텐츠의 다운로드 이력을 조회합니다.
     *
     * channel이 없으면 전체 채널의 이력을 조회하고,
     * channel이 있으면 해당 채널의 이력만 조회합니다.
     */
    @Transactional(readOnly = true)
    public List<DownloadHistoryResponse> getHistory(
            Long contentId,
            String channel
    ) {
        if (!contentRepository.existsById(contentId)) {
            throw new CustomException(ErrorCode.CONTENT_NOT_FOUND);
        }

        List<DownloadHistory> histories =
                channel == null || channel.isBlank()
                        ? downloadHistoryRepository
                          .findByContentIdOrderByRequestedAtDesc(contentId)
                        : downloadHistoryRepository
                          .findByContentIdAndChannelOrderByRequestedAtDesc(
                                  contentId,
                                  channel.trim()
                          );

        return histories.stream()
                .map(DownloadHistoryResponse::from)
                .toList();
    }
}
