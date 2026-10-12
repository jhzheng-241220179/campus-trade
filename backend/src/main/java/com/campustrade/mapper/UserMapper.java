package com.campustrade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("SELECT COUNT(*) FROM users WHERE id = #{id}")
    int countById(Long id);
}