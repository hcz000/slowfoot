package org.hcz.mapper.history;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.hcz.entity.history.SearchHistory;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface SearchHistoryMapper extends BaseMapper<SearchHistory> {

    @Insert("INSERT INTO user_search_history(user_id, keyword, searched_at) VALUES(#{userId}, #{keyword}, #{searchedAt}) "
            + "ON DUPLICATE KEY UPDATE searched_at = VALUES(searched_at)")
    int saveOrTouch(@Param("userId") Long userId,
                    @Param("keyword") String keyword,
                    @Param("searchedAt") Date searchedAt);

    @Select("SELECT keyword FROM user_search_history WHERE user_id = #{userId} "
            + "ORDER BY searched_at DESC LIMIT #{limit}")
    List<String> listRecentKeywords(@Param("userId") Long userId, @Param("limit") int limit);

    @Delete("DELETE FROM user_search_history WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
