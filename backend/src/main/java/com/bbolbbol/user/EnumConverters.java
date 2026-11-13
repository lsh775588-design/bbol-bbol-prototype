package com.bbolbbol.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * enum을 항상 평범한 VARCHAR로 저장한다(Hibernate 6의 MySQL 네이티브 ENUM 매핑과 스키마 검증 차이를 피한다).
 */
final class EnumConverters {

    private EnumConverters() {}

    @Converter
    static class UserStatusConverter implements AttributeConverter<UserStatus, String> {
        @Override
        public String convertToDatabaseColumn(UserStatus attribute) {
            return attribute == null ? null : attribute.name();
        }

        @Override
        public UserStatus convertToEntityAttribute(String dbData) {
            return dbData == null ? null : UserStatus.valueOf(dbData);
        }
    }

    @Converter
    static class LoginTypeConverter implements AttributeConverter<LoginType, String> {
        @Override
        public String convertToDatabaseColumn(LoginType attribute) {
            return attribute == null ? null : attribute.name();
        }

        @Override
        public LoginType convertToEntityAttribute(String dbData) {
            return dbData == null ? null : LoginType.valueOf(dbData);
        }
    }
}
