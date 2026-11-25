package dev.olx.ads.domain;

import java.util.Arrays;

public enum Category {
    ELECTRONICS("eletronicos"), FURNITURE("moveis"), VEHICLES("veiculos"),
    REAL_ESTATE("imoveis"), SPORTS("esportes"), COLLECTIBLES("colecionaveis");
    private final String code;
    Category(String code) { this.code = code; }
    public String code() { return code; }
    public static Category fromCode(String code) {
        return Arrays.stream(values()).filter(c -> c.code.equalsIgnoreCase(code))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("Unsupported category: " + code));
    }
}
