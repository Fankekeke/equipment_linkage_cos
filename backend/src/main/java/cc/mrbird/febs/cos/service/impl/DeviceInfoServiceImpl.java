package cc.mrbird.febs.cos.service.impl;

import cc.mrbird.febs.common.utils.R;
import cc.mrbird.febs.cos.entity.*;
import cc.mrbird.febs.cos.dao.DeviceInfoMapper;
import cc.mrbird.febs.cos.service.IDeviceInfoService;
import cc.mrbird.febs.cos.service.IDeviceOfflineRecordService;
import cc.mrbird.febs.cos.service.IEventDetailService;
import cc.mrbird.febs.cos.service.IOperateRecordInfoService;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备管理 实现层
 *
 * @author FanK
 */
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class DeviceInfoServiceImpl extends ServiceImpl<DeviceInfoMapper, DeviceInfo> implements IDeviceInfoService {

    private final IEventDetailService eventDetailService;

    private final IDeviceOfflineRecordService deviceOfflineRecordService;

    private final IOperateRecordInfoService operateRecordInfoService;

    /**
     * 分页获取设备管理信息
     *
     * @param page       分页对象
     * @param deviceInfo 设备管理信息
     * @return 结果
     */
    @Override
    public IPage<LinkedHashMap<String, Object>> selectDevicePage(Page<DeviceInfo> page, DeviceInfo deviceInfo) {
        return baseMapper.selectDevicePage(page, deviceInfo);
    }

    /**
     * 场景事件处理
     *
     * @param eventId 事件ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void eventCheck(Integer eventId) {
        if (eventId == null) {
            return;
        }
        // 获取事件详情
        List<EventDetail> eventDetailList = eventDetailService.list(Wrappers.<EventDetail>lambdaQuery().eq(EventDetail::getEventId, eventId));
        if (CollectionUtil.isEmpty(eventDetailList)) {
            return;
        }

        // 待添加的上下线记录
        List<DeviceOfflineRecord> deviceOfflineRecordList = CollectionUtil.newArrayList();
        List<DeviceInfo> toUpdateList = CollectionUtil.newArrayList();
        for (EventDetail eventDetail : eventDetailList) {
            // 获取设备信息
            DeviceInfo deviceInfo = new DeviceInfo();
            deviceInfo.setId(eventDetail.getDeviceId());
            deviceInfo.setOnlineFlag(eventDetail.getOpenFlag());
            deviceInfo.setOpenFlag(eventDetail.getOpenFlag());
            toUpdateList.add(deviceInfo);

            // 设置上下线记录
            DeviceOfflineRecord deviceOfflineRecord = new DeviceOfflineRecord();
            deviceOfflineRecord.setType(eventDetail.getOpenFlag());
            deviceOfflineRecord.setOnlineDate(DateUtil.formatDateTime(new Date()));
            deviceOfflineRecord.setDeviceId(eventDetail.getDeviceId());
            deviceOfflineRecordList.add(deviceOfflineRecord);
        }


        deviceOfflineRecordService.saveBatch(deviceOfflineRecordList);
        this.updateBatchById(toUpdateList);
    }

    /**
     * 推荐场景
     *
     * @param userId 用户ID
     * @return 结果
     */
    @Override
    public List<SceneRecommendation> analyzeAndRecommend(Integer userId) {
        // 获取用户的所有设备
        List<DeviceInfo> deviceList = this.list(Wrappers.<DeviceInfo>lambdaQuery().eq(DeviceInfo::getUserId, userId));
        if (CollectionUtil.isEmpty(deviceList)) {
            return Collections.emptyList();
        }

        // 提取设备ID列表
        List<Integer> deviceIds = deviceList.stream().map(DeviceInfo::getId).collect(Collectors.toList());

        // 获取最近180天的操作记录
        Date startDate = DateUtil.offsetDay(new Date(), -180);
        List<OperateRecordInfo> operateRecords = operateRecordInfoService.list(Wrappers.<OperateRecordInfo>lambdaQuery()
                .in(OperateRecordInfo::getDeviceId, deviceIds)
                .ge(OperateRecordInfo::getCreateDate, DateUtil.formatDateTime(startDate))
                .orderByAsc(OperateRecordInfo::getCreateDate));

        if (CollectionUtil.isEmpty(operateRecords)) {
            return Collections.emptyList();
        }

        // 分析操作记录，发现设备间的关联性
        List<SceneRecommendation> recommendations = analyzeDeviceCorrelations(operateRecords, deviceList);

        return recommendations;
    }

    /**
     * 分析设备操作相关性
     *
     * @param operateRecords 操作记录列表
     * @param deviceList 设备列表
     * @return 推荐场景列表
     */
    private List<SceneRecommendation> analyzeDeviceCorrelations(List<OperateRecordInfo> operateRecords, List<DeviceInfo> deviceList) {
        List<SceneRecommendation> recommendations = new ArrayList<>();

        // 按时间分组操作记录，时间窗口为10分钟
        Map<String, List<OperateRecordInfo>> timeGroupedRecords = new HashMap<>();
        for (OperateRecordInfo record : operateRecords) {
            Date recordDate = DateUtil.parseDateTime(record.getCreateDate());
            // 将时间按5分钟分组
            long timeGroup = recordDate.getTime() / (10 * 60 * 1000);
            String groupKey = timeGroup + "";

            timeGroupedRecords.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(record);
        }

        // 统计设备组合出现频率
        Map<String, Integer> deviceCombinationCount = new HashMap<>();
        Map<String, List<OperateRecordInfo>> combinationDetails = new HashMap<>();

        // 新增：统计同时开启/关闭的设备组合
        Map<String, Integer> simultaneousOpenCount = new HashMap<>();
        Map<String, Integer> simultaneousCloseCount = new HashMap<>();
        Map<String, List<OperateRecordInfo>> simultaneousOpenDetails = new HashMap<>();
        Map<String, List<OperateRecordInfo>> simultaneousCloseDetails = new HashMap<>();

        for (List<OperateRecordInfo> group : timeGroupedRecords.values()) {
            if (group.size() > 1) {
                // 提取设备ID并排序，确保组合唯一性
                List<Integer> deviceIdsInGroup = group.stream()
                        .map(OperateRecordInfo::getDeviceId)
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());

                if (deviceIdsInGroup.size() > 1) {
                    String combinationKey = deviceIdsInGroup.toString();
                    deviceCombinationCount.put(combinationKey, deviceCombinationCount.getOrDefault(combinationKey, 0) + 1);
                    combinationDetails.put(combinationKey, group);

                    // 新增：分析同时开启或关闭的场景
                    analyzeSimultaneousOperations(group, simultaneousOpenCount, simultaneousCloseCount,
                            simultaneousOpenDetails, simultaneousCloseDetails);
                }
            }
        }

        // 根据出现频率生成推荐
        for (Map.Entry<String, Integer> entry : deviceCombinationCount.entrySet()) {
            if (entry.getValue() >= 2) { // 至少出现2次才推荐
                List<OperateRecordInfo> sampleRecords = combinationDetails.get(entry.getKey());
                if (CollectionUtil.isNotEmpty(sampleRecords)) {
                    SceneRecommendation recommendation = generateRecommendation(sampleRecords, deviceList);
                    if (recommendation != null) {
                        recommendations.add(recommendation);
                    }
                }
            }
        }

        // 新增：生成同时开启的推荐
        for (Map.Entry<String, Integer> entry : simultaneousOpenCount.entrySet()) {
            if (entry.getValue() >= 3) {
                List<OperateRecordInfo> sampleRecords = simultaneousOpenDetails.get(entry.getKey());
                SceneRecommendation recommendation = generateSimultaneousRecommendation(sampleRecords, deviceList, "1");
                if (recommendation != null) {
                    recommendations.add(recommendation);
                }
            }
        }

        // 新增：生成同时关闭的推荐
        for (Map.Entry<String, Integer> entry : simultaneousCloseCount.entrySet()) {
            if (entry.getValue() >= 3) {
                List<OperateRecordInfo> sampleRecords = simultaneousCloseDetails.get(entry.getKey());
                SceneRecommendation recommendation = generateSimultaneousRecommendation(sampleRecords, deviceList, "0");
                if (recommendation != null) {
                    recommendations.add(recommendation);
                }
            }
        }

        return recommendations;
    }

    /**
     * 生成推荐场景
     *
     * @param sampleRecords 样本操作记录
     * @param deviceList 设备列表
     * @return 推荐场景
     */
    private SceneRecommendation generateRecommendation(List<OperateRecordInfo> sampleRecords, List<DeviceInfo> deviceList) {
        if (sampleRecords.size() < 2) {

            return null;
        }

        // 获取所有设备
        List<DeviceInfo> allDevices = this.list();
        Map<Integer, String> deviceNameMap = allDevices.stream()
                .collect(Collectors.toMap(DeviceInfo::getId, DeviceInfo::getName));

        // 按时间排序
        sampleRecords.sort(Comparator.comparing(OperateRecordInfo::getCreateDate));

        OperateRecordInfo firstOperation = sampleRecords.get(0);
        OperateRecordInfo secondOperation = sampleRecords.get(1);

        DeviceInfo triggerDevice = deviceList.stream()
                .filter(d -> d.getId().equals(firstOperation.getDeviceId()))
                .findFirst()
                .orElse(null);

        DeviceInfo targetDevice = deviceList.stream()
                .filter(d -> d.getId().equals(secondOperation.getDeviceId()))
                .findFirst()
                .orElse(null);

        if (triggerDevice == null || targetDevice == null) {
            return null;
        }
        if (firstOperation.getDeviceId().equals(secondOperation.getDeviceId())) {
            return null; // 同一设备的操作不生成推荐
        }
        SceneRecommendation recommendation = new SceneRecommendation();
        recommendation.setSceneName(String.format("当%s%s时，自动%s%s",
                triggerDevice.getName(),
                "1".equals(firstOperation.getOpenFlag()) ? "开启" : "关闭",
                targetDevice.getName(),
                "1".equals(secondOperation.getOpenFlag()) ? "开启" : "关闭"));
        recommendation.setDescription(String.format("根据您的使用习惯，系统检测到您经常在%s%s后%s%s，建议创建自动化场景。",
                triggerDevice.getName(),
                "1".equals(firstOperation.getOpenFlag()) ? "开启" : "关闭",
                targetDevice.getName(),
                "1".equals(secondOperation.getOpenFlag()) ? "开启" : "关闭"));
        recommendation.setTriggerDeviceId(firstOperation.getDeviceId());
        recommendation.setTriggerDeviceName(deviceNameMap.get(firstOperation.getDeviceId()));
        recommendation.setTargetDeviceId(secondOperation.getDeviceId());
        recommendation.setTargetDeviceName(deviceNameMap.get(secondOperation.getDeviceId()));
        recommendation.setTriggerAction(firstOperation.getOpenFlag());
        recommendation.setTargetAction(secondOperation.getOpenFlag());

        return recommendation;
    }

    /**
     * 分析同时操作场景
     */
    private void analyzeSimultaneousOperations(List<OperateRecordInfo> group,
                                               Map<String, Integer> simultaneousOpenCount,
                                               Map<String, Integer> simultaneousCloseCount,
                                               Map<String, List<OperateRecordInfo>> simultaneousOpenDetails,
                                               Map<String, List<OperateRecordInfo>> simultaneousCloseDetails) {
        // 分别统计同时开启和同时关闭的设备
        List<OperateRecordInfo> openOperations = group.stream()
                .filter(op -> "1".equals(op.getOpenFlag()))
                .collect(Collectors.toList());

        List<OperateRecordInfo> closeOperations = group.stream()
                .filter(op -> "0".equals(op.getOpenFlag()))
                .collect(Collectors.toList());

        // 如果同时开启的设备数大于1
        if (openOperations.size() > 1) {
            List<Integer> openDeviceIds = openOperations.stream()
                    .map(OperateRecordInfo::getDeviceId)
                    .sorted()
                    .collect(Collectors.toList());
            String openKey = openDeviceIds.toString();
            simultaneousOpenCount.put(openKey, simultaneousOpenCount.getOrDefault(openKey, 0) + 1);
            simultaneousOpenDetails.put(openKey, openOperations);
        }

        // 如果同时关闭的设备数大于1
        if (closeOperations.size() > 1) {
            List<Integer> closeDeviceIds = closeOperations.stream()
                    .map(OperateRecordInfo::getDeviceId)
                    .sorted()
                    .collect(Collectors.toList());
            String closeKey = closeDeviceIds.toString();
            simultaneousCloseCount.put(closeKey, simultaneousCloseCount.getOrDefault(closeKey, 0) + 1);
            simultaneousCloseDetails.put(closeKey, closeOperations);
        }
    }

    /**
     * 生成同时操作推荐场景
     */
    private SceneRecommendation generateSimultaneousRecommendation(List<OperateRecordInfo> sampleRecords,
                                                                   List<DeviceInfo> deviceList,
                                                                   String operationType) {
        if (sampleRecords.size() < 2) {
            return null;
        }

        // 获取所有设备
        List<DeviceInfo> allDevices = this.list();
        Map<Integer, String> deviceNameMap = allDevices.stream()
                .collect(Collectors.toMap(DeviceInfo::getId, DeviceInfo::getName));

        List<String> deviceNames = sampleRecords.stream()
                .map(record -> deviceNameMap.get(record.getDeviceId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (deviceNames.size() < 2) {
            return null;
        }

        SceneRecommendation recommendation = new SceneRecommendation();
        String operationText = "1".equals(operationType) ? "开启" : "关闭";

        recommendation.setSceneName(String.format("一键%s%s等设备", operationText,
                String.join("、", deviceNames.subList(0, Math.min(3, deviceNames.size())))));

        recommendation.setDescription(String.format("根据您的使用习惯，系统检测到您经常同时%s%s等设备，建议创建一键%s场景。",
                operationText, String.join("、", deviceNames), operationText));

        // 设置设备列表
        recommendation.setDeviceIds(sampleRecords.stream()
                .map(OperateRecordInfo::getDeviceId)
                .collect(Collectors.toList()));

        recommendation.setDeviceNames(deviceNames);
        recommendation.setTargetAction(operationType);

        return recommendation;
    }
}
