package org.hcz.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.hcz.user.entity.User;
import org.hcz.admin.dto.AssignRoleVO;
import org.hcz.admin.dto.AuthorityVO;
import org.hcz.common.vo.BasePage;
import org.hcz.file.dto.CloudFileInfo;
import org.hcz.comment.dto.CommentAuditRequest;
import org.hcz.comment.dto.CommentCreateRequest;
import org.hcz.auth.dto.FindPWVO;
import org.hcz.user.dto.FollowVO;
import org.hcz.work.dto.ImageWorkPublishRequest;
import org.hcz.auth.dto.LoginVO;
import org.hcz.common.vo.Model;
import org.hcz.user.dto.ModelVO;
import org.hcz.file.dto.OssUploadTicket;
import org.hcz.auth.dto.RefreshTokenVO;
import org.hcz.auth.dto.RegisterVO;
import org.hcz.work.dto.ShareResult;
import org.hcz.work.dto.TextWorkPublishRequest;
import org.hcz.user.dto.UpdateUserVO;
import org.hcz.user.dto.UserModel;
import org.hcz.user.dto.UserVO;
import org.hcz.work.dto.VideoWorkPublishRequest;
import org.hcz.audit.dto.WorkAuditRequest;
import org.hcz.audit.dto.WorkAuditVO;
import org.hcz.comment.dto.WorkCommentVO;
import org.hcz.work.dto.WorkRankItem;
import org.hcz.work.dto.WorkStatistics;
import org.hcz.work.dto.WorkVO;
import org.hcz.work.entity.WorkCategory;

import java.util.Collection;
import java.util.List;
import java.util.Set;



public interface UserService extends IService<User> {


// 注册
// @param registerVO
// @return
// @throws Exception

    boolean register(RegisterVO registerVO) throws Exception;


// 获取用户信息:
// 1.用户基本信息
// 2.关注数量
// 3.粉丝数量
// @param userId 用户id
// @return

    UserVO getInfo(Long userId);


// 获取关注
// @param userId
// @param basePage
// @return

    Page<User> getFollows(Long userId, BasePage basePage);


// 获取粉丝
// @param userId
// @param basePage
// @return

    Page<User> getFans(Long userId, BasePage basePage);


// 获取用户基本信息
// @param userIds
// @return

    List<User> list(Collection<Long> userIds);


// 订阅分类
// @param typeIds

    void subscribe(Set<Long> typeIds);


// 获取订阅分类
// @param userId
// @return

    Collection<WorkCategory> listSubscribeType(Long userId);


// 关注/取关
// @param followsUserId
// @return

    boolean follows(Long followsUserId);


// 修改用户模型
// @param userModel

    void updateUserModel(UserModel userModel);


// 找回密码
// @param findPWVO
// @return

    Boolean findPassword(FindPWVO findPWVO);


// 修改用户资料
// @param user

    void updateUser(UpdateUserVO user);


// 获取用户搜索记录
// @return 搜索的值

    Collection<String> searchHistory(Long userId);


// 添加搜索记录
// @param userId
// @param search

    void addSearchHistory(Long userId, String search);


// 删除搜索记录
// @param userId

    void deleteSearchHistory(Long userId);


    Collection<WorkCategory> listNoSubscribeType(Long aLong);
}
