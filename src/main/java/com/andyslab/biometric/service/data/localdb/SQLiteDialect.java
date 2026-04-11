package com.andyslab.biometric.service.data.localdb;

import java.sql.Types;

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.identity.IdentityColumnSupportImpl;

///
/// Because Spring Boot guesses the database dialect based on JDBC URL, but SQLite
/// doesn't have an official Hibernate dialect, this class provides a custom dialect
/// to tweak some of the SQL generation rules.
/// 
/// This isn’t a complete set of overrides, but it covers the basics for common 
// read/write applications.
/// 

public class SQLiteDialect extends Dialect {
    public SQLiteDialect() {
        registerColumnType(Types.INTEGER, "integer");
        registerColumnType(Types.VARCHAR, "text");
        registerColumnType(Types.BLOB, "blob");
        registerColumnType(Types.BOOLEAN, "integer");
        registerColumnType(Types.TIMESTAMP, "datetime");
    }

    @Override
    public boolean supportsIdentityColumns() {
        return true;
    }

    @Override
    public IdentityColumnSupportImpl getIdentityColumnSupport() {
        return new IdentityColumnSupportImpl(this);
    }

    @Override
    public String getIdentitySelectString(String table, String column, int type) {
        return "select last_insert_rowid()";
    }

    @Override
    public String getLimitString(String query, boolean hasOffset) {
        return query + (hasOffset ? " limit ? offset ?" : " limit ?");
    }

    @Override
    public boolean supportsCurrentTimestampSelection() {
        return true;
    }

    @Override
    public String getCurrentTimestampSelectString() {
        return "select current_timestamp";
    }

    @Override
    public boolean hasAlterTable() {
        return false;
    }
}
