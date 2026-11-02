package com.campustrade.mapper;

import com.campustrade.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNull;
@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void findByEmail_shouldReturnNullWhenNotFound() {

        User user = userMapper.findByEmail(
                "nonexistent-user@example.invalid"
        );

        assertNull(user);
    }
}