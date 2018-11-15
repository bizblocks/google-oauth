package com.groupstp.googleoauth.data;

import com.haulmont.chile.core.datatypes.impl.EnumClass;

import javax.annotation.Nullable;

/**
 * Google login type
 */
public enum OAuth2ResponseType implements EnumClass<String> {
    CODE("code"),
    TOKEN("token"),
    CODE_TOKEN("code%20token");

    private final String id;

    OAuth2ResponseType(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static OAuth2ResponseType fromId(String id) {
        if (id != null) {
            for (OAuth2ResponseType i : values()) {
                if (i.getId().equalsIgnoreCase(id)) {
                    return i;
                }
            }
        }
        return null;
    }
}