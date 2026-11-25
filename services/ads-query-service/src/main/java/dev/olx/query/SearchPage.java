package dev.olx.query;

import java.util.List;

public record SearchPage(List<SearchDocument> items,long total,String totalRelation,String nextCursor){ }
