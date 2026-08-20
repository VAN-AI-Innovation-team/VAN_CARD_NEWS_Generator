package com.van.cardnews.domain.generatedimage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.van.cardnews.domain.content.entity.Content;
import com.van.cardnews.domain.content.entity.ContentImage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ImagePlacementResolver {

    private final ObjectMapper objectMapper;

    @Value("${app.upload.public-base-url}")
    private String uploadPublicBaseUrl;

    /**
     * Content.cardImagePlacements의 imageId를 실제 접근 가능한 imageUrl로 치환한다.
     */
    public JsonNode resolve(Content content) {
        JsonNode placements = content.getCardImagePlacements();
        if (placements == null || !placements.isArray()) {
            return objectMapper.createArrayNode();
        }

        Map<Long, ContentImage> imagesById = content.getImages().stream()
                .collect(Collectors.toMap(ContentImage::getId, image -> image));

        ArrayNode resolved = objectMapper.createArrayNode();
        for (JsonNode placement : placements) {
            long imageId = placement.get("imageId").asLong();
            ContentImage original = imagesById.get(imageId);
            if (original == null) {
                throw new IllegalStateException(
                        "cardImagePlacements가 참조하는 imageId를 찾을 수 없습니다: " + imageId
                );
            }

            ObjectNode node = placement.deepCopy();
            node.put("resolvedImageUrl", toPublicUrl(original.getImageUrl()));
            resolved.add(node);
        }
        return resolved;
    }

    private String toPublicUrl(String imageUrl) {
        // [확인 필요] ContentImage.imageUrl에 이미 절대 URL(http로 시작)이 저장되는지,
        // 아니면 상대 경로(파일명 등)만 저장되는지 확인 필요. 아래는 두 경우를 모두 처리.
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            return imageUrl;
        }
        return uploadPublicBaseUrl + "/" + imageUrl;
    }
}
