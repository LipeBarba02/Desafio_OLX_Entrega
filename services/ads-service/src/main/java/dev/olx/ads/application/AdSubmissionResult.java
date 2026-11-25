package dev.olx.ads.application;

import dev.olx.ads.domain.AdStatus;
import java.util.UUID;

public record AdSubmissionResult(UUID adId, AdStatus status) { }
