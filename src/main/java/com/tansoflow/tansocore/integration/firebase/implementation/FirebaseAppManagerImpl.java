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
package com.tansoflow.tansocore.integration.firebase.implementation;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.tansoflow.tansocore.integration.firebase.FirebaseAppManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class FirebaseAppManagerImpl implements FirebaseAppManager {

    private final ConcurrentMap<String, FirebaseApp> firebaseApps = new ConcurrentHashMap<>();

    @Override
    public Firestore getFirestoreForTenant(String accountId, byte[] jsonCredentials) {
        FirebaseApp app = firebaseApps.computeIfAbsent(accountId, id -> {
            try {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(jsonCredentials)))
                        .build();
                return FirebaseApp.initializeApp(options, id);
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize Firebase for " + id, e);
            }
        });
        return FirestoreClient.getFirestore(app);
    }

    @Override
    public void invalidateApp(String accountId) {
        FirebaseApp app = firebaseApps.remove(accountId);
        if (app != null) app.delete();
    }
}
