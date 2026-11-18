
package com.campustrade.service;

import com.campustrade.dto.ProductImageResponse;
import com.campustrade.exception.ProductNotFoundException;
import com.campustrade.exception.ProductStatusException;
import com.campustrade.mapper.ProductImageMapper;
import com.campustrade.mapper.ProductMapper;
import com.campustrade.model.Product;
import com.campustrade.model.ProductImage;
import com.campustrade.security.CurrentUserIdResolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProductImageService {

    private static final Logger log =
            LoggerFactory.getLogger(ProductImageService.class);

    private static final int MAX_IMAGES = 5;
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private final ProductMapper productMapper;
    private final ProductImageMapper imageMapper;
    private final CurrentUserIdResolver userIdResolver;
    private final Path storageRoot;

    public ProductImageService(
            ProductMapper productMapper,
            ProductImageMapper imageMapper,
            CurrentUserIdResolver userIdResolver,
            @Value("${campustrade.images.directory:./uploads/products}")
            String storageDirectory
    ) {
        this.productMapper = productMapper;
        this.imageMapper = imageMapper;
        this.userIdResolver = userIdResolver;
        this.storageRoot = Path.of(storageDirectory)
                .toAbsolutePath()
                .normalize();
    }

    @Transactional
    public ProductImageResponse upload(
            Long productId,
            MultipartFile file,
            Authentication authentication
    ) {
        Long sellerId = userIdResolver.requireCurrentUserId(
                authentication
        );

        // 同一商品的上传、删除通过商品行锁串行化。
        Product product = productMapper.findByIdForUpdate(productId);
        requireEditableOwner(product, productId, sellerId);

        byte[] bytes = readAndValidateFile(file);
        ImageFormat format = detectFormat(bytes);

        List<ProductImage> existing =
                imageMapper.findByProductId(productId);

        if (existing.size() >= MAX_IMAGES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "每件商品最多上传5张图片"
            );
        }

        int displayOrder = firstAvailableOrder(existing);

        String storageName =
                UUID.randomUUID() + format.extension();

        Path directory = storageRoot.resolve(
                productId.toString()
        );

        Path destination = directory.resolve(storageName);

        try {
            Files.createDirectories(directory);

            Files.write(
                    destination,
                    bytes,
                    StandardOpenOption.CREATE_NEW
            );
        } catch (IOException ex) {
            deleteFileQuietly(destination);

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "图片文件保存失败",
                    ex
            );
        }

        // 如果数据库事务最终回滚，也需要删除刚写入的文件。
        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                if (status != STATUS_COMMITTED) {
                                    deleteFileQuietly(destination);
                                }
                            }
                        }
                );

        ProductImage image = new ProductImage();
        image.setProductId(productId);
        image.setStorageName(storageName);
        image.setContentType(format.contentType());
        image.setDisplayOrder(displayOrder);

        int rows = imageMapper.insert(image);

        if (rows != 1 || image.getId() == null) {
            throw new IllegalStateException(
                    "图片记录创建失败"
            );
        }

        return toResponse(image);
    }

    @Transactional(readOnly = true)
    public List<ProductImageResponse> listImages(
            Long productId,
            Authentication authentication
    ) {
        requireReadableProduct(productId, authentication);

        List<ProductImage> images =
                imageMapper.findByProductId(productId);

        List<ProductImageResponse> responses =
                new ArrayList<>();

        for (ProductImage image : images) {
            responses.add(toResponse(image));
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public ImageContent readImage(
            Long productId,
            Long imageId,
            Authentication authentication
    ) {
        requireReadableProduct(productId, authentication);

        ProductImage image =
                imageMapper.findByProductAndImageId(
                        productId,
                        imageId
                );

        if (image == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "图片不存在"
            );
        }

        Path filePath = pathFor(image);

        try {
            if (!Files.isRegularFile(filePath)) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "图片文件不存在"
                );
            }

            return new ImageContent(
                    Files.readAllBytes(filePath),
                    image.getContentType()
            );
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "读取图片失败",
                    ex
            );
        }
    }

    @Transactional
    public void deleteImage(
            Long productId,
            Long imageId,
            Authentication authentication
    ) {
        Long sellerId = userIdResolver.requireCurrentUserId(
                authentication
        );

        Product product =
                productMapper.findByIdForUpdate(productId);

        requireEditableOwner(product, productId, sellerId);

        ProductImage image =
                imageMapper.findByProductAndImageId(
                        productId,
                        imageId
                );

        if (image == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "图片不存在"
            );
        }

        int rows = imageMapper.deleteById(
                productId,
                imageId
        );

        if (rows != 1) {
            throw new IllegalStateException(
                    "图片删除失败"
            );
        }

        Path filePath = pathFor(image);

        // 数据库提交成功后再删除文件。
        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                deleteFileQuietly(filePath);
                            }
                        }
                );
    }

    private void requireEditableOwner(
            Product product,
            Long productId,
            Long sellerId
    ) {
        if (product == null) {
            throw new ProductNotFoundException(productId);
        }

        if (!sellerId.equals(product.getSellerId())) {
            throw new AccessDeniedException(
                    "无权修改其他用户的商品图片"
            );
        }

        if (!"DRAFT".equals(product.getStatus())) {
            throw new ProductStatusException(
                    "只有草稿状态的商品允许修改图片"
            );
        }
    }

    private void requireReadableProduct(
            Long productId,
            Authentication authentication
    ) {
        Product product = productMapper.findById(productId);

        if (product == null) {
            throw new ProductNotFoundException(productId);
        }

        if ("PUBLISHED".equals(product.getStatus())) {
            return;
        }

        // 草稿图片仅允许所属卖家预览。
        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new ProductNotFoundException(productId);
        }

        Long currentUserId;

        try {
            currentUserId =
                    userIdResolver.requireCurrentUserId(
                            authentication
                    );
        } catch (AccessDeniedException ex) {
            throw new ProductNotFoundException(productId);
        }

        if (!currentUserId.equals(product.getSellerId())) {
            throw new ProductNotFoundException(productId);
        }
    }

    private byte[] readAndValidateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "请选择需要上传的图片"
            );
        }

        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "单张图片不能超过5MB"
            );
        }

        try {
            byte[] bytes = file.getBytes();

            if (bytes.length == 0
                    || bytes.length > MAX_IMAGE_BYTES) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "图片大小不符合要求"
                );
            }

            return bytes;
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "无法读取上传文件",
                    ex
            );
        }
    }

    private ImageFormat detectFormat(byte[] bytes) {
        if (bytes.length >= 3
                && unsigned(bytes[0]) == 0xFF
                && unsigned(bytes[1]) == 0xD8
                && unsigned(bytes[2]) == 0xFF) {
            return new ImageFormat(".jpg", "image/jpeg");
        }

        int[] pngSignature = {
                137, 80, 78, 71, 13, 10, 26, 10
        };

        if (bytes.length >= 8) {
            boolean png = true;

            for (int i = 0; i < pngSignature.length; i++) {
                if (unsigned(bytes[i]) != pngSignature[i]) {
                    png = false;
                    break;
                }
            }

            if (png) {
                return new ImageFormat(".png", "image/png");
            }
        }

        if (bytes.length >= 12
                && hasAscii(bytes, 0, "RIFF")
                && hasAscii(bytes, 8, "WEBP")) {
            return new ImageFormat(".webp", "image/webp");
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "只支持JPEG、PNG和WebP格式的图片"
        );
    }

    private boolean hasAscii(
            byte[] bytes,
            int offset,
            String expected
    ) {
        for (int i = 0; i < expected.length(); i++) {
            if ((char) bytes[offset + i]
                    != expected.charAt(i)) {
                return false;
            }
        }

        return true;
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    private int firstAvailableOrder(
            List<ProductImage> images
    ) {
        for (int candidate = 0;
                candidate < MAX_IMAGES;
                candidate++) {

            final int current = candidate;

            boolean used = images.stream()
                    .anyMatch(image ->
                            image.getDisplayOrder() != null
                            && image.getDisplayOrder() == current
                    );

            if (!used) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "没有可用的图片展示位置"
        );
    }

    private Path pathFor(ProductImage image) {
        Path productDirectory = storageRoot.resolve(
                image.getProductId().toString()
        );

        Path filePath = productDirectory
                .resolve(image.getStorageName())
                .normalize();

        if (!filePath.startsWith(productDirectory)) {
            throw new IllegalStateException(
                    "图片存储路径不合法"
            );
        }

        return filePath;
    }

    private ProductImageResponse toResponse(
            ProductImage image
    ) {
        String url = "/api/products/"
                + image.getProductId()
                + "/images/"
                + image.getId();

        return new ProductImageResponse(
                image.getId(),
                image.getProductId(),
                url,
                image.getDisplayOrder()
        );
    }

    private void deleteFileQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Unable to delete image file: {}", path, ex);
        }
    }

    private record ImageFormat(
            String extension,
            String contentType
    ) {
    }

    public record ImageContent(
            byte[] bytes,
            String contentType
    ) {
    }
}
