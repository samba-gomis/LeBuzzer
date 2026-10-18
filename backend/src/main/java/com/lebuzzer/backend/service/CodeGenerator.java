package com.lebuzzer.backend.service;

/** Produces candidate game codes. An interface so that tests can force a collision. */
public interface CodeGenerator {

	String nextCode();

}
