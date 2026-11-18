
CREATE TABLE product_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    storage_name VARCHAR(100) NOT NULL,
    content_type VARCHAR(30) NOT NULL,
    display_order INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT fk_product_images_product
        FOREIGN KEY (product_id)
        REFERENCES products(id),

    UNIQUE KEY uk_product_images_storage_name (storage_name),

    UNIQUE KEY uk_product_images_order (
        product_id,
        display_order
    ),

    INDEX idx_product_images_product_id (product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
