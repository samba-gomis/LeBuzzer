package com.lebuzzer.backend.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RandomCodeGeneratorTest {

	@Test
	void codes_haveFiveCapitalLetters_withoutIorO() {
		RandomCodeGenerator generator = new RandomCodeGenerator();
		for (int i = 0; i < 1_000; i++) {
			String code = generator.nextCode();
			assertTrue(code.matches("[A-HJ-NP-Z]{5}"), "unexpected code: " + code);
		}
	}

}
