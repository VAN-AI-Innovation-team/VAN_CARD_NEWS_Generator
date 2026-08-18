package com.van.cardnews.domain.content.service;

import com.van.cardnews.domain.content.dto.request.ContentCreateRequest;
import com.van.cardnews.domain.content.dto.response.ContentCreateResponse;
import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.entity.ContentImage;
import com.van.cardnews.domain.content.repository.ContentRepository;
import com.van.cardnews.domain.jobhistory.entity.JobHistory;
import com.van.cardnews.domain.jobhistory.entity.JobType;
import com.van.cardnews.domain.jobhistory.repository.JobHistoryRepository;
import com.van.cardnews.domain.template.entity.Template;
import com.van.cardnews.domain.template.repository.TemplateRepository;
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
    private final TemplateRepository templateRepository;

    @Transactional
    public ContentCreateResponse createContent(
            ContentCreateRequest request,
            List<MultipartFile> images
    ) {
        validateImageCount(images);

        /*
         * 생성 시점의 활성 템플릿 버전을 조회합니다.
         *
         * 이후 관리자가 새로운 버전을 만들어도
         * 이미 저장된 Content는 이 Template row를 계속 참조합니다.
         */
        Template template = templateRepository
                .findByIdAndActiveTrue(request.templateId())
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.TEMPLATE_NOT_FOUND
                        )
                );

        Content content = Content.create(
                request.title(),
                request.body(),
                template
        );

        attachImages(content, images);

        contentRepository.save(content);

        JobHistory jobHistory =
                JobHistory.createPending(
                        content,
                        JobType.FULL_PIPELINE
                );

        jobHistoryRepository.save(jobHistory);

        pipelineClient.triggerGeneration(
                content.getId(),
                jobHistory.getId()
        );

        log.info(
                "콘텐츠 생성 요청 접수 완료 - contentId={}, jobHistoryId={}, templateId={}, templateCode={}, templateVersion={}",
                content.getId(),
                jobHistory.getId(),
                template.getId(),
                template.getCode(),
                template.getVersion()
        );

        return ContentCreateResponse.of(
                content,
                jobHistory
        );
    }

    private void validateImageCount(
            List<MultipartFile> images
    ) {
        if (images != null &&
                images.size() > MAX_IMAGE_COUNT) {

            throw new CustomException(
                    ErrorCode.TOO_MANY_IMAGES
            );
        }
    }

    private void attachImages(
            Content content,
            List<MultipartFile> images
    ) {
        if (images == null || images.isEmpty()) {
            return;
        }

        int order = 0;

        for (MultipartFile image : images) {
            String imageUrl =
                    imageStorageService.store(image);

            content.addImage(
                    ContentImage.create(
                            imageUrl,
                            order++
                    )
            );
        }
    }
}
