
package com.campustrade.controller;

import com.campustrade.common.ApiResponse;
import com.campustrade.dto.ProductImageResponse;
import com.campustrade.service.ProductImageService;
import com.campustrade.service.ProductImageService.ImageContent;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/products/{productId}/images")
public class ProductImageController {

    private final ProductImageService imageService;

    public ProductImageController(
            ProductImageService imageService
    ) {
        this.imageService = imageService;
    }

    @GetMapping
    public ApiResponse<List<ProductImageResponse>> listImages(
            @PathVariable Long productId,
            Authentication authentication
    ) {
        return ApiResponse.success(
                imageService.listImages(productId, authentication)
        );
    }

    @GetMapping("/{imageId}")
    public ResponseEntity<byte[]> getImage(
            @PathVariable Long productId,
            @PathVariable Long imageId,
            Authentication authentication
    ) {
        ImageContent image = imageService.readImage(
                productId,
                imageId,
                authentication
        );

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                image.contentType()
                        )
                )
                .header(
                        "X-Content-Type-Options",
                        "nosniff"
                )
                .cacheControl(CacheControl.noStore())
                .body(image.bytes());
    }

    @PostMapping
    public ApiResponse<ProductImageResponse> uploadImage(
            @PathVariable Long productId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        ProductImageResponse uploaded = imageService.upload(
                productId,
                file,
                authentication
        );

        return ApiResponse.success(uploaded);
    }

    @DeleteMapping("/{imageId}")
    public ApiResponse<Void> deleteImage(
            @PathVariable Long productId,
            @PathVariable Long imageId,
            Authentication authentication
    ) {
        imageService.deleteImage(
                productId,
                imageId,
                authentication
        );

        return ApiResponse.success(null);
    }
}
