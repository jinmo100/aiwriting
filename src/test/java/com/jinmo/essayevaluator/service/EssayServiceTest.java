package com.jinmo.essayevaluator.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EssayServiceTest {

    @Test
    void countWordsCountsEnglishWordsAndCommonCompounds() {
        assertEquals(0, EssayService.countWords(null));
        assertEquals(0, EssayService.countWords("   "));
        assertEquals(7, EssayService.countWords("Online learning helps students' long-term self-discipline, too."));
    }
}
