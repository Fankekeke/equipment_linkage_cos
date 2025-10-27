package cc.mrbird.febs.cos.entity;

import lombok.Data;

import java.util.List;

@Data
public class SceneRecommendation {

    private String sceneName;
    private String description;
    private Integer triggerDeviceId;
    private Integer targetDeviceId;

    private String triggerDeviceName;
    private String targetDeviceName;

    private String triggerAction;
    private String targetAction;
    private List<Integer> deviceIds;

    private List<String> deviceNames;
}
