package cc.mrbird.febs.cos.entity;

import lombok.Data;

/**
 * 设备用电统计
 */
@Data
public class DeviceElectricityStats {

    private Integer deviceId;
    private String deviceName;
    private double powerRating;
    private double totalRunTime;
    private double avgRunTime;
    private double maxRunTime;
    private double minRunTime;
    private double stdDev;
    private int sessionCount;
    private double totalConsumption;
}
