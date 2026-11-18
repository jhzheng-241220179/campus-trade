
package com.campustrade.mapper;

import com.campustrade.model.Product;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductMapper {

    @Select("SELECT * FROM products ORDER BY id")
    List<Product> findAll();

    @Select("""
            SELECT *
            FROM products
            WHERE status = 'PUBLISHED'
            ORDER BY id
            """)
    List<Product> findPublished();

    @Select("""
            SELECT *
            FROM products
            WHERE id = #{id}
            """)
    Product findById(Long id);

    @Select("""
            SELECT *
            FROM products
            WHERE id = #{id}
            FOR UPDATE
            """)
    Product findByIdForUpdate(Long id);

    @Select("""
            SELECT *
            FROM products
            WHERE seller_id = #{sellerId}
            ORDER BY created_at DESC, id DESC
            """)
    List<Product> findBySellerId(Long sellerId);

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

    @Update("""
            UPDATE products
            SET title = #{title},
                price = #{price},
                category = #{category},
                description = #{description},
                condition_level = #{conditionLevel},
                contact = #{contact},
                version = version + 1
            WHERE id = #{id}
              AND status = 'DRAFT'
              AND version = #{version}
            """)
    int update(Product product);

    @Update("""
            UPDATE products
            SET title = #{product.title},
                price = #{product.price},
                category = #{product.category},
                description = #{product.description},
                condition_level = #{product.conditionLevel},
                contact = #{product.contact},
                version = version + 1
            WHERE id = #{product.id}
              AND seller_id = #{sellerId}
              AND status = 'DRAFT'
              AND version = #{product.version}
            """)
    int updateOwnedDraft(
            @Param("product") Product product,
            @Param("sellerId") Long sellerId
    );

    @Update("""
            UPDATE products
            SET status = 'PUBLISHED',
                version = version + 1
            WHERE id = #{id}
              AND seller_id = #{sellerId}
              AND status = 'DRAFT'
              AND version = #{version}
            """)
    int publishOwnedDraft(
            @Param("id") Long id,
            @Param("sellerId") Long sellerId,
            @Param("version") Integer version
    );
}
