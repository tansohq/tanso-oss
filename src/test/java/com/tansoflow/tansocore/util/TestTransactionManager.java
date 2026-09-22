/*
 * Tanso Core - open-source B2B SaaS monetization engine
 * Copyright (C) 2026  Douglas Baek
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.tansoflow.tansocore.util;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A transaction manager with no database behind it. It opens real Spring transactions, so
 * TransactionSynchronizationManager.isActualTransactionActive() tells a unit test whether code runs inside one,
 * and it counts commits and rollbacks.
 */
public class TestTransactionManager extends AbstractPlatformTransactionManager {
    private int commits;
    private int rollbacks;

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    // A REQUIRED call inside an open transaction joins it, as it would against a real database.
    @Override
    protected boolean isExistingTransaction(Object transaction) {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        commits++;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        rollbacks++;
    }

    // An inner transaction that fails marks the outer one for rollback; the outer rollback is what gets counted.
    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) {
    }

    public int commits() {
        return commits;
    }

    public int rollbacks() {
        return rollbacks;
    }
}
