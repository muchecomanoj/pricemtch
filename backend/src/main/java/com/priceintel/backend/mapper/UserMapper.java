package com.priceintel.backend.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.priceintel.backend.dto.request.UpdateUserRequest;
import com.priceintel.backend.dto.response.UserResponse;
import com.priceintel.backend.entity.User;

/**
 * MapStruct mapper for {@link User}.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "role", expression = "java(resolveRole(user))")
    @Mapping(target = "account", ignore = true)   // set by the auth service, not from the user row
    UserResponse toResponse(User user);

    /** SUPER_ADMIN for the platform owner, otherwise the tenant access type. */
    default String resolveRole(User user) {
        if (user.isSuperAdmin()) {
            return "SUPER_ADMIN";
        }
        return user.getAccessType() != null ? user.getAccessType().name() : null;
    }

    /** Applies non-null fields from the update request onto an existing user. */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "superAdmin", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "status", ignore = true)
    void updateUserFromRequest(UpdateUserRequest request, @MappingTarget User user);
}
