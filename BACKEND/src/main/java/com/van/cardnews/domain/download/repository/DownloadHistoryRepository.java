package com.van.cardnews.domain.download.repository;

import com.van.cardnews.domain.download.entity.DownloadHistory;
import com.van.cardnews.domain.download.entity.DownloadType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DownloadHistoryRepository
        extends JpaRepository<DownloadHistory, Long> {

    List<DownloadHistory> findByContentIdOrderByRequestedAtDesc(Long contentId);

    List<DownloadHistory> findByContentIdAndChannelOrderByRequestedAtDesc(
            Long contentId,
            String channel
    );

    Optional<DownloadHistory> findTopByContentIdAndChannelAndDownloadTypeOrderByRequestedAtDesc(
            Long contentId,
            String channel,
            DownloadType downloadType
    );
}
