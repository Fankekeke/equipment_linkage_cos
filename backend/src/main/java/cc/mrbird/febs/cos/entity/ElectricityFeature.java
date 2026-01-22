package cc.mrbird.febs.cos.entity;

import lombok.Data;

/**
 * 用电特征类
 */
@Data
public class ElectricityFeature {

    private Integer deviceId;
    private String deviceName;
    private double averageRunTime;
    private double totalRunTime;
    private int frequencyOfUse;
    private double powerRating;
    private double efficiencyScore;
}
