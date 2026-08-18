package org.example.dto;

/**
 * Text submitted by the browser for speech synthesis.
 */
public record SpeechSynthesisRequest(String text) {
}