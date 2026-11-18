
package com.campustrade.mapper;

import com.campustrade.model.ProductImage;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductImageMapper {

    @Select("""
            SELECT *
            FROM product_images
            WHERE product_id = #{productId}
            ORDER BY display_order ASC, id ASC
            """)
    @Results(id = "imageResult", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "product_id", property = "productId"),
            @Result(column = "storage_name", property = "storageName"),
            @Result(column = "content_type", property = "contentType"),
            @Result(column = "display_order", property = "displayOrder"),
            @Result(column = "created_at", property = "createdAt")
    })
    List<ProductImage> findByProductId(Long productId);

    @Select("""
            SELECT *
            FROM product_images
            WHERE id = #{imageId}
              AND product_id = #{productId}
            """)
    @org.apache.ibatis.annotations.ResultMap("imageResult")
    ProductImage findByProductAndImageId(
            @Param("productId") Long productId,
            @Param("imageId") Long imageId
    );

    @Insert("""
            INSERT INTO product_images (
                product_id,
                storage_name,
                content_type,
                display_order
            )
            VALUES (
                #{productId},
                #{storageName},
                #{contentType},
                #{displayOrder}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProductImage image);

    @Delete("""
            DELETE FROM product_images
            WHERE id = #{imageId}
              AND product_id = #{productId}
            """)
    int deleteById(
            @Param("productId") Long productId,
            @Param("imageId") Long imageId
    );
}
