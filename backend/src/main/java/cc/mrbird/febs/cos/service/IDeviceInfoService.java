package cc.mrbird.febs.cos.service;

import cc.mrbird.febs.cos.entity.DeviceInfo;
import cc.mrbird.febs.cos.entity.SceneRecommendation;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.apache.ibatis.annotations.Param;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * 设备管理 service层
 *
 * @author FanK
 */
public interface IDeviceInfoService extends IService<DeviceInfo> {

    /**
     * 分页获取设备管理信息
     *
     * @param page       分页对象
     * @param deviceInfo 设备管理信息
     * @return 结果
     */
    IPage<LinkedHashMap<String, Object>> selectDevicePage(Page<DeviceInfo> page, DeviceInfo deviceInfo);

    /**
     * 查询用户设备用电率
     *
     * @param userId 用户ID
     * @return 用电率
     */
    LinkedHashMap<String, Object> queryElectricityRateByUser(Integer userId);

    /**
     * 查询设备电量历史
     *
     * @param deviceId 设备ID
     * @return 结果
     */
    LinkedHashMap<String, Object> queryDeviceElectricityHistory(Integer deviceId);

    /**
     * 预测设备用电
     *
     * @param deviceId 设备ID
     * @return 预测结果
     */
    LinkedHashMap<String, Object> queryDeviceElectricityFuture(Integer deviceId);

    /**
     * 场景事件处理
     *
     * @param eventId 事件ID
     */
    void eventCheck(Integer eventId);

    /**
     * 推荐场景
     *
     * @param userId 用户ID
     * @return 结果
     */
    List<SceneRecommendation> analyzeAndRecommend(Integer userId);
}
