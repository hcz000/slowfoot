package org.hcz.recommendation.service;

import org.hcz.work.entity.Work;
import org.hcz.user.entity.User;
import org.hcz.user.dto.UserModel;

import java.util.Collection;
import java.util.List;



public interface InterestPushService {


// 推入标签库
// 传 workId,typeId
// @param work

    void pushSystemStockIn(Work work);

    void pushSystemStockInSync(Work work);


// 添加分类库,用于后续随机推送分类作品
// @param work

    void pushSystemTypeStockIn(Work work);

    void pushSystemTypeStockInSync(Work work);


// 根据分类随机推送作品
// @param typeId
// @return

    Collection<Long> listWorkIdByTypeId(Long typeId);


// 删除标签内作品
// @param work

    void deleteSystemStockIn(Work work);

    void deleteSystemStockInSync(Work work);


// 用户初始化模型 -> 订阅分类
// @param userId 用户id
// @param labels 标签名

    void initUserModel(Long userId, List<String> labels);


// 用户模型修改概率 : 可分批次发送
// 修改场景:
// 1.观看浏览量到达总时长1/5  +1概率
// 2.观看浏览量未到总时长1/5 -0.5概率
// 3.点赞作品  +2概率
// 4.收藏作品  +3概率

    void updateUserModel(UserModel userModel);

    void updateUserModel(UserModel userModel, String eventId);


// 用于给用户推送作品 -> 兴趣推送
// 推送 X 作品,包含一条和性别有关
// @param user 传id和sex
// @return videoIds

    Collection<Long> listWorkIdByUserModel(User user);


// 根据标签获取作品id
// @param labelNames
// @return

    Collection<Long> listWorkIdByLabels(List<String> labelNames);


// 删除分类库中的作品
// @param work

    void deleteSystemTypeStockIn(Work work);

    void deleteSystemTypeStockInSync(Work work);
}
