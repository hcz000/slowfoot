package org.hcz.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.entity.user.Favorites;
import org.hcz.entity.user.FavoritesWork;
import org.hcz.exception.AppException;
import org.hcz.exception.ErrorCode;
import org.hcz.util.UserHolder;
import org.hcz.mapper.user.FavoritesMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.hcz.service.user.FavoritesService;
import org.hcz.service.user.FavoritesWorkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;



@Service
public class FavoritesServiceImpl extends ServiceImpl<FavoritesMapper, Favorites> implements FavoritesService {


    @Autowired
    private FavoritesWorkService favoritesWorkService;


    @Override
    @Transactional
    public void remove(Long id, Long userId) {

        // 不能删除默认收藏夹
        final Favorites favorites = getOne(new LambdaQueryWrapper<Favorites>().eq(Favorites::getId, id).eq(Favorites::getUserId, userId));
        if (favorites.getName().equals("默认收藏夹")){
            throw new AppException(ErrorCode.FORBIDDEN, "默认收藏夹不允许被删除");
        }

        final boolean result = remove(new LambdaQueryWrapper<Favorites>().eq(Favorites::getId, id).eq(Favorites::getUserId, userId));
        // 如果能删除成功说明是自己的收藏夹
        if (result){
            favoritesWorkService.remove(new LambdaQueryWrapper<FavoritesWork>()
                    .eq(FavoritesWork::getFavoritesId, id)
                    .eq(FavoritesWork::getUserId, userId));
        }else {
            throw new AppException(ErrorCode.FORBIDDEN, "无权删除其他用户的收藏夹");
        }
    }

    @Override
    public List<Favorites> listByUserId(Long userId) {
        // 查出收藏夹id
        final List<Favorites> favorites = list(new LambdaQueryWrapper<Favorites>().eq(Favorites::getUserId, userId));
        if (ObjectUtils.isEmpty(favorites)) return Collections.emptyList();
        // 根据收藏夹id获取对应数
        final List<Long> fIds = favorites.stream().map(Favorites::getId).collect(Collectors.toList());
        final Map<Long, Long> fMap = favoritesWorkService.list(new LambdaQueryWrapper<FavoritesWork>()
                        .in(FavoritesWork::getFavoritesId, fIds)
                        .eq(FavoritesWork::getUserId, userId))
                .stream().collect(Collectors.groupingBy(FavoritesWork::getFavoritesId,
                        Collectors.counting()));
        // 计算对应作品总数
        for (Favorites favorite : favorites) {
            final Long workCount = fMap.get(favorite.getId());
            favorite.setWorkCount(workCount == null ? 0 : workCount);
        }

        return favorites;
    }

    @Override
    public List<Long> listWorkIds(Long favoritesId, Long userId) {

        // 不直接返回中间表是为了隐私性 (当前没实现收藏夹公开功能)

        // 校验
        final Favorites favorites = getOne(new LambdaQueryWrapper<Favorites>().eq(Favorites::getId, favoritesId).eq(Favorites::getUserId, userId));
        if (favorites == null){
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "收藏夹不存在");
        }

        final List<Long> videoIds = favoritesWorkService.list(new LambdaQueryWrapper<FavoritesWork>()
                        .eq(FavoritesWork::getFavoritesId, favoritesId)
                        .eq(FavoritesWork::getUserId, userId))
                .stream().map(FavoritesWork::getWorkId).collect(Collectors.toList());

        return videoIds;
    }

    @Override
    public boolean favorites(Long fId, Long vId) {

        final Long userId = UserHolder.get();

        try {
            final FavoritesWork favoritesWork = new FavoritesWork();
            favoritesWork.setFavoritesId(fId);
            favoritesWork.setWorkId(vId);
            favoritesWork.setUserId(userId);
            favoritesWorkService.save(favoritesWork);

        }catch (DuplicateKeyException e){
            favoritesWorkService.remove(new LambdaQueryWrapper<FavoritesWork>().eq(FavoritesWork::getFavoritesId, fId)
                    .eq(FavoritesWork::getWorkId, vId).eq(FavoritesWork::getUserId, userId));
            return false;
        }
        return true;
    }

    @Override
    public Boolean favoritesState(Long videoId, Long userId) {
        if (userId == null) return false;
        return favoritesWorkService.count(new LambdaQueryWrapper<FavoritesWork>().eq(FavoritesWork::getWorkId,videoId).eq(FavoritesWork::getUserId,userId)) == 1;
    }

    @Override
    public void exist(Long userId, Long fId) {
        final long count = count(new LambdaQueryWrapper<Favorites>().eq(Favorites::getUserId, userId).eq(Favorites::getId, fId));
        if (count == 0){
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "收藏夹不存在或无权访问");
        }
    }
}
