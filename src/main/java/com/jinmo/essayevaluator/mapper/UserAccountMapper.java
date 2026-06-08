package com.jinmo.essayevaluator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinmo.essayevaluator.domain.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
}
