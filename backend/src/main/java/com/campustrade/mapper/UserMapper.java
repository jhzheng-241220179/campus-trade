package com.campustrade.mapper;
import com.campustrade.model.User;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface UserMapper {
    @Select("SELECT * FROM users WHERE email = #{email}")
    User findByEmail(String email);

    @Select("SELECT COUNT(*) FROM users WHERE id = #{id}")
    int countById(Long id);
    @Insert("""
    INSERT INTO users (
        email,
        password_hash,
        email_verified,
        nickname,
        role,
        status
    )
    VALUES (
        #{email},
        #{passwordHash},
        #{emailVerified},
        #{nickname},
        #{role},
        #{status}
    )
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);
}
