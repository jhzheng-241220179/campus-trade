package com.campustrade.mapper;

import com.campustrade.model.Product;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductMapper {

    @Select("SELECT * FROM products ORDER BY id")
    List<Product> findAll();
    @Select("SELECT * FROM products WHERE status = 'PUBLISHED' ORDER BY id")
    List<Product> findPublished();
    @Select("SELECT * FROM products WHERE id = #{id}")
    Product findById(Long id);
    @Insert("""
        INSERT INTO products (
            seller_id,
            title,
            price,
            category,
            description,
            condition_level,
            contact
        )
        VALUES (
            #{sellerId},
            #{title},
            #{price},
            #{category},
            #{description},
            #{conditionLevel},
            #{contact}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Product product);
}