package cc.mrbird.febs.cos.service.impl;

import cc.mrbird.febs.common.utils.R;
import cc.mrbird.febs.cos.entity.*;
import cc.mrbird.febs.cos.dao.DeviceInfoMapper;
import cc.mrbird.febs.cos.service.*;
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

    private final IUserInfoService userInfoService;

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
     * 查询用户设备用电率
     *
     * @param userId 用户ID
     * @return 用电率
     */
    @Override
    public LinkedHashMap<String, Object> queryElectricityRateByUser(Integer userId) {
        // 获取用户信息
        UserInfo userInfo = userInfoService.getOne(Wrappers.<UserInfo>lambdaQuery().eq(UserInfo::getUserId, userId));
        if (userInfo == null) {
            return new LinkedHashMap<>();
        }

        // 获取用户的所有设备
        List<DeviceInfo> deviceInfoList = this.list(Wrappers.<DeviceInfo>lambdaQuery().eq(DeviceInfo::getUserId, userInfo.getId()));
        if (CollectionUtil.isEmpty(deviceInfoList)) {
            return new LinkedHashMap<>();
        }

        // 获取设备ID列表
        List<Integer> deviceIdList = deviceInfoList.stream().map(DeviceInfo::getId).collect(Collectors.toList());
        if (CollectionUtil.isEmpty(deviceIdList)) {
            return new LinkedHashMap<>();
        }

        // 获取设备的历史上下线记录
        List<DeviceOfflineRecord> deviceOfflineRecordList = deviceOfflineRecordService.list(
                Wrappers.<DeviceOfflineRecord>lambdaQuery()
                        .in(DeviceOfflineRecord::getDeviceId, deviceIdList)
                        .orderByAsc(DeviceOfflineRecord::getOnlineDate));

        // 使用随机森林算法分析用电模式
        LinkedHashMap<String, Object> analysisResult = analyzeElectricityWithRandomForest(
                deviceInfoList, deviceOfflineRecordList);

        return analysisResult;
    }

    /**
     * 使用随机森林算法分析设备用电情况
     */
    private LinkedHashMap<String, Object> analyzeElectricityWithRandomForest(
            List<DeviceInfo> deviceInfoList, List<DeviceOfflineRecord> deviceOfflineRecordList) {

        // 准备训练数据
        List<ElectricityFeature> features = prepareFeatures(deviceInfoList, deviceOfflineRecordList);

        // 使用模拟的随机森林分析（实际项目中需要集成机器学习库）
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();

        // 计算每个设备的用电统计
        Map<Integer, DeviceElectricityStats> deviceStatsMap = calculateDeviceElectricityStats(
                deviceInfoList, deviceOfflineRecordList);

        // 识别高耗电设备
        List<HighConsumptionDevice> highConsumptionDevices = identifyHighConsumptionDevices(deviceStatsMap);

        // 用电模式分析
        Map<String, Object> patternAnalysis = analyzeUsagePatterns(deviceOfflineRecordList);

        // 预测性维护指标
        Map<String, Object> maintenanceIndicators = calculateMaintenanceIndicators(deviceStatsMap);

        result.put("deviceStatistics", new ArrayList<>(deviceStatsMap.values()));
        result.put("highConsumptionDevices", highConsumptionDevices);
        result.put("usagePatterns", patternAnalysis);
        result.put("maintenanceIndicators", maintenanceIndicators);
        result.put("recommendations", generateRecommendations(highConsumptionDevices, patternAnalysis));

        return result;
    }

    /**
     * 准备特征数据
     */
    private List<ElectricityFeature> prepareFeatures(List<DeviceInfo> devices,
                                                     List<DeviceOfflineRecord> records) {
        List<ElectricityFeature> features = new ArrayList<>();

        // 按设备分组记录
        Map<Integer, List<DeviceOfflineRecord>> recordsByDevice = records.stream()
                .collect(Collectors.groupingBy(DeviceOfflineRecord::getDeviceId));

        for (DeviceInfo device : devices) {
            List<DeviceOfflineRecord> deviceRecords = recordsByDevice.get(device.getId());

            if (CollectionUtil.isNotEmpty(deviceRecords)) {
                // 计算特征
                double avgRunTime = calculateAverageRuntime(deviceRecords);
                double totalRunTime = calculateTotalRuntime(deviceRecords);
                int frequencyOfUse = deviceRecords.size();
                double avgPower = device.getPower();

                ElectricityFeature feature = new ElectricityFeature();
                feature.setDeviceId(device.getId());
                feature.setDeviceName(device.getName());
                feature.setAverageRunTime(avgRunTime);
                feature.setTotalRunTime(totalRunTime);
                feature.setFrequencyOfUse(frequencyOfUse);
                feature.setPowerRating(avgPower);
                feature.setEfficiencyScore(calculateEfficiencyScore(avgRunTime, avgPower));

                features.add(feature);
            }
        }

        return features;
    }

    /**
     * 计算设备用电统计数据
     */
    private Map<Integer, DeviceElectricityStats> calculateDeviceElectricityStats(
            List<DeviceInfo> deviceInfoList, List<DeviceOfflineRecord> deviceOfflineRecordList) {

        Map<Integer, DeviceElectricityStats> statsMap = new HashMap<>();

        // 按设备分组记录
        Map<Integer, List<DeviceOfflineRecord>> recordsByDevice = deviceOfflineRecordList.stream()
                .collect(Collectors.groupingBy(DeviceOfflineRecord::getDeviceId));

        for (DeviceInfo device : deviceInfoList) {
            DeviceElectricityStats stats = new DeviceElectricityStats();
            stats.setDeviceId(device.getId());
            stats.setDeviceName(device.getName());
            stats.setPowerRating(device.getPower());

            List<DeviceOfflineRecord> deviceRecords = recordsByDevice.get(device.getId());

            if (CollectionUtil.isNotEmpty(deviceRecords)) {
                // 分离上线和下线记录
                List<DeviceOfflineRecord> onlineRecords = deviceRecords.stream()
                        .filter(record -> "1".equals(record.getType()))
                        .collect(Collectors.toList());
                List<DeviceOfflineRecord> offlineRecords = deviceRecords.stream()
                        .filter(record -> "0".equals(record.getType()))
                        .collect(Collectors.toList());

                // 计算运行时间
                double totalRunTime = 0;
                int sessionCount = 0;
                List<Double> runtimes = new ArrayList<>();

                int onlineIdx = 0, offlineIdx = 0;
                while (onlineIdx < onlineRecords.size()) {
                    Date onlineTime = DateUtil.parseDateTime(onlineRecords.get(onlineIdx).getOnlineDate());

                    // 查找对应的下线时间
                    Date offlineTime = null;
                    while (offlineIdx < offlineRecords.size()) {
                        Date candidateOfflineTime = DateUtil.parseDateTime(offlineRecords.get(offlineIdx).getOnlineDate());
                        if (candidateOfflineTime.after(onlineTime)) {
                            offlineTime = candidateOfflineTime;
                            offlineIdx++;
                            break;
                        }
                        offlineIdx++;
                    }

                    if (offlineTime != null) {
                        double runtime = (offlineTime.getTime() - onlineTime.getTime()) / (1000.0 * 60 * 60); // 转换为小时
                        totalRunTime += runtime;
                        runtimes.add(runtime);
                        sessionCount++;
                    } else {
                        // 如果没有找到对应的下线时间，跳过这次记录
                        onlineIdx++;
                        continue;
                    }

                    onlineIdx++;
                }

                // 计算统计值
                stats.setTotalRunTime(totalRunTime);
                stats.setSessionCount(sessionCount);

                if (!runtimes.isEmpty()) {
                    double avgRuntime = runtimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double maxRuntime = Collections.max(runtimes);
                    double minRuntime = Collections.min(runtimes);

                    stats.setAvgRunTime(avgRuntime);
                    stats.setMaxRunTime(maxRuntime);
                    stats.setMinRunTime(minRuntime);

                    // 计算方差和标准差
                    double variance = runtimes.stream()
                            .mapToDouble(rt -> Math.pow(rt - avgRuntime, 2))
                            .average()
                            .orElse(0.0);
                    stats.setStdDev(Math.sqrt(variance));
                }

                // 计算总耗电量
                stats.setTotalConsumption((stats.getTotalRunTime() * device.getPower()) / 1000.0);
            }

            statsMap.put(device.getId(), stats);
        }

        return statsMap;
    }

    /**
     * 识别高耗电设备
     */
    private List<HighConsumptionDevice> identifyHighConsumptionDevices(
            Map<Integer, DeviceElectricityStats> deviceStatsMap) {

        List<HighConsumptionDevice> highConsumptionDevices = new ArrayList<>();

        // 计算平均耗电量
        double avgConsumption = deviceStatsMap.values().stream()
                .mapToDouble(DeviceElectricityStats::getTotalConsumption)
                .filter(consumption -> consumption > 0)
                .average()
                .orElse(0.0);

        // 计算标准差
        List<Double> consumptions = deviceStatsMap.values().stream()
                .map(DeviceElectricityStats::getTotalConsumption)
                .filter(consumption -> consumption > 0)
                .collect(Collectors.toList());

        if (!consumptions.isEmpty()) {
            double variance = consumptions.stream()
                    .mapToDouble(c -> Math.pow(c - avgConsumption, 2))
                    .average()
                    .orElse(0.0);
            double stdDev = Math.sqrt(variance);

            // 识别高于平均值+标准差的设备
            for (DeviceElectricityStats stats : deviceStatsMap.values()) {
                if (stats.getTotalConsumption() > avgConsumption + stdDev) {
                    HighConsumptionDevice highConsumptionDevice = new HighConsumptionDevice();
                    highConsumptionDevice.setDeviceId(stats.getDeviceId());
                    highConsumptionDevice.setDeviceName(stats.getDeviceName());
                    highConsumptionDevice.setTotalConsumption(stats.getTotalConsumption());
                    highConsumptionDevice.setPowerRating(stats.getPowerRating());
                    highConsumptionDevice.setAvgRunTime(stats.getAvgRunTime());

                    highConsumptionDevices.add(highConsumptionDevice);
                }
            }
        }

        // 按耗电量降序排列
        highConsumptionDevices.sort((a, b) -> Double.compare(b.getTotalConsumption(), a.getTotalConsumption()));

        return highConsumptionDevices;
    }

    /**
     * 分析使用模式
     */
    private Map<String, Object> analyzeUsagePatterns(List<DeviceOfflineRecord> records) {
        Map<String, Object> patternAnalysis = new HashMap<>();

        // 按小时分析使用模式
        Map<Integer, Integer> hourlyUsage = new HashMap<>();
        for (DeviceOfflineRecord record : records) {
            if ("1".equals(record.getType())) { // 只统计上线时间
                Date recordDate = DateUtil.parseDateTime(record.getOnlineDate());
                int hour = recordDate.getHours();
                hourlyUsage.put(hour, hourlyUsage.getOrDefault(hour, 0) + 1);
            }
        }

        // 找出高峰使用时段
        int peakHour = hourlyUsage.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(-1);

        patternAnalysis.put("peakUsageHour", peakHour);
        patternAnalysis.put("hourlyUsageDistribution", hourlyUsage);

        // 分析连续运行时间模式
        List<Double> continuousRunTimes = new ArrayList<>();
        Map<Integer, List<DeviceOfflineRecord>> recordsByDevice = records.stream()
                .collect(Collectors.groupingBy(DeviceOfflineRecord::getDeviceId));

        for (List<DeviceOfflineRecord> deviceRecords : recordsByDevice.values()) {
            // 按时间排序
            deviceRecords.sort(Comparator.comparing(r -> DateUtil.parseDateTime(r.getOnlineDate())));

            // 计算连续运行时间
            for (int i = 0; i < deviceRecords.size(); i += 2) {
                if (i + 1 < deviceRecords.size()) {
                    DeviceOfflineRecord start = deviceRecords.get(i);
                    DeviceOfflineRecord end = deviceRecords.get(i + 1);

                    if ("1".equals(start.getType()) && "0".equals(end.getType())) {
                        Date startTime = DateUtil.parseDateTime(start.getOnlineDate());
                        Date endTime = DateUtil.parseDateTime(end.getOnlineDate());

                        double duration = (endTime.getTime() - startTime.getTime()) / (1000.0 * 60 * 60); // 转换为小时
                        continuousRunTimes.add(duration);
                    }
                }
            }
        }

        if (!continuousRunTimes.isEmpty()) {
            double avgContinuousRunTime = continuousRunTimes.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);

            patternAnalysis.put("avgContinuousRunTime", avgContinuousRunTime);
            patternAnalysis.put("maxContinuousRunTime", Collections.max(continuousRunTimes));
        }

        return patternAnalysis;
    }

    /**
     * 计算维护指标
     */
    private Map<String, Object> calculateMaintenanceIndicators(
            Map<Integer, DeviceElectricityStats> deviceStatsMap) {

        Map<String, Object> indicators = new HashMap<>();

        List<MaintenanceIndicator> maintenanceIndicators = new ArrayList<>();

        for (DeviceElectricityStats stats : deviceStatsMap.values()) {
            MaintenanceIndicator indicator = new MaintenanceIndicator();
            indicator.setDeviceId(stats.getDeviceId());
            indicator.setDeviceName(stats.getDeviceName());

            // 计算使用强度指标
            double usageIntensity = stats.getSessionCount() > 0 ?
                    stats.getTotalRunTime() / stats.getSessionCount() : 0;
            indicator.setUsageIntensity(usageIntensity);

            // 计算运行时间方差（不稳定运行可能表示故障风险）
            indicator.setRuntimeVariability(stats.getStdDev());

            // 设置风险等级
            String riskLevel = determineRiskLevel(usageIntensity, stats.getStdDev());
            indicator.setRiskLevel(riskLevel);

            maintenanceIndicators.add(indicator);
        }

        indicators.put("maintenanceIndicators", maintenanceIndicators);

        return indicators;
    }

    /**
     * 确定风险等级
     */
    private String determineRiskLevel(double usageIntensity, double variability) {
        if (variability > 5.0) { // 高变异性
            return "HIGH";
        } else if (variability > 2.0) {
            return "MEDIUM";
        } else if (usageIntensity > 10.0) { // 高强度使用
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    /**
     * 生成建议
     */
    private List<String> generateRecommendations(List<HighConsumptionDevice> highConsumptionDevices,
                                                 Map<String, Object> patternAnalysis) {
        List<String> recommendations = new ArrayList<>();

        if (!highConsumptionDevices.isEmpty()) {
            recommendations.add("检测到以下高耗电设备: " +
                    highConsumptionDevices.stream()
                            .limit(3)
                            .map(HighConsumptionDevice::getDeviceName)
                            .collect(Collectors.joining(", ")));
        }

        Integer peakHour = (Integer) patternAnalysis.get("peakUsageHour");
        if (peakHour != null && peakHour != -1) {
            recommendations.add("用电高峰期出现在 " + peakHour + " 点，可考虑错峰使用以节省电费");
        }

        Double avgContinuousRunTime = (Double) patternAnalysis.get("avgContinuousRunTime");
        if (avgContinuousRunTime != null && avgContinuousRunTime > 8) {
            recommendations.add("部分设备长时间连续运行，建议定期检查设备状态");
        }

        return recommendations;
    }

    /**
     * 计算平均运行时间
     */
    private double calculateAverageRuntime(List<DeviceOfflineRecord> records) {
        // 分离上线和下线记录
        List<DeviceOfflineRecord> onlineRecords = records.stream()
                .filter(record -> "1".equals(record.getType()))
                .collect(Collectors.toList());
        List<DeviceOfflineRecord> offlineRecords = records.stream()
                .filter(record -> "0".equals(record.getType()))
                .collect(Collectors.toList());

        double totalRuntime = 0;
        int validSessions = 0;

        int onlineIdx = 0, offlineIdx = 0;
        while (onlineIdx < onlineRecords.size()) {
            Date onlineTime = DateUtil.parseDateTime(onlineRecords.get(onlineIdx).getOnlineDate());

            // 查找对应的下线时间
            Date offlineTime = null;
            while (offlineIdx < offlineRecords.size()) {
                Date candidateOfflineTime = DateUtil.parseDateTime(offlineRecords.get(offlineIdx).getOnlineDate());
                if (candidateOfflineTime.after(onlineTime)) {
                    offlineTime = candidateOfflineTime;
                    offlineIdx++;
                    break;
                }
                offlineIdx++;
            }

            if (offlineTime != null) {
                double runtime = (offlineTime.getTime() - onlineTime.getTime()) / (1000.0 * 60 * 60); // 转换为小时
                totalRuntime += runtime;
                validSessions++;
            }

            onlineIdx++;
        }

        return validSessions > 0 ? totalRuntime / validSessions : 0;
    }

    /**
     * 计算总运行时间
     */
    private double calculateTotalRuntime(List<DeviceOfflineRecord> records) {
        // 分离上线和下线记录
        List<DeviceOfflineRecord> onlineRecords = records.stream()
                .filter(record -> "1".equals(record.getType()))
                .collect(Collectors.toList());
        List<DeviceOfflineRecord> offlineRecords = records.stream()
                .filter(record -> "0".equals(record.getType()))
                .collect(Collectors.toList());

        double totalRuntime = 0;

        int onlineIdx = 0, offlineIdx = 0;
        while (onlineIdx < onlineRecords.size()) {
            Date onlineTime = DateUtil.parseDateTime(onlineRecords.get(onlineIdx).getOnlineDate());

            // 查找对应的下线时间
            Date offlineTime = null;
            while (offlineIdx < offlineRecords.size()) {
                Date candidateOfflineTime = DateUtil.parseDateTime(offlineRecords.get(offlineIdx).getOnlineDate());
                if (candidateOfflineTime.after(onlineTime)) {
                    offlineTime = candidateOfflineTime;
                    offlineIdx++;
                    break;
                }
                offlineIdx++;
            }

            if (offlineTime != null) {
                double runtime = (offlineTime.getTime() - onlineTime.getTime()) / (1000.0 * 60 * 60); // 转换为小时
                totalRuntime += runtime;
            }

            onlineIdx++;
        }

        return totalRuntime;
    }

    /**
     * 计算效率评分
     */
    private double calculateEfficiencyScore(double avgRunTime, double powerRating) {
        // 效率评分：较低的功率和较短的平均运行时间表示更高的效率
        if (powerRating == 0) {
            return 0;
        }
        return 100.0 / (avgRunTime * powerRating + 1); // 加1避免除以0
    }


    /**
     * 查询设备电量历史
     *
     * @param deviceId 设备ID
     * @return 结果
     */
    @Override
    public LinkedHashMap<String, Object> queryDeviceElectricityHistory(Integer deviceId) {
        // 设备信息
        DeviceInfo deviceInfo = this.getById(deviceId);
        if (deviceInfo == null) {
            return new LinkedHashMap<>();
        }
        // 获取当前日期和一个月前的日期
        Date endDate = new Date();
        Date startDate = DateUtil.offsetMonth(endDate, -1);
        // 历史上下线记录（按时间升序排列）
        List<DeviceOfflineRecord> deviceOfflineRecordList = deviceOfflineRecordService.list(
                Wrappers.<DeviceOfflineRecord>lambdaQuery()
                        .eq(DeviceOfflineRecord::getDeviceId, deviceId)
                        .between(DeviceOfflineRecord::getOnlineDate, DateUtil.formatDateTime(startDate), DateUtil.formatDateTime(endDate))
                        .orderByAsc(DeviceOfflineRecord::getOnlineDate));

        double totalPowerConsumption = 0.0; // 总耗电量
        List<ElectricityData> electricityDataList = new ArrayList<>(); // 每日耗电数据
        // 如果没有上下线记录，则返回空结果
        if (CollectionUtil.isEmpty(deviceOfflineRecordList)) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("totalPowerConsumption", totalPowerConsumption);
            result.put("dailyElectricity", electricityDataList);
            result.put("deviceInfo", deviceInfo);
            return result;
        }
        // 将上下线记录按时间顺序处理，找到上线-下线对来计算运行时间
        List<Date> onlineTimes = new ArrayList<>(); // 存储上线时间
        List<Date> offlineTimes = new ArrayList<>(); // 存储下线时间
        for (DeviceOfflineRecord record : deviceOfflineRecordList) {
            Date recordTime = DateUtil.parseDateTime(record.getOnlineDate());
            if ("1".equals(record.getType())) { // 上线
                onlineTimes.add(recordTime);
            } else if ("0".equals(record.getType())) { // 下线
                offlineTimes.add(recordTime);
            }
        }
        // 匹配上线和下线时间对，计算运行时间
        int onlineIndex = 0;
        int offlineIndex = 0;

        while (onlineIndex < onlineTimes.size()) {
            Date onlineTime = onlineTimes.get(onlineIndex);
            // 找到下一个下线时间（必须在上线时间之后）
            Date offlineTime = null;
            while (offlineIndex < offlineTimes.size()) {
                Date potentialOfflineTime = offlineTimes.get(offlineIndex);
                if (potentialOfflineTime.after(onlineTime)) {
                    offlineTime = potentialOfflineTime;
                    offlineIndex++;
                    break;
                }
                offlineIndex++;
            }
            // 如果没有找到对应的下线时间，使用当前时间作为结束时间
            if (offlineTime == null) {
                offlineTime = new Date();
            }
            // 计算运行时间（小时）
            long runningTimeMillis = offlineTime.getTime() - onlineTime.getTime();
            double runningHours = runningTimeMillis / (1000.0 * 60 * 60); // 转换为小时
            // 计算该时段耗电量（功率单位：瓦特，转换为千瓦时）
            double powerConsumption = (deviceInfo.getPower() * runningHours) / 1000.0;
            totalPowerConsumption += powerConsumption;
            // 按日期统计耗电量
            String dateStr = DateUtil.formatDate(onlineTime);
            ElectricityData dailyData = electricityDataList.stream()
                    .filter(data -> data.getDate().equals(dateStr))
                    .findFirst()
                    .orElse(null);
            if (dailyData == null) {
                dailyData = new ElectricityData();
                dailyData.setDate(dateStr);
                dailyData.setConsumption(powerConsumption);
                electricityDataList.add(dailyData);
            } else {
                dailyData.setConsumption(dailyData.getConsumption() + powerConsumption);
            }
            onlineIndex++;
        }
        // 按日期排序
        electricityDataList.sort(Comparator.comparing(ElectricityData::getDate));
        // 构建返回结果
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("totalPowerConsumption", totalPowerConsumption);
        result.put("dailyElectricity", electricityDataList);
        result.put("deviceInfo", deviceInfo);
        result.put("periodStart", DateUtil.formatDate(startDate));
        result.put("periodEnd", DateUtil.formatDate(endDate));
        return result;
    }

    // 内部类用于存储每日用电量数据
    class ElectricityData {
        private String date;
        private double consumption;

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public double getConsumption() {
            return consumption;
        }

        public void setConsumption(double consumption) {
            this.consumption = consumption;
        }
    }

    /**
     * 预测设备用电
     *
     * @param deviceId 设备ID
     * @return 结果
     */
    @Override
    public LinkedHashMap<String, Object> queryDeviceElectricityFuture(Integer deviceId) {
        // 获取历史用电数据
        LinkedHashMap<String, Object> historyResult = queryDeviceElectricityHistory(deviceId);
        List<ElectricityData> dailyElectricity = (List<ElectricityData>) historyResult.get("dailyElectricity");
        DeviceInfo deviceInfo = (DeviceInfo) historyResult.get("deviceInfo");

        if (deviceInfo == null || CollectionUtil.isEmpty(dailyElectricity)) {
            return new LinkedHashMap<>();
        }

        // 准备历史数据用于预测
        double[] historicalConsumptions = dailyElectricity.stream()
                .mapToDouble(ElectricityData::getConsumption)
                .toArray();

        // 如果历史数据不足，无法进行有效预测
        if (historicalConsumptions.length < 7) {
            // 使用平均值进行简单预测
            double avgConsumption = Arrays.stream(historicalConsumptions).average().orElse(0.0);
            List<Double> futurePredictions = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                futurePredictions.add(avgConsumption);
            }

            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("historicalData", dailyElectricity);
            result.put("futurePredictions", futurePredictions);
            result.put("deviceInfo", deviceInfo);
            result.put("predictionPeriod", "未来30天");
            return result;
        }

        // 使用LSTM进行预测（这里提供框架，实际实现需要引入DL4J或其他ML库）
        double[] futurePredictions = predictUsingLSTM(historicalConsumptions, 30);

        // 构建未来30天的日期序列
        List<String> futureDates = new ArrayList<>();
        Date currentDate = new Date();
        for (int i = 1; i <= 30; i++) {
            Date futureDate = DateUtil.offsetDay(currentDate, i);
            futureDates.add(DateUtil.formatDate(futureDate));
        }

        // 创建预测结果
        List<ElectricityPredictionData> predictionList = new ArrayList<>();
        for (int i = 0; i < futurePredictions.length; i++) {
            ElectricityPredictionData prediction = new ElectricityPredictionData();
            prediction.setDate(futureDates.get(i));
            prediction.setPredictedConsumption(futurePredictions[i]);
            predictionList.add(prediction);
        }

        // 计算未来30天总预测用电量
        double totalFutureConsumption = Arrays.stream(futurePredictions).sum();

        // 构建返回结果
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("historicalData", dailyElectricity);
        result.put("futurePredictions", predictionList);
        result.put("totalFutureConsumption", totalFutureConsumption);
        result.put("deviceInfo", deviceInfo);
        result.put("predictionPeriod", "未来30天");
        result.put("confidence", calculateConfidence(historicalConsumptions)); // 添加置信度

        return result;
    }

    /**
     * 使用LSTM预测未来用电量
     * 这里提供框架，实际实现需要引入DL4J或其他机器学习库
     */
    private double[] predictUsingLSTM(double[] historicalConsumptions, int predictionDays) {
        try {
            // 数据预处理：归一化
            double[] normalizedData = normalizeData(historicalConsumptions);
            // 准备训练数据
            return simpleTrendBasedPrediction(normalizedData, predictionDays);

        } catch (Exception e) {
            // 如果LSTM预测失败，回退到简单预测方法
            System.err.println("LSTM预测失败，使用简单预测方法: " + e.getMessage());
            double[] lastValues = Arrays.copyOfRange(historicalConsumptions,
                    Math.max(0, historicalConsumptions.length - 7),
                    historicalConsumptions.length);
            double avgValue = Arrays.stream(lastValues).average().orElse(0.0);
            double[] fallbackPredictions = new double[predictionDays];
            Arrays.fill(fallbackPredictions, avgValue);
            return fallbackPredictions;
        }
    }

    /**
     * 归一化数据
     */
    private double[] normalizeData(double[] data) {
        if (data.length == 0) {
            return data;
        }

        double max = Arrays.stream(data).max().orElse(1.0);
        double min = Arrays.stream(data).min().orElse(0.0);

        if (max == min) {
            // 如果所有值相同，返回全零数组
            return Arrays.stream(data).map(x -> 0.0).toArray();
        }

        return Arrays.stream(data).map(x -> (x - min) / (max - min)).toArray();
    }

    /**
     * 反归一化数据
     */
    private double[] denormalizeData(double[] normalizedData, double originalMax, double originalMin) {
        return Arrays.stream(normalizedData).map(x -> x * (originalMax - originalMin) + originalMin).toArray();
    }

    /**
     * 基于趋势的简单预测（用于替代LSTM）
     */
    private double[] simpleTrendBasedPrediction(double[] historicalData, int predictionDays) {
        if (historicalData.length < 2) {
            // 如果数据不足，返回最后的值
            double[] result = new double[predictionDays];
            Arrays.fill(result, historicalData[historicalData.length - 1]);
            return result;
        }

        // 计算最近的趋势
        int recentPoints = Math.min(7, historicalData.length);
        double[] recentData = Arrays.copyOfRange(historicalData, historicalData.length - recentPoints, historicalData.length);

        // 计算简单线性趋势
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < recentData.length; i++) {
            sumX += i;
            sumY += recentData[i];
            sumXY += i * recentData[i];
            sumXX += i * i;
        }

        double n = recentData.length;
        double slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
        double intercept = (sumY - slope * sumX) / n;

        // 生成预测值
        double[] predictions = new double[predictionDays];
        for (int i = 0; i < predictionDays; i++) {
            predictions[i] = slope * (recentData.length + i) + intercept;
            // 确保预测值在合理范围内 [0, 1]
            predictions[i] = Math.max(0, Math.min(1, predictions[i]));
        }

        return predictions;
    }

    /**
     * 计算预测置信度
     */
    private double calculateConfidence(double[] historicalData) {
        if (historicalData.length < 2) {
            return 0.5; // 默认置信度
        }
        // 计算数据的标准差，标准差越小，预测越可靠
        double mean = Arrays.stream(historicalData).average().orElse(0.0);
        double variance = Arrays.stream(historicalData)
                .map(x -> Math.pow(x - mean, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);
        // 标准差越小，置信度越高
        // 使用经验公式将标准差映射到[0,1]区间
        double maxExpectedStdDev = 0.5; // 假设的最大标准差
        double confidence = Math.max(0, 1 - (stdDev / maxExpectedStdDev));
        return Math.min(1.0, confidence);
    }

    // 用于存储预测数据的内部类
    class ElectricityPredictionData {
        private String date;
        private double predictedConsumption;

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public double getPredictedConsumption() {
            return predictedConsumption;
        }

        public void setPredictedConsumption(double predictedConsumption) {
            this.predictedConsumption = predictedConsumption;
        }
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
     * @param deviceList     设备列表
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
     * @param deviceList    设备列表
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
