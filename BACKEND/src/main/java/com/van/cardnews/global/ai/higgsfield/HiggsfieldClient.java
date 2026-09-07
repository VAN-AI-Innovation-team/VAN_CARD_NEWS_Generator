package com.van.cardnews.global.ai.higgsfield;

import com.van.cardnews.global.ai.higgsfield.dto.HiggsfieldGenerationRequest;
import com.van.cardnews.global.ai.higgsfield.dto.HiggsfieldGenerationResult;

public interface HiggsfieldClient {
    HiggsfieldGenerationResult generateCardImages(HiggsfieldGenerationRequest request);
}
