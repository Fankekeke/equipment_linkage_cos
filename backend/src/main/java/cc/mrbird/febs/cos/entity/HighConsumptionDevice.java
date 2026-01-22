package cc.mrbird.febs.cos.entity;

import lombok.Data;

/**
 * 高耗电设备
 */
@Data
public class HighConsumptionDevice {

    private Integer deviceId;
    private String deviceName;
    private double totalConsumption;
    private double powerRating;
    private double avgRunTime;

}
