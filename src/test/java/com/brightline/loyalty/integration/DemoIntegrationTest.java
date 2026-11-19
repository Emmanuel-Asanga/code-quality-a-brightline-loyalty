package com.brightline.loyalty.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.brightline.loyalty.Demo;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DemoIntegrationTest {

    private final PrintStream originalOut = System.out;
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

    @AfterEach
    void restoreStandardOutput() {
        System.setOut(originalOut);
    }

    @Test
    @DisplayName("The demo prints points for valid purchases and a clear message for failures")
    void shouldPrintPointsForValidPurchasesAndReportFailures() {
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));

        Demo.main(new String[0]);

        assertEquals(List.of(
                "Member m1, GHS 100 -> 10 points",
                "Member m2, GHS 250 -> 50 points",
                "Member m3, GHS 600 -> 140 points",
                "Member unknown, GHS 100 -> FAILED: No member found with id 'unknown'",
                "Member m1, GHS -5 -> FAILED: Purchase amount must not be negative: -5"),
                captured.toString(StandardCharsets.UTF_8).lines().toList());
    }
}
