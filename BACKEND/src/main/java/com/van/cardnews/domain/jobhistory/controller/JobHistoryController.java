package com.van.cardnews.domain.jobhistory.controller;

import com.van.cardnews.domain.jobhistory.dto.response.JobHistoryResponse;
import com.van.cardnews.domain.jobhistory.service.JobHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobHistoryController {

    private final JobHistoryService jobHistoryService;

    /**
     * 비동기 작업의 상태 및 결과를 조회합니다.
     *
     * GET /api/jobs/{jobId}
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<JobHistoryResponse> getJobHistory(
            @PathVariable Long jobId
    ) {
        JobHistoryResponse response =
                jobHistoryService.getJobHistory(jobId);

        return ResponseEntity.ok(response);
    }
}
