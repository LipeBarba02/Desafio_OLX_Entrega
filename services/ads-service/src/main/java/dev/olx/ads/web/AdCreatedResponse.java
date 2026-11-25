package dev.olx.ads.web;

import dev.olx.ads.domain.AdStatus;
import java.util.UUID;

public record AdCreatedResponse(UUID id,AdStatus status) { }
