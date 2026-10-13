package com.campustrade.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long id) {
        super("商品不存在,ID:" + id);
    }
}