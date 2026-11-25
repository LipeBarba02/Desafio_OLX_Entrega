package dev.olx.ads.application;

import java.util.UUID;

public class AdNotFoundException extends RuntimeException { public AdNotFoundException(UUID id){super("Ad not found: "+id);} }
