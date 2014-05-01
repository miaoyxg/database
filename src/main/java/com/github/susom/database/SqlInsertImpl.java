/*
 * Copyright 2014 The Board of Trustees of The Leland Stanford Junior University.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.susom.database;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the key class for configuring (query parameters) and executing a database query.
 *
 * @author garricko
 */
public class SqlInsertImpl implements SqlInsert {
  private static final Logger log = LoggerFactory.getLogger(SqlInsertImpl.class);
  private static final Object[] ZERO_LENGTH_OBJECT_ARRAY = new Object[0];
  private final Connection connection;
  private final StatementAdaptor adaptor;
  private final String sql;
  private final LogOptions logOptions;
  private List<Object> parameterList;       // !null ==> traditional ? args
  private Map<String, Object> parameterMap; // !null ==> named :abc args

  public SqlInsertImpl(Connection connection, String sql, LogOptions logOptions) {
    this.connection = connection;
    this.sql = sql;
    this.logOptions = logOptions;
    adaptor = new StatementAdaptor();
  }

  @Override
  public SqlInsert argInteger(Integer arg) {
    return positionalArg(adaptor.nullNumeric(arg));
  }

  @Override
  public SqlInsert argInteger(String argName, Integer arg) {
    return namedArg(argName, adaptor.nullNumeric(arg));
  }

  @Override
  public SqlInsert argLong(Long arg) {
    return positionalArg(adaptor.nullNumeric(arg));
  }

  @Override
  public SqlInsert argLong(String argName, Long arg) {
    return namedArg(argName, adaptor.nullNumeric(arg));
  }

  @Override
  public SqlInsert argFloat(Float arg) {
    return positionalArg(adaptor.nullNumeric(arg));
  }

  @Override
  public SqlInsert argFloat(String argName, Float arg) {
    return namedArg(argName, adaptor.nullNumeric(arg));
  }

  @Override
  public SqlInsert argDouble(Double arg) {
    return positionalArg(adaptor.nullNumeric(arg));
  }

  @Override
  public SqlInsert argDouble(String argName, Double arg) {
    return namedArg(argName, adaptor.nullNumeric(arg));
  }

  @Override
  public SqlInsert argBigDecimal(BigDecimal arg) {
    return positionalArg(adaptor.nullNumeric(arg));
  }

  @Override
  public SqlInsert argBigDecimal(String argName, BigDecimal arg) {
    return namedArg(argName, adaptor.nullNumeric(arg));
  }

  @Override
  public SqlInsert argString(String arg) {
    return positionalArg(adaptor.nullString(arg));
  }

  @Override
  public SqlInsert argString(String argName, String arg) {
    return namedArg(argName, adaptor.nullString(arg));
  }

  @Override
  public SqlInsert argDate(Date arg) {
    return positionalArg(adaptor.nullDate(arg));
  }

  @Override
  public SqlInsert argDate(String argName, Date arg) {
    return namedArg(argName, adaptor.nullDate(arg));
  }

  @Override
  public SqlInsert argBlobBytes(byte[] arg) {
    return positionalArg(adaptor.nullBytes(arg));
  }

  @Override
  public SqlInsert argBlobBytes(String argName, byte[] arg) {
    return namedArg(argName, adaptor.nullBytes(arg));
  }

  @Override
  public SqlInsert argBlobStream(InputStream arg) {
    return positionalArg(adaptor.nullInputStream(arg));
  }

  @Override
  public SqlInsert argBlobStream(String argName, InputStream arg) {
    return namedArg(argName, adaptor.nullInputStream(arg));
  }

  @Override
  public SqlInsert argClobString(String arg) {
    return positionalArg(adaptor.nullClobReader(arg == null ? null : new StringReader(arg)));
  }

  @Override
  public SqlInsert argClobString(String argName, String arg) {
    return namedArg(argName, adaptor.nullClobReader(arg == null ? null : new StringReader(arg)));
  }

  @Override
  public SqlInsert argClobReader(Reader arg) {
    return positionalArg(adaptor.nullClobReader(arg));
  }

  @Override
  public SqlInsert argClobReader(String argName, Reader arg) {
    return namedArg(argName, adaptor.nullClobReader(arg));
  }

  @Override
  public int insert() {
    return updateInternal(0);
  }

  @Override
  public void insert(int expectedRowsUpdated) {
    updateInternal(expectedRowsUpdated);
  }

  private int updateInternal(int expectedNumAffectedRows) {
    PreparedStatement ps = null;
    Metric metric = new Metric(log.isDebugEnabled());

    String executeSql;
    Object[] parameters = ZERO_LENGTH_OBJECT_ARRAY;
    if (parameterMap != null && parameterMap.size() > 0) {
      NamedParameterSql paramSql = new NamedParameterSql(sql);
      executeSql = paramSql.getSqlToExecute();
      parameters = paramSql.toArgs(parameterMap);
    } else {
      executeSql = sql;
      if (parameterList != null) {
        parameters = parameterList.toArray(new Object[parameterList.size()]);
      }
    }

    boolean isSuccess = false;
    String errorCode = null;
    try {
      ps = connection.prepareStatement(executeSql);

      adaptor.addParameters(ps, parameters);
      metric.checkpoint("prep");
      int numAffectedRows = ps.executeUpdate();
      metric.checkpoint("exec");
      if (expectedNumAffectedRows > 0 && numAffectedRows != expectedNumAffectedRows) {
        errorCode = logOptions.generateErrorCode();
        throw new WrongNumberOfRowsException("The number of affected rows was " + numAffectedRows + ", but "
            + expectedNumAffectedRows + " were expected." + "\n"
            + DebugSql.exceptionMessage(executeSql, parameters, errorCode, logOptions));
      }
      isSuccess = true;
      return numAffectedRows;
    } catch (Exception e) {
      throw new DatabaseException(DebugSql.exceptionMessage(executeSql, parameters, errorCode, logOptions), e);
    } finally {
      adaptor.closeQuietly(ps, log);
      metric.done("close");
      if (isSuccess) {
        DebugSql.logSuccess("Insert", log, metric, executeSql, parameters, logOptions);
      } else {
        DebugSql.logError("Insert", log, metric, errorCode, executeSql, parameters, logOptions);
      }
    }
  }

  private SqlInsert positionalArg(Object arg) {
    if (parameterMap != null) {
      throw new DatabaseException("Use either positional or named query parameters, not both");
    }
    if (parameterList == null) {
      parameterList = new ArrayList<>();
    }
    parameterList.add(arg);
    return this;
  }

  private SqlInsert namedArg(String argName, Object arg) {
    if (parameterList != null) {
      throw new DatabaseException("Use either positional or named query parameters, not both");
    }
    if (parameterMap == null) {
      parameterMap = new HashMap<>();
    }
    if (argName.startsWith(":")) {
      argName = argName.substring(1);
    }
    parameterMap.put(argName, arg);
    return this;
  }
}