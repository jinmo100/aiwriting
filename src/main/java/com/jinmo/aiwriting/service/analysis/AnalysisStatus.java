package com.jinmo.aiwriting.service.analysis;

public enum AnalysisStatus {
    PASS,
    WARN,
    REJECT;

    public boolean isWorseThan(AnalysisStatus other) {
        return this.ordinal() > other.ordinal();
    }
}
