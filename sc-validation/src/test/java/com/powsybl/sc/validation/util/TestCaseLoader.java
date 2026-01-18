/*
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sc.validation.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for loading test cases from JSON reference data files.
 * Parses JSON files from the classpath (typically src/test/resources/reference-data/).
 *
 * <p>This class is thread-safe. The shared ObjectMapper instance is configured once
 * at class load time and is only used for read operations (deserialization), which
 * are thread-safe per Jackson documentation.</p>
 *
 * @author GridMV Validation Team
 */
public final class TestCaseLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestCaseLoader.class);

    /**
     * Shared ObjectMapper instance for JSON deserialization.
     * Thread-safe for read operations after configuration is complete.
     */
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private TestCaseLoader() {
        // Utility class - prevent instantiation
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Allow forward compatibility - ignore unknown fields in JSON
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * Loads a list of test cases from a JSON resource file.
     *
     * @param resourcePath path to the JSON file relative to the classpath
     *                     (e.g., "reference-data/iec60909-section-3.1.json")
     * @return list of strongly-typed TestCase objects
     * @throws UncheckedIOException if the resource cannot be read or parsed
     * @throws IllegalArgumentException if the resource path is null or resource not found
     */
    public static List<TestCase> loadTestCases(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath must not be null");

        LOGGER.debug("Loading test cases from: {}", resourcePath);

        try (InputStream is = TestCaseLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }

            List<TestCase> testCases = OBJECT_MAPPER.readValue(is, new TypeReference<List<TestCase>>() { });

            LOGGER.info("Loaded {} test cases from {}", testCases.size(), resourcePath);
            return testCases;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load test cases from: " + resourcePath, e);
        }
    }

    /**
     * Finds a test case by its test ID from a list of test cases.
     *
     * @param testCases the list of test cases to search
     * @param testId the unique identifier of the test case to find
     * @return the matching TestCase
     * @throws IllegalArgumentException if testCases or testId is null, or if the test case is not found
     */
    public static TestCase findTestCase(List<TestCase> testCases, String testId) {
        Objects.requireNonNull(testCases, "testCases must not be null");
        Objects.requireNonNull(testId, "testId must not be null");

        return testCases.stream()
                .filter(tc -> tc.getTestId().equals(testId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Test case not found: " + testId));
    }

    /**
     * Loads a single test case from a JSON resource file.
     * Useful when the JSON file contains a single test case object rather than an array.
     *
     * @param resourcePath path to the JSON file relative to the classpath
     * @return a strongly-typed TestCase object
     * @throws UncheckedIOException if the resource cannot be read or parsed
     * @throws IllegalArgumentException if the resource path is null or resource not found
     */
    public static TestCase loadSingleTestCase(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath must not be null");

        LOGGER.debug("Loading single test case from: {}", resourcePath);

        try (InputStream is = TestCaseLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }

            TestCase testCase = OBJECT_MAPPER.readValue(is, TestCase.class);

            LOGGER.info("Loaded test case {} from {}", testCase.getTestId(), resourcePath);
            return testCase;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load test case from: " + resourcePath, e);
        }
    }
}
