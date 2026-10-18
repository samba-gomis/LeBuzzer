package com.lebuzzer.backend.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * 5 random capital letters. I and O are left out because they look like 1 and 0:
 * 24 letters, so about 8 million possible codes.
 * SecureRandom, because the code is the key of the room: it must not be guessable.
 */
@Component
public class RandomCodeGenerator implements CodeGenerator {

	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ";
	private static final int CODE_LENGTH = 5;

	private final SecureRandom random = new SecureRandom();

	@Override
	public String nextCode() {
		StringBuilder code = new StringBuilder(CODE_LENGTH);
		for (int i = 0; i < CODE_LENGTH; i++) {
			code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}

}
