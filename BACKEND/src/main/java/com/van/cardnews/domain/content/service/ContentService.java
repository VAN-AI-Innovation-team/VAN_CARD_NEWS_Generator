package com.van.cardnews.domain.content.service;

import com.van.cardnews.domain.content.dto.request.ContentCreateRequest;
import com.van.cardnews.domain.content.dto.response.ContentCreateResponse;
import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.entity.ContentImage;
import com.van.cardnews.domain.content.repository.ContentRepository;
import com.van.cardnews.domain.jobhistory.entity.JobHistory;
import com.van.cardnews.domain.jobhistory.entity.JobType;
import com.van.cardnews.domain.jobhistory.repository.JobHistoryRepository;
import com.van.cardnews.global.exception.CustomException;
import com.van.cardnews.global.exception.ErrorCode;
import com.van.cardnews.global.pipeline.PipelineClient;
import com.van.cardnews.global.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService {

    private static final int MAX_IMAGE_COUNT = 10;

    private final ContentRepository contentRepository;
    private final JobHistoryRepository jobHistoryRepository;
    private final ImageStorageService imageStorageService;
    private final PipelineClient pipelineClient;

    @Transactional
    public ContentCreateResponse createContent(ContentCreateRequest request, List<MultipartFile> images) {
        validateImageCount(images);

        // 1. 요청 데이터 저장 (content_images 정규화 테이블에 이미지별로 한 행씩 저장)
        Content content = Content.create(request.title(), request.body(), request.template());
        attachImages(content, images);
        contentRepository.save(content);

        // 2. 작업 이력(job_histories) 최초 레코드 생성 - 완료 기준: "DB에 이력 저장"
        //    이슈 #7의 폼 제출(문구+이미지 전체 생성)은 FULL_PIPELINE 유형으로 기록합니다.
        JobHistory jobHistory = JobHistory.createPending(content, JobType.FULL_PIPELINE);
        jobHistoryRepository.save(jobHistory);

        // 3. 생성 파이프라인 트리거 호출 - 완료 기준: "저장 후 파이프라인에 작업 전달 확인"
        pipelineClient.triggerGeneration(content.getId(), jobHistory.getId());

        log.info("콘텐츠 생성 요청 접수 완료 - contentId={}, jobHistoryId={}",
                content.getId(), jobHistory.getId());

        return ContentCreateResponse.of(content, jobHistory);
    }

    private void validateImageCount(List<MultipartFile> images) {
        if (images != null && images.size() > MAX_IMAGE_COUNT) {
            throw new CustomException(ErrorCode.TOO_MANY_IMAGES);
        }
    }

    private void attachImages(Content content, List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return;
        }
        int order = 0;
        for (MultipartFile image : images) {
            String imageUrl = imageStorageService.store(image);
            content.addImage(ContentImage.create(imageUrl, order++));
        }
    }
}
