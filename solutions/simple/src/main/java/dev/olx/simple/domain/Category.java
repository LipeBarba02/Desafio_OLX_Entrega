package dev.olx.simple.domain;

import java.util.Arrays;

/** API category codes are explicit values: enum names are implementation details. */
public enum Category {
    ELECTRONICS("eletronicos"), FURNITURE("moveis"), VEHICLES("veiculos"),
    REAL_ESTATE("imoveis"), SPORTS("esportes"), COLLECTIBLES("colecionaveis");

    private final String code;
    Category(String code) { this.code = code; }
    public String code() { return code; }

    public static Category fromCode(String code) {
        return Arrays.stream(values())
            .filter(category -> category.code.equalsIgnoreCase(code))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported category: " + code));
    }
}
