package cc.mrbird.febs.cos.entity;

import lombok.Data;

/**
 * 维护指标
 */
@Data
public class MaintenanceIndicator {

    private Integer deviceId;
    private String deviceName;
    private double usageIntensity;
    private double runtimeVariability;
    private String riskLevel;
}
