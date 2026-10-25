package com.campustrade.mapper;
import com.campustrade.model.User;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface UserMapper {
    @Select("SELECT * FROM users WHERE email = #{email}")
    User findByEmail(String email);

    @Select("SELECT COUNT(*) FROM users WHERE id = #{id}")
    int countById(Long id);
}