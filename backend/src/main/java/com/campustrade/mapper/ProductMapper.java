package com.campustrade.mapper;

import com.campustrade.model.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductMapper {

    @Select("SELECT * FROM products ORDER BY id")
    List<Product> findAll();
}