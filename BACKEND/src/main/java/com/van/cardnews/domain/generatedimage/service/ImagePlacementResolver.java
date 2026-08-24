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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ImagePlacementResolver {

    private final ObjectMapper objectMapper;
    private final ImageCropService imageCropService;

    @Value("${app.upload.public-base-url}")
    private String uploadPublicBaseUrl;

    /**
     * 카드별 imageId와 cropArea를 실제 생성기에 전달할 수 있는
     * 이미지 URL로 변환합니다.
     *
     * 저장된 cardImagePlacements가 있으면 그것을 우선 사용하고,
     * 최초 생성처럼 아직 placements가 없는 경우에는
     * cardGenerationResult의 카드별 cropArea를 사용합니다.
     */
    public JsonNode resolve(Content content) {
        JsonNode placements = content.getCardImagePlacements();

        if (placements == null || !placements.isArray() || placements.isEmpty()) {
            placements = buildPlacementsFromGenerationResult(content.getCardGenerationResult());
        }

        if (placements == null || !placements.isArray()) {
            return objectMapper.createArrayNode();
        }

        Map<Long, ContentImage> imagesById = content.getImages().stream()
                .collect(Collectors.toMap(ContentImage::getId, image -> image));

        ArrayNode resolved = objectMapper.createArrayNode();

        for (JsonNode placement : placements) {
            if (!placement.hasNonNull("imageId")) {
                continue;
            }

            long imageId = placement.get("imageId").asLong();
            ContentImage original = imagesById.get(imageId);

            if (original == null) {
                throw new IllegalStateException(
                        "카드 이미지 배치가 참조하는 imageId를 찾을 수 없습니다: " + imageId
                );
            }

            ObjectNode node = placement.deepCopy();

            double x = placement.path("cropArea").path("x").asDouble(0.0);
            double y = placement.path("cropArea").path("y").asDouble(0.0);
            double width = placement.path("cropArea").path("width").asDouble(100.0);
            double height = placement.path("cropArea").path("height").asDouble(100.0);

            String originalUrl = toPublicUrl(original.getImageUrl());
            String croppedUrl = imageCropService.cropToPublicUrl(
                    originalUrl,
                    x,
                    y,
                    width,
                    height
            );

            node.put("resolvedImageUrl", croppedUrl);
            resolved.add(node);
        }

        return resolved;
    }

    private JsonNode buildPlacementsFromGenerationResult(JsonNode result) {
        if (result == null || result.isNull()) {
            return objectMapper.createArrayNode();
        }

        ArrayNode placements = objectMapper.createArrayNode();

        addPlacement(
                placements,
                "cover",
                0,
                result.path("cover")
        );

        JsonNode content = result.path("content");
        if (content.isArray()) {
            for (int i = 0; i < content.size(); i++) {
                addPlacement(
                        placements,
                        "content",
                        i,
                        content.get(i)
                );
            }
        }

        addPlacement(
                placements,
                "closing",
                0,
                result.path("closing")
        );

        return placements;
    }

    private void addPlacement(
            ArrayNode placements,
            String cardType,
            int cardIndex,
            JsonNode card
    ) {
        if (card == null || card.isMissingNode() || !card.hasNonNull("imageId")) {
            return;
        }

        ObjectNode placement = objectMapper.createObjectNode();
        placement.put("cardType", cardType);
        placement.put("cardIndex", cardIndex);
        placement.put("imageId", card.path("imageId").asLong());

        JsonNode cropArea = card.path("cropArea");
        if (cropArea.isObject()) {
            placement.set("cropArea", cropArea.deepCopy());
        } else {
            ObjectNode full = objectMapper.createObjectNode();
            full.put("x", 0.0);
            full.put("y", 0.0);
            full.put("width", 100.0);
            full.put("height", 100.0);
            placement.set("cropArea", full);
        }

        placements.add(placement);
    }

    private String toPublicUrl(String imageUrl) {
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            return imageUrl;
        }

        return uploadPublicBaseUrl + "/" + imageUrl;
    }
}
