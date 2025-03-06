package com.jinmo.aiwriting.service.ai;

import java.util.List;

public record EssayAnalysis(
    Integer score,
    List<String> strengths,
    List<String> suggestions
) {} 