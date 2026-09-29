package com.priceintel.backend.mapper;

import java.util.List;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.priceintel.backend.dto.request.UpdateProductRequest;
import com.priceintel.backend.dto.response.ProductAttributeResponse;
import com.priceintel.backend.dto.response.ProductIdentifierResponse;
import com.priceintel.backend.dto.response.ProductImageResponse;
import com.priceintel.backend.dto.response.ProductResponse;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.entity.ProductAttribute;
import com.priceintel.backend.entity.ProductIdentifier;
import com.priceintel.backend.entity.ProductImage;

/**
 * MapStruct mapper for products and their child records. MapStruct wires the
 * nested list mappings automatically using the element methods below.
 */
@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "category", source = "category.name")
    ProductResponse toResponse(Product product);

    ProductIdentifierResponse toIdentifierResponse(ProductIdentifier identifier);

    ProductImageResponse toImageResponse(ProductImage image);

    ProductAttributeResponse toAttributeResponse(ProductAttribute attribute);

    List<ProductIdentifierResponse> toIdentifierResponses(List<ProductIdentifier> identifiers);

    List<ProductImageResponse> toImageResponses(List<ProductImage> images);

    List<ProductAttributeResponse> toAttributeResponses(List<ProductAttribute> attributes);

    /**
     * Partial scalar update. Collections, category, sku and id are handled in
     * the service, so they are ignored here.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sku", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "identifiers", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "attributes", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    void updateProductFromRequest(UpdateProductRequest request, @MappingTarget Product product);
}
