/**
 * Copyright (c) 2013-2022 Contributors to the Eclipse Foundation
 *
 * <p> See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
 * available at http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package org.locationtech.geowave.core.store.query.gwql.function.operator;

import org.locationtech.geowave.core.store.query.filter.expression.Expression;
import org.locationtech.geowave.core.store.query.filter.expression.Predicate;
import org.locationtech.geowave.core.store.query.gwql.QLFunction;

public interface OperatorFunction extends QLFunction<Boolean> {

  @Override
  default Class<Boolean> getReturnType() {
    return Boolean.class;
  }

  Predicate create(Expression<?> expression1, Expression<?> expression2);

}
